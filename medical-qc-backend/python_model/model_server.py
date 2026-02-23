import asyncio
import websockets
import json
import torch
import os
import sys
import time
import cv2
import numpy as np
from PIL import Image
from torchvision import transforms
import torch.nn as nn

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

# Global model variable
model = None

# Force CUDA (required)
if not torch.cuda.is_available():
    raise RuntimeError("CUDA is not available. Hemorrhage model inference requires CUDA.")

device = torch.device("cuda")
print(f"✅ Using CUDA device: {torch.cuda.get_device_name(0)}")

model_path = "models/hemorrhage_model_advanced.pth"

def load_model():
    """
    加载多任务出血检测模型权重到 CUDA 并进入 eval 模式。

    返回:
        None（使用全局变量 model）
    异常:
        捕获并打印模型加载异常，加载失败时 model 置为 None
    """
    global model
    print(f"Loading model from {model_path}...")
    model = AdvancedHemorrhageModel()
    if os.path.exists(model_path):
        try:
            checkpoint = torch.load(model_path, map_location=device, weights_only=False)
            if isinstance(checkpoint, dict) and 'model_state_dict' in checkpoint:
                 model.load_state_dict(checkpoint['model_state_dict'])
            else:
                 model.load_state_dict(checkpoint)
            
            model.to(device)
            model.eval()
            print(f"Model loaded successfully on {device}.")
        except Exception as e:
            print(f"Error loading model: {e}")
            model = None
    else:
        print("Error: Model file not found. Please run train_advanced.py first.")
        model = None

# Preprocessing
transform = transforms.Compose([
    transforms.Resize((224, 224)),
    transforms.ToTensor(),
    transforms.Normalize((0.5,), (0.5,))
])

async def handle_connection(websocket):
    print(f"New connection from {websocket.remote_address}")
    try:
        async for message in websocket:
            data = json.loads(message)
            image_path = data.get("image_path")
            
            print(f"Received request for: {image_path}")
            
            if not model:
                await websocket.send(json.dumps({"error": "Model not loaded"}))
                continue

            if not os.path.exists(image_path):
                 await websocket.send(json.dumps({"error": f"File not found: {image_path}"}))
                 continue

            try:
                start_time = time.time()
                # Inference
                image = Image.open(image_path).convert("L")
                image = transform(image).unsqueeze(0).to(device)
                
                with torch.no_grad():
                    out_hem, out_mid, out_ven = model(image)
                    
                    # Hemorrhage
                    probs_hem = torch.softmax(out_hem, dim=1)
                    hemorrhage_prob = probs_hem[0][1].item()
                    no_hemorrhage_prob = probs_hem[0][0].item()
                    pred_hem_idx = torch.argmax(probs_hem, dim=1).item()
                    
                    # Midline
                    probs_mid = torch.softmax(out_mid, dim=1)
                    midline_shift_prob = probs_mid[0][1].item()
                    pred_mid_idx = torch.argmax(probs_mid, dim=1).item()
                    
                    # Ventricle
                    probs_ven = torch.softmax(out_ven, dim=1)
                    ventricle_issue_prob = probs_ven[0][1].item()
                    pred_ven_idx = torch.argmax(probs_ven, dim=1).item()
                    
                prediction = "Hemorrhage" if pred_hem_idx == 1 else "Normal"
                confidence = f"{max(hemorrhage_prob, no_hemorrhage_prob) * 100:.2f}%"
                
                duration = (time.time() - start_time) * 1000  # ms

                # --- Midline & Ventricle Details ---
                midline_shift = (pred_mid_idx == 1)
                ventricle_issue = (pred_ven_idx == 1)
                
                midline_detail = "中线结构居中"
                shift_score = 0.0
                
                if midline_shift:
                    midline_detail = f"检测到中线偏移 (置信度: {midline_shift_prob*100:.1f}%)"
                    # Calculate approximate shift score
                    try:
                        # Use imdecode to support unicode paths on Windows
                        cv_img = cv2.imdecode(np.fromfile(image_path, dtype=np.uint8), cv2.IMREAD_GRAYSCALE)
                        if cv_img is None:
                            raise Exception("Failed to read image")
                        
                        _, thresh = cv2.threshold(cv_img, 30, 255, cv2.THRESH_BINARY)
                        M = cv2.moments(thresh)
                        if M["m00"] != 0:
                            cX = int(M["m10"] / M["m00"])
                            image_center_x = cv_img.shape[1] // 2
                            shift_pixels = abs(cX - image_center_x)
                            shift_score = round(shift_pixels * 0.5, 2)
                    except:
                        shift_score = 5.0 # Default fallback
                    
                    midline_detail += f" 偏移约 {shift_score}mm"
                    
                ventricle_detail = "脑室系统形态正常"
                if ventricle_issue:
                    ventricle_detail = f"脑室区域密度/形态异常 (置信度: {ventricle_issue_prob*100:.1f}%)"

                result = {
                    "prediction": prediction,
                    "confidence_level": confidence,
                    "hemorrhage_probability": hemorrhage_prob,
                    "no_hemorrhage_probability": no_hemorrhage_prob,
                    "analysis_duration": round(duration, 2),
                    "device": torch.cuda.get_device_name(0) if torch.cuda.is_available() else "CPU",
                    # Additional metrics
                    "midline_shift": midline_shift,
                    "shift_score": shift_score,
                    "midline_detail": midline_detail,
                    "ventricle_issue": ventricle_issue,
                    "ventricle_detail": ventricle_detail
                }
                
                await websocket.send(json.dumps(result))
                print(f"Result sent for {image_path}")
                
            except Exception as e:
                print(f"Error processing image: {e}")
                await websocket.send(json.dumps({"error": str(e)}))

    except websockets.exceptions.ConnectionClosed:
        print("Connection closed")

async def main():
    load_model()
    async with websockets.serve(handle_connection, "localhost", 8765):
        print("WebSocket server started on ws://localhost:8765")
        await asyncio.Future()  # Run forever

if __name__ == "__main__":
    asyncio.run(main())
