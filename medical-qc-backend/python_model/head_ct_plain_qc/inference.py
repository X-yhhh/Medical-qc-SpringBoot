"""
CT 头部平扫质控模型单例推理脚本。

输出结构尽量贴近当前前端/后端的 mock 结果格式：
- patientInfo
- qcItems
- summary
"""

from __future__ import annotations

import argparse
import json
import time
from pathlib import Path

import cv2
import nibabel as nib
import numpy as np
import torch

try:
    from head_ct_plain_qc.model import (
        DEFAULT_THRESHOLD,
        MODEL_WEIGHTS_PATH,
        QC_TASK_SPECS,
        HeadCtPlainQcModel,
        load_model_state,
        load_preprocessed_volume,
    )
except ImportError:
    from model import (
        DEFAULT_THRESHOLD,
        MODEL_WEIGHTS_PATH,
        QC_TASK_SPECS,
        HeadCtPlainQcModel,
        load_model_state,
        load_preprocessed_volume,
    )


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description="Run head CT plain QC inference on a single NIfTI volume.")
    parser.add_argument("--volume-path", type=Path, required=True, help="Input NIfTI volume path.")
    parser.add_argument("--output-json", type=Path, default=None, help="Optional path to save the JSON result.")
    parser.add_argument(
        "--thresholds",
        type=float,
        nargs="*",
        default=None,
        help="Optional manual thresholds for 6 QC tasks. If omitted, checkpoint thresholds are used.",
    )
    return parser.parse_args()


def load_checkpoint(device: torch.device) -> tuple[HeadCtPlainQcModel, np.ndarray]:
    """加载训练好的模型权重和阈值。"""
    if not MODEL_WEIGHTS_PATH.exists():
        raise FileNotFoundError(f"Model weights not found: {MODEL_WEIGHTS_PATH}")

    model = HeadCtPlainQcModel().to(device)
    checkpoint = torch.load(MODEL_WEIGHTS_PATH, map_location=device)
    metadata = load_model_state(model, checkpoint)
    model.eval()

    checkpoint_thresholds = metadata.get("thresholds")
    if isinstance(checkpoint_thresholds, list) and len(checkpoint_thresholds) == len(QC_TASK_SPECS):
        thresholds = np.array(checkpoint_thresholds, dtype=np.float32)
    else:
        thresholds = np.full(len(QC_TASK_SPECS), DEFAULT_THRESHOLD, dtype=np.float32)
    return model, thresholds


def volume_id(volume_path: Path) -> str:
    """提取不带 `.nii.gz` 后缀的体数据编号。"""
    name = volume_path.name
    return name[:-7] if name.endswith(".nii.gz") else volume_path.stem


def build_patient_info(volume_path: Path) -> dict[str, object]:
    """从 NIfTI header 中提取前端展示需要的基础元数据。"""
    image = nib.load(str(volume_path))
    zooms = tuple(float(value) for value in image.header.get_zooms()[:3])
    case_id = volume_id(volume_path)
    return {
        "name": case_id,
        "gender": "未知",
        "age": 0,
        "studyId": case_id,
        "accessionNumber": case_id,
        "studyDate": "",
        "device": "NIfTI import",
        "sliceCount": int(image.shape[2]),
        "sliceThickness": round(zooms[2], 3),
        "pixelSpacing": [round(zooms[0], 3), round(zooms[1], 3)],
        "sourceMode": "local",
        "originalFilename": volume_path.name,
    }


def load_raw_volume(volume_path: Path) -> np.ndarray:
    """读取原始体数据，并统一转成 Z,Y,X。"""
    image = nib.load(str(volume_path))
    volume = np.asanyarray(image.dataobj).astype(np.float32, copy=False)
    return np.transpose(volume, (2, 1, 0))


