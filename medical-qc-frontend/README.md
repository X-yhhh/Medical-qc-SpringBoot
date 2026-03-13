# medical-qc-frontend

Medical QC SYS 前端子项目，基于 `Vue 3 + Vite + Element Plus` 实现，负责登录注册、仪表盘、质控任务、患者信息、异常工单、用户管理和规则中心页面。

## 技术栈

- Vue 3
- Vue Router 4
- Pinia 3
- Element Plus 2
- ECharts 6
- Vite 7
- TypeScript 类型检查 + ESLint

## 目录概览

```text
medical-qc-frontend/
├─ public/
├─ scripts/
├─ src/
│  ├─ app/
│  ├─ assets/
│  ├─ components/
│  ├─ composables/
│  ├─ modules/
│  └─ utils/
├─ package.json
└─ README.md
```

## 开发启动

```powershell
npm install
npm run dev
```

默认开发地址：`http://localhost:5173`

## 常用命令

```powershell
npm run dev
npm run build
npm run preview
npm run lint
npm run type-check
```

## 主要页面模块

| 模块 | 页面 |
| --- | --- |
| `auth` | 登录、注册 |
| `dashboard` | 仪表盘 |
| `qctask` | 头部平扫、胸部平扫、胸部增强、冠脉 CTA、头部出血检测、任务中心 |
| `patient` | 五类患者信息管理 |
| `issue` | 异常汇总与工单处理 |
| `admin-user` | 用户管理 |
| `qcrule` | 规则中心 |

## 相关文档

- 项目入口：`../README.md`
- 开发文档：`../docs/development-guide.md`
- 功能介绍：`../docs/feature-overview.md`
- 使用说明：`../docs/user-guide.md`
