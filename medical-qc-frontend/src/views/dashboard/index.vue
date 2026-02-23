<!-- src/views/dashboard/index.vue -->
<template>
  <div class="dashboard-container">
    <!-- 顶部欢迎区: 展示用户信息及今日待办概览 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h2>你好, XXX医生</h2>
        <p>今日待处理影像质控任务 <span class="highlight-text">28</span> 项，请及时处理。</p>
      </div>
      <div class="date-info">
        <el-tag effect="dark" type="primary">{{ currentDate }}</el-tag>
      </div>
    </div>

    <!-- 核心数据看板: 展示关键质控指标 (目前使用静态模拟数据) -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="item in statsData" :key="item.title">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-icon" :class="item.type" style="margin-left: 10px;">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="stats-info">
            <div class="stats-title">{{ item.title }}</div>
            <div class="stats-value">
              {{ item.value }}
              <span class="stats-unit" v-if="item.unit">{{ item.unit }}</span>
            </div>
            <div class="stats-trend">
              <span :class="item.trend > 0 ? 'up' : 'down'">
                <el-icon><component :is="item.trend > 0 ? 'CaretTop' : 'CaretBottom'" /></el-icon>
                {{ Math.abs(item.trend) }}%
              </span>
              <span class="trend-label">较昨日</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体内容区: 包含快捷入口、图表和侧边栏 -->
    <el-row :gutter="20" class="main-content">
      <!-- 左侧主要区域: 快捷质控入口 & 趋势图表 -->
      <el-col :span="16">
        <!-- 快捷质控入口卡片 -->
        <el-card class="box-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><Monitor /></el-icon> 影像质控工作台
              </span>
              <el-button text type="primary">查看全部</el-button>
            </div>
          </template>

          <div class="quick-access-layout">
            <!-- 网格布局: 核心功能导航 -->
            <div class="quick-access-grid">
              <div
                class="quick-access-item"
                v-for="item in quickAccessItems"
                :key="item.path"
                @click="$router.push(item.path)"
              >
                <div class="icon-wrapper" :style="{ background: item.color }">
                  <el-icon><component :is="item.icon" /></el-icon>
                </div>
                <div class="item-name">{{ item.name }}</div>
                <div class="item-desc">{{ item.desc }}</div>
              </div>
            </div>

            <!-- 侧边栏: 最近访问与常用操作 -->
            <div class="quick-access-sidebar">
              <div class="sidebar-title">最近访问</div>
              <div class="recent-list">
                <div class="recent-item" v-for="item in recentVisits" :key="item.id">
                  <el-tag size="small" :type="item.type" effect="plain" class="recent-tag">{{
                    item.tag
                  }}</el-tag>
                  <span class="recent-text">{{ item.name }}</span>
                  <span class="recent-time">{{ item.time }}</span>
                </div>
              </div>

              <div class="sidebar-divider"></div>

              <div class="sidebar-title">快捷操作</div>
              <div class="action-buttons">
                <el-button type="primary" plain size="small" icon="Upload">上传影像</el-button>
                <el-button type="success" plain size="small" icon="DocumentAdd">新建报告</el-button>
                <el-button type="warning" plain size="small" icon="Setting">质控配置</el-button>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 质控趋势图表区域 (预留 ECharts 挂载点) -->
        <el-card class="box-card" shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><TrendCharts /></el-icon> 质控合格率趋势
              </span>
              <el-radio-group v-model="chartPeriod" size="small">
                <el-radio-button label="week">本周</el-radio-button>
                <el-radio-button label="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div class="chart-placeholder">
            <el-empty description="数据可视化图表区域 (ECharts)" :image-size="100"></el-empty>
          </div>
        </el-card>
      </el-col>

      <!-- 右侧辅助区域: 风险提示与待办事项 -->
      <el-col :span="8">
        <!-- 风险预警卡片 -->
        <el-card class="box-card risk-card" shadow="never">
          <template #header>
            <div class="card-header">
              <span class="header-title risk-title">
                <el-icon><Warning /></el-icon> 近期风险预警
              </span>
              <el-tag type="danger" effect="plain" size="small">3 项高危</el-tag>
            </div>
          </template>
          <div class="risk-list">
            <div class="risk-item" v-for="(risk, index) in riskList" :key="index">
              <div class="risk-icon">
                <el-icon color="#F56C6C"><WarningFilled /></el-icon>
              </div>
              <div class="risk-content">
                <div class="risk-text">{{ risk.content }}</div>
                <div class="risk-time">{{ risk.time }}</div>
              </div>
              <el-button type="primary" link size="small" style="margin-left: 15px">处理</el-button>
            </div>
          </div>
        </el-card>

        <!-- 待办事项时间轴 -->
        <el-card class="box-card" shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><List /></el-icon> 待办事项
              </span>
            </div>
          </template>
          <el-timeline style="padding-left: 10px">
            <el-timeline-item
              v-for="(activity, index) in activities"
              :key="index"
              :type="activity.type"
              :color="activity.color"
              :timestamp="activity.timestamp"
              size="large"
            >
              {{ activity.content }}
            </el-timeline-item>
          </el-timeline>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