def estimate_coverage_probability(volume: np.ndarray) -> float:
    """基于前景层面分布估计扫描覆盖不足概率。"""
    active = np.where(volume.max(axis=(1, 2)) > 0.05)[0]
    if active.size == 0:
        return 1.0
    coverage_ratio = float(active.size) / float(volume.shape[0])
    max_margin = float(max(int(active[0]), int(volume.shape[0] - 1 - active[-1])))
    ratio_score = max(0.0, (0.80 - coverage_ratio) / 0.12)
    margin_score = max(0.0, (max_margin - 2.0) / 6.0)
    return float(np.clip(max(ratio_score, margin_score), 0.0, 1.0))


def estimate_positioning_probability(volume: np.ndarray) -> float:
    """基于头部前景质心偏移估计体位不正概率。"""
    coordinates = np.argwhere(volume > 0.05)
    if coordinates.size == 0:
        return 1.0
    midpoint = (np.array(volume.shape) - 1) / 2.0
    offset = float(np.abs(coordinates.mean(axis=0)[1:] - midpoint[1:]).max())
    return float(np.clip((offset - 11.5) / 3.5, 0.0, 1.0))


def estimate_motion_probability(volume: np.ndarray) -> float:
    """基于层间灰度漂移与边缘变化估计运动伪影概率。"""
    slice_means = volume.mean(axis=(1, 2))
    slice_shift = float(np.mean(np.abs(np.diff(slice_means)))) if slice_means.size > 1 else 0.0
    sample_step = max(1, volume.shape[0] // 12)
    edge_diffs: list[float] = []
    for index in range(0, volume.shape[0] - 1, sample_step):
        current_slice = (volume[index] * 255).astype(np.uint8)
        next_slice = (volume[min(index + 1, volume.shape[0] - 1)] * 255).astype(np.uint8)
        current_edges = cv2.Canny(current_slice, 40, 120)
        next_edges = cv2.Canny(next_slice, 40, 120)
        edge_diff = np.mean(np.abs(current_edges.astype(np.float32) - next_edges.astype(np.float32))) / 255.0
        edge_diffs.append(float(edge_diff))
    edge_score = max(0.0, ((float(np.mean(edge_diffs)) if edge_diffs else 0.0) - 0.16) / 0.12)
    shift_score = max(0.0, (slice_shift - 0.0038) / 0.0015)
    return float(np.clip(max(edge_score, shift_score), 0.0, 1.0))


def estimate_metal_probability(raw_volume: np.ndarray) -> float:
    """基于高密度峰值和高密度体素占比估计金属伪影概率。"""
    high_density_ratio = float(np.mean(raw_volume >= 2000.0))
    max_value = float(raw_volume.max())
    peak_score = max(0.0, (max_value - 1500.0) / 1500.0)
    ratio_score = max(0.0, (high_density_ratio - 0.00001) / 0.00005)
    return float(np.clip(max(peak_score, ratio_score), 0.0, 1.0))


def estimate_dose_proxy_probability(volume: np.ndarray) -> float:
    """基于前景区域噪声水平估计低剂量噪声代理概率。"""
    foreground = volume[volume > 0.05]
    if foreground.size == 0:
        return 0.0
    noise_level = float(np.std(foreground))
    return float(np.clip((noise_level - 0.18) / 0.08, 0.0, 1.0))


def build_qc_items(
    probabilities: np.ndarray,
    thresholds: np.ndarray,
    patient_info: dict[str, object],
    volume: np.ndarray,
    raw_volume: np.ndarray,
) -> list[dict[str, object]]:
    """将模型输出概率转换为前端质控项列表。"""
    heuristic_probabilities = {
        "coverage": estimate_coverage_probability(volume),
        "positioning": estimate_positioning_probability(volume),
        "motion": estimate_motion_probability(volume),
        "metal": estimate_metal_probability(raw_volume),
        "dose_proxy": estimate_dose_proxy_probability(volume),
    }
    qc_items: list[dict[str, object]] = []
    for index, spec in enumerate(QC_TASK_SPECS):
        model_probability = float(probabilities[index])
        heuristic_probability = float(heuristic_probabilities.get(spec.key, 0.0))
        fail_probability = max(model_probability, heuristic_probability)
        review_probability = max(model_probability, heuristic_probability)
        fail = fail_probability >= float(thresholds[index])
        review = False

        # 层厚层间距本质上属于扫描元数据，直接使用 header 里的 spacing_z 更可靠。
        if spec.key == "slice_thickness":
            slice_thickness = float(patient_info["sliceThickness"])
            if slice_thickness > 5.0:
                fail = True
                review = False
                fail_probability = 1.0
            elif slice_thickness > 4.0:
                fail = False
                review = True
                fail_probability = 0.5
            else:
                fail = False
                review = False
                fail_probability = 0.0
        elif not fail:
            review_threshold = max(0.35, float(thresholds[index]) * 0.75)
            review = review_probability >= review_threshold

        threshold_value = 5.0 if spec.key == "slice_thickness" else float(thresholds[index])
        status = "不合格" if fail else "待人工确认" if review else "合格"
        detail = spec.fail_detail if status != "合格" else ""
        qc_items.append(
            {
                "key": spec.key,
                "name": spec.name,
                "status": status,
                "description": spec.description,
                "detail": detail,
                "failProbability": round(fail_probability, 4),
                "passProbability": round(1.0 - fail_probability, 4),
                "threshold": round(threshold_value, 4),
                "ruleBased": spec.key == "slice_thickness",
                "modelProbability": round(model_probability, 4),
                "heuristicProbability": round(heuristic_probability, 4),
            }
        )
    return qc_items


def build_summary(qc_items: list[dict[str, object]]) -> dict[str, object]:
    """根据单项结果计算总分和结论。"""
    fail_count = sum(1 for item in qc_items if item["status"] == "不合格")
    review_count = sum(1 for item in qc_items if item["status"] == "待人工确认")
    abnormal_count = fail_count + review_count
    total_items = len(qc_items)
    quality_score = int(round((total_items - abnormal_count) * 100.0 / max(total_items, 1)))
    return {
        "totalItems": total_items,
        "abnormalCount": abnormal_count,
        "failCount": fail_count,
        "reviewCount": review_count,
        "qualityScore": quality_score,
        "result": "不合格" if fail_count > 0 else "待人工确认" if review_count > 0 else "合格",
    }


def run_inference(
    volume_path: Path,
    device: torch.device,
    model: HeadCtPlainQcModel,
    thresholds: np.ndarray,
) -> dict[str, object]:
    """对单个头部 CT 平扫 NIfTI 体数据执行质控推理。"""
    volume = load_preprocessed_volume(volume_path)
    raw_volume = load_raw_volume(volume_path)
    input_tensor = torch.from_numpy(volume[None, None, ...]).float().to(device)

    with torch.no_grad():
        probabilities = torch.sigmoid(model(input_tensor)).detach().cpu().numpy()[0]

    patient_info = build_patient_info(volume_path)
    qc_items = build_qc_items(probabilities, thresholds, patient_info, volume, raw_volume)
    return {
        "taskType": "head",
        "taskTypeName": "CT头部平扫质控",
        "mock": False,
        "modelCode": "head_ct_plain_qc",
        "modelVersion": "head_ct_plain_qc_multitask_model_v1",
        "patientInfo": patient_info,
        "qcItems": qc_items,
        "summary": build_summary(qc_items),
    }


def main() -> None:
    args = parse_args()
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    model, checkpoint_thresholds = load_checkpoint(device)
    thresholds = checkpoint_thresholds
    if args.thresholds:
        if len(args.thresholds) != len(QC_TASK_SPECS):
            raise ValueError(f"Expected {len(QC_TASK_SPECS)} thresholds, got {len(args.thresholds)}")
        thresholds = np.array(args.thresholds, dtype=np.float32)

    start_time = time.time()
    result = run_inference(args.volume_path, device=device, model=model, thresholds=thresholds)
    result["duration"] = int(round((time.time() - start_time) * 1000))

    if args.output_json:
        args.output_json.write_text(json.dumps(result, ensure_ascii=False, indent=2), encoding="utf-8")
        print(f"Saved result to {args.output_json}")

    print(json.dumps(result, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
