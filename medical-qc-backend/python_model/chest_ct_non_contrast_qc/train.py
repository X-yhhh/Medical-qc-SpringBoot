"""
CT 胸部平扫质控多任务模型训练脚本。

训练策略：
1. 使用 LUNA16 标准化后的 clean 胸部 CT 体数据作为 base volume
2. 合成 6 个图像质控失败项的伪标签样本
3. 在验证集上搜索每个任务的最佳阈值，并输出 checkpoint / 历史 / 预览
"""

from __future__ import annotations

import argparse
import json
import math
import random
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import torch
from torch import nn
from torch.amp import GradScaler, autocast
from torch.utils.data import DataLoader, Dataset

try:
    from chest_ct_non_contrast_qc.model import (
        MODEL_WEIGHTS_PATH,
        NORMALIZED_DATASET_DIR,
        QC_TASK_SPECS,
        RESULTS_DIR,
        TARGET_SHAPE,
        ChestCtNonContrastQcModel,
        ensure_output_directories,
        load_preprocessed_chest_volume,
        task_specs_as_dicts,
    )
except ImportError:
    from model import (
        MODEL_WEIGHTS_PATH,
        NORMALIZED_DATASET_DIR,
        QC_TASK_SPECS,
        RESULTS_DIR,
        TARGET_SHAPE,
        ChestCtNonContrastQcModel,
        ensure_output_directories,
        load_preprocessed_chest_volume,
        task_specs_as_dicts,
    )


SEED = 42
VAL_RATIO = 0.2
DEFAULT_BATCH_SIZE = 2
DEFAULT_EPOCHS = 8
DEFAULT_LEARNING_RATE = 1e-3
DEFAULT_WEIGHT_DECAY = 1e-4
DEFAULT_TRAIN_VARIANTS = 5
DEFAULT_VAL_VARIANTS = 3
MIN_COVERAGE_SLICE_RATIO = 0.68
POSITIONING_OFFSET_THRESHOLD = 7.5

IMAGE_TASK_SPECS = tuple(spec for spec in QC_TASK_SPECS if not spec.rule_based)
IMAGE_TASK_KEYS = tuple(spec.key for spec in IMAGE_TASK_SPECS)
IMAGE_TASK_INDEX = {spec.key: index for index, spec in enumerate(IMAGE_TASK_SPECS)}
IMAGE_TASK_COUNT = len(IMAGE_TASK_SPECS)


@dataclass(frozen=True)
class VariantPlan:
    """单个合成样本的生成计划。"""

    volume_id: str
    volume_path: str
    variant_index: int
    failed_task_keys: tuple[str, ...]
    seed: int


class SyntheticChestQcDataset(Dataset):
    """基于 clean 体数据与合成缺陷的胸部平扫多任务数据集。"""

    def __init__(self, base_volumes: dict[str, np.ndarray], plans: list[VariantPlan]) -> None:
        self.base_volumes = base_volumes
        self.plans = plans

    def __len__(self) -> int:
        return len(self.plans)

    def __getitem__(self, index: int) -> tuple[torch.Tensor, torch.Tensor]:
        plan = self.plans[index]
        rng = np.random.default_rng(plan.seed)
        volume = np.copy(self.base_volumes[plan.volume_id])
        volume = apply_benign_intensity_jitter(volume, rng)
        volume = apply_selected_failures(volume, rng, plan.failed_task_keys)

        labels = np.zeros(IMAGE_TASK_COUNT, dtype=np.float32)
        labels[IMAGE_TASK_INDEX["coverage"]] = 1.0 if estimate_coverage_failure(volume) else 0.0
        labels[IMAGE_TASK_INDEX["positioning"]] = 1.0 if estimate_positioning_failure(volume) else 0.0
        labels[IMAGE_TASK_INDEX["respiratory_motion"]] = 1.0 if "respiratory_motion" in plan.failed_task_keys else 0.0
        labels[IMAGE_TASK_INDEX["metal"]] = 1.0 if "metal" in plan.failed_task_keys else 0.0
        labels[IMAGE_TASK_INDEX["noise"]] = 1.0 if "noise" in plan.failed_task_keys else 0.0
        labels[IMAGE_TASK_INDEX["cardiac_motion"]] = 1.0 if "cardiac_motion" in plan.failed_task_keys else 0.0

        return torch.from_numpy(volume[None, ...]).float(), torch.from_numpy(labels).float()


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Train chest non contrast QC model with synthetic pseudo labels.")
    parser.add_argument("--epochs", type=int, default=DEFAULT_EPOCHS)
    parser.add_argument("--batch-size", type=int, default=DEFAULT_BATCH_SIZE)
    parser.add_argument("--learning-rate", type=float, default=DEFAULT_LEARNING_RATE)
    parser.add_argument("--weight-decay", type=float, default=DEFAULT_WEIGHT_DECAY)
    parser.add_argument("--train-variants", type=int, default=DEFAULT_TRAIN_VARIANTS)
    parser.add_argument("--val-variants", type=int, default=DEFAULT_VAL_VARIANTS)
    return parser.parse_args()


