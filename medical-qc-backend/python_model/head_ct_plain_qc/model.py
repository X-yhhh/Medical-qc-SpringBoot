"""
CT 头部平扫质控模型的共享定义。

当前模型对应前端头部平扫质控页的 6 个检测项：
1. 扫描覆盖范围
2. 体位不正
3. 运动伪影
4. 金属伪影
5. 层厚层间距
6. 剂量控制 (CTDI 代理指标)

由于现有训练数据只有 NIfTI 体数据，没有 DICOM 剂量标签，因此“剂量控制”
采用图像噪声/低剂量外观的代理监督信号。
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path

import nibabel as nib
import numpy as np
import torch
import torch.nn as nn
import torch.nn.functional as F


MODULE_DIR = Path(__file__).resolve().parent
PYTHON_MODEL_DIR = MODULE_DIR.parent
WORKSPACE_ROOT = MODULE_DIR.parents[2]

DATASET_ROOT = WORKSPACE_ROOT / "datasets" / "head_ct_plain_qc"
CT_DATASET_DIR = DATASET_ROOT / "raw" / "ct"
CBCT_DATASET_DIR = DATASET_ROOT / "raw" / "cbct"

MODELS_DIR = PYTHON_MODEL_DIR / "models" / "head_ct_plain_qc"
RESULTS_DIR = PYTHON_MODEL_DIR / "results" / "head_ct_plain_qc"
MODEL_WEIGHTS_PATH = MODELS_DIR / "head_ct_plain_qc_multitask_model.pth"

TARGET_SHAPE = (48, 96, 96)  # D, H, W
DEFAULT_THRESHOLD = 0.5


@dataclass(frozen=True)
class QcTaskSpec:
    """单个质控项的名称、描述和失败提示。"""

    key: str
    name: str
    description: str
    fail_detail: str


QC_TASK_SPECS = (
    QcTaskSpec("coverage", "扫描覆盖范围", "扫描范围应覆盖从颅底至颅顶完整区域", "扫描范围不足，颅底或颅顶覆盖不完整"),
    QcTaskSpec("positioning", "体位不正", "正中矢状面应与扫描架中心线重合", "头部存在明显偏斜或偏心，建议重新摆位"),
    QcTaskSpec("motion", "运动伪影", "图像中不应出现因患者运动导致的模糊或重影", "检测到明显运动伪影，建议固定头部后重新扫描"),
    QcTaskSpec("metal", "金属伪影", "应避免假牙、发卡等金属异物干扰", "检测到明显金属伪影，影响关键解剖结构观察"),
    QcTaskSpec("slice_thickness", "层厚层间距", "常规扫描层厚应≤5mm", "层厚/层间距异常，三维重建和细节观察受限"),
    QcTaskSpec("dose_proxy", "剂量控制 (CTDI)", "CTDIvol 应低于参考水平 (当前使用图像噪声代理)", "图像噪声偏高，提示剂量控制可能不稳定"),
)

QC_TASK_INDEX = {spec.key: index for index, spec in enumerate(QC_TASK_SPECS)}
QC_TASK_COUNT = len(QC_TASK_SPECS)


def ensure_output_directories() -> None:
    """确保当前质控项的模型和结果目录存在。"""
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)


def task_specs_as_dicts() -> list[dict[str, str]]:
    """将任务定义转换为可序列化结构，便于写入 checkpoint。"""
    return [asdict(spec) for spec in QC_TASK_SPECS]


def clip_and_normalize(volume: np.ndarray) -> np.ndarray:
    """
    将体数据裁剪到稳定强度范围并归一化到 [0, 1]。

    当前数据集整体维度一致，因此保留完整体数据，不再做前景裁剪，
    这样扫描覆盖范围和体位偏心信息不会在预处理阶段被抹掉。
    """
    volume = volume.astype(np.float32, copy=False)
    foreground = volume[volume > 0]
    sample = foreground if foreground.size else volume.reshape(-1)

    low = float(np.percentile(sample, 1))
    high = float(np.percentile(sample, 99.5))
    if high <= low:
        low = float(sample.min())
        high = float(sample.max())
    if high <= low:
        high = low + 1.0

    volume = np.clip(volume, low, high)
    volume = (volume - low) / (high - low)
    return volume.astype(np.float32, copy=False)


def resize_volume(volume: np.ndarray, target_shape: tuple[int, int, int] = TARGET_SHAPE) -> np.ndarray:
    """将 `Z,Y,X` 体数据缩放到固定 3D 输入尺寸。"""
    tensor = torch.from_numpy(volume).unsqueeze(0).unsqueeze(0)
    resized = F.interpolate(tensor, size=target_shape, mode="trilinear", align_corners=False)
    return resized.squeeze(0).squeeze(0).numpy().astype(np.float32, copy=False)


def load_preprocessed_volume(volume_path: Path, target_shape: tuple[int, int, int] = TARGET_SHAPE) -> np.ndarray:
    """
    读取 NIfTI 体数据并转换为模型使用的 `D,H,W` 浮点数组。

    nibabel 读取出来是 `X,Y,Z`，这里统一转成 `Z,Y,X`，与 PyTorch 3D 卷积约定一致。
    """
    image = nib.load(str(volume_path))
    volume = np.asanyarray(image.dataobj)
    volume = np.transpose(volume, (2, 1, 0))
    volume = clip_and_normalize(volume)
    return resize_volume(volume, target_shape=target_shape)


class ConvBlock3d(nn.Module):
    """小型 3D 卷积块，适合 8GB 显存下的多任务训练。"""

    def __init__(self, in_channels: int, out_channels: int) -> None:
        super().__init__()
        groups = 8 if out_channels >= 8 else 1
        self.block = nn.Sequential(
            nn.Conv3d(in_channels, out_channels, kernel_size=3, padding=1, bias=False),
            nn.GroupNorm(groups, out_channels),
            nn.SiLU(inplace=True),
            nn.Conv3d(out_channels, out_channels, kernel_size=3, padding=1, bias=False),
            nn.GroupNorm(groups, out_channels),
            nn.SiLU(inplace=True),
            nn.MaxPool3d(kernel_size=2, stride=2),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.block(x)


class HeadCtPlainQcModel(nn.Module):
    """
    头部 CT 平扫质控多任务模型。

    输出为 6 个任务的 failure logits，数值越大表示对应质控项越可能不合格。
    """

    def __init__(self, task_count: int = QC_TASK_COUNT) -> None:
        super().__init__()
        self.backbone = nn.Sequential(
            ConvBlock3d(1, 16),
            ConvBlock3d(16, 32),
            ConvBlock3d(32, 64),
            ConvBlock3d(64, 96),
        )
        self.pool = nn.AdaptiveAvgPool3d((1, 1, 1))
        self.head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(96, 96),
            nn.SiLU(inplace=True),
            nn.Dropout(p=0.25),
            nn.Linear(96, task_count),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        features = self.backbone(x)
        pooled = self.pool(features)
        return self.head(pooled)


def load_model_state(model: nn.Module, checkpoint: object) -> dict[str, object]:
    """
    兼容直接 state_dict 和包含元数据的 checkpoint。

    返回:
        checkpoint 中除权重之外的附加元数据；若没有则返回空字典。
    """
    if isinstance(checkpoint, dict) and "model_state_dict" in checkpoint:
        model.load_state_dict(checkpoint["model_state_dict"])
        return {key: value for key, value in checkpoint.items() if key != "model_state_dict"}

    model.load_state_dict(checkpoint)
    return {}
