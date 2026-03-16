"""
CT胸部平扫质控单例推理辅助模块。
"""

from __future__ import annotations

import cv2
import nibabel as nib
import numpy as np
import torch
from pathlib import Path

from chest_ct_non_contrast_qc.model import (
    DEFAULT_THRESHOLD,
    MODEL_WEIGHTS_PATH,
    QC_TASK_INDEX,
    QC_TASK_SPECS,
    ChestCtNonContrastQcModel,
    load_model_state,
    load_preprocessed_chest_volume,
)


def load_checkpoint(device: torch.device) -> tuple[ChestCtNonContrastQcModel, np.ndarray]:
    if not MODEL_WEIGHTS_PATH.exists():
        raise FileNotFoundError(f"Model weights not found: {MODEL_WEIGHTS_PATH}")
    model = ChestCtNonContrastQcModel().to(device)
    checkpoint = torch.load(MODEL_WEIGHTS_PATH, map_location=device)
    metadata = load_model_state(model, checkpoint)
    model.eval()
    thresholds = metadata.get("thresholds")
    if isinstance(thresholds, list) and len(thresholds) == 6:
        return model, np.array(thresholds, dtype=np.float32)
    return model, np.full(6, DEFAULT_THRESHOLD, dtype=np.float32)


def build_patient_info(volume_path: Path) -> dict[str, object]:
    image = nib.load(str(volume_path))
    zooms = tuple(float(value) for value in image.header.get_zooms()[:3])
    shape = tuple(int(value) for value in image.shape[:3])
    case_id = volume_path.stem.replace(".nii", "")
    return {
        "name": case_id,
        "gender": "未知",
        "age": 0,
        "studyId": case_id,
        "accessionNumber": case_id,
        "studyDate": "",
        "device": "NIfTI import",
        "sliceCount": shape[2] if len(shape) >= 3 else 0,
        "sliceThickness": round(zooms[2], 3) if len(zooms) >= 3 else 0,
        "pixelSpacing": [
            round(zooms[0], 3) if len(zooms) >= 1 else 0,
            round(zooms[1], 3) if len(zooms) >= 2 else 0,
        ],
        "sourceMode": "local",
        "originalFilename": volume_path.name,
    }


def load_raw_volume(volume_path: Path) -> np.ndarray:
    image = nib.load(str(volume_path))
    volume = np.asanyarray(image.dataobj)
    return np.transpose(volume, (2, 1, 0)).astype(np.float32, copy=False)


def estimate_coverage_probability(volume: np.ndarray) -> float:
    """用预处理后的 3D 体数据估计扫描范围异常概率。"""
    active = np.where(volume.max(axis=(1, 2)) > 0.05)[0]
    if active.size == 0:
        return 1.0
    coverage_ratio = float(active.size) / float(volume.shape[0])
    max_margin = float(max(int(active[0]), int(volume.shape[0] - 1 - active[-1])))
    ratio_score = max(0.0, (0.88 - coverage_ratio) / 0.18)
    margin_score = max(0.0, (max_margin - 2.0) / 8.0)
    return float(np.clip(max(ratio_score, margin_score), 0.0, 1.0))


def estimate_positioning_probability(volume: np.ndarray) -> float:
    """用胸廓前景质心偏移估计体位不正概率。"""
    coordinates = np.argwhere(volume > 0.08)
    if coordinates.size == 0:
        return 1.0
    midpoint = (np.array(volume.shape) - 1) / 2.0
    offset = float(np.abs(coordinates.mean(axis=0)[1:] - midpoint[1:]).max())
    return float(np.clip((offset - 9.5) / 6.0, 0.0, 1.0))


