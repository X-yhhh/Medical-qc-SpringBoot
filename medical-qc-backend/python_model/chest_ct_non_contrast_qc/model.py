"""
CT胸部平扫质控模型共享定义。
"""

from __future__ import annotations

from dataclasses import asdict, dataclass
from pathlib import Path

import torch
import torch.nn as nn

try:
    from head_ct_plain_qc.model import ConvBlock3d, load_preprocessed_volume
except ImportError:
    import sys
    sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
    from head_ct_plain_qc.model import ConvBlock3d, load_preprocessed_volume


MODULE_DIR = Path(__file__).resolve().parent
PYTHON_MODEL_DIR = MODULE_DIR.parent
WORKSPACE_ROOT = MODULE_DIR.parents[2]

DATASET_ROOT = WORKSPACE_ROOT / "datasets" / "chest_ct_non_contrast_qc"
NORMALIZED_DATASET_DIR = DATASET_ROOT / "normalized"

MODELS_DIR = PYTHON_MODEL_DIR / "models" / "chest_ct_non_contrast_qc"
RESULTS_DIR = PYTHON_MODEL_DIR / "results" / "chest_ct_non_contrast_qc"
MODEL_WEIGHTS_PATH = MODELS_DIR / "chest_ct_non_contrast_qc_multitask_model.pth"

TARGET_SHAPE = (64, 96, 96)
DEFAULT_THRESHOLD = 0.5


@dataclass(frozen=True)
class QcTaskSpec:
    key: str
    name: str
    description: str
    fail_detail: str
    rule_based: bool = False


QC_TASK_SPECS = (
    QcTaskSpec("coverage", "扫描范围", "肺尖至肺底完整覆盖", "胸部扫描范围不完整，建议重新定位"),
    QcTaskSpec("respiratory_motion", "呼吸伪影", "无明显呼吸运动伪影", "存在明显呼吸伪影，建议加强屏气"),
    QcTaskSpec("positioning", "体位不正", "患者居中，无倾斜", "体位偏斜明显，建议重新摆位"),
    QcTaskSpec("metal", "金属伪影", "无明显金属伪影干扰", "金属伪影影响肺部评估"),
    QcTaskSpec("noise", "图像噪声", "噪声指数符合诊断要求", "图像噪声偏高，细节显示不足"),
    QcTaskSpec("cardiac_motion", "心影干扰", "心脏搏动伪影在可接受范围内", "心影拖影明显，建议优化采集"),
    QcTaskSpec("lung_window", "肺窗设置", "窗宽窗位适宜观察肺纹理", "肺窗显示范围异常", True),
    QcTaskSpec("mediastinal_window", "纵隔窗设置", "窗宽窗位适宜观察纵隔结构", "纵隔窗显示范围异常", True),
)

QC_TASK_INDEX = {spec.key: index for index, spec in enumerate(QC_TASK_SPECS)}
QC_TASK_COUNT = len(QC_TASK_SPECS)
RULE_BASED_TASKS = {spec.key for spec in QC_TASK_SPECS if spec.rule_based}


def ensure_output_directories() -> None:
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)


def task_specs_as_dicts() -> list[dict[str, object]]:
    return [asdict(spec) for spec in QC_TASK_SPECS]


def load_preprocessed_chest_volume(volume_path: Path) -> torch.Tensor:
    volume = load_preprocessed_volume(volume_path, target_shape=TARGET_SHAPE)
    return torch.from_numpy(volume)


class ChestCtNonContrastQcModel(nn.Module):
    """
    胸部平扫多任务质控模型。

    仅输出图像模型项；规则项在推理阶段直接基于体数据统计判断。
    """

    def __init__(self, task_count: int = 6) -> None:
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
            nn.Dropout(0.25),
            nn.Linear(96, task_count),
        )

    def forward(self, x: torch.Tensor) -> torch.Tensor:
        return self.head(self.pool(self.backbone(x)))


def load_model_state(model: nn.Module, checkpoint: object) -> dict[str, object]:
    if isinstance(checkpoint, dict) and "model_state_dict" in checkpoint:
        model.load_state_dict(checkpoint["model_state_dict"])
        return {key: value for key, value in checkpoint.items() if key != "model_state_dict"}
    model.load_state_dict(checkpoint)
    return {}
