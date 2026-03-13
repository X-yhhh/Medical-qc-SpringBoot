# Medical QC SYS 生产部署说明

更新时间：2026-03-13

## 1. 推荐部署拓扑

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

## 2. 生产环境原则

- 后端只连接外部依赖，不自动拉起本地进程
- 数据库结构变更只通过 Flyway 执行
- 上传目录 `uploads/` 需要独立持久化
- 前后端统一经由 `Nginx` 暴露入口

## 3. 关键配置

建议在生产环境显式设置：

- `spring.datasource.url=jdbc:mysql://<host>:3306/medical_qc_sys_unified...`
- `spring.datasource.username=<user>`
- `spring.datasource.password=<password>`
- `spring.flyway.enabled=true`
- `spring.data.redis.host=<redis-host>`
- `spring.activemq.broker-url=tcp://<mq-host>:61616`
- `python.model_server.url=ws://<python-host>:8765`
- `python.model.autostart=false`
- `app.messaging.activemq.autostart=false`

## 4. Nginx 模板

生产反向代理模板位于：

- `deploy/nginx/medical-qc.conf`

模板已覆盖：

- `/` 前端入口
- `/api/v1/` 后端接口
- `/uploads/` 上传文件访问

## 5. 部署顺序

1. 准备 MySQL、Redis、ActiveMQ、Python 推理服务
2. 创建数据库 `medical_qc_sys_unified`
3. 部署后端并执行 Flyway 初始化
4. 构建并部署前端静态资源
5. 配置 `Nginx` 反向代理
6. 回归登录、头部出血检测、任务中心、患者管理、规则中心和异常工单

## 6. 回归重点

- 登录、注册、当前用户
- 头部出血检测
- 异步任务提交与任务中心查询
- 异常工单详情与流转
- 患者信息 CRUD 与 PACS 查询
- 管理员用户管理与规则中心

## 7. 相关文档

- `README.md`
- `docs/project-documentation.md`
- `deploy/README.md`
