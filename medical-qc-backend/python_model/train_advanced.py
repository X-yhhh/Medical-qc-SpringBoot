import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image
import numpy as np
import os
import random
import time
import csv
import cv2
import copy

# ==========================================
# 1. Advanced Model Architecture (Multi-Task)
# ==========================================
class AdvancedHemorrhageModel(nn.Module):
    def __init__(self):
        super(AdvancedHemorrhageModel, self).__init__()
        
        # Shared Feature Extractor (Backbone)
        self.features = nn.Sequential(
            # Block 1
            nn.Conv2d(1, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2), # 112
            
            # Block 2
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2), # 56
            
            # Block 3
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2), # 28
            
            # Block 4
            nn.Conv2d(128, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(2, 2), # 14
            
            # Global Average Pooling
            nn.AdaptiveAvgPool2d((1, 1))
        )
        
        # Task-specific Heads
        
        # Head 1: Hemorrhage Detection (Binary)
        self.hemorrhage_head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(256, 128),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(128, 2)
        )
        
        # Head 2: Midline Shift (Binary)
        self.midline_head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(256, 128),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(128, 2)
        )
        
        # Head 3: Ventricle Issue (Binary)
        self.ventricle_head = nn.Sequential(
            nn.Flatten(),
            nn.Linear(256, 128),
            nn.ReLU(),
            nn.Dropout(0.5),
            nn.Linear(128, 2)
        )

    def forward(self, x):
        features = self.features(x)
        
        hemorrhage_out = self.hemorrhage_head(features)
        midline_out = self.midline_head(features)
        ventricle_out = self.ventricle_head(features)
        
        return hemorrhage_out, midline_out, ventricle_out

# ==========================================
# 2. Dataset (Real Head CT + Pseudo Labels)
# ==========================================
def _read_image_grayscale_cv2(image_path: str):
    """
    读取灰度图（Windows 下支持中文路径）。

    参数:
        image_path: 图片文件路径
    返回:
        numpy.ndarray | None: 灰度图数组，读取失败返回 None
    """
    try:
        data = np.fromfile(image_path, dtype=np.uint8)
        if data.size == 0:
            return None
        return cv2.imdecode(data, cv2.IMREAD_GRAYSCALE)
    except Exception:
        return None


def _compute_midline_shift_label(gray_224: np.ndarray):
    """
    使用简单的几何特征生成“中线偏移”伪标签。

    参数:
        gray_224: 224x224 灰度图
    返回:
        int: 0(无偏移) / 1(偏移)
    """
    h, w = gray_224.shape[:2]
    _, mask = cv2.threshold(gray_224, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)
    mask = cv2.morphologyEx(mask, cv2.MORPH_CLOSE, np.ones((5, 5), np.uint8), iterations=2)
    m = cv2.moments(mask)
    if m["m00"] == 0:
        return 0
    cx = int(m["m10"] / m["m00"])
    center_x = w // 2
    shift_pixels = abs(cx - center_x)
    return 1 if shift_pixels >= 10 else 0


def _compute_ventricle_issue_label(gray_224: np.ndarray):
    """
    使用中央 ROI 的暗区比例生成“脑室异常”伪标签。

    参数:
        gray_224: 224x224 灰度图
    返回:
        int: 0(正常) / 1(异常)
    """
    h, w = gray_224.shape[:2]
    x0, x1 = int(w * 0.35), int(w * 0.65)
    y0, y1 = int(h * 0.35), int(h * 0.65)
    roi = gray_224[y0:y1, x0:x1]
    if roi.size == 0:
        return 0
    dark_ratio = float(np.mean(roi < 40))
    return 1 if (dark_ratio < 0.03 or dark_ratio > 0.35) else 0


class HeadCTMultiTaskDataset(Dataset):
    """
    真实头部 CT 数据集（出血标签来自 labels.csv，中线/脑室标签为伪标签）。

    参数:
        samples: 样本列表，每个元素为 dict，包含 image_path 与 labels(tuple)
        transform: torchvision transform
    返回:
        (image_tensor, labels_tensor): labels_tensor 为 shape=(3,) 的 long 张量
    异常:
        FileNotFoundError: 图片文件不存在
        RuntimeError: 图片读取失败
    """
    def __init__(self, samples, transform=None):
        self.samples = samples
        self.transform = transform

    def __len__(self):
        return len(self.samples)

    def __getitem__(self, idx):
        item = self.samples[idx]
        image_path = item["image_path"]
        if not os.path.exists(image_path):
            raise FileNotFoundError(f"Image not found: {image_path}")

        img = Image.open(image_path).convert("L")
        labels = torch.tensor(item["labels"], dtype=torch.long)
        
        if self.transform:
            img = self.transform(img)
            
        return img, labels

