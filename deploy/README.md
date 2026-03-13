# 部署目录说明

当前目录只存放部署骨架和反向代理模板，不承载完整的生产部署步骤。

## 目录内容

| 路径 | 说明 |
| --- | --- |
| `nginx/medical-qc.conf` | `Nginx` 反向代理模板，覆盖前端、后端接口和上传文件访问 |

## 使用方式

1. 先阅读 `docs/deployment-production.md`
2. 按实际环境修改 `nginx/medical-qc.conf`
3. 将前端静态资源、后端服务和外部依赖按生产部署文档发布

## 说明

- 如果后端需要扩容，可在 `upstream medical_qc_backend` 中增加节点
- 如果需要 HTTPS，可在 `server` 段补充证书与 `443 ssl` 配置
- 如果上传文件目录发生变化，需要同步调整 `/uploads/` 的映射路径