/**
 * @file dashboard/index.vue
 * @description 首页仪表盘
 * 展示核心质控数据指标、快捷功能入口、风险预警及待办事项。
 * 
 * 对接API说明:
 * - 本页面目前主要使用静态模拟数据进行展示
 * - statsData: 对应后端 /api/dashboard/stats 接口 (待开发)
 * - riskList: 对应后端 /api/dashboard/risks 接口 (待开发)
 * - activities: 对应后端 /api/dashboard/activities 接口 (待开发)
 */

import { ref, onMounted } from 'vue'
import dayjs from 'dayjs'

// 当前日期显示
const currentDate = ref(dayjs().format('YYYY年MM月DD日 dddd'))
// 图表统计周期选择 (week/month)
const chartPeriod = ref('week')

/**
 * 核心统计数据 (模拟)
 * 用于展示今日工作量及质控评分趋势
 */
const statsData = [
  { title: '今日检查总量', value: 427, unit: '例', icon: 'DataLine', trend: 12.5, type: 'primary' },
  { title: 'AI 自动审核', value: 356, unit: '例', icon: 'Cpu', trend: 8.2, type: 'success' },
  { title: '人工复核数', value: 71, unit: '例', icon: 'Edit', trend: -5.3, type: 'warning' },
  { title: '平均质控分', value: 94.8, unit: '分', icon: 'Trophy', trend: 1.2, type: 'info' },
]

/**
 * 快捷入口配置
 * 配置系统各个子模块的路由跳转信息
 */
const quickAccessItems = [
  {
    name: 'CT头部平扫',
    desc: '脑梗/出血筛查',
    path: '/head',
    icon: 'Aim',
    color: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  },
  {
    name: '头部出血检测',
    desc: '急诊高危预警',
    path: '/hemorrhage',
    icon: 'FirstAidKit',
    color: 'linear-gradient(135deg, #ff9a9e 0%, #fecfef 99%, #fecfef 100%)',
  },
  {
    name: 'CT胸部平扫',
    desc: '肺结节筛查',
    path: '/chest-non-contrast',
    icon: 'View',
    color: 'linear-gradient(135deg, #84fab0 0%, #8fd3f4 100%)',
  },
  {
    name: 'CT胸部增强',
    desc: '纵隔/血管病变',
    path: '/chest-contrast',
    icon: 'MagicStick',
    color: 'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  },
  {
    name: '冠脉CTA',
    desc: '心脏血管造影',
    path: '/coronary-cta',
    icon: 'Odometer',
    color: 'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)',
  },
  {
    name: '异常汇总',
    desc: '问题报告统计',
    path: '/issues',
    icon: 'DataAnalysis',
    color: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)',
  },
]

/**
 * 风险提示列表 (模拟)
 * 展示最新的高风险质控问题
 */
const riskList = [
  { content: '发现 3 例 CT 头部扫描伪影过重，需重新扫描', time: '10:23' },
  { content: '冠脉 CTA 重建失败率上升，建议检查设备', time: '09:45' },
  { content: '急诊胸部 CT 辐射剂量超标预警', time: '08:30' },
]

/**
 * 待办事项列表 (模拟)
 * 展示医生的个人待办任务
 */
const activities = [
  { content: '审核早班急诊报告', timestamp: '08:00', type: 'primary', color: '#409EFF' },
  { content: '参加科室质控周会', timestamp: '14:00', type: 'warning', color: '#E6A23C' },
  { content: '整理月度质控报表', timestamp: '17:00', type: 'info', color: '#909399' },
]
</script>

<style scoped>
/* 页面整体容器 */
.dashboard-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 60px);
}

