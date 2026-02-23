# Medical Quality Control System (Medical-QC-SpringBoot)

这是一个基于 AI 的医学影像智能质控平台，专注于 CT 头部影像的分析、脑出血检测以及医疗质控流程管理。本项目基于 `Medical-QC` 进行重构，采用 **Spring Boot + Vue 3** 前后端分离架构，集成了 Python 深度学习模型服务进行辅助诊断。

## 📁 项目结构

项目采用标准的前后端分离目录结构：

*   **`medical-qc-backend` (Backend)**: 基于 Java Spring Boot 3 的后端核心服务，负责业务逻辑、API 接口、数据持久化及与 AI 模型的交互。
    *   内嵌 **`python_model`**: Python 子模块，运行 AI 推理服务（WebSocket），加载 PyTorch 模型进行实时分析。
*   **`medical-qc-frontend` (Frontend)**: 基于 Vue 3 + Vite 的前端应用，提供现代化的用户界面、数据可视化看板和交互式影像查看器。

## 🚀 技术栈

### 后端 (Backend)
*   **核心框架**: Spring Boot 3.2.2
*   **ORM 框架**: MyBatis-Plus 3.5.5
*   **数据库**: MySQL 8.0+
*   **Python 集成**: Java-WebSocket (与 AI 服务通信) / ProcessBuilder (进程管理)
*   **构建工具**: Maven

### AI 模型服务 (Python Sub-module)
*   **推理引擎**: PyTorch (CUDA 加速支持)
*   **通信协议**: Websockets (ws://localhost:8765)
*   **图像处理**: OpenCV, Pillow, NumPy
*   **模型架构**: Multi-task CNN (同时检测出血、中线偏移、脑室异常)

### 前端 (Frontend)
*   **框架**: Vue.js 3 (Composition API)
*   **构建工具**: Vite
*   **UI 组件库**: Element Plus
*   **网络请求**: Axios
*   **路由管理**: Vue Router

## ✨ 主要功能

1.  **用户认证与权限**
    *   支持医生/管理员注册与登录。
    *   基于 Session 的会话管理与拦截器鉴权。

2.  **AI 智能质控 (核心功能)**
    *   **脑出血检测**: 
        *   上传 CT 影像，后端自动调用 Python 模型进行分析。
        *   返回**出血概率**、**置信度**、**中线结构**（正常/偏移及偏移量 mm）、**脑室结构**（正常/异常）等多维指标。
        *   支持 CUDA GPU 加速推理，毫秒级响应。
    *   **模拟质控项**: 包含头部平扫、胸部平扫/增强、冠脉 CTA 的模拟质控流程，返回预设的质控报告（如运动伪影、扫描范围不足等）。

3.  **数据看板与历史记录**
    *   **异常汇总**: 可视化展示质控合格率、异常类型分布。
    *   **检测历史**: 记录每一次 AI 分析的影像、结果及详情，支持回溯查看。

4.  **影像可视化**
    *   支持上传并预览医学影像（PNG/JPG/DICOM 转换后的格式）。
    *   结果页展示带有分析标注的影像信息。

## 🛠️ 快速开始

### 1. 环境准备
*   **Java**: JDK 17+
*   **Node.js**: 16+
*   **Python**: 3.10+ (需安装 `torch` 等依赖)
*   **MySQL**: 8.0+
*   **GPU**: NVIDIA 显卡 (推荐，需安装 CUDA 驱动以启用模型加速)

### 2. 数据库设置
1.  创建数据库 `medical_qc_sys`。
2.  执行项目根目录下的 `init.sql` 脚本初始化表结构。
3.  在 `medical-qc-backend/src/main/resources/application.properties` 中配置数据库连接信息：
    ```properties
    spring.datasource.url=jdbc:mysql://localhost:3306/medical_qc_sys?...
    spring.datasource.username=root
    spring.datasource.password=your_password
    ```

### 3. 后端启动 (`medical-qc-backend`)
后端服务启动时会自动拉起 Python 模型服务，无需单独手动运行 Python 脚本（需确保 Python 环境已就绪）。

```bash
# 进入后端目录
cd medical-qc-backend

# 安装 Python 依赖 (仅首次)
cd python_model
pip install -r requirements.txt
# 确保安装了适配 CUDA 的 PyTorch 版本
cd ..

# 启动 Spring Boot 应用
mvn spring-boot:run
```
*   后端服务端口: `8080`
*   Python 模型服务端口: `8765` (自动管理)

### 4. 前端启动 (`medical-qc-frontend`)

```bash
# 进入前端目录
cd medical-qc-frontend

# 安装依赖
npm install

# 启动开发服务器
npm run dev
```
*   前端访问地址: `http://localhost:5173`

## 📝 开发注意事项
*   **模型文件**: 默认使用的模型权重文件位于 `medical-qc-backend/python_model/models/`。若需重新训练，请运行 `train_advanced.py`。
*   **静态资源**: 上传的影像文件存储在 `medical-qc-backend/uploads/`，并通过 Spring Boot 配置映射访问。
*   **跨域配置**: 前端请求已通过 Vite 代理 (`vite.config.js`) 转发至后端 8080 端口。
