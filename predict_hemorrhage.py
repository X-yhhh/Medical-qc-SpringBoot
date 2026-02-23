import torch
import torch.nn as nn
from torchvision import transforms
from PIL import Image
import sys
import json
import time
import os

# ======================
# Model Definition
# ======================
class Classifier(nn.Module):
    def __init__(self):
        super().__init__()
        self.features = nn.Sequential(
            # Block 1
            nn.Conv2d(1, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.Conv2d(32, 32, kernel_size=3, padding=1),
            nn.BatchNorm2d(32),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Dropout2d(0.1),

            # Block 2
            nn.Conv2d(32, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.Conv2d(64, 64, kernel_size=3, padding=1),
            nn.BatchNorm2d(64),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Dropout2d(0.1),

            # Block 3
            nn.Conv2d(64, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.Conv2d(128, 128, kernel_size=3, padding=1),
            nn.BatchNorm2d(128),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Dropout2d(0.1),

            # Block 4
            nn.Conv2d(128, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.Conv2d(256, 256, kernel_size=3, padding=1),
            nn.BatchNorm2d(256),
            nn.ReLU(inplace=True),
            nn.MaxPool2d(kernel_size=2, stride=2),
            nn.Dropout2d(0.1),

            # Global Average Pooling
            nn.AdaptiveAvgPool2d((1, 1)),
        )
        self.classifier = nn.Sequential(
            nn.Flatten(),
            nn.Dropout(0.5),
            nn.Linear(256, 128),
            nn.ReLU(inplace=True),
            nn.Dropout(0.5),
            nn.Linear(128, 64),
            nn.ReLU(inplace=True),
            nn.Dropout(0.3),
            nn.Linear(64, 2)
        )

    def forward(self, x):
        x = self.features(x)
        x = self.classifier(x)
        return x

def predict(image_path, model_path):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    
    # Load Model
    model = Classifier()
    if os.path.exists(model_path):
        model.load_state_dict(torch.load(model_path, map_location=device))
    else:
        # Return dummy result if model not found yet (training in progress)
        return {
            "prediction": "Unknown",
            "confidence_level": "Low",
            "hemorrhage_probability": 0.0,
            "no_hemorrhage_probability": 0.0,
            "analysis_duration": 0.0,
            "error": "Model not found"
        }
        
    model.to(device)
    model.eval()

    # Preprocess
    transform = transforms.Compose([
        transforms.Resize((224, 224)),
        transforms.ToTensor(),
        transforms.Normalize(mean=[0.5], std=[0.5])
    ])

    try:
        start_time = time.time()
        image = Image.open(image_path).convert("L")
        image = transform(image).unsqueeze(0).to(device)

        with torch.no_grad():
            outputs = model(image)
            probs = torch.softmax(outputs, dim=1)
            
            # Assuming class 1 is Hemorrhage, 0 is Normal based on folder structure usually
            # But need to check labels.csv or training code.
            # Usually 0: Normal, 1: Hemorrhage.
            no_hemorrhage_prob = probs[0][0].item()
            hemorrhage_prob = probs[0][1].item()
            
            prediction = "出血" if hemorrhage_prob > 0.5 else "未出血"
            
            confidence = max(hemorrhage_prob, no_hemorrhage_prob)
            if confidence > 0.9:
                conf_level = "High"
            elif confidence > 0.7:
                conf_level = "Medium"
            else:
                conf_level = "Low"

        duration = time.time() - start_time

        return {
            "prediction": prediction,
            "confidence_level": conf_level,
            "hemorrhage_probability": hemorrhage_prob,
            "no_hemorrhage_probability": no_hemorrhage_prob,
            "analysis_duration": duration
        }
    except Exception as e:
        return {"error": str(e)}

if __name__ == "__main__":
    if len(sys.argv) < 3:
        print(json.dumps({"error": "Usage: python predict.py <image_path> <model_path>"}))
        sys.exit(1)
        
    img_path = sys.argv[1]
    mdl_path = sys.argv[2]
    
    result = predict(img_path, mdl_path)
    print(json.dumps(result))