# ==========================================
# 3. Training Loop
# ==========================================
def _load_samples(data_dir: str, labels_csv: str, seed: int):
    """
    加载样本并生成多任务标签。

    参数:
        data_dir: 头部 CT 图片目录（如 data/head_ct）
        labels_csv: 出血标签文件（如 data/labels.csv）
        seed: 随机种子（用于打乱）
    返回:
        list[dict]: 每个 dict 包含 image_path 与 labels(hem, mid, ven)
    """
    samples = []
    with open(labels_csv, newline="", encoding="utf-8") as f:
        reader = csv.DictReader(f, skipinitialspace=True)
        for row in reader:
            img_id = int(str(row["id"]).strip())
            hem = int(str(row["hemorrhage"]).strip())
            image_path = os.path.join(data_dir, f"{img_id:03d}.png")

            gray = _read_image_grayscale_cv2(image_path)
            if gray is None:
                continue
            gray_224 = cv2.resize(gray, (224, 224), interpolation=cv2.INTER_AREA)
            mid = _compute_midline_shift_label(gray_224)
            ven = _compute_ventricle_issue_label(gray_224)

            samples.append({
                "image_path": image_path,
                "labels": (hem, mid, ven),
            })

    random.Random(seed).shuffle(samples)
    return samples


def _train_val_split(samples, val_ratio: float, seed: int):
    """
    简单划分训练/验证集（保持可复现）。

    参数:
        samples: 样本列表
        val_ratio: 验证集比例
        seed: 随机种子
    返回:
        (train_samples, val_samples)
    """
    rnd = random.Random(seed)
    shuffled = list(samples)
    rnd.shuffle(shuffled)
    val_size = int(len(shuffled) * val_ratio)
    val_samples = shuffled[:val_size]
    train_samples = shuffled[val_size:]
    return train_samples, val_samples


def _compute_class_weights(labels_0_1: list[int]):
    """
    计算二分类的 class weights（用于 CrossEntropyLoss）。

    参数:
        labels_0_1: 0/1 标签列表
    返回:
        torch.FloatTensor: shape=(2,) 的权重张量
    """
    zeros = sum(1 for v in labels_0_1 if v == 0)
    ones = sum(1 for v in labels_0_1 if v == 1)
    total = max(1, zeros + ones)
    w0 = total / max(1, zeros)
    w1 = total / max(1, ones)
    return torch.tensor([w0, w1], dtype=torch.float32)


