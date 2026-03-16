"""
CT 头部平扫质控多任务模型训练脚本。

训练思路：
1. 使用原始头部 CT 平扫 NIfTI 作为 clean base volume
2. 按前端 6 个质控项，合成对应的缺陷样本
3. 用 clean / defect 伪标签训练 3D 多任务模型

说明：
- 当前数据集没有人工标注，也没有 DICOM 剂量标签
- 因此“剂量控制 (CTDI)”任务使用低剂量噪声外观作为代理监督信号
"""

from __future__ import annotations

import json
import math
import random
from dataclasses import asdict, dataclass
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torch.amp import GradScaler, autocast
from torch.utils.data import DataLoader, Dataset

from model import (
    CT_DATASET_DIR,
    MODEL_WEIGHTS_PATH,
    QC_TASK_COUNT,
    QC_TASK_INDEX,
    QC_TASK_SPECS,
    RESULTS_DIR,
    TARGET_SHAPE,
    HeadCtPlainQcModel,
    ensure_output_directories,
    load_preprocessed_volume,
    task_specs_as_dicts,
)


SEED = 42
VAL_RATIO = 0.2
BATCH_SIZE = 4
EPOCHS = 10
LEARNING_RATE = 1e-3
WEIGHT_DECAY = 1e-4
TRAIN_VARIANTS_PER_VOLUME = 6
VAL_VARIANTS_PER_VOLUME = 3
POSITIONING_OFFSET_THRESHOLD = 6.0
MIN_COVERAGE_SLICE_RATIO = 0.68


@dataclass(frozen=True)
class VariantPlan:
    """单个合成样本的生成计划。"""

    volume_id: str
    volume_path: str
    variant_index: int
    failed_task_keys: tuple[str, ...]
    seed: int


class SyntheticHeadCtQcDataset(Dataset):
    """
    基于 clean CT 体数据和合成缺陷的多任务数据集。

    每个样本返回：
    - `volume_tensor`: shape=(1, D, H, W)
    - `labels_tensor`: shape=(6,)；1 表示该质控项不合格
    """

    def __init__(self, base_volumes: dict[str, np.ndarray], plans: list[VariantPlan]) -> None:
        self.base_volumes = base_volumes
        self.plans = plans

    def __len__(self) -> int:
        return len(self.plans)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
        plan = self.plans[index]
        rng = np.random.default_rng(plan.seed)
        volume = np.copy(self.base_volumes[plan.volume_id])

        # 所有样本都加轻微亮度/对比度扰动，避免模型记住固定灰度分布。
        volume = apply_benign_intensity_jitter(volume, rng)

        labels = np.zeros(QC_TASK_COUNT, dtype=np.float32)
        selected_failures = set(plan.failed_task_keys)
        for task_key in selected_failures:
            if task_key == "coverage":
                volume = apply_coverage_failure(volume, rng)
            elif task_key == "positioning":
                volume = apply_positioning_failure(volume, rng)
            elif task_key == "motion":
                volume = apply_motion_failure(volume, rng)
            elif task_key == "metal":
                volume = apply_metal_failure(volume, rng)
            elif task_key == "slice_thickness":
                volume = apply_slice_thickness_failure(volume, rng)
            elif task_key == "dose_proxy":
                volume = apply_dose_proxy_failure(volume, rng)

        # 覆盖范围和体位使用最终体数据启发式重算标签，避免 clean 原始数据被错误视为全合格。
        labels[QC_TASK_INDEX["coverage"]] = 1.0 if estimate_coverage_failure(volume) else 0.0
        labels[QC_TASK_INDEX["positioning"]] = 1.0 if estimate_positioning_failure(volume) else 0.0
        labels[QC_TASK_INDEX["motion"]] = 1.0 if "motion" in selected_failures else 0.0
        labels[QC_TASK_INDEX["metal"]] = 1.0 if "metal" in selected_failures else 0.0
        labels[QC_TASK_INDEX["slice_thickness"]] = 1.0 if "slice_thickness" in selected_failures else 0.0
        labels[QC_TASK_INDEX["dose_proxy"]] = 1.0 if "dose_proxy" in selected_failures else 0.0

        volume_tensor = torch.from_numpy(volume[None, ...]).float()
        labels_tensor = torch.from_numpy(labels).float()
        return volume_tensor, labels_tensor


