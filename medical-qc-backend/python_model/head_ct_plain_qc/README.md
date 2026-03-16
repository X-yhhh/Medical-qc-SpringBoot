# Head CT Plain QC Python Files

当前目录对应“CT 头部平扫质控”这一项，单独管理，不与出血检测共用目录。

## 当前文件

- `model.py`
  - 头部平扫质控 3D 多任务模型定义
  - 统一管理任务配置、数据路径、权重路径和体数据预处理
- `train.py`
  - 使用原始 CT 平扫数据 + 合成缺陷伪标签训练 6 项质控能力
- `inference.py`
  - 对单个 NIfTI 体数据执行推理，并输出前端可消费的 `patientInfo + qcItems + summary`
  - 其中 `层厚层间距` 直接使用 NIfTI header 的 `spacing_z` 做规则判断
  - 同时作为统一 WebSocket 推理服务的头部平扫推理辅助模块
- `inspect_dataset.py`
  - 读取 `datasets/head_ct_plain_qc/raw/` 下的 CT / CBCT NIfTI 数据
  - 生成尺寸、spacing、方向、强度统计
  - 生成三视图 PNG 预览和总览图

## 当前状态

- 该质控项的数据集已经整理完成。
- 该质控项已经补齐模型定义、训练脚本和单例推理脚本。
- `剂量控制 (CTDI)` 当前使用图像噪声外观作为代理监督信号，因为现有 NIfTI 数据不包含 DICOM 剂量标签。
- `层厚层间距` 在最终推理阶段采用元数据规则判断，因为该项天然属于扫描参数而不是纯图像外观。
