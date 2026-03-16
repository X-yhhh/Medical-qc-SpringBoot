"""
统一 WebSocket 推理服务。

当前仅承载真实推理质控项：
1. 头部出血检测
2. CT 头部平扫质控
3. CT 胸部平扫质控
"""

from __future__ import annotations

import asyncio
import json
import os
import time
from pathlib import Path

import torch
import websockets

from chest_contrast_qc.inference import run_inference as run_chest_contrast_qc_inference
from chest_ct_non_contrast_qc.inference import load_checkpoint as load_chest_qc_checkpoint
from chest_ct_non_contrast_qc.inference import run_inference as run_chest_qc_inference
from coronary_cta_qc.inference import run_inference as run_coronary_cta_qc_inference
from head_ct_plain_qc.inference import load_checkpoint as load_head_qc_checkpoint
from head_ct_plain_qc.inference import run_inference as run_head_qc_inference
from hemorrhage_detection.inference import load_checkpoint as load_hemorrhage_checkpoint
from hemorrhage_detection.inference import run_inference as run_hemorrhage_inference
from medical_volume_utils import enrich_result_with_medical_input, prepare_volume_input


hemorrhage_model = None
head_qc_model = None
head_qc_thresholds = None
chest_qc_model = None
chest_qc_thresholds = None

if not torch.cuda.is_available():
    raise RuntimeError("CUDA is not available. Unified inference server requires CUDA.")

cuda_index = int(os.environ.get("PYTHON_MODEL_CUDA_INDEX", "0"))
device = torch.device(f"cuda:{cuda_index}")
print(f"Using CUDA device: {torch.cuda.get_device_name(cuda_index)}")


def load_models() -> None:
    """加载当前服务承载的全部模型。"""
    global hemorrhage_model, head_qc_model, head_qc_thresholds
    global chest_qc_model, chest_qc_thresholds

    print("Loading hemorrhage model ...")
    hemorrhage_model = load_hemorrhage_checkpoint(device)
    print("Hemorrhage model loaded.")

    print("Loading head plain QC model ...")
    head_qc_model, head_qc_thresholds = load_head_qc_checkpoint(device)
    print("Head plain QC model loaded.")

    try:
        print("Loading chest non contrast QC model ...")
        chest_qc_model, chest_qc_thresholds = load_chest_qc_checkpoint(device)
        print("Chest non contrast QC model loaded.")
    except Exception as exc:
        chest_qc_model = None
        chest_qc_thresholds = None
        print(f"Chest non contrast QC model unavailable: {exc}")


def analyze_request(payload: dict[str, object]) -> dict[str, object]:
    """根据 task_type 将请求分发到对应模型。"""
    task_type = str(payload.get("task_type") or "hemorrhage").strip().lower()
    if task_type == "hemorrhage":
        image_path = Path(str(payload.get("image_path", "")))
        if hemorrhage_model is None:
            return {"error": "Hemorrhage model not loaded"}
        if not image_path.exists():
            return {"error": f"File not found: {image_path}"}
        return run_hemorrhage_inference(image_path, model=hemorrhage_model, device=device)

    if task_type == "head":
        if head_qc_model is None or head_qc_thresholds is None:
            return {"error": "Head CT plain QC model not loaded"}
        managed_input = prepare_volume_input(str(payload.get("volume_path", "") or payload.get("input_path", "")))
        try:
            result = run_head_qc_inference(
                managed_input.analysis_path,
                device=device,
                model=head_qc_model,
                thresholds=head_qc_thresholds,
            )
            return enrich_result_with_medical_input(result, managed_input, "head")
        finally:
            managed_input.cleanup()

    if task_type == "chest-non-contrast":
        if chest_qc_model is None or chest_qc_thresholds is None:
            return {"error": "Chest non contrast QC model not loaded"}
        managed_input = prepare_volume_input(str(payload.get("volume_path", "") or payload.get("input_path", "")))
        try:
            result = run_chest_qc_inference(
                managed_input.analysis_path,
                model=chest_qc_model,
                thresholds=chest_qc_thresholds,
                device=device,
            )
            return enrich_result_with_medical_input(result, managed_input, "chest-non-contrast")
        finally:
            managed_input.cleanup()

    if task_type == "chest-contrast":
        managed_input = prepare_volume_input(str(payload.get("volume_path", "") or payload.get("input_path", "")))
        try:
            result = run_chest_contrast_qc_inference(managed_input.analysis_path, metadata=payload)
            return enrich_result_with_medical_input(result, managed_input, "chest-contrast")
        finally:
            managed_input.cleanup()

    if task_type == "coronary-cta":
        managed_input = prepare_volume_input(str(payload.get("volume_path", "") or payload.get("input_path", "")))
        try:
            result = run_coronary_cta_qc_inference(managed_input.analysis_path, metadata=payload)
            return enrich_result_with_medical_input(result, managed_input, "coronary-cta")
        finally:
            managed_input.cleanup()

    return {"error": f"Unsupported task_type: {task_type}"}


async def handle_connection(websocket: websockets.WebSocketServerProtocol) -> None:
    """处理 Java 后端发来的健康检查或推理请求。"""
    print(f"New connection from {websocket.remote_address}")
    try:
        async for message in websocket:
            payload = json.loads(message)

            if payload.get("health_check"):
                # 健康检查只把三条真实模型链路纳入“模型已加载”语义。
                real_models = {
                    "hemorrhage": hemorrhage_model is not None,
                    "head": head_qc_model is not None,
                    "chest-non-contrast": chest_qc_model is not None,
                }
                mock_capabilities = {
                    "chest-contrast": True,
                    "coronary-cta": True,
                }
                await websocket.send(
                    json.dumps(
                        {
                            "status": "ok",
                            # 统一服务仅以三条真实链路是否全部可用作为整体健康信号。
                            "model_loaded": all(real_models.values()),
                            "device": torch.cuda.get_device_name(cuda_index),
                            # 为兼容旧排查脚本，models 字段仍保留，但 mock 链路明确标记为 false。
                            "models": {
                                **real_models,
                                "chest-contrast": False,
                                "coronary-cta": False,
                            },
                            "realModels": real_models,
                            "mockCapabilities": mock_capabilities,
                        }
                    )
                )
                continue

            try:
                start_time = time.time()
                result = analyze_request(payload)
                if "error" not in result:
                    result["analysis_duration"] = round((time.time() - start_time) * 1000, 2)
                    result["device"] = torch.cuda.get_device_name(cuda_index)
                await websocket.send(json.dumps(result, ensure_ascii=False))
            except Exception as exc:
                await websocket.send(json.dumps({"error": str(exc)}))
    except websockets.exceptions.ConnectionClosed:
        print("Connection closed")


async def main() -> None:
    """启动统一推理 WebSocket 服务。"""
    load_models()
    async with websockets.serve(handle_connection, "localhost", 8765):
        print("Unified WebSocket inference server started on ws://localhost:8765")
        await asyncio.Future()


if __name__ == "__main__":
    asyncio.run(main())