def estimate_respiratory_motion_probability(volume: np.ndarray) -> float:
    """基于相邻层边缘变化和层间均值漂移估计呼吸伪影概率。"""
    slice_means = volume.mean(axis=(1, 2))
    slice_shift = float(np.mean(np.abs(np.diff(slice_means)))) if slice_means.size > 1 else 0.0
    sample_step = max(1, volume.shape[0] // 12)
    edge_diffs: list[float] = []
    for index in range(0, max(volume.shape[0] - 1, 1), sample_step):
        current_slice = (volume[index] * 255).astype(np.uint8)
        next_slice = (volume[min(index + 1, volume.shape[0] - 1)] * 255).astype(np.uint8)
        current_edges = cv2.Canny(current_slice, 30, 90)
        next_edges = cv2.Canny(next_slice, 30, 90)
        edge_diff = np.mean(np.abs(current_edges.astype(np.float32) - next_edges.astype(np.float32))) / 255.0
        edge_diffs.append(float(edge_diff))
    edge_score = max(0.0, ((float(np.mean(edge_diffs)) if edge_diffs else 0.0) - 0.11) / 0.08)
    shift_score = max(0.0, (slice_shift - 0.0017) / 0.0012)
    return float(np.clip(max(edge_score, shift_score), 0.0, 1.0))


def estimate_cardiac_motion_probability(volume: np.ndarray) -> float:
    """用中央胸部区域层间变化粗评估心影拖影概率。"""
    depth, height, width = volume.shape
    h_start, h_end = int(height * 0.35), int(height * 0.75)
    w_start, w_end = int(width * 0.35), int(width * 0.75)
    roi = volume[:, h_start:h_end, w_start:w_end]
    if roi.size == 0:
        return 0.0
    roi_means = roi.mean(axis=(1, 2))
    roi_shift = float(np.mean(np.abs(np.diff(roi_means)))) if roi_means.size > 1 else 0.0
    return float(np.clip((roi_shift - 0.0060) / 0.0040, 0.0, 1.0))


def estimate_noise_probability(volume: np.ndarray) -> float:
    """基于前景区域标准差估计图像噪声概率。"""
    foreground = volume[volume > 0.08]
    if foreground.size == 0:
        return 0.0
    noise_level = float(np.std(foreground))
    return float(np.clip((noise_level - 0.16) / 0.08, 0.0, 1.0))


def build_rule_based_items(raw_volume: np.ndarray) -> dict[str, dict[str, object]]:
    p1 = float(np.percentile(raw_volume, 1))
    p5 = float(np.percentile(raw_volume, 5))
    p95 = float(np.percentile(raw_volume, 95))
    p99 = float(np.percentile(raw_volume, 99))

    # 对原始 HU 分布做简单规则判断。若体数据已经被不恰当地窗宽窗位压缩，分布会明显变窄。
    lung_window_ok = p1 <= -850.0 and p99 >= 200.0
    mediastinal_ok = p5 <= -250.0 and p95 >= 100.0
    lung_window_review = not lung_window_ok and p1 <= -750.0 and p99 >= 150.0
    mediastinal_review = not mediastinal_ok and p5 <= -180.0 and p95 >= 80.0
    return {
        "lung_window": {
            "status": "合格" if lung_window_ok else "待人工确认" if lung_window_review else "不合格",
            "failProbability": 0.0 if lung_window_ok else 0.5 if lung_window_review else 1.0,
            "threshold": "p1<=-850HU & p99>=200HU",
        },
        "mediastinal_window": {
            "status": "合格" if mediastinal_ok else "待人工确认" if mediastinal_review else "不合格",
            "failProbability": 0.0 if mediastinal_ok else 0.5 if mediastinal_review else 1.0,
            "threshold": "p5<=-250HU & p95>=100HU",
        },
    }


def run_inference(volume_path: Path, model: ChestCtNonContrastQcModel, thresholds: np.ndarray, device: torch.device) -> dict[str, object]:
    volume_tensor = load_preprocessed_chest_volume(volume_path)
    raw_volume = load_raw_volume(volume_path)
    with torch.no_grad():
        probabilities = torch.sigmoid(model(volume_tensor.unsqueeze(0).unsqueeze(0).float().to(device))).cpu().numpy()[0]

    geometry_probabilities = {
        "coverage": estimate_coverage_probability(volume_tensor.numpy()),
        "positioning": estimate_positioning_probability(volume_tensor.numpy()),
        "respiratory_motion": estimate_respiratory_motion_probability(volume_tensor.numpy()),
        "noise": estimate_noise_probability(volume_tensor.numpy()),
    }
    rule_items = build_rule_based_items(raw_volume)
    qc_items: list[dict[str, object]] = []
    image_specs = [spec for spec in QC_TASK_SPECS if not spec.rule_based]
    for index, spec in enumerate(image_specs):
        model_probability = float(probabilities[index])
        heuristic_probability = float(geometry_probabilities.get(spec.key, 0.0))
        if spec.key == "coverage":
            fail_probability = 0.25 * model_probability + 0.75 * heuristic_probability
        elif spec.key == "positioning":
            fail_probability = 0.25 * model_probability + 0.75 * heuristic_probability
        elif spec.key == "respiratory_motion":
            fail_probability = 0.35 * model_probability + 0.65 * heuristic_probability
        elif spec.key == "noise":
            fail_probability = 0.45 * model_probability + 0.55 * heuristic_probability
        else:
            fail_probability = model_probability
        fail = fail_probability >= float(thresholds[index])
        review = False
        if not fail:
            review_threshold = max(0.42, float(thresholds[index]) * 0.82)
            if spec.key in {"coverage", "positioning", "respiratory_motion", "noise"}:
                review = fail_probability >= review_threshold and heuristic_probability >= 0.18
            else:
                review = fail_probability >= review_threshold
        status = "不合格" if fail else "待人工确认" if review else "合格"
        qc_items.append({
            "key": spec.key,
            "name": spec.name,
            "status": status,
            "description": spec.description,
            "detail": spec.fail_detail if status != "合格" else "",
            "failProbability": round(float(fail_probability), 4),
            "passProbability": round(1.0 - float(fail_probability), 4),
            "threshold": round(float(thresholds[index]), 4),
            "ruleBased": False,
            "modelProbability": round(model_probability, 4),
            "heuristicProbability": round(heuristic_probability, 4),
        })
    for spec in QC_TASK_SPECS:
        if not spec.rule_based:
            continue
        item = rule_items[spec.key]
        qc_items.append({
            "key": spec.key,
            "name": spec.name,
            "status": item["status"],
            "description": spec.description,
            "detail": spec.fail_detail if item["status"] == "不合格" else "",
            "failProbability": item["failProbability"],
            "passProbability": round(1.0 - float(item["failProbability"]), 4),
            "threshold": item["threshold"],
            "ruleBased": True,
        })

    fail_count = sum(1 for item in qc_items if item["status"] == "不合格")
    review_count = sum(1 for item in qc_items if item["status"] == "待人工确认")
    abnormal_count = fail_count + review_count
    quality_score = int(round((len(qc_items) - abnormal_count) * 100.0 / max(len(qc_items), 1)))
    summary_result = "不合格" if fail_count > 0 else "待人工确认" if review_count > 0 else "合格"
    return {
        "taskType": "chest-non-contrast",
        "taskTypeName": "CT胸部平扫质控",
        "mock": False,
        "modelCode": "chest_ct_non_contrast_qc",
        "modelVersion": "chest_ct_non_contrast_qc_multitask_model_v1",
        "patientInfo": build_patient_info(volume_path),
        "qcItems": qc_items,
        "summary": {
            "totalItems": len(qc_items),
            "abnormalCount": abnormal_count,
            "failCount": fail_count,
            "reviewCount": review_count,
            "qualityScore": quality_score,
            "result": summary_result,
        },
        "qcStatus": summary_result,
        "qualityScore": quality_score,
        "abnormalCount": abnormal_count,
        "primaryIssue": next((item["name"] for item in qc_items if item["status"] != "合格"), "未见明显异常"),
    }
