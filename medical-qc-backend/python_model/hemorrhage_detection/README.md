# Hemorrhage Detection Python Files

当前目录存放“头部出血检测”相关的 Python 代码，按职责拆分如下：

- `model.py`
  - 共享模型定义、数据集路径、权重路径、基础预处理。
- `train.py`
  - 当前生产使用的多任务出血检测模型训练入口。
- `inference.py`
  - 头部出血检测单例推理辅助模块
  - 由上层 `python_model/inference_server.py` 统一 WebSocket 服务复用

## 当前约定

- 训练数据来自 `datasets/head_ct_hemorrhage_detection/`
- 模型权重保存到 `python_model/models/hemorrhage_detection/`
- 训练结果输出到 `python_model/results/hemorrhage_detection/`
