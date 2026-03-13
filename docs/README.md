# 文档索引

更新时间：2026-03-13

## 1. 推荐阅读顺序

1. `README.md`：了解项目定位、技术栈和快速启动方式
2. `docs/project-documentation.md`：了解当前架构、模块边界和数据模型
3. `docs/development-guide.md`：按开发环境完成启动、调试和提交流程
4. `docs/feature-overview.md`：查看系统提供的功能模块和接口映射
5. `docs/user-guide.md`：了解医生和管理员的日常使用路径
6. `docs/deployment-production.md`：准备生产部署

## 2. 文档清单

| 文档 | 用途 |
| --- | --- |
| `README.md` | 项目总入口和快速开始 |
| `docs/project-documentation.md` | 当前架构、模块边界和数据模型 |
| `docs/development-guide.md` | 本地开发、联调、验证、目录约定 |
| `docs/feature-overview.md` | 功能模块、角色权限、页面与接口映射 |
| `docs/user-guide.md` | 医生和管理员操作说明 |
| `docs/deployment-production.md` | 生产部署说明 |
| `deploy/README.md` | 部署目录和模板说明 |
| `medical-qc-frontend/README.md` | 前端子项目说明 |

## 3. 当前整理策略

- 根 `README.md` 只保留高层概览和启动入口
- `docs` 目录承载详细文档，避免 README 过长
- 架构、开发、功能、使用、部署分别独立，减少内容重复
- 子项目文档只保留和子项目直接相关的信息

## 4. 哪些文档适合删除

当前仓库中，明显应该清理的是“模板型、失效型、重复型”文档。

本次已处理：

- 已将 `medical-qc-frontend/README.md` 从默认 Vue 模板内容改为项目实际说明

当前不建议删除：

- `docs/project-documentation.md`：仍承担架构说明职责
- `docs/deployment-production.md`：仍承担生产部署职责
- `deploy/README.md`：仍承担部署目录说明职责

后续若再出现以下情况，建议直接删除或重写：

- 与根 `README.md` 完全重复的文档
- 仍保留脚手架默认内容的模板文档
- 描述旧架构、旧库名、旧启动方式且已不再可用的文档
