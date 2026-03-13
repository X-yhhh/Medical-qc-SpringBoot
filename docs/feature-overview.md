# 功能介绍

更新时间：2026-03-13

## 1. 角色说明

当前系统有两类角色：

| 角色 | 说明 |
| --- | --- |
| `doctor` | 执行影像质控、查看结果、维护患者信息、处理异常 |
| `admin` | 查看全局任务、管理用户、维护质控规则 |

## 2. 功能总览

| 功能域 | 页面入口 | 主要能力 | 适用角色 |
| --- | --- | --- | --- |
| 认证中心 | `/login` `/register` | 登录、注册、当前用户查询、退出登录 | 全部 |
| 仪表盘 | `/dashboard` | 总览统计、质控趋势、风险预警、待办事项 | 医生、管理员 |
| 头部平扫质控 | `/head` | 提交头部平扫质控任务 | 医生 |
| 胸部平扫质控 | `/chest-non-contrast` | 提交胸部平扫质控任务 | 医生 |
| 胸部增强质控 | `/chest-contrast` | 提交胸部增强质控任务 | 医生 |
| 冠脉 CTA 质控 | `/coronary-cta` | 提交冠脉 CTA 质控任务 | 医生 |
| 头部出血检测 | `/hemorrhage` | 上传影像、PACS 调取、AI 分析、历史结果查看、报告导出 | 医生 |
| 任务中心 | `/quality-tasks` | 统一查看任务状态、来源和结果摘要 | 医生、管理员 |
| 患者信息管理 | `/patient-info/*` | 患者档案维护、影像上传、PACS 初始化同步 | 医生、管理员 |
| 异常工单 | `/issues` | 异常统计、工单详情、状态流转、CAPA | 医生、管理员 |
| 用户管理 | `/admin/users` | 用户分页查询、状态和角色维护 | 管理员 |
| 规则中心 | `/admin/qc-rules` | 维护优先级、责任角色、SLA、自动建单策略 | 管理员 |

## 3. 重点功能说明

### 3.1 仪表盘

仪表盘提供：

- 当日待办与欢迎信息
- 核心统计卡片
- 质控趋势图
- 风险预警列表
- 最近访问与快捷入口

接口映射：

- `GET /api/v1/dashboard/overview`
- `GET /api/v1/dashboard/trend`

### 3.2 头部出血检测

头部出血检测是当前唯一真实接入 AI 推理的功能，支持：

- 本地图片上传
- PACS 检查记录选择
- AI 分析过程与日志展示
- 风险评分、主要异常和明细项展示
- 历史记录回看
- 检测报告导出

接口映射：

- `POST /api/v1/quality/hemorrhage`
- `GET /api/v1/quality/hemorrhage/history`
- `GET /api/v1/quality/hemorrhage/history/{recordId}`
- `GET /api/v1/pacs/search`

### 3.3 统一任务中心

任务中心覆盖：

- `head`
- `chest-non-contrast`
- `chest-contrast`
- `coronary-cta`
- 与统一任务模型相关的任务详情查询

接口映射：

- `POST /api/v1/quality/head/detect`
- `POST /api/v1/quality/chest-non-contrast/detect`
- `POST /api/v1/quality/chest-contrast/detect`
- `POST /api/v1/quality/coronary-cta/detect`
- `GET /api/v1/quality/tasks`
- `GET /api/v1/quality/tasks/{taskId}`

说明：

- 当前除头部出血检测外，其余任务结果以 mock 数据为主
- 任务写入、状态管理、结果查询和异常建单路径已统一

### 3.4 患者信息管理

患者管理支持五类任务的统一档案管理，包括：

- 分页查询
- 新增患者与检查信息
- 编辑患者信息
- 删除患者记录
- 从 PACS 缓存批量同步

接口映射：

- `GET /api/v1/patient-info/{taskType}`
- `POST /api/v1/patient-info/{taskType}`
- `PUT /api/v1/patient-info/{taskType}/{id}`
- `DELETE /api/v1/patient-info/{taskType}/{id}`
- `POST /api/v1/patient-info/{taskType}/sync-from-pacs`

### 3.5 异常工单

异常工单模块支持：

- 总异常统计
- 趋势分析
- 异常类型分布
- 工单列表查询
- 工单详情查看
- 状态流转
- CAPA 记录
- 指派处理人

接口映射：

- `GET /api/v1/summary/stats`
- `GET /api/v1/summary/trend`
- `GET /api/v1/summary/distribution`
- `GET /api/v1/summary/operators`
- `GET /api/v1/summary/recent`
- `GET /api/v1/summary/issues/{issueId}`
- `PATCH /api/v1/summary/issues/{issueId}/status`
- `PATCH /api/v1/summary/issues/{issueId}/workflow`

### 3.6 用户管理

用户管理支持：

- 用户分页查询
- 按角色和启用状态筛选
- 更新用户资料、角色和状态

接口映射：

- `GET /api/v1/admin/users`
- `PATCH /api/v1/admin/users/{userId}`
- `PUT /api/v1/admin/users/{userId}`

### 3.7 规则中心

规则中心支持：

- 规则分页查询
- 按模块、关键字、状态筛选
- 新增规则
- 编辑规则
- 维护自动建单、责任角色和 SLA

接口映射：

- `GET /api/v1/admin/qc-rules`
- `POST /api/v1/admin/qc-rules`
- `PUT /api/v1/admin/qc-rules/{ruleId}`

## 4. 页面与路由映射

| 路由 | 页面组件 |
| --- | --- |
| `/login` | `modules/auth/pages/LoginPage.vue` |
| `/register` | `modules/auth/pages/RegisterPage.vue` |
| `/dashboard` | `modules/dashboard/pages/DashboardPage.vue` |
| `/head` | `modules/qctask/pages/HeadQualityPage.vue` |
| `/chest-non-contrast` | `modules/qctask/pages/ChestNonContrastPage.vue` |
| `/chest-contrast` | `modules/qctask/pages/ChestContrastPage.vue` |
| `/coronary-cta` | `modules/qctask/pages/CoronaryCtaPage.vue` |
| `/hemorrhage` | `modules/qctask/pages/HemorrhagePage.vue` |
| `/quality-tasks` | `modules/qctask/pages/QualityTaskCenterPage.vue` |
| `/patient-info/*` | `modules/patient/pages/QualityPatientManagementPage.vue` |
| `/issues` | `modules/issue/pages/IssueSummaryPage.vue` |
| `/admin/users` | `modules/admin-user/pages/UserManagementPage.vue` |
| `/admin/qc-rules` | `modules/qcrule/pages/QcRuleCenterPage.vue` |

## 5. 当前实现边界

- `hemorrhage` 是当前真实链路
- 其他四类质控任务还不是完整 AI 推理链路
- PACS 读取的是缓存表，不代表已完成真实 PACS 网关对接
- 使用说明中的操作路径以当前前端页面为准