def set_seed(seed: int) -> None:
    """固定随机种子，保证训练和验证可复现。"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = False
        torch.backends.cudnn.benchmark = True


def natural_key(path: Path) -> tuple[object, ...]:
    """按数字顺序排序 `0.nii.gz`、`1.nii.gz` 这类文件名。"""
    stem = path.name[:-7] if path.name.endswith(".nii.gz") else path.stem
    parts: list[object] = []
    for token in stem.replace("-", "_").split("_"):
        parts.append(int(token) if token.isdigit() else token.lower())
    return tuple(parts)


def list_ct_volumes(ct_dir: Path) -> list[Path]:
    """列出训练所用的原始 CT 体数据。"""
    return sorted(ct_dir.glob("*.nii.gz"), key=natural_key)


def split_volume_paths(volume_paths: list[Path], val_ratio: float, seed: int) -> tuple[list[Path], list[Path]]:
    """按固定随机种子划分 train / val。"""
    paths = list(volume_paths)
    random.Random(seed).shuffle(paths)
    val_size = max(1, int(len(paths) * val_ratio))
    return paths[val_size:], paths[:val_size]


def preload_base_volumes(volume_paths: list[Path]) -> dict[str, np.ndarray]:
    """
    预加载并预处理所有 base volume。

    数据集只有 190 例，低分辨率预处理后直接放内存可显著降低训练期 I/O。
    """
    base_volumes: dict[str, np.ndarray] = {}
    total = len(volume_paths)
    for index, volume_path in enumerate(volume_paths, start=1):
        volume_id = volume_path.name[:-7]
        base_volumes[volume_id] = load_preprocessed_volume(volume_path, target_shape=TARGET_SHAPE)
        if index == 1 or index == total or index % 25 == 0:
            print(f"Preloaded volumes: {index}/{total}")
    return base_volumes


def build_variant_plans(volume_paths: list[Path], variants_per_volume: int, seed: int) -> list[VariantPlan]:
    """
    为每个体数据生成固定数量的合成样本计划。

    规则：
    - 第 0 个变体永远是 clean sample
    - 其余变体随机包含 1-3 个失败项
    """
    task_keys = [spec.key for spec in QC_TASK_SPECS]
    plans: list[VariantPlan] = []
    for volume_path in volume_paths:
        volume_id = volume_path.name[:-7]
        numeric_id = int(volume_id) if volume_id.isdigit() else abs(hash(volume_id)) % 100000

        plans.append(
            VariantPlan(
                volume_id=volume_id,
                volume_path=str(volume_path),
                variant_index=0,
                failed_task_keys=tuple(),
                seed=seed + numeric_id * 1000,
            )
        )

        for variant_index in range(1, variants_per_volume):
            variant_seed = seed + numeric_id * 1000 + variant_index * 97
            rng = random.Random(variant_seed)
            fail_count = 1 if rng.random() < 0.55 else 2 if rng.random() < 0.9 else 3
            failed_task_keys = tuple(sorted(rng.sample(task_keys, k=fail_count)))
            plans.append(
                VariantPlan(
                    volume_id=volume_id,
                    volume_path=str(volume_path),
                    variant_index=variant_index,
                    failed_task_keys=failed_task_keys,
                    seed=variant_seed,
                )
            )
    return plans


def shift_volume(volume: np.ndarray, shift_d: int = 0, shift_h: int = 0, shift_w: int = 0) -> np.ndarray:
    """在不环绕的情况下平移 3D 体数据。"""
    result = np.zeros_like(volume)
    depth, height, width = volume.shape

    src_d_start = max(0, -shift_d)
    src_d_end = min(depth, depth - shift_d) if shift_d >= 0 else depth
    dst_d_start = max(0, shift_d)
    dst_d_end = min(depth, depth + shift_d) if shift_d <= 0 else depth

    src_h_start = max(0, -shift_h)
    src_h_end = min(height, height - shift_h) if shift_h >= 0 else height
    dst_h_start = max(0, shift_h)
    dst_h_end = min(height, height + shift_h) if shift_h <= 0 else height

    src_w_start = max(0, -shift_w)
    src_w_end = min(width, width - shift_w) if shift_w >= 0 else width
    dst_w_start = max(0, shift_w)
    dst_w_end = min(width, width + shift_w) if shift_w <= 0 else width

    result[dst_d_start:dst_d_end, dst_h_start:dst_h_end, dst_w_start:dst_w_end] = volume[
        src_d_start:src_d_end,
        src_h_start:src_h_end,
        src_w_start:src_w_end,
    ]
    return result


def apply_benign_intensity_jitter(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """对所有样本做轻度亮度/对比度扰动，避免固定灰度过拟合。"""
    contrast = float(rng.uniform(0.92, 1.08))
    brightness = float(rng.uniform(-0.03, 0.03))
    jittered = volume * contrast + brightness
    return np.clip(jittered, 0.0, 1.0).astype(np.float32, copy=False)


def apply_coverage_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过丢失颅顶或颅底切片模拟扫描覆盖不足。"""
    degraded = volume.copy()
    depth = degraded.shape[0]
    missing = int(round(depth * float(rng.uniform(0.18, 0.28))))
    if rng.random() < 0.5:
        degraded[:missing] = 0.0
    else:
        degraded[-missing:] = 0.0
    return degraded