def set_seed(seed: int) -> None:
    """固定随机种子，保证训练和验证结果可复现。"""
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)
    if torch.cuda.is_available():
        torch.cuda.manual_seed_all(seed)
        torch.backends.cudnn.deterministic = False
        torch.backends.cudnn.benchmark = True


def natural_key(path: Path) -> tuple[object, ...]:
    """按更接近人类直觉的方式排序文件名。"""
    stem = path.name[:-7] if path.name.endswith(".nii.gz") else path.stem
    parts: list[object] = []
    for token in stem.replace("-", "_").split("_"):
        parts.append(int(token) if token.isdigit() else token.lower())
    return tuple(parts)


def list_ct_volumes(ct_dir: Path) -> list[Path]:
    """列出标准化后的胸部 NIfTI 体数据。"""
    return sorted(ct_dir.glob("*.nii.gz"), key=natural_key)


def split_volume_paths(volume_paths: list[Path], val_ratio: float, seed: int) -> tuple[list[Path], list[Path]]:
    """按固定随机种子切分 train / val。"""
    paths = list(volume_paths)
    random.Random(seed).shuffle(paths)
    val_size = max(1, int(len(paths) * val_ratio))
    return paths[val_size:], paths[:val_size]


def preload_base_volumes(volume_paths: list[Path]) -> dict[str, np.ndarray]:
    """预加载全部 clean volume，减少训练期磁盘 I/O。"""
    base_volumes: dict[str, np.ndarray] = {}
    total = len(volume_paths)
    for index, volume_path in enumerate(volume_paths, start=1):
        volume_id = volume_path.name[:-7]
        base_volumes[volume_id] = load_preprocessed_chest_volume(volume_path).numpy()
        if index == 1 or index == total or index % 20 == 0:
            print(f"Preloaded volumes: {index}/{total}")
    return base_volumes


def build_variant_plans(volume_paths: list[Path], variants_per_volume: int, seed: int) -> list[VariantPlan]:
    """
    为每个体数据构造固定数量的合成样本计划。

    规则：
    - 第 0 个变体始终为 clean sample
    - 其余变体随机包含 1-3 个失败项
    """
    plans: list[VariantPlan] = []
    for volume_path in volume_paths:
        volume_id = volume_path.name[:-7]
        numeric_id = abs(hash(volume_id)) % 100000
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
            fail_count_seed = rng.random()
            fail_count = 1 if fail_count_seed < 0.5 else 2 if fail_count_seed < 0.85 else 3
            failed_task_keys = tuple(sorted(rng.sample(IMAGE_TASK_KEYS, k=fail_count)))
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
    """在不环绕的前提下平移 3D 体数据。"""
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


def shift_slice(slice_2d: np.ndarray, shift_h: int, shift_w: int) -> np.ndarray:
    """在不环绕的前提下平移二维切片。"""
    return shift_volume(slice_2d[None, ...], shift_h=shift_h, shift_w=shift_w)[0]


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


def draw_streak(mask: np.ndarray, center: tuple[int, int], angle_deg: float, thickness: int) -> None:
    """在二维 mask 上绘制穿过中心的线束伪影。"""
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


