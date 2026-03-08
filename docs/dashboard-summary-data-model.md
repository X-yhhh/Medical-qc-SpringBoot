# 首页与异常汇总页数据模型分析

## 1. 当前已接入真实后端的数据

### `users`
- 作用：登录用户信息、首页欢迎语、数据隔离（按用户查询）
- 当前已用于：首页欢迎区 `welcomeName`

### `hemorrhage_records`
- 作用：头部出血检测历史记录表
- 当前已用于：
  - 首页最近访问
  - 首页统计卡片
  - 首页待办事项时间轴
  - 首页质控合格率趋势
  - 脑出血检测历史回显
- 关键字段：`qc_status`、`prediction`、`midline_shift`、`ventricle_issue`、`created_at`

### `qc_issue_records`
- 作用：统一异常工单表
- 当前已用于：
  - 首页待处理任务数
  - 首页近期风险预警
  - 异常汇总页统计卡片
  - 异常汇总页趋势图
  - 异常汇总页异常类型分布
  - 异常汇总页异常详细记录列表
  - 异常工单状态流转（待处理 / 处理中 / 已解决）
  - 异常处理备注持久化

### `qc_issue_handle_logs`
- 作用：异常工单处理日志表
- 当前已用于：
  - 记录工单创建日志
  - 记录工单状态变更与处理备注

## 2. 当前页面与表的对应关系

### 首页 `dashboard`
- 欢迎语：`users`
- 今日/当前待处理任务：`qc_issue_records.status`
- 核心统计卡片：`hemorrhage_records`
- 最近访问：`hemorrhage_records`
- 风险预警：`qc_issue_records`
- 待办事项：`hemorrhage_records`
- 合格率趋势：`hemorrhage_records`

### 异常汇总页 `summary`
- 顶部卡片：`qc_issue_records`
- 异常趋势：`qc_issue_records.created_at`
- 异常类型分布：`qc_issue_records.issue_type`
- 异常详细记录：`qc_issue_records`
- 处理备注与状态变更：`qc_issue_records` + `qc_issue_handle_logs`

## 3. 当前表关系

### 已落地关系
- `users (1) -> (N) hemorrhage_records`
- `users (1) -> (N) qc_issue_records`
- `qc_issue_records (1) -> (N) qc_issue_handle_logs`
- `users (1) -> (N) qc_issue_handle_logs`
- `hemorrhage_records (1) -> (0..1) qc_issue_records`

### 来源关系说明
- 当前只实现 `hemorrhage_records -> qc_issue_records`
- 通过 `source_type = hemorrhage` + `source_record_id = hemorrhage_records.id` 保证唯一映射
- 后续其他质控项接入时，沿用相同建模方式即可

## 4. 当前后端对接实现

### 首页接口
- `GET /api/v1/dashboard/overview`
  - 欢迎语：用户表
  - 风险预警 / 待处理任务：异常工单表
  - 统计卡片 / 时间轴：脑出血历史表
- `GET /api/v1/dashboard/trend`
  - 合格率趋势：脑出血历史表按日聚合

### 异常汇总接口
- `GET /api/v1/summary/stats`
- `GET /api/v1/summary/trend`
- `GET /api/v1/summary/distribution`
- `GET /api/v1/summary/recent`
- `PATCH /api/v1/summary/issues/{issueId}/status`

## 5. 前端页面当前落地状态

### 首页 `src/views/dashboard/index.vue`
- 最近访问：真实读取 `/quality/hemorrhage/history`
- 风险预警 / 待处理任务：真实读取 `/dashboard/overview`
- 样式未改动，仅替换数据源为后端实时数据

### 异常汇总页 `src/views/summary/index.vue`
- 顶部卡片、趋势图、分布图、分页列表：全部真实读取后端
- 处理按钮：真实调用状态更新接口并回写数据库
- 导出按钮：导出当前筛选条件下的真实后端数据
- 分页：按后端返回的 `total/page/pages` 正确显示与回退

## 6. 当前未接入的部分

以下 4 个质控项仍为 mock，当前状态为“已建独立历史表，待真实接口接入”：
- `head_qc_records`
- `chest_non_contrast_qc_records`
- `chest_contrast_qc_records`
- `coronary_cta_qc_records`

## 7. 后续扩展建议

- 将其余 4 个质控项的真实检测结果落到已建好的历史表
- 在检测结果落库后，同步写入 `qc_issue_records`
- 首页与异常汇总页继续通过统一工单表聚合，避免每个页面重复拼装多张业务表
- 若数据量持续增长，可新增 `qc_daily_metrics` 做日维度预聚合