def estimate_coverage_failure(volume: np.ndarray) -> bool:
    """根据 Z 方向有效层面分布判断是否存在覆盖不足。"""
    active = np.where(volume.max(axis=(1, 2)) > 0.05)[0]
    if active.size == 0:
        return True
    coverage_ratio = float(active.size) / float(volume.shape[0])
    leading_margin = int(active[0])
    trailing_margin = int(volume.shape[0] - 1 - active[-1])
    return coverage_ratio < MIN_COVERAGE_SLICE_RATIO or leading_margin > 3 or trailing_margin > 3


def warp_slice(slice_2d: np.ndarray, angle: float, tx: float, ty: float) -> np.ndarray:
    """对单张切片做旋转和平移。"""
    height, width = slice_2d.shape
    center = (width * 0.5, height * 0.5)
    matrix = cv2.getRotationMatrix2D(center, angle, 1.0)
    matrix[0, 2] += tx
    matrix[1, 2] += ty
    warped = cv2.warpAffine(
        slice_2d,
        matrix,
        (width, height),
        flags=cv2.INTER_LINEAR,
        borderMode=cv2.BORDER_CONSTANT,
        borderValue=0.0,
    )
    return warped.astype(np.float32, copy=False)


def apply_positioning_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过整体偏移和轻微倾斜模拟体位不正。"""
    angle = float(rng.uniform(-18.0, 18.0))
    shift_h = float(rng.uniform(-22.0, 22.0))
    shift_w = float(rng.uniform(-22.0, 22.0))
    transformed = np.empty_like(volume)
    for index in range(volume.shape[0]):
        transformed[index] = warp_slice(volume[index], angle=angle, tx=shift_w, ty=shift_h)
    return np.clip(transformed, 0.0, 1.0)


def estimate_positioning_failure(volume: np.ndarray) -> bool:
    """根据头部前景质心偏离图像中心的幅度判断体位是否偏斜。"""
    mask = volume > 0.05
    coordinates = np.argwhere(mask)
    if coordinates.size == 0:
        return True
    center = coordinates.mean(axis=0)
    midpoint = (np.array(volume.shape) - 1) / 2.0
    offset = np.abs(center[1:] - midpoint[1:])
    return float(offset.max()) >= POSITIONING_OFFSET_THRESHOLD


def shift_slice(slice_2d: np.ndarray, shift_h: int, shift_w: int) -> np.ndarray:
    """在不环绕的前提下平移二维切片。"""
    return shift_volume(slice_2d[None, ...], shift_h=shift_h, shift_w=shift_w)[0]


def apply_motion_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过逐层错位和重影模拟运动伪影。"""
    degraded = np.empty_like(volume)
    depth = volume.shape[0]
    amplitude_h = float(rng.uniform(5.0, 10.0))
    amplitude_w = float(rng.uniform(6.0, 12.0))
    phase = float(rng.uniform(0.0, math.pi))

    for index, slice_2d in enumerate(volume):
        offset_h = int(round(amplitude_h * math.sin((2 * math.pi * index / max(1, depth)) + phase)))
        offset_w = int(round(amplitude_w * math.cos((2 * math.pi * index / max(1, depth)) + phase)))
        shifted = shift_slice(slice_2d, shift_h=offset_h, shift_w=offset_w)
        ghost = 0.5 * slice_2d + 0.5 * shifted
        ghost = cv2.GaussianBlur(ghost, (3, 3), sigmaX=1.2)
        degraded[index] = ghost

    return np.clip(degraded, 0.0, 1.0)


