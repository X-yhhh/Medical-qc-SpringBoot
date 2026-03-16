"""
头部出血多任务模型的共享定义。

本文件集中管理：
1. 模型结构定义
2. 数据集/权重/结果目录
3. 推理与训练共用的基础预处理
"""

from __future__ import annotations

from pathlib import Path

import torch
import torch.nn as nn
from torchvision import transforms


MODULE_DIR = Path(__file__).resolve().parent
PYTHON_MODEL_DIR = MODULE_DIR.parent
PROJECT_ROOT = PYTHON_MODEL_DIR.parent.parent

DATASET_ROOT = PROJECT_ROOT / "datasets" / "head_ct_hemorrhage_detection"
IMAGES_DIR = DATASET_ROOT / "images"
LABELS_FILE = DATASET_ROOT / "labels.csv"

MODELS_DIR = PYTHON_MODEL_DIR / "models" / "hemorrhage_detection"
RESULTS_DIR = PYTHON_MODEL_DIR / "results" / "hemorrhage_detection"
MODEL_WEIGHTS_PATH = MODELS_DIR / "hemorrhage_multitask_model.pth"

IMAGE_SIZE = (224, 224)
INPUT_MEAN = (0.5,)
INPUT_STD = (0.5,)


def ensure_output_directories() -> None:
    """确保当前任务的模型与结果目录存在。"""
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    RESULTS_DIR.mkdir(parents=True, exist_ok=True)


def build_inference_transform() -> transforms.Compose:
    """构建与训练保持一致的基础推理预处理。"""
    return transforms.Compose(
        [
            transforms.Resize(IMAGE_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(INPUT_MEAN, INPUT_STD),
        ]
    )


class HemorrhageMultiTaskModel(nn.Module):
    """
    头部出血多任务模型。

    输出三个二分类任务：
    1. 脑出血
    2. 中线偏移
    3. 脑室异常
    """

    def __init__(self) -> None:
        super().__init__()

        # 三个任务共享同一套卷积特征，减少参数量并保持推理一致性。
        self.features = nn.Sequential(
            nn.Conv2d(1, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.Conv2d(128, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2),
            nn.AdaptiveAvgPool2d((1, 1)),
        )

        # 每个任务各自维护一个输出头，避免标签语义相互污染。
        self.hemorrhage_head = self._build_task_head()
        self.midline_head = self._build_task_head()
        self.ventricle_head = self._build_task_head()

    @staticmethod
    def _build_task_head() -> nn.Sequential:
        """构建统一规格的二分类任务头。"""
        return nn.Sequential(
            nn.Flatten(),
            nn.Linear(256, 128),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),
            nn.Linear(128, 2),
        )

    def forward(self, x: torch.Tensor) -> tuple[torch.Tensor, torch.Tensor, torch.Tensor]:
        """返回三个任务头的 logits。"""
        features = self.features(x)
        hemorrhage_out = self.hemorrhage_head(features)
        midline_out = self.midline_head(features)
        ventricle_out = self.ventricle_head(features)
        return hemorrhage_out, midline_out, ventricle_out


def load_model_state(model: nn.Module, checkpoint: object) -> None:
    """
    兼容两种权重格式：
    1. 直接保存的 state_dict
    2. 含 model_state_dict 的训练 checkpoint
    """
    if isinstance(checkpoint, dict) and "model_state_dict" in checkpoint:
        model.load_state_dict(checkpoint["model_state_dict"])
        return
    model.load_state_dict(checkpoint)
