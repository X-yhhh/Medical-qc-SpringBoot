import os
import torch
import torch.nn as nn
import torch.optim as optim
from torch.utils.data import Dataset, DataLoader
from torchvision import transforms
from PIL import Image
import pandas as pd
import random
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, confusion_matrix, roc_auc_score
import numpy as np
from collections import Counter
import matplotlib.pyplot as plt
import seaborn as sns

# ======================
# 配置
# ======================
DATA_DIR = "data/head_ct"
LABELS_FILE = "data/labels.csv"
MODEL_SAVE_PATH = "models/hemorrhage_model_best.pth"
BATCH_SIZE = 8
EPOCHS = 150  # 增加最大轮次
LEARNING_RATE = 0.0005  # 稍微降低学习率，更精细
IMAGE_SIZE = (224, 224)
PATIENCE = 20  # 早停耐心值增加
DEVICE = torch.device("cuda" if torch.cuda.is_available() else "cpu")
SEED = 42  # 固定随机种子，保证结果可复现

# 设置随机种子
random.seed(SEED)
np.random.seed(SEED)
torch.manual_seed(SEED)
if torch.cuda.is_available():
    torch.cuda.manual_seed(SEED)
    torch.backends.cudnn.deterministic = True
    torch.backends.cudnn.benchmark = False

# 确保模型目录存在
os.makedirs("models", exist_ok=True)
os.makedirs("results", exist_ok=True)  # 用于保存结果图表


# ======================
# 数据集类
# ======================
class HemorrhageDataset(Dataset):
    def __init__(self, image_ids, labels, transform=None):
        self.image_ids = image_ids
        self.labels = labels
        self.transform = transform

    def __len__(self):
        return len(self.image_ids)

    def __getitem__(self, idx):
        img_id = self.image_ids[idx]
        label = self.labels[idx]
        # 图像路径: data/head_ct/001.png
        img_path = os.path.join(DATA_DIR, f"{img_id:03d}.png")
        image = Image.open(img_path).convert("L")  # 灰度图
        if self.transform:
            image = self.transform(image)
        return image, torch.tensor(label, dtype=torch.long)


# ======================
# 模型定义（更强大、更健壮）
# ======================
class Classifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            # Block 1
            nn.Conv2d(1, 32, kernel_size=3, padding=1),  # Input: (224, 224)
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.Conv2d(32, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),  # Output: (112, 112)
            nn.Dropout2d(0.1),  # 轻微Dropout

            # Block 2
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),  # Output: (56, 56)
            nn.Dropout2d(0.1),

            # Block 3
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.Conv2d(128, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),  # Output: (28, 28)
            nn.Dropout2d(0.1),

            # Block 4
            nn.Conv2d(128, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.Conv2d(256, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),  # Output: (14, 14)
            nn.Dropout2d(0.1),

            # Global Average Pooling
            nn.AdaptiveAvgPool2d((1, 1)),  # Output: (1, 1)
        )
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Dropout(0.5),  # Classifier中的Dropout
            nn.Linear(256, 128),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),
            nn.Linear(128, 64),
            nn.ReLU(inplace=True),
            nn.Dropout(0.3),
            nn.Linear(64, 2)  # 二分类
        )

    def forward(self, x):
        x = self.features(x)
        x = self.classifier(x)
        return x


# ======================
# 绘图函数
# ======================
def plot_metrics(history, save_path):
    fig, axes = plt.subplots(2, 2, figsize=(15, 10))

    # Plot Loss
    axes[0, 0].plot(history['train_loss'], label='Training Loss')
    axes[0, 0].plot(history['val_loss'], label='Validation Loss')
    axes[0, 0].set_title('Model Loss')
    axes[0, 0].set_xlabel('Epoch')
    axes[0, 0].set_ylabel('Loss')
    axes[0, 0].legend()
    axes[0, 0].grid(True)

    # Plot Accuracy
    axes[0, 1].plot(history['train_acc'], label='Training Acc')
    axes[0, 1].plot(history['val_acc'], label='Validation Acc')
    axes[0, 1].set_title('Model Accuracy')
    axes[0, 1].set_xlabel('Epoch')
    axes[0, 1].set_ylabel('Accuracy')
    axes[0, 1].legend()
    axes[0, 1].grid(True)

    # Plot F1-Score
    axes[1, 0].plot(history['train_f1'], label='Training F1')
    axes[1, 0].plot(history['val_f1'], label='Validation F1')
    axes[1, 0].set_title('Model F1-Score')
    axes[1, 0].set_xlabel('Epoch')
    axes[1, 0].set_ylabel('F1-Score')
    axes[1, 0].legend()
    axes[1, 0].grid(True)

    # Plot AUC
    axes[1, 1].plot(history['train_auc'], label='Training AUC')
    axes[1, 1].plot(history['val_auc'], label='Validation AUC')
    axes[1, 1].set_title('Model AUC')
    axes[1, 1].set_xlabel('Epoch')
    axes[1, 1].set_ylabel('AUC')
    axes[1, 1].legend()
    axes[1, 1].grid(True)

    plt.tight_layout()
    plt.savefig(save_path)
    plt.close()


