"""
头部出血多任务模型训练入口。

当前训练的是后端正在使用的多任务模型，输出：
1. 脑出血
2. 中线偏移
3. 脑室异常
"""

from __future__ import annotations

import copy
import csv
import random
from dataclasses import dataclass
from pathlib import Path

import cv2
import numpy as np
import torch
import torch.nn as nn
import torch.optim as optim
from PIL import Image
from torch.utils.data import DataLoader, Dataset
from torchvision import transforms

from model import (
    IMAGE_SIZE,
    IMAGES_DIR,
    INPUT_MEAN,
    INPUT_STD,
    LABELS_FILE,
    MODEL_WEIGHTS_PATH,
    RESULTS_DIR,
    HemorrhageMultiTaskModel,
    ensure_output_directories,
)


SEED = 42
BATCH_SIZE = 16
EPOCHS = 25
LEARNING_RATE = 0.0008
VAL_RATIO = 0.2


@dataclass
class Sample:
    """单个训练样本。"""

    image_path: Path
    hemorrhage_label: int
    midline_label: int
    ventricle_label: int


class HeadCtHemorrhageDataset(Dataset):
    """将多任务标签与灰度 CT 图像打包为 DataLoader 可消费的样本。"""

    def __init__(self, samples: list[Sample], transform: transforms.Compose) -> None:
        self.samples = samples
        self.transform = transform

    def __len__(self) -> int:
        return len(self.samples)

    def __getitem__(self, idx: int) -> tuple[torch.Tensor, torch.Tensor]:
        sample = self.samples[idx]
        image = Image.open(sample.image_path).convert("L")
        image_tensor = self.transform(image)
        labels_tensor = torch.tensor(
            [sample.hemorrhage_label, sample.midline_label, sample.ventricle_label],
            dtype=torch.long,
        )
        return image_tensor, labels_tensor


def read_grayscale_cv2(image_path: Path) -> np.ndarray | None:
    """使用 imdecode 兼容 Windows 中文路径。"""
    try:
        raw = np.fromfile(image_path, dtype=np.uint8)
        if raw.size == 0:
            return None
        return cv2.imdecode(raw, cv2.IMREAD_GRAYSCALE)
    except Exception:
        return None


def compute_midline_shift_label(gray_224: np.ndarray) -> int:
    """使用图像二值化后的重心偏移生成中线偏移伪标签。"""
    _, mask = cv2.threshold(gray_224, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8), iterations=2)
    moments = cv2.moments(mask)
    if moments["m00"] == 0:
        return 0
    center_x = gray_224.shape[1] // 2
    mass_center_x = int(moments["m10"] / moments["m00"])
    shift_pixels = abs(mass_center_x - center_x)
    return 1 if shift_pixels >= 10 else 0


def compute_ventricle_issue_label(gray_224: np.ndarray) -> int:
    """使用中央 ROI 暗区比例生成脑室异常伪标签。"""
    height, width = gray_224.shape[:2]
    x0, x1 = int(width * 0.35), int(width * 0.65)
    y0, y1 = int(height * 0.35), int(height * 0.65)
    roi = gray_224[y0:y1, x0:x1]
    if roi.size == 0:
        return 0
    dark_ratio = float(np.mean(roi < 40))
    return 1 if (dark_ratio < 0.03 or dark_ratio > 0.35) else 0


def load_samples(images_dir: Path, labels_file: Path, seed: int) -> list[Sample]:
    """从 `labels.csv` 构建多任务训练样本列表。"""
    samples: list[Sample] = []
    with labels_file.open("r", newline="", encoding="utf-8") as file:
        reader = csv.DictReader(file, skipinitialspace=True)
        for row in reader:
            image_id = int(str(row["id"]).strip())
            hemorrhage_label = int(str(row["hemorrhage"]).strip())
            image_path = images_dir / f"{image_id:03d}.png"

            gray = read_grayscale_cv2(image_path)
            if gray is None:
                continue

            gray_224 = cv2.resize(gray, IMAGE_SIZE, interpolation=cv2.INTER_AREA)
            samples.append(
                Sample(
                    image_path=image_path,
                    hemorrhage_label=hemorrhage_label,
                    midline_label=compute_midline_shift_label(gray_224),
                    ventricle_label=compute_ventricle_issue_label(gray_224),
                )
            )

    random.Random(seed).shuffle(samples)
    return samples


