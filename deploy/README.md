# 部署骨架

当前目录提供生产化部署的基础模板。

## 目录说明

- `nginx/medical-qc.conf`
  - `Nginx` 反向代理配置模板
  - 已预留前端入口、后端 API、上传文件访问和后端多实例扩展位置

## 当前推荐部署形态

1. `Nginx` 作为统一入口
2. 前端静态资源由前端服务或静态文件托管
3. 后端 `Spring Boot` 独立运行
4. `MySQL / Redis / ActiveMQ / Python` 模型服务全部外置

## 演进方向

- 单实例后端：直接使用 `medical_qc_backend` 一个实例
- 多实例后端：在 `upstream medical_qc_backend` 中新增多个后端节点
- HTTPS：在 `server` 段补充证书配置并将 `listen 80` 升级为 `listen 443 ssl`