def plot_confusion_matrix(cm, classes, save_path):
    plt.figure(figsize=(8, 6))
    sns.heatmap(cm, annot=True, fmt='d', cmap='Blues', xticklabels=classes, yticklabels=classes)
    plt.title('Confusion Matrix')
    plt.xlabel('Predicted Label')
    plt.ylabel('True Label')
    plt.savefig(save_path)
    plt.close()


# ======================
# 主训练流程
# ======================
def main():
    print("🚀 开始训练脑出血检测模型...")
    print(f"✅ 使用设备: {DEVICE}")
    print(f"✅ 随机种子: {SEED}")

    # 1. 加载标签
    print("✅ 加载标签文件...")
    labels_df = pd.read_csv(LABELS_FILE)
    labels_df.columns = labels_df.columns.str.strip()  # 清理列名

    if 'hemorrhage' not in labels_df.columns:
        raise ValueError(f"CSV 必须包含 'hemorrhage' 列！当前列: {list(labels_df.columns)}")

    print("\n📊 原始标签分布:")
    label_counts = labels_df['hemorrhage'].value_counts()
    print(label_counts)
    print(f"总样本数: {len(labels_df)}")

    # --- 关键步骤：打乱数据框 ---
    print("\n🔄 打乱数据顺序...")
    labels_df_shuffled = labels_df.sample(frac=1, random_state=SEED).reset_index(drop=True)
    print("📊 打乱后标签分布:")
    print(labels_df_shuffled['hemorrhage'].value_counts())

    # 2. 准备数据
    image_ids = labels_df_shuffled['id'].tolist()
    labels = labels_df_shuffled['hemorrhage'].tolist()

    # 划分训练/验证集 (按标签比例分层抽样) - 从打乱后的数据中划分
    train_ids, val_ids, train_labels, val_labels = train_test_split(
        image_ids, labels, test_size=0.2, random_state=SEED, stratify=labels
    )

    print(f"\n📊 训练集大小: {len(train_ids)}, 验证集大小: {len(val_ids)}")
    print(f"📊 训练集标签分布: {Counter(train_labels)}")
    print(f"📊 验证集标签分布: {Counter(val_labels)}")

    # 数据增强（仅训练集）
    train_transform = transforms.Compose([
        transforms.Resize(IMAGE_SIZE),
        transforms.RandomHorizontalFlip(p=0.5),
        transforms.RandomVerticalFlip(p=0.5),  # 增加垂直翻转
        transforms.RandomRotation(degrees=15),  # 增加旋转角度
        transforms.ColorJitter(brightness=0.1, contrast=0.1, saturation=0.05, hue=0.05),  # 增加颜色抖动
        transforms.ToTensor(),
        transforms.Normalize((0.5,), (0.5,))
    ])
    val_transform = transforms.Compose([
        transforms.Resize(IMAGE_SIZE),
        transforms.ToTensor(),
        transforms.Normalize((0.5,), (0.5,))
    ])

    # 创建数据集
    train_dataset = HemorrhageDataset(train_ids, train_labels, transform=train_transform)
    val_dataset = HemorrhageDataset(val_ids, val_labels, transform=val_transform)
    train_loader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True, num_workers=2, pin_memory=True)
    val_loader = DataLoader(val_dataset, batch_size=BATCH_SIZE, shuffle=False, num_workers=2, pin_memory=True)

    # 3. 初始化模型、损失函数、优化器
    model = Classifier().to(DEVICE)
    criterion = nn.CrossEntropyLoss()
    # 使用 AdamW 和 weight_decay，学习率稍低
    optimizer = optim.AdamW(model.parameters(), lr=LEARNING_RATE, weight_decay=1e-4)
    # 余弦退火调度器，可能比 ReduceLROnPlateau 更平滑
    scheduler = optim.lr_scheduler.CosineAnnealingLR(optimizer, T_max=EPOCHS, eta_min=1e-7)

    print(f"✅ 模型参数量: {sum(p.numel() for p in model.parameters()):,}\n")

    # 4. 训练循环
    best_val_f1 = 0.0
    best_val_auc = 0.0
    patience_counter = 0
    history = {
        'train_loss': [], 'val_loss': [],
        'train_acc': [], 'val_acc': [],
        'train_f1': [], 'val_f1': [],
        'train_auc': [], 'val_auc': []
    }

    for epoch in range(EPOCHS):
        # 训练阶段
        model.train()
        train_loss = 0.0
        correct_train = 0
        total_train = 0
        all_train_preds = []
        all_train_targets = []
        all_train_probs = []

        for images, targets in train_loader:
            images, targets = images.to(DEVICE), targets.to(DEVICE)
            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, targets)
            loss.backward()
            optimizer.step()
            train_loss += loss.item()

            _, predicted = outputs.max(1)
            total_train += targets.size(0)
            correct_train += predicted.eq(targets).sum().item()

            all_train_preds.extend(predicted.cpu().numpy())
            all_train_targets.extend(targets.cpu().numpy())
            # 修复错误：使用 detach() 分离后再转 numpy
            all_train_probs.extend(torch.softmax(outputs, dim=1)[:, 1].detach().cpu().numpy())

        # 计算训练集指标
        avg_train_loss = train_loss / len(train_loader)
        train_acc = correct_train / total_train
        train_f1 = \
        classification_report(all_train_targets, all_train_preds, target_names=['No Hemorrhage', 'Hemorrhage'],
                              zero_division=0, output_dict=True)['weighted avg']['f1-score']
        train_auc = roc_auc_score(all_train_targets, all_train_probs) if len(set(all_train_targets)) > 1 else float(
            'nan')

        # 验证阶段
        model.eval()
        val_loss = 0.0
        all_val_preds = []
        all_val_targets = []
        all_val_probs = []

        with torch.no_grad():
            for images, targets in val_loader:
                images, targets = images.to(DEVICE), targets.to(DEVICE)
                outputs = model(images)
                loss = criterion(outputs, targets)
                val_loss += loss.item()

                # 修复错误：使用 detach() 分离后再转 numpy
                probs = torch.softmax(outputs, dim=1)[:, 1].detach().cpu().numpy()
                all_val_probs.extend(probs)

                _, predicted = outputs.max(1)
                all_val_preds.extend(predicted.cpu().numpy())
                all_val_targets.extend(targets.cpu().numpy())

        # 计算验证集指标
        avg_val_loss = val_loss / len(val_loader)

        correct_val = sum(p == t for p, t in zip(all_val_preds, all_val_targets))
        val_acc = correct_val / len(all_val_targets)

        val_f1 = classification_report(all_val_targets, all_val_preds, target_names=['No Hemorrhage', 'Hemorrhage'],
                                       zero_division=0, output_dict=True)['weighted avg']['f1-score']
        val_auc = roc_auc_score(all_val_targets, all_val_probs) if len(set(all_val_targets)) > 1 else float('nan')

        # 更新历史记录
        history['train_loss'].append(avg_train_loss)
        history['val_loss'].append(avg_val_loss)
        history['train_acc'].append(train_acc)
        history['val_acc'].append(val_acc)
        history['train_f1'].append(train_f1)
        history['val_f1'].append(val_f1)
        history['train_auc'].append(train_auc)
        history['val_auc'].append(val_auc)

        # 学习率调度
        scheduler.step()

        # 早停逻辑 - 基于 F1 分数
        if val_f1 > best_val_f1 or (abs(val_f1 - best_val_f1) < 1e-4 and val_auc > best_val_auc):
            best_val_f1 = val_f1
            best_val_auc = val_auc
            patience_counter = 0

            # 保存最佳模型
            torch.save({
                'model_state_dict': model.state_dict(),
                'optimizer_state_dict': optimizer.state_dict(),
                'epoch': epoch,
                'val_loss': avg_val_loss,
                'val_f1': val_f1,
                'val_auc': val_auc,
                'history': history
            }, MODEL_SAVE_PATH)
            print(f"🏆 Epoch {epoch + 1}: 保存最佳模型 (F1: {val_f1:.4f}, AUC: {val_auc:.4f})")
        else:
            patience_counter += 1

        # 打印日志
        if epoch % 10 == 0 or epoch == EPOCHS - 1 or patience_counter == 0:
            print(f"Epoch [{epoch + 1}/{EPOCHS}] | "
                  f"Train Loss: {avg_train_loss:.4f} | Val Loss: {avg_val_loss:.4f} | "
                  f"Train Acc: {train_acc:.4f} | Val Acc: {val_acc:.4f} | "
                  f"Train F1: {train_f1:.4f} | Val F1: {val_f1:.4f} | "
                  f"Train AUC: {train_auc:.4f} | Val AUC: {val_auc:.4f}")

        # 检查早停
        if patience_counter >= PATIENCE:
            print(f"Early stopping triggered after {PATIENCE} epochs without improvement in F1/AUC.")
            break

    print(f"\n🎉 训练完成！")
    print(f"🏆 最佳验证 F1 分数: {best_val_f1:.4f}")
    print(f"🏆 最佳验证 AUC: {best_val_auc:.4f}")
    print(f"🏆 最佳模型已保存至: {MODEL_SAVE_PATH}")

    # 绘制并保存结果图表
    plot_metrics(history, "results/training_history.png")
    print("📊 训练历史图表已保存至 results/training_history.png")

    # 在最终验证集上生成详细报告
    final_cm = confusion_matrix(all_val_targets, all_val_preds)
    plot_confusion_matrix(final_cm, ['No Hemorrhage', 'Hemorrhage'], "results/confusion_matrix_final.png")
    print("📊 最终混淆矩阵已保存至 results/confusion_matrix_final.png")

    final_cr = classification_report(all_val_targets, all_val_preds, target_names=['No Hemorrhage', 'Hemorrhage'],
                                     output_dict=True)
    print("\n📋 最终验证集分类报告:")
    print(classification_report(all_val_targets, all_val_preds, target_names=['No Hemorrhage', 'Hemorrhage']))


if __name__ == "__main__":
    main()