def train():
    """
    训练多任务模型并保存权重文件 models/hemorrhage_model_advanced.pth。

    输出:
        训练过程日志、验证集准确率与若干样本推理结果（用于验证不会“全部出血”）。
    """
    # Setup Device - FORCE CUDA
    if not torch.cuda.is_available():
        print("ERROR: CUDA is not available! User required CUDA.")
        return
        
    device = torch.device("cuda")
    print(f"Using device: {device} ({torch.cuda.get_device_name(0)})")
    
    # Hyperparameters
    BATCH_SIZE = 16
    EPOCHS = 25
    LR = 0.0008
    SEED = 42
    VAL_RATIO = 0.2

    DATA_DIR = "data/head_ct"
    LABELS_FILE = "data/labels.csv"
    SAVE_PATH = "models/hemorrhage_model_advanced.pth"
    
    # Transforms
    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize((0.5,), (0.5,))
    ])
    
    # Dataset & Loader
    samples = _load_samples(DATA_DIR, LABELS_FILE, seed=SEED)
    if len(samples) == 0:
        print("ERROR: No training samples loaded. Check data/head_ct and data/labels.csv")
        return

    train_samples, val_samples = _train_val_split(samples, val_ratio=VAL_RATIO, seed=SEED)
    train_dataset = HeadCTMultiTaskDataset(train_samples, transform=transform)
    val_dataset = HeadCTMultiTaskDataset(val_samples, transform=transform)
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False)

    train_hem_labels = [s["labels"][0] for s in train_samples]
    train_mid_labels = [s["labels"][1] for s in train_samples]
    train_ven_labels = [s["labels"][2] for s in train_samples]

    hem_weights = _compute_class_weights(train_hem_labels).to(device)
    mid_weights = _compute_class_weights(train_mid_labels).to(device)
    ven_weights = _compute_class_weights(train_ven_labels).to(device)
    
    # Model
    model = AdvancedHemorrhageModel().to(device)
    
    # Optimizer & Loss
    optimizer = optim.Adam(model.parameters(), lr=LR)
    criterion_hem = nn.CrossEntropyLoss(weight=hem_weights)
    criterion_mid = nn.CrossEntropyLoss(weight=mid_weights)
    criterion_ven = nn.CrossEntropyLoss(weight=ven_weights)
    
    # Training Loop
    model.train()
    print("Starting training on CUDA...")
    best_val_acc = -1.0
    best_state_dict = None
    
    for epoch in range(EPOCHS):
        running_loss = 0.0
        correct_hem = 0
        correct_mid = 0
        correct_ven = 0
        total = 0
        
        for images, labels in train_loader:
            images = images.to(device)
            labels_hem = labels[:, 0].to(device)
            labels_mid = labels[:, 1].to(device)
            labels_ven = labels[:, 2].to(device)
            
            optimizer.zero_grad()
            
            out_hem, out_mid, out_ven = model(images)
            
            loss_hem = criterion_hem(out_hem, labels_hem)
            loss_mid = criterion_mid(out_mid, labels_mid)
            loss_ven = criterion_ven(out_ven, labels_ven)
            
            # Multi-task Loss
            total_loss = loss_hem + loss_mid + loss_ven
            
            total_loss.backward()
            optimizer.step()
            
            running_loss += total_loss.item()
            
            # Accuracy Calc
            _, pred_hem = torch.max(out_hem, 1)
            _, pred_mid = torch.max(out_mid, 1)
            _, pred_ven = torch.max(out_ven, 1)
            
            total += labels.size(0)
            correct_hem += (pred_hem == labels_hem).sum().item()
            correct_mid += (pred_mid == labels_mid).sum().item()
            correct_ven += (pred_ven == labels_ven).sum().item()
            
        print(f"Epoch [{epoch+1}/{EPOCHS}] Loss: {running_loss/max(1, len(train_loader)):.4f} | "
              f"Acc Hem: {100*correct_hem/total:.1f}% | "
              f"Acc Mid: {100*correct_mid/total:.1f}% | "
              f"Acc Ven: {100*correct_ven/total:.1f}%")

        # Validation (Hemorrhage Head)
        model.eval()
        val_acc_hem = 0.0
        with torch.no_grad():
            v_total = 0
            v_correct = 0
            for v_images, v_labels in val_loader:
                v_images = v_images.to(device)
                v_labels_hem = v_labels[:, 0].to(device)
                v_out_hem, _, _ = model(v_images)
                _, v_pred_hem = torch.max(v_out_hem, 1)
                v_total += v_labels_hem.size(0)
                v_correct += (v_pred_hem == v_labels_hem).sum().item()
            if v_total > 0:
                val_acc_hem = float(v_correct / v_total)
                print(f"  Val Acc Hem: {100*val_acc_hem:.1f}%")

        if val_acc_hem > best_val_acc:
            best_val_acc = val_acc_hem
            best_state_dict = copy.deepcopy(model.state_dict())
            print(f"  Best Val Acc Hem updated: {100*best_val_acc:.1f}%")
        model.train()
              
    # Save Model
    os.makedirs("models", exist_ok=True)
    if best_state_dict is not None:
        model.load_state_dict(best_state_dict)
    torch.save(model.state_dict(), SAVE_PATH)
    print(f"Model saved to {SAVE_PATH}")

    # Quick Sanity Check: print predictions for a few samples
    model.eval()
    print("Sanity check (hemorrhage predictions on a few validation samples):")
    with torch.no_grad():
        for i in range(min(10, len(val_samples))):
            sample = val_samples[i]
            img = Image.open(sample["image_path"]).convert("L")
            x = transform(img).unsqueeze(0).to(device)
            out_hem, out_mid, out_ven = model(x)
            p_hem = torch.softmax(out_hem, dim=1)[0].detach().cpu().numpy().tolist()
            p_mid = torch.softmax(out_mid, dim=1)[0].detach().cpu().numpy().tolist()
            p_ven = torch.softmax(out_ven, dim=1)[0].detach().cpu().numpy().tolist()
            pred_hem = int(np.argmax(p_hem))
            pred_mid = int(np.argmax(p_mid))
            pred_ven = int(np.argmax(p_ven))
            print(f"  file={os.path.basename(sample['image_path'])} "
                  f"gt_hem={sample['labels'][0]} pred_hem={pred_hem} p_hem={p_hem} "
                  f"pred_mid={pred_mid} p_mid={p_mid} pred_ven={pred_ven} p_ven={p_ven}")

if __name__ == "__main__":
    train()