/* 欢迎区样式 */
.welcome-section {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24px;
  background: #fff;
  padding: 20px 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.welcome-text h2 {
  margin: 0 0 8px 0;
  font-size: 20px;
  color: #303133;
  font-weight: 600;
}

.welcome-text p {
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.highlight-text {
  color: #409eff;
  font-weight: bold;
  font-size: 16px;
  margin: 0 4px;
}

/* 统计卡片样式 */
.stats-row {
  margin-bottom: 24px;
}

.stats-card {
  display: flex;
  align-items: center;
  border: none;
  border-radius: 8px;
  transition: all 0.3s;
}

.stats-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.1);
}

:deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px !important;
  width: 100%;
}

.stats-icon {
  width: 56px;
  height: 56px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16px;
  font-size: 28px;
}

.stats-icon.primary {
  background: #ecf5ff;
  color: #409eff;
}
.stats-icon.success {
  background: #f0f9eb;
  color: #67c23a;
}
.stats-icon.warning {
  background: #fdf6ec;
  color: #e6a23c;
}
.stats-icon.info {
  background: #f4f4f5;
  color: #909399;
}

.stats-info {
  flex: 1;
}

.stats-title {
  color: #909399;
  font-size: 13px;
  margin-bottom: 4px;
}

.stats-value {
  color: #303133;
  font-size: 24px;
  font-weight: bold;
  margin-bottom: 4px;
}

.stats-unit {
  font-size: 12px;
  font-weight: normal;
  color: #909399;
  margin-left: 2px;
}

.stats-trend {
  font-size: 12px;
  display: flex;
  align-items: center;
}

.stats-trend .up {
  color: #f56c6c;
}
.stats-trend .down {
  color: #67c23a;
}
.stats-trend .trend-label {
  color: #c0c4cc;
  margin-left: 4px;
}

/* 通用卡片头部 */
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* 快捷入口布局容器 */
.quick-access-layout {
  display: flex;
  gap: 20px;
}

/* 快捷入口网格 - 占据左侧主要空间 */
.quick-access-grid {
  flex: 1;
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 20px;
  padding: 10px 0;
}

/* 右侧侧边栏 - 填充空白 */
.quick-access-sidebar {
  width: 280px;
  border-left: 1px solid #ebeef5;
  padding-left: 20px;
  padding-top: 10px;
  display: flex;
  flex-direction: column;
}

.sidebar-title {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 12px;
}

.sidebar-divider {
  height: 1px;
  background-color: #ebeef5;
  margin: 16px 0;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.recent-item {
  display: flex;
  align-items: center;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  padding: 4px 0;
}

.recent-item:hover {
  color: #409eff;
}

.recent-tag {
  margin-right: 8px;
  min-width: 48px;
  text-align: center;
}

.recent-text {
  flex: 1;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  margin-right: 8px;
}

.recent-time {
  color: #909399;
  font-size: 12px;
}

.action-buttons {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.action-buttons .el-button {
  margin-left: 0;
  justify-content: flex-start;
}

.quick-access-item {
  background: #f8f9fa;
  border-radius: 8px;
  padding: 20px;
  cursor: pointer;
  transition: all 0.3s;
  display: flex;
  flex-direction: column;
  align-items: center;
  text-align: center;
  border: 1px solid #ebeef5;
}

.quick-access-item:hover {
  background: #fff;
  box-shadow: 0 4px 16px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
  border-color: #dcdfe6;
}

.icon-wrapper {
  width: 48px;
  height: 48px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 24px;
  margin-bottom: 12px;
  box-shadow: 0 4px 10px rgba(0, 0, 0, 0.1);
}

.item-name {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 4px;
}

.item-desc {
  font-size: 12px;
  color: #909399;
}

/* 风险提示列表 */
.risk-card :deep(.el-card__header) {
  border-bottom: 1px solid #ebeef5;
  padding-bottom: 15px;
}

.risk-title {
  color: #f56c6c;
}

.risk-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.risk-item {
  display: flex;
  align-items: flex-start;
  padding-bottom: 12px;
  border-bottom: 1px dashed #f2f2f2;
}

.risk-item:last-child {
  border-bottom: none;
  padding-bottom: 0;
}

.risk-icon {
  margin-right: 12px;
  margin-top: 2px;
  font-size: 18px;
}

.risk-content {
  flex: 1;
}

.risk-text {
  font-size: 14px;
  color: #606266;
  line-height: 1.4;
  margin-bottom: 4px;
}

.risk-time {
  font-size: 12px;
  color: #909399;
}

/* 图表占位 */
.chart-placeholder {
  height: 240px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #fcfcfc;
  border-radius: 4px;
}
</style>