def split_train_val(samples: list[Sample], val_ratio: float, seed: int) -> tuple[list[Sample], list[Sample]]:
    """按固定随机种子划分训练集与验证集。"""
    shuffled = list(samples)
    random.Random(seed).shuffle(shuffled)
    val_size = int(len(shuffled) * val_ratio)
    return shuffled[val_size:], shuffled[:val_size]


def compute_class_weights(labels: list[int]) -> torch.Tensor:
    """计算二分类交叉熵所需的类别权重。"""
    negatives = sum(1 for label in labels if label == 0)
    positives = sum(1 for label in labels if label == 1)
    total = max(1, negatives + positives)
    weight_negative = total / max(1, negatives)
    weight_positive = total / max(1, positives)
    return torch.tensor([weight_negative, weight_positive], dtype=torch.float32)


def build_train_transform() -> transforms.Compose:
    """训练阶段启用轻量数据增强。"""
    return transforms.Compose(
        [
            transforms.Resize(IMAGE_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(INPUT_MEAN, INPUT_STD),
        ]
    )


def build_val_transform() -> transforms.Compose:
    """验证阶段只做基础预处理，保持评估稳定。"""
    return transforms.Compose(
        [
            transforms.Resize(IMAGE_SIZE),
            transforms.ToTensor(),
            transforms.Normalize(INPUT_MEAN, INPUT_STD),
        ]
    )


def train() -> None:
    """训练当前生产使用的出血多任务模型。"""
    if not torch.cuda.is_available():
        print("ERROR: CUDA is not available! Hemorrhage training requires CUDA.")
        return

    ensure_output_directories()
    device = torch.device("cuda")
    print(f"Using device: {device} ({torch.cuda.get_device_name(0)})")
    print(f"Dataset images dir: {IMAGES_DIR}")
    print(f"Labels file: {LABELS_FILE}")

    samples = load_samples(IMAGES_DIR, LABELS_FILE, seed=SEED)
    if not samples:
        print(f"ERROR: No training samples loaded. Check {IMAGES_DIR} and {LABELS_FILE}")
        return

    train_samples, val_samples = split_train_val(samples, val_ratio=VAL_RATIO, seed=SEED)
    train_dataset = HeadCtHemorrhageDataset(train_samples, transform=build_train_transform())
    val_dataset = HeadCtHemorrhageDataset(val_samples, transform=build_val_transform())

    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

    hemorrhage_weights = compute_class_weights([sample.hemorrhage_label for sample in train_samples]).to(device)
    midline_weights = compute_class_weights([sample.midline_label for sample in train_samples]).to(device)
    ventricle_weights = compute_class_weights([sample.ventricle_label for sample in train_samples]).to(device)

    model = HemorrhageMultiTaskModel().to(device)
    optimizer = optim.Adam(model.parameters(), lr=LEARNING_RATE)
    criterion_hemorrhage = nn.CrossEntropyLoss(weight=hemorrhage_weights)
    criterion_midline = nn.CrossEntropyLoss(weight=midline_weights)
    criterion_ventricle = nn.CrossEntropyLoss(weight=ventricle_weights)

    best_val_acc = -1.0
    best_state_dict: dict[str, torch.Tensor] | None = None
    model.train()

    for epoch in range(EPOCHS):
        running_loss = 0.0
        correct_hemorrhage = 0
        correct_midline = 0
        correct_ventricle = 0
        total = 0

        for images, labels in train_loader:
            images = images.to(device)
            labels_hemorrhage = labels[:, 0].to(device)
            labels_midline = labels[:, 1].to(device)
            labels_ventricle = labels[:, 2].to(device)

            optimizer.zero_grad()
            out_hemorrhage, out_midline, out_ventricle = model(images)

            loss_hemorrhage = criterion_hemorrhage(out_hemorrhage, labels_hemorrhage)
            loss_midline = criterion_midline(out_midline, labels_midline)
            loss_ventricle = criterion_ventricle(out_ventricle, labels_ventricle)
            total_loss = loss_hemorrhage + loss_midline + loss_ventricle

            total_loss.backward()
            optimizer.step()

            running_loss += total_loss.item()
            total += labels.size(0)
            correct_hemorrhage += (torch.argmax(out_hemorrhage, dim=1) == labels_hemorrhage).sum().item()
            correct_midline += (torch.argmax(out_midline, dim=1) == labels_midline).sum().item()
            correct_ventricle += (torch.argmax(out_ventricle, dim=1) == labels_ventricle).sum().item()

        print(
            f"Epoch [{epoch + 1}/{EPOCHS}] "
            f"Loss: {running_loss / max(1, len(train_loader)):.4f} | "
            f"Acc Hem: {100 * correct_hemorrhage / max(1, total):.1f}% | "
            f"Acc Mid: {100 * correct_midline / max(1, total):.1f}% | "
            f"Acc Ven: {100 * correct_ventricle / max(1, total):.1f}%"
        )

        model.eval()
        val_acc_hemorrhage = 0.0
        with torch.no_grad():
            val_total = 0
            val_correct = 0
            for val_images, val_labels in val_loader:
                val_images = val_images.to(device)
                val_labels_hemorrhage = val_labels[:, 0].to(device)
                val_out_hemorrhage, _, _ = model(val_images)
                val_pred_hemorrhage = torch.argmax(val_out_hemorrhage, dim=1)
                val_total += val_labels_hemorrhage.size(0)
                val_correct += (val_pred_hemorrhage == val_labels_hemorrhage).sum().item()

            if val_total > 0:
                val_acc_hemorrhage = float(val_correct / val_total)
                print(f"  Val Acc Hem: {100 * val_acc_hemorrhage:.1f}%")

        if val_acc_hemorrhage > best_val_acc:
            best_val_acc = val_acc_hemorrhage
            best_state_dict = copy.deepcopy(model.state_dict())
            print(f"  Best Val Acc Hem updated: {100 * best_val_acc:.1f}%")

        model.train()

    if best_state_dict is not None:
        model.load_state_dict(best_state_dict)

    torch.save(model.state_dict(), MODEL_WEIGHTS_PATH)
    print(f"Model saved to {MODEL_WEIGHTS_PATH}")

    model.eval()
    print("Sanity check on validation samples:")
    with torch.no_grad():
        val_transform = build_val_transform()
        for sample in val_samples[:10]:
            image = Image.open(sample.image_path).convert("L")
            image_tensor = val_transform(image).unsqueeze(0).to(device)
            out_hemorrhage, out_midline, out_ventricle = model(image_tensor)
            probs_hemorrhage = torch.softmax(out_hemorrhage, dim=1)[0].detach().cpu().numpy().tolist()
            probs_midline = torch.softmax(out_midline, dim=1)[0].detach().cpu().numpy().tolist()
            probs_ventricle = torch.softmax(out_ventricle, dim=1)[0].detach().cpu().numpy().tolist()
            print(
                f"  file={sample.image_path.name} "
                f"gt_hem={sample.hemorrhage_label} pred_hem={int(np.argmax(probs_hemorrhage))} p_hem={probs_hemorrhage} "
                f"pred_mid={int(np.argmax(probs_midline))} p_mid={probs_midline} "
                f"pred_ven={int(np.argmax(probs_ventricle))} p_ven={probs_ventricle}"
            )


if __name__ == "__main__":
    train()