def apply_benign_intensity_jitter(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """给全部样本增加轻微强度扰动，降低灰度分布记忆。"""
    contrast = float(rng.uniform(0.94, 1.06))
    brightness = float(rng.uniform(-0.03, 0.03))
    jittered = volume * contrast + brightness
    return np.clip(jittered, 0.0, 1.0).astype(np.float32, copy=False)


def apply_coverage_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过丢失肺尖或肺底切片模拟扫描范围不足。"""
    degraded = volume.copy()
    depth = degraded.shape[0]
    missing = int(round(depth * float(rng.uniform(0.18, 0.28))))
    if rng.random() < 0.5:
        degraded[:missing] = 0.0
    else:
        degraded[-missing:] = 0.0
    return degraded


def estimate_coverage_failure(volume: np.ndarray) -> bool:
    """根据 Z 向有效层面分布判断是否存在扫描覆盖不足。"""
    active = np.where(volume.max(axis=(1, 2)) > 0.05)[0]
    if active.size == 0:
        return True
    coverage_ratio = float(active.size) / float(volume.shape[0])
    leading_margin = int(active[0])
    trailing_margin = int(volume.shape[0] - 1 - active[-1])
    return coverage_ratio < MIN_COVERAGE_SLICE_RATIO or leading_margin > 4 or trailing_margin > 4


def apply_respiratory_motion_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过下肺逐层错位和重影模拟呼吸伪影。"""
    degraded = volume.copy()
    depth = degraded.shape[0]
    start_index = int(depth * float(rng.uniform(0.35, 0.45)))
    amplitude_h = float(rng.uniform(3.0, 7.0))
    amplitude_w = float(rng.uniform(5.0, 11.0))
    phase = float(rng.uniform(0.0, math.pi))

    for index in range(start_index, depth):
        slice_2d = degraded[index]
        progress = (index - start_index) / max(depth - start_index, 1)
        weight = 0.4 + 0.8 * progress
        offset_h = int(round(weight * amplitude_h * math.sin((2 * math.pi * progress) + phase)))
        offset_w = int(round(weight * amplitude_w * math.cos((2 * math.pi * progress) + phase)))
        shifted = shift_slice(slice_2d, shift_h=offset_h, shift_w=offset_w)
        ghost = 0.45 * slice_2d + 0.55 * shifted
        ghost = cv2.GaussianBlur(ghost, (5, 5), sigmaX=1.2)
        degraded[index] = ghost

    return np.clip(degraded, 0.0, 1.0)


def apply_positioning_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过整体偏移和轻度旋转模拟患者摆位不正。"""
    angle = float(rng.uniform(-12.0, 12.0))
    shift_h = float(rng.uniform(-16.0, 16.0))
    shift_w = float(rng.uniform(-18.0, 18.0))
    transformed = np.empty_like(volume)
    for index in range(volume.shape[0]):
        transformed[index] = warp_slice(volume[index], angle=angle, tx=shift_w, ty=shift_h)
    return np.clip(transformed, 0.0, 1.0)


def estimate_positioning_failure(volume: np.ndarray) -> bool:
    """根据胸廓前景质心偏离图像中心的幅度判断体位是否偏心。"""
    mask = volume > 0.08
    coordinates = np.argwhere(mask)
    if coordinates.size == 0:
        return True
    center = coordinates.mean(axis=0)
    midpoint = (np.array(volume.shape) - 1) / 2.0
    offset = np.abs(center[1:] - midpoint[1:])
    return float(offset.max()) >= POSITIONING_OFFSET_THRESHOLD


def apply_metal_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过高亮团块和条束线模拟金属伪影。"""
    degraded = volume.copy()
    depth, height, width = degraded.shape
    center_z = int(rng.integers(low=depth // 3, high=max(depth // 3 + 1, depth * 2 // 3)))
    span = int(rng.integers(low=4, high=9))
    center = (
        int(rng.integers(low=width // 4, high=max(width // 4 + 1, width * 3 // 4))),
        int(rng.integers(low=height // 4, high=max(height // 4 + 1, height * 3 // 4))),
    )

    for z in range(max(0, center_z - span), min(depth, center_z + span + 1)):
        mask = np.zeros((height, width), dtype=np.float32)
        cv2.circle(mask, center, radius=int(rng.integers(4, 8)), color=1.0, thickness=-1)
        for angle in rng.uniform(0.0, 180.0, size=int(rng.integers(6, 10))):
            draw_streak(mask, center, float(angle), thickness=int(rng.integers(1, 3)))
        mask = cv2.GaussianBlur(mask, (5, 5), sigmaX=1.2)
        degraded[z] = np.clip(np.maximum(degraded[z], mask), 0.0, 1.0)

    return degraded


def apply_noise_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """通过低剂量样式噪声和局部纹理退化模拟图像噪声偏高。"""
    scale = float(rng.uniform(20.0, 40.0))
    poisson = rng.poisson(np.clip(volume, 0.0, 1.0) * scale) / scale
    gaussian = rng.normal(loc=0.0, scale=float(rng.uniform(0.05, 0.1)), size=volume.shape)
    degraded = 0.7 * poisson + 0.3 * volume + gaussian
    degraded = degraded * float(rng.uniform(0.86, 0.95))
    return np.clip(degraded, 0.0, 1.0).astype(np.float32, copy=False)


def build_ellipse_mask(height: int, width: int, center_x: int, center_y: int, axis_x: int, axis_y: int) -> np.ndarray:
    """构造二维椭圆区域，用于模拟心影拖影区域。"""
    mask = np.zeros((height, width), dtype=np.float32)
    cv2.ellipse(mask, (center_x, center_y), (axis_x, axis_y), 0, 0, 360, 1.0, thickness=-1)
    return cv2.GaussianBlur(mask, (9, 9), sigmaX=2.0)


def apply_cardiac_motion_failure(volume: np.ndarray, rng: np.random.Generator) -> np.ndarray:
    """在心影区域叠加重复边缘和局部拖影，模拟心影干扰。"""
    degraded = volume.copy()
    depth, height, width = degraded.shape
    z_start = int(depth * 0.42)
    z_end = int(depth * 0.72)
    center_x = int(width * float(rng.uniform(0.48, 0.6)))
    center_y = int(height * float(rng.uniform(0.5, 0.66)))
    axis_x = int(width * float(rng.uniform(0.11, 0.16)))
    axis_y = int(height * float(rng.uniform(0.12, 0.18)))
    mask = build_ellipse_mask(height, width, center_x, center_y, axis_x, axis_y)

    for index in range(z_start, min(z_end, depth)):
        slice_2d = degraded[index]
        offset_w = int(round((1.0 + math.sin(index * 0.45)) * float(rng.uniform(2.0, 5.0))))
        shifted = shift_slice(slice_2d, shift_h=0, shift_w=offset_w)
        ghost = np.clip(0.6 * slice_2d + 0.4 * shifted, 0.0, 1.0)
        degraded[index] = np.clip(slice_2d * (1.0 - 0.65 * mask) + ghost * (0.65 * mask), 0.0, 1.0)

    return degraded


def apply_selected_failures(volume: np.ndarray, rng: np.random.Generator, failed_task_keys: tuple[str, ...]) -> np.ndarray:
    """按计划对体数据施加对应失败项。"""
    degraded = volume
    for task_key in failed_task_keys:
        if task_key == "coverage":
            degraded = apply_coverage_failure(degraded, rng)
        elif task_key == "respiratory_motion":
            degraded = apply_respiratory_motion_failure(degraded, rng)
        elif task_key == "positioning":
            degraded = apply_positioning_failure(degraded, rng)
        elif task_key == "metal":
            degraded = apply_metal_failure(degraded, rng)
        elif task_key == "noise":
            degraded = apply_noise_failure(degraded, rng)
        elif task_key == "cardiac_motion":
            degraded = apply_cardiac_motion_failure(degraded, rng)
    return degraded


def compute_pos_weight(plans: list[VariantPlan]) -> torch.Tensor:
    """根据训练计划中的 fail / pass 分布计算 BCE `pos_weight`。"""
    positives = np.zeros(IMAGE_TASK_COUNT, dtype=np.float64)
    total = len(plans)
    for plan in plans:
        for task_key in plan.failed_task_keys:
            positives[IMAGE_TASK_INDEX[task_key]] += 1.0
    negatives = total - positives
    pos_weight = negatives / np.maximum(positives, 1.0)
    return torch.tensor(pos_weight, dtype=torch.float32)


def tensor_to_numpy(tensor: torch.Tensor) -> np.ndarray:
    """将 Tensor 拉回 CPU numpy。"""
    return tensor.detach().float().cpu().numpy()


def compute_binary_metrics(probabilities: np.ndarray, targets: np.ndarray, thresholds: np.ndarray) -> dict[str, object]:
    """计算每个图像质控项的准确率、精确率、召回率与 F1。"""
    predictions = (probabilities >= thresholds[None, :]).astype(np.float32)
    metrics: dict[str, object] = {"per_task": {}, "mean_f1": 0.0, "mean_accuracy": 0.0}
    f1_values: list[float] = []
    accuracy_values: list[float] = []

    for index, spec in enumerate(IMAGE_TASK_SPECS):
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
    thresholds = np.full(IMAGE_TASK_COUNT, 0.5, dtype=np.float32)
    search_space = np.arange(0.2, 0.81, 0.05, dtype=np.float32)

    for index in range(IMAGE_TASK_COUNT):
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
    model: ChestCtNonContrastQcModel,
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
    """将训练指标或验证预览写入 JSON。"""
    output_path.write_text(json.dumps(payload, indent=2, ensure_ascii=False), encoding="utf-8")


def build_preview_predictions(
    model: ChestCtNonContrastQcModel,
    plans: list[VariantPlan],
    base_volumes: dict[str, np.ndarray],
    thresholds: np.ndarray,
    device: torch.device,
    limit: int = 8,
) -> list[dict[str, object]]:
    """抽样生成验证集预测结果，便于快速人工检查置信度。"""
    preview: list[dict[str, object]] = []
    model.eval()
    with torch.no_grad():
        for plan in plans[:limit]:
            rng = np.random.default_rng(plan.seed)
            volume = np.copy(base_volumes[plan.volume_id])
            volume = apply_benign_intensity_jitter(volume, rng)
            volume = apply_selected_failures(volume, rng, plan.failed_task_keys)
            input_tensor = torch.from_numpy(volume[None, None, ...]).float().to(device)
            probabilities = tensor_to_numpy(torch.sigmoid(model(input_tensor)))[0]
            preview.append(
                {
                    "volume_id": plan.volume_id,
                    "variant_index": plan.variant_index,
                    "expected_failed_tasks": list(plan.failed_task_keys),
                    "predicted_failed_tasks": [
                        IMAGE_TASK_SPECS[index].key
                        for index, probability in enumerate(probabilities)
                        if probability >= thresholds[index]
                    ],
                    "task_probabilities": {
                        spec.key: round(float(probabilities[index]), 4)
                        for index, spec in enumerate(IMAGE_TASK_SPECS)
                    },
                }
            )
    return preview


def train() -> None:
    """训练胸部平扫质控模型，并保存最佳 checkpoint。"""
    args = parse_args()
    if not torch.cuda.is_available():
        raise SystemExit("CUDA is not available. Chest non contrast QC training requires CUDA.")

    set_seed(SEED)
    ensure_output_directories()

    ct_volume_paths = list_ct_volumes(NORMALIZED_DATASET_DIR)
    if not ct_volume_paths:
        raise SystemExit(f"No chest NIfTI volumes found in {NORMALIZED_DATASET_DIR}")

    train_paths, val_paths = split_volume_paths(ct_volume_paths, val_ratio=VAL_RATIO, seed=SEED)
    print(f"Train volumes: {len(train_paths)}, Val volumes: {len(val_paths)}")
    print(f"Target shape: {TARGET_SHAPE}")
    print("Preloading base chest CT volumes into memory...")
    base_volumes = preload_base_volumes(ct_volume_paths)

    train_plans = build_variant_plans(train_paths, variants_per_volume=args.train_variants, seed=SEED)
    val_plans = build_variant_plans(val_paths, variants_per_volume=args.val_variants, seed=SEED + 10000)
    print(f"Train synthetic samples: {len(train_plans)}, Val synthetic samples: {len(val_plans)}")

    train_dataset = SyntheticChestQcDataset(base_volumes=base_volumes, plans=train_plans)
    val_dataset = SyntheticChestQcDataset(base_volumes=base_volumes, plans=val_plans)
    train_loader = DataLoader(train_dataset, batch_size=args.batch_size, shuffle=True, num_workers=0, pin_memory=True)
    val_loader = DataLoader(val_dataset, batch_size=args.batch_size, shuffle=False, num_workers=0, pin_memory=True)

    device = torch.device("cuda")
    model = ChestCtNonContrastQcModel(task_count=IMAGE_TASK_COUNT).to(device)
    optimizer = torch.optim.AdamW(model.parameters(), lr=args.learning_rate, weight_decay=args.weight_decay)
    scheduler = torch.optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=args.epochs)
    criterion = nn.BCEWithLogitsLoss(pos_weight=compute_pos_weight(train_plans).to(device))
    scaler = GradScaler("cuda", enabled=True)

    history: list[dict[str, object]] = []
    best_state: dict[str, object] | None = None
    best_mean_f1 = -1.0

    for epoch in range(1, args.epochs + 1):
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
            "thresholds": {spec.key: float(thresholds[index]) for index, spec in enumerate(IMAGE_TASK_SPECS)},
            "train_per_task": train_metrics["per_task"],
            "val_per_task": val_metrics["per_task"],
        }
        history.append(epoch_summary)

        print(
            f"Epoch [{epoch}/{args.epochs}] "
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
                "image_task_keys": list(IMAGE_TASK_KEYS),
                "target_shape": list(TARGET_SHAPE),
                "train_volume_count": len(train_paths),
                "val_volume_count": len(val_paths),
            }
            torch.save(best_state, MODEL_WEIGHTS_PATH)
            print(f"  Best checkpoint updated -> {MODEL_WEIGHTS_PATH}")

    if best_state is None:
        raise SystemExit("Training finished without a valid checkpoint.")

    history_payload = {
        "config": {
            "seed": SEED,
            "batch_size": args.batch_size,
            "epochs": args.epochs,
            "learning_rate": args.learning_rate,
            "weight_decay": args.weight_decay,
            "train_variants_per_volume": args.train_variants,
            "val_variants_per_volume": args.val_variants,
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
