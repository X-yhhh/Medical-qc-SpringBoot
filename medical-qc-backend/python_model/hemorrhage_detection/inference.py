"""
头部出血多任务模型单例推理辅助模块。

供统一 WebSocket 推理服务复用，不直接监听端口。
"""

from __future__ import annotations

from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image

try:
    from hemorrhage_detection.model import (
        MODEL_WEIGHTS_PATH,
        HemorrhageMultiTaskModel,
        build_inference_transform,
        ensure_output_directories,
        load_model_state,
    )
except ImportError:
    from model import (
        MODEL_WEIGHTS_PATH,
        HemorrhageMultiTaskModel,
        build_inference_transform,
        ensure_output_directories,
        load_model_state,
    )


def load_checkpoint(device: torch.device) -> HemorrhageMultiTaskModel:
    """加载头部出血多任务模型。"""
    ensure_output_directories()
    if not MODEL_WEIGHTS_PATH.exists():
        raise FileNotFoundError(f"Model weights not found: {MODEL_WEIGHTS_PATH}")

    model = HemorrhageMultiTaskModel().to(device)
    checkpoint = torch.load(MODEL_WEIGHTS_PATH, map_location=device)
    load_model_state(model, checkpoint)
    model.eval()
    return model


def estimate_shift_score(image_path: Path) -> float:
    """使用图像重心粗略估计中线偏移距离。"""
    try:
        cv_image = cv2.imdecode(np.fromfile(image_path, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
        if cv_image is None:
            raise ValueError("Failed to read image")

        _, threshold = cv2.threshold(cv_image, 30, 255, cv2.THRESH_BINARY)
        moments = cv2.moments(threshold)
        if moments["m00"] == 0:
            return 0.0

        center_x = cv_image.shape[1] // 2
        mass_center_x = int(moments["m10"] / moments["m00"])
        shift_pixels = abs(mass_center_x - center_x)
        return round(shift_pixels * 0.5, 2)
    except Exception:
        return 5.0


def run_inference(image_path: Path, model: HemorrhageMultiTaskModel, device: torch.device) -> dict[str, object]:
    """对单张头部图片执行多任务出血推理。"""
    transform = build_inference_transform()
    image = Image.open(image_path).convert("L")
    image_tensor = transform(image).unsqueeze(0).to(device)

    with torch.no_grad():
        out_hemorrhage, out_midline, out_ventricle = model(image_tensor)
        probs_hemorrhage = torch.softmax(out_hemorrhage, dim=1)
        probs_midline = torch.softmax(out_midline, dim=1)
        probs_ventricle = torch.softmax(out_ventricle, dim=1)

    hemorrhage_probability = probs_hemorrhage[0][1].item()
    no_hemorrhage_probability = probs_hemorrhage[0][0].item()
    midline_shift_probability = probs_midline[0][1].item()
    ventricle_issue_probability = probs_ventricle[0][1].item()

    pred_hemorrhage = torch.argmax(probs_hemorrhage, dim=1).item()
    pred_midline = torch.argmax(probs_midline, dim=1).item()
    pred_ventricle = torch.argmax(probs_ventricle, dim=1).item()

    midline_shift = pred_midline == 1
    ventricle_issue = pred_ventricle == 1
    shift_score = estimate_shift_score(image_path) if midline_shift else 0.0

    return {
        "task_type": "hemorrhage",
        "prediction": "Hemorrhage" if pred_hemorrhage == 1 else "Normal",
        "confidence_level": f"{max(hemorrhage_probability, no_hemorrhage_probability) * 100:.2f}%",
        "hemorrhage_probability": hemorrhage_probability,
        "no_hemorrhage_probability": no_hemorrhage_probability,
        "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
        "midline_shift": midline_shift,
        "shift_score": shift_score,
        "midline_detail": (
            f"检测到中线偏移 (置信度: {midline_shift_probability * 100:.1f}%) 偏移约 {shift_score}mm"
            if midline_shift
            else "中线结构居中"
        ),
        "ventricle_issue": ventricle_issue,
        "ventricle_detail": (
            f"脑室区域密度/形态异常 (置信度: {ventricle_issue_probability * 100:.1f}%)"
            if ventricle_issue
            else "脑室系统形态正常"
        ),
    }
