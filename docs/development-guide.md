# 开发文档

更新时间：2026-03-13

## 1. 适用范围

本文档面向项目开发、联调、排查和日常维护人员，描述当前仓库的本地开发方式和目录约定。

## 2. 开发环境要求

### 2.1 必备依赖

- JDK 17
- Maven 3.9+
- Node.js `^20.19.0 || >=22.12.0`
- npm
- Python 3.10+
- MySQL 8+
- Redis 6+
- ActiveMQ 5.16+

### 2.2 默认服务地址

| 服务 | 默认地址 |
| --- | --- |
| 前端 | `http://localhost:5173` |
| 后端 | `http://localhost:8080` |
| MySQL | `localhost:3306` |
| Redis | `localhost:6379` |
| ActiveMQ | `tcp://127.0.0.1:61616` |
| Python 推理服务 | `ws://localhost:8765` |

## 3. 本地启动流程

### 3.1 初始化数据库

```sql
CREATE DATABASE medical_qc_sys_unified CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

说明：

- 启动后端时，Flyway 会自动执行 `V7__create_unified_schema_baseline.sql`
- 当前只维护统一模型，不需要执行旧版本迁移

### 3.2 启动后端

```powershell
cd medical-qc-backend
mvn spring-boot:run
```

默认读取：

- `src/main/resources/application.properties`
- `src/main/resources/application-dev.properties`

开发环境下：

- `python.model.autostart=true`
- `app.messaging.activemq.autostart=true`

### 3.3 启动前端

```powershell
cd medical-qc-frontend
npm install
npm run dev
```

### 3.4 Python 服务

Python 服务代码位于：

- `medical-qc-backend/python_model/inference_server.py`
- `medical-qc-backend/python_model/hemorrhage_detection/`
- `medical-qc-backend/python_model/head_ct_plain_qc/`
- `medical-qc-backend/python_model/chest_ct_non_contrast_qc/`
- `medical-qc-backend/python_model/chest_contrast_qc/`
- `medical-qc-backend/python_model/coronary_cta_qc/`
- `medical-qc-backend/python_model/medical_volume_utils.py`

说明：

- 在当前 `dev` 配置下，后端允许自动拉起本地 Python 服务
- 若本地未能自动拉起，需要单独检查 Python 环境、模型文件和端口占用
- 当前 WebSocket 健康检查状态应为：
  - `hemorrhage=true`
  - `head=true`
  - `chest-non-contrast=true`
- 健康检查中的真实模型状态只对应上述三条链路
- `chest-contrast`、`coronary-cta` 当前固定为规则型 mock 链路，不参与真实模型健康检查
- 本地上传统一支持 `.dcm` / `.dicom` / `.nii` / `.nii.gz` / `.zip`
- Python 依赖清单位于 `medical-qc-backend/python_model/requirements.txt`
- 三条真实链路若遇到空结果、错误结果或结果结构不完整，会直接失败并回写 `errorMessage`

## 4. 目录说明

### 4.1 根目录

| 路径 | 说明 |
| --- | --- |
| `docs/` | 项目详细文档 |
| `deploy/` | 生产部署骨架 |
| `medical-qc-backend/` | 后端服务 |
| `medical-qc-frontend/` | 前端应用 |

### 4.2 后端目录

| 路径 | 说明 |
| --- | --- |
| `src/main/java/com/medical/qc/modules` | 业务模块 |
| `src/main/resources/db/baseline` | Flyway 基线脚本 |
| `src/main/resources/mapper` | MyBatis XML |
| `python_model/` | Python 推理与训练代码，按质控项拆分到子目录 |
| `scripts/` | 启动与自检脚本 |
| `uploads/` | 本地上传目录 |

### 4.3 前端目录

| 路径 | 说明 |
| --- | --- |
| `src/app` | 应用装配、路由 |
| `src/modules` | 页面模块 |
| `src/components` | 通用组件 |
| `src/utils` | 权限、认证和工具方法 |
| `scripts/` | 启动脚本 |

## 5. 当前开发重点模块

### 5.1 后端

- `modules/qcresult`：头部出血检测真实链路
- `modules/qctask`：统一任务中心与异步质控任务编排
- `modules/patient`：患者信息管理
- `modules/issue`：异常工单与 CAPA
- `modules/qcrule`：规则中心
- `modules/unified`：统一任务、结果、异常聚合读写
- `modules/pacs`：任务专属 PACS 缓存查询

### 5.2 前端

- `modules/qctask/pages/HemorrhagePage.vue`：真实 AI 检测页面
- `modules/qctask/pages/QualityTaskCenterPage.vue`：任务中心
- `modules/patient/pages/QualityPatientManagementPage.vue`：患者管理
- `modules/issue/pages/IssueSummaryPage.vue`：异常汇总
- `modules/qcrule/pages/QcRuleCenterPage.vue`：规则中心

## 6. 常用命令

### 6.1 后端

```powershell
cd medical-qc-backend
mvn spring-boot:run
mvn clean test
mvn clean package
```

可用脚本：

- `scripts/smoke-test.ps1`
- `scripts/activemq.ps1`
- `scripts/restart-backend-jdk17.ps1`

### 6.2 前端

```powershell
cd medical-qc-frontend
npm run dev
npm run build
npm run preview
npm run lint
npm run type-check
```

## 7. 配置说明

### 7.1 后端配置文件

| 文件 | 用途 |
| --- | --- |
| `application.properties` | 默认配置 |
| `application-dev.properties` | 开发环境补充配置 |
| `application-prod.properties` | 生产环境差异配置 |

### 7.2 当前关键配置

| 配置项 | 说明 |
| --- | --- |
| `spring.datasource.*` | MySQL 连接 |
| `spring.flyway.*` | 结构迁移配置 |
| `spring.data.redis.*` | Redis 会话与缓存 |
| `spring.activemq.*` | ActiveMQ 连接 |
| `app.messaging.activemq.*` | MQ 自动拉起与队列配置 |
| `python.model_server.url` | Python 推理服务 |
| `python.model.autostart` | 是否自动拉起 Python |
| `app.storage.local.*` | 本地上传目录和公开路径 |

## 8. 联调建议

### 8.1 功能联调顺序

1. 登录注册
2. 当前用户接口
3. 仪表盘概览
4. 头部出血检测（真实）
5. CT头部平扫质控（真实）
6. CT胸部平扫质控（真实）
7. CT胸部增强质控（规则型 mock）
8. 冠脉CTA质控（规则型 mock）
9. 任务中心
10. 患者信息管理
11. 异常工单
12. 规则中心

### 8.2 推荐关注点

- Redis 未启动时，登录态和会话相关功能会异常
- Python 服务未启动时，头部出血检测、CT头部平扫质控、CT胸部平扫质控会失败
- 真实链路失败时前端会显示任务失败原因，不再回退为“合格/100分”
- ActiveMQ 不可用时，异步任务链路可能受影响
- PACS 当前统一读取任务专属缓存表，不要按真实 DICOM 网关行为排查
- 当前任务专属源表为唯一有效源数据入口：
  - 本地上传走 `*_patient_info`
  - PACS 调取走 `*_pacs_study_cache`
- 真实链路 `head`、`chest-non-contrast` 提交时必须携带 `patient_name`、`exam_id`、`gender`、`age`、`study_date`

## 9. 开发约束

- 数据库变更统一通过 Flyway 追加版本完成
- 统一模型是唯一运行模型
- 不再维护旧库兼容说明
- 文档和代码要与当前实际行为一致，发现模板内容应及时替换

## 10. 提交前最小检查

建议至少执行：

```powershell
cd medical-qc-backend
mvn clean test
```

```powershell
cd medical-qc-frontend
npm run build
```
