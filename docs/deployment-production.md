# Medical QC SYS 生产部署说明

更新时间：2026-03-13

## 1. 部署目标

本文档说明当前项目的生产部署边界、依赖准备、配置项和回归建议，适用于统一模型版本的部署。

## 2. 推荐拓扑

```text
Browser
  -> Nginx
    -> Frontend Static Assets
    -> Spring Boot Backend
       -> MySQL
       -> Redis
       -> ActiveMQ
       -> Python Inference Service
```

## 3. 生产环境原则

- 后端只连接外部依赖，不自动拉起本地 Python 和 ActiveMQ
- 数据库结构变更只通过 Flyway 执行
- 上传目录 `uploads/` 需要独立持久化
- 前后端统一通过 `Nginx` 作为访问入口
- Python 推理服务建议独立部署并单独监控

## 4. 依赖准备

部署前需要准备：

- MySQL 8+
- Redis 6+
- ActiveMQ 5.16+
- Python 3.10+ 推理服务
- JDK 17
- Node.js `^20.19.0 || >=22.12.0`（仅前端构建阶段需要）

数据库初始化：

```sql
CREATE DATABASE medical_qc_sys_unified CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
```

## 5. 关键配置

建议通过环境变量、启动参数或外置配置覆盖以下参数：

- `spring.datasource.url=jdbc:mysql://<host>:3306/medical_qc_sys_unified?...`
- `spring.datasource.username=<user>`
- `spring.datasource.password=<password>`
- `spring.flyway.enabled=true`
- `spring.data.redis.host=<redis-host>`
- `spring.data.redis.port=<redis-port>`
- `spring.activemq.broker-url=tcp://<mq-host>:61616`
- `spring.activemq.user=<mq-user>`
- `spring.activemq.password=<mq-password>`
- `python.model_server.url=ws://<python-host>:8765`
- `python.model.autostart=false`
- `app.messaging.activemq.autostart=false`
- `app.storage.local.root=<persistent-upload-dir>`

## 6. 构建与发布

### 6.1 后端

```powershell
cd medical-qc-backend
mvn clean package
```

产物为 Spring Boot 可执行 Jar。

### 6.2 前端

```powershell
cd medical-qc-frontend
npm install
npm run build
```

构建产物位于 `medical-qc-frontend/dist`。

## 7. Nginx 配置

生产反向代理模板位于：

- `deploy/nginx/medical-qc.conf`

模板覆盖以下入口：

- `/`：前端静态资源
- `/api/v1/`：后端接口
- `/uploads/`：上传文件访问

## 8. 建议部署顺序

1. 准备 MySQL、Redis、ActiveMQ、Python 推理服务
2. 创建数据库 `medical_qc_sys_unified`
3. 发布后端并执行 Flyway 初始化
4. 构建并发布前端静态资源
5. 配置 `Nginx` 反向代理
6. 执行业务回归

## 9. 回归重点

- 登录、注册、当前用户查询
- 仪表盘数据加载
- 头部出血检测上传、本地分析、结果回显
- 统一任务中心查询与详情查看
- 患者信息 CRUD 与 PACS 选择
- 异常工单流转与 CAPA
- 用户管理与规则中心

## 10. 已知部署注意事项

- 头部出血检测对 Python 服务可用性敏感
- `head`、`chest-non-contrast` 依赖 Python 真实推理服务
- `chest-contrast`、`coronary-cta` 当前为后端 mock 链路，不依赖 Python 模型文件
- Redis 不可用会影响会话登录状态
- `uploads/` 若不持久化，重启后会丢失本地文件引用

## 11. 配套文档

- 根说明：`README.md`
- 开发文档：`docs/development-guide.md`
- 功能介绍：`docs/feature-overview.md`
- 使用说明：`docs/user-guide.md`