def draw_streak(mask: np.ndarray, center: tuple[int, int], angle_deg: float, thickness: int) -> None:
    """在二维 mask 上绘制穿过中心的放射状线束伪影。"""
    height, width = mask.shape
    radians = math.radians(angle_deg)
    dx = math.cos(radians)
    dy = math.sin(radians)
    radius = int(math.hypot(height, width))
    x0 = int(center[0] - dx * radius)
    y0 = int(center[1] - dy * radius)
    x1 = int(center[0] + dx * radius)
    y1 = int(center[1] + dy * radius)
    cv2.line(mask, (x0, y0), (x1, y1), color=1.0, thickness=thickness)


def apply_metal_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过高亮圆点与放射状线束模拟金属伪影。"""
    degraded = volume.copy()
    depth, height, width = degraded.shape
    center_z = int(rng.integers(low=depth // 3, high=max(depth // 3 + 1, depth * 2 // 3)))
    span = int(rng.integers(low=3, high=7))
    center = (
        int(rng.integers(low=width // 3, high=max(width // 3 + 1, width * 2 // 3))),
        int(rng.integers(low=height // 3, high=max(height // 3 + 1, height * 2 // 3))),
    )

    for z in range(max(0, center_z - span), min(depth, center_z + span + 1)):
        mask = np.zeros((height, width), dtype=np.float32)
        cv2.circle(mask, center, radius=int(rng.integers(3, 7)), color=1.0, thickness=-1)
        for angle in rng.uniform(0.0, 180.0, size=int(rng.integers(5, 9))):
            draw_streak(mask, center, float(angle), thickness=int(rng.integers(1, 3)))
        mask = cv2.GaussianBlur(mask, (5, 5), sigmaX=1.0)
        degraded[z] = np.clip(np.maximum(degraded[z], mask), 0.0, 1.0)

    return degraded


def resize_depth_only(volume: np.ndarray, new_depth: int) -> np.ndarray:
    """仅沿 Z 方向重采样，用于模拟层厚层间距变粗。"""
    tensor = torch.from_numpy(volume).unsqueeze(0).unsqueeze(0)
    resized = torch.nn.functional.interpolate(
        tensor,
        size=(new_depth, volume.shape[1], volume.shape[2]),
        mode="trilinear",
        align_corners=False,
    )
    return resized.squeeze(0).squeeze(0).numpy().astype(np.float32, copy=False)


def apply_slice_thickness_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过 Z 方向降采样再恢复，模拟层厚/层间距过大。"""
    depth = volume.shape[0]
    factor = int(rng.integers(2, 4))
    reduced_depth = max(8, depth // factor)
    degraded = resize_depth_only(volume, new_depth=reduced_depth)
    degraded = resize_depth_only(degraded, new_depth=depth)
    return np.clip(degraded, 0.0, 1.0)


def apply_dose_proxy_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过强噪声和轻度对比度下降模拟低剂量外观。"""
    scale = float(rng.uniform(24.0, 48.0))
    poisson = rng.poisson(np.clip(volume, 0.0, 1.0) * scale) / scale
    gaussian = rng.normal(loc=0.0, scale=float(rng.uniform(0.06, 0.12)), size=volume.shape)
    degraded = 0.75 * poisson + 0.25 * volume + gaussian
    degraded = degraded * float(rng.uniform(0.82, 0.92))
    return np.clip(degraded, 0.0, 1.0).astype(np.float32, copy=False)


def compute_pos_weight(plans: list[VariantPlan]) -> torch.Tensor:
    """根据训练计划中的 fail / pass 分布计算 BCE 的 `pos_weight`。"""
    positives = np.zeros(QC_TASK_COUNT, dtype=np.float64)
    total = len(plans)
    for plan in plans:
        for task_key in plan.failed_task_keys:
            positives[QC_TASK_INDEX[task_key]] += 1.0
    negatives = total - positives
    pos_weight = negatives / np.maximum(positives, 1.0)
    return torch.tensor(pos_weight, dtype=torch.float32)


def tensor_to_numpy(tensor: torch.Tensor) -> np.ndarray:
    """将 Tensor 拉回 CPU numpy。"""
    return tensor.detach().float().cpu().numpy()


def compute_binary_metrics(probabilities: np.ndarray, targets: np.ndarray, thresholds: np.ndarray) -> dict[str, object]:
    """计算每个质控项的准确率、精确率、召回率和 F1。"""
    predictions = (probabilities >= thresholds[None, :]).astype(np.float32)
    metrics: dict[str, object] = {"per_task": {}, "mean_f1": 0.0, "mean_accuracy": 0.0}
    f1_values: list[float] = []
    accuracy_values: list[float] = []

    for index, spec in enumerate(QC_TASK_SPECS):
        pred = predictions[:, index]
        truth = targets[:, index]
        tp = float(np.sum((pred == 1) & (truth == 1)))
        tn = float(np.sum((pred == 0) & (truth == 0)))
        fp = float(np.sum((pred == 1) & (truth == 0)))
        fn = float(np.sum((pred == 0) & (truth == 1)))

        precision = tp / max(tp + fp, 1.0)
        recall = tp / max(tp + fn, 1.0)
        f1 = 2 * precision * recall / max(precision + recall, 1e-8)
        accuracy = (tp + tn) / max(tp + tn + fp + fn, 1.0)

        metrics["per_task"][spec.key] = {
            "name": spec.name,
            "threshold": float(thresholds[index]),
            "accuracy": round(accuracy, 4),
            "precision": round(precision, 4),
            "recall": round(recall, 4),
            "f1": round(f1, 4),
        }
        f1_values.append(f1)
        accuracy_values.append(accuracy)

    metrics["mean_f1"] = round(float(np.mean(f1_values)), 4)
    metrics["mean_accuracy"] = round(float(np.mean(accuracy_values)), 4)
    return metrics


def find_best_thresholds(probabilities: np.ndarray, targets: np.ndarray) -> np.ndarray:
    """在验证集上为每个任务搜索最优 F1 阈值。"""
    thresholds = np.full(QC_TASK_COUNT, 0.5, dtype=np.float32)
    search_space = np.arange(0.2, 0.81, 0.05, dtype=np.float32)

    for index in range(QC_TASK_COUNT):
        best_threshold = 0.5
        best_f1 = -1.0
        best_accuracy = -1.0
        truth = targets[:, index]
        prob = probabilities[:, index]
        for threshold in search_space:
            pred = (prob >= threshold).astype(np.float32)
            tp = float(np.sum((pred == 1) & (truth == 1)))
            tn = float(np.sum((pred == 0) & (truth == 0)))
            fp = float(np.sum((pred == 1) & (truth == 0)))
            fn = float(np.sum((pred == 0) & (truth == 1)))
            precision = tp / max(tp + fp, 1.0)
            recall = tp / max(tp + fn, 1.0)
            f1 = 2 * precision * recall / max(precision + recall, 1e-8)
            accuracy = (tp + tn) / max(tp + tn + fp + fn, 1.0)
            if f1 > best_f1 or (abs(f1 - best_f1) < 1e-6 and accuracy > best_accuracy):
                best_f1 = f1
                best_accuracy = accuracy
                best_threshold = float(threshold)
        thresholds[index] = best_threshold

    return thresholds


def run_epoch(
    model: HeadCtPlainQcModel,
    data_loader: DataLoader,
    criterion: nn.Module,
    device: torch.device,
    optimizer: torch.optim.Optimizer | None = None,
    scaler: GradScaler | None = None,
) -> tuple[float, np.ndarray, np.ndarray]:
    """执行单轮 train 或 eval，并返回 loss、概率和标签。"""
    training = optimizer is not None
    model.train(training)

    total_loss = 0.0
    all_probabilities: list[np.ndarray] = []
    all_targets: list[np.ndarray] = []
    autocast_enabled = device.type == "cuda"

    for volumes, labels in data_loader:
        volumes = volumes.to(device, non_blocking=True)
        labels = labels.to(device, non_blocking=True)

        if training:
            optimizer.zero_grad(set_to_none=True)

        with autocast(device_type=device.type, enabled=autocast_enabled):
            logits = model(volumes)
            loss = criterion(logits, labels)

        if training:
            assert optimizer is not None and scaler is not None
            scaler.scale(loss).backward()
            scaler.step(optimizer)
            scaler.update()

        total_loss += float(loss.item()) * volumes.size(0)
        all_probabilities.append(tensor_to_numpy(torch.sigmoid(logits)))
        all_targets.append(tensor_to_numpy(labels))

    mean_loss = total_loss / max(len(data_loader.dataset), 1)
    probabilities = np.concatenate(all_probabilities, axis=0)
    targets = np.concatenate(all_targets, axis=0)
    return mean_loss, probabilities, targets


def save_json(payload: dict[str, object], output_path: Path) -> None:
    """将训练指标或验证结果写入 JSON。"""
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def build_preview_predictions(
    model: HeadCtPlainQcModel,
    plans: list[VariantPlan],
    base_volumes: dict[str, np.ndarray],
    thresholds: np.ndarray,
    device: torch.device,
    limit: int = 8,
) -> list[dict[str, object]]:
    """抽样生成若干验证集预测结果，便于人工快速检查置信度。"""
    preview: list[dict[str, object]] = []
    model.eval()
    with torch.no_grad():
        for plan in plans[:limit]:
            volume = np.copy(base_volumes[plan.volume_id])
            rng = np.random.default_rng(plan.seed)
            volume = apply_benign_intensity_jitter(volume, rng)
            for task_key in plan.failed_task_keys:
                if task_key == "coverage":
                    volume = apply_coverage_failure(volume, rng)
                elif task_key == "positioning":
                    volume = apply_positioning_failure(volume, rng)
                elif task_key == "motion":
                    volume = apply_motion_failure(volume, rng)
                elif task_key == "metal":
                    volume = apply_metal_failure(volume, rng)
                elif task_key == "slice_thickness":
                    volume = apply_slice_thickness_failure(volume, rng)
                elif task_key == "dose_proxy":
                    volume = apply_dose_proxy_failure(volume, rng)

            input_tensor = torch.from_numpy(volume[None, None, ...]).float().to(device)
            probabilities = tensor_to_numpy(torch.sigmoid(model(input_tensor)))[0]

            preview.append(
                {
                    "volume_id": plan.volume_id,
                    "variant_index": plan.variant_index,
                    "expected_failed_tasks": list(plan.failed_task_keys),
                    "predicted_failed_tasks": [
                        QC_TASK_SPECS[index].key
                        for index, probability in enumerate(probabilities)
                        if probability >= thresholds[index]
                    ],
                    "task_probabilities": {
                        spec.key: round(float(probabilities[index]), 4)
                        for index, spec in enumerate(QC_TASK_SPECS)
                    },
                }
            )
    return preview


def train() -> None:
    """训练头部 CT 平扫质控模型，并保存最佳 checkpoint。"""
    if not torch.cuda.is_available():
        print("ERROR: CUDA is not available. Head CT plain QC training requires CUDA.")
        return

    set_seed(SEED)
    ensure_output_directories()

    ct_volume_paths = list_ct_volumes(CT_DATASET_DIR)
    if not ct_volume_paths:
        print(f"ERROR: No CT volumes found in {CT_DATASET_DIR}")
        return

    train_paths, val_paths = split_volume_paths(ct_volume_paths, val_ratio=VAL_RATIO, seed=SEED)
    print(f"Train volumes: {len(train_paths)}, Val volumes: {len(val_paths)}")
    print(f"Target shape: {TARGET_SHAPE}")
    print("Preloading base CT volumes into memory...")
    base_volumes = preload_base_volumes(ct_volume_paths)

    train_plans = build_variant_plans(train_paths, variants_per_volume=TRAIN_VARIANTS_PER_VOLUME, seed=SEED)
    val_plans = build_variant_plans(val_paths, variants_per_volume=VAL_VARIANTS_PER_VOLUME, seed=SEED + 10000)
    print(f"Train synthetic samples: {len(train_plans)}, Val synthetic samples: {len(val_plans)}")

    train_dataset = SyntheticHeadCtQcDataset(base_volumes=base_volumes, plans=train_plans)
    val_dataset = SyntheticHeadCtQcDataset(base_volumes=base_volumes, plans=val_plans)

    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, num_workers=0, pin_memory=True)
    val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, num_workers=0, pin_memory=True)

    device = torch.device("cuda")
    model = HeadCtPlainQcModel().to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=WEIGHT_DECAY)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS)
    criterion = nn.BCEWithLogitsLoss(pos_weight=compute_pos_weight(train_plans).to(device))
    scaler = GradScaler("cuda", enabled=True)

    history: list[dict[str, object]] = []
    best_state: dict[str, object] | None = None
    best_mean_f1 = -1.0

    for epoch in range(1, EPOCHS + 1):
        train_loss, train_probabilities, train_targets = run_epoch(
            model=model,
            data_loader=train_loader,
            criterion=criterion,
            device=device,
            optimizer=optimizer,
            scaler=scaler,
        )

        val_loss, val_probabilities, val_targets = run_epoch(
            model=model,
            data_loader=val_loader,
            criterion=criterion,
            device=device,
        )

        thresholds = find_best_thresholds(val_probabilities, val_targets)
        train_metrics = compute_binary_metrics(train_probabilities, train_targets, thresholds=thresholds)
        val_metrics = compute_binary_metrics(val_probabilities, val_targets, thresholds=thresholds)
        scheduler.step()

        epoch_summary = {
            "epoch": epoch,
            "train_loss": round(train_loss, 4),
            "val_loss": round(val_loss, 4),
            "train_mean_f1": train_metrics["mean_f1"],
            "val_mean_f1": val_metrics["mean_f1"],
            "train_mean_accuracy": train_metrics["mean_accuracy"],
            "val_mean_accuracy": val_metrics["mean_accuracy"],
            "thresholds": {spec.key: float(thresholds[index]) for index, spec in enumerate(QC_TASK_SPECS)},
            "train_per_task": train_metrics["per_task"],
            "val_per_task": val_metrics["per_task"],
        }
        history.append(epoch_summary)

        print(
            f"Epoch [{epoch}/{EPOCHS}] "
            f"train_loss={train_loss:.4f} val_loss={val_loss:.4f} "
            f"train_f1={train_metrics['mean_f1']:.4f} val_f1={val_metrics['mean_f1']:.4f}"
        )

        if float(val_metrics["mean_f1"]) > best_mean_f1:
            best_mean_f1 = float(val_metrics["mean_f1"])
            best_state = {
                "model_state_dict": model.state_dict(),
                "thresholds": thresholds.tolist(),
                "epoch": epoch,
                "train_loss": train_loss,
                "val_loss": val_loss,
                "train_metrics": train_metrics,
                "val_metrics": val_metrics,
                "task_specs": task_specs_as_dicts(),
                "target_shape": list(TARGET_SHAPE),
                "train_volume_count": len(train_paths),
                "val_volume_count": len(val_paths),
            }
            torch.save(best_state, MODEL_WEIGHTS_PATH)
            print(f"  Best checkpoint updated -> {MODEL_WEIGHTS_PATH}")

    if best_state is None:
        print("ERROR: Training finished without a valid checkpoint.")
        return

    history_payload = {
        "config": {
            "seed": SEED,
            "batch_size": BATCH_SIZE,
            "epochs": EPOCHS,
            "learning_rate": LEARNING_RATE,
            "weight_decay": WEIGHT_DECAY,
            "train_variants_per_volume": TRAIN_VARIANTS_PER_VOLUME,
            "val_variants_per_volume": VAL_VARIANTS_PER_VOLUME,
            "target_shape": list(TARGET_SHAPE),
        },
        "history": history,
        "best_checkpoint": {
            "epoch": best_state["epoch"],
            "val_metrics": best_state["val_metrics"],
            "thresholds": best_state["thresholds"],
        },
    }
    save_json(history_payload, RESULTS_DIR / "training_history.json")

    model.load_state_dict(best_state["model_state_dict"])
    thresholds = np.array(best_state["thresholds"], dtype=np.float32)
    preview = build_preview_predictions(
        model=model,
        plans=val_plans,
        base_volumes=base_volumes,
        thresholds=thresholds,
        device=device,
        limit=10,
    )
    save_json({"preview_predictions": preview}, RESULTS_DIR / "validation_preview.json")

    print("Training finished.")
    print(f"Best model saved to: {MODEL_WEIGHTS_PATH}")
    print(f"History saved to: {RESULTS_DIR / 'training_history.json'}")
    print(f"Preview predictions saved to: {RESULTS_DIR / 'validation_preview.json'}")


if __name__ == "__main__":
    train()
