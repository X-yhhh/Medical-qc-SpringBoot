<!-- DashboardPage.vue -->
<template>
  <div class="dashboard-container">
    <!-- 顶部欢迎区: 展示用户信息及今日待办概览 -->
    <div class="welcome-section">
      <div class="welcome-text">
        <h2>你好, {{ welcomeName }}</h2>
        <p>{{ welcomeDescription }} <span class="highlight-text">{{ pendingTaskCount }}</span> 项，请及时处理。</p>
      </div>
      <div class="date-info">
        <el-tag effect="dark" type="primary">{{ currentDate }}</el-tag>
      </div>
    </div>

    <!-- 核心数据看板: 展示关键质控指标 (实时来自后端聚合数据) -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="item in statsData" :key="item.title">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-icon" :class="item.type" style="margin-left: 10px">
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
                <el-icon><Monitor /></el-icon> {{ quickAccessTitle }}
              </span>
              <el-button text type="primary" @click="handleQuickAccessHeaderClick">{{ quickAccessActionText }}</el-button>
            </div>
          </template>

          <div class="quick-access-layout">
            <!-- 网格布局: 核心功能导航 -->
            <div class="quick-access-grid">
              <div
                class="quick-access-item"
                v-for="item in quickAccessItems"
                :key="item.path"
                @click="router.push(item.path)"
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
              <div class="sidebar-title">{{ recentPanelTitle }}</div>
              <div v-if="recentVisits.length" class="recent-list">
                <div class="recent-item" v-for="item in recentVisits" :key="item.id" @click="handleRecentVisitClick(item)">
                  <el-image
                    v-if="item.imageUrl"
                    :src="item.imageUrl"
                    fit="cover"
                    class="recent-image"
                    :preview-src-list="[item.imageUrl]"
                    preview-teleported
                    @click.stop
                  />
                  <div v-else class="recent-image-placeholder">无图</div>
                  <el-tag size="small" :type="item.type" effect="plain" class="recent-tag">{{
                    item.tag
                  }}</el-tag>
                  <span class="recent-text">
                    <span class="recent-name">{{ item.name }}</span>
                    <span :class="['recent-issue', item.tag === '合格' ? 'recent-issue-normal' : '']">{{ item.issue }}</span>
                  </span>
                  <span class="recent-time">{{ item.time }}</span>
                </div>
              </div>
              <el-empty v-else :description="recentEmptyText" :image-size="70" />

              <div class="sidebar-divider"></div>

              <div class="sidebar-title">{{ actionPanelTitle }}</div>
              <div class="action-buttons">
                <el-button
                  v-for="action in sideActionButtons"
                  :key="action.label"
                  :type="action.type"
                  plain
                  size="small"
                  :icon="action.icon"
                  @click="router.push(action.path)"
                >{{ action.label }}</el-button>
              </div>
            </div>
          </div>
        </el-card>

        <!-- 质控运行趋势区域 -->
        <el-card class="box-card" shadow="never" style="margin-top: 20px">
          <template #header>
            <div class="card-header">
              <span class="header-title">
                <el-icon><TrendCharts /></el-icon> 质控运行趋势
              </span>
              <el-radio-group v-model="chartPeriod" size="small">
                <el-radio-button label="week">本周</el-radio-button>
                <el-radio-button label="month">本月</el-radio-button>
              </el-radio-group>
            </div>
          </template>

          <div class="trend-summary-grid">
            <div
              v-for="item in trendSummaryItems"
              :key="item.label"
              :class="['trend-summary-item', `is-${item.type}`]"
            >
              <div class="trend-summary-label">{{ item.label }}</div>
              <div class="trend-summary-value">
                {{ item.value }}
                <span v-if="item.unit" class="trend-summary-unit">{{ item.unit }}</span>
              </div>
              <div class="trend-summary-extra">{{ item.extra }}</div>
            </div>
          </div>

          <div v-if="hasDashboardTrendData" ref="dashboardTrendChartRef" class="trend-chart"></div>
          <div v-else class="trend-empty-state">
            <el-empty :image-size="88">
              <template #description>
                <div class="trend-empty-desc">
                  <div class="trend-empty-title">暂无可视化趋势数据</div>
                    <div class="trend-empty-text">完成任一质控任务后，这里将展示平台检查量、异常量、合格率与平均质控分的变化。</div>
                </div>
              </template>
            </el-empty>
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
              <el-tag type="danger" effect="plain" size="small">{{ highRiskCount }} 项高危</el-tag>
            </div>
          </template>
          <div v-if="riskList.length" class="risk-list">
            <div class="risk-item" v-for="(risk, index) in riskList" :key="index">
              <div class="risk-icon">
                <el-icon color="#F56C6C"><WarningFilled /></el-icon>
              </div>
              <div class="risk-content">
                <div class="risk-text">{{ risk.content }}</div>
                <div class="risk-time">{{ risk.time }}</div>
              </div>
              <el-button type="primary" link size="small" style="margin-left: 15px" @click="$router.push(risk.targetRoute || '/issues')">处理</el-button>
            </div>
          </div>
          <el-empty v-else description="暂无风险预警" :image-size="80"></el-empty>
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
          <el-timeline v-if="activities.length" style="padding-left: 10px">
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
          <el-empty v-else description="暂无待办事项" :image-size="80"></el-empty>
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
 * - statsData / riskList / activities / welcome: 对应后端 /api/v1/dashboard/overview
 * - chartTrend: 对应后端 /api/v1/dashboard/trend
 * - recentVisits: 对应后端 /api/v1/dashboard/overview 聚合返回
 */

import { computed, ref, onMounted, onUnmounted, watch, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { echarts } from '@/utils/echarts'
import { getDashboardOverview, getDashboardTrend } from '@/modules/dashboard/api/dashboardApi'
import { getStoredUserInfo } from '@/utils/auth'

const router = useRouter()

// 当前日期显示
const currentDate = ref(dayjs().format('YYYY年MM月DD日 dddd'))
// 图表统计周期选择 (week/month)
const chartPeriod = ref('week')
const welcomeName = ref('医生')
const pendingTaskCount = ref(0)
const statsData = ref([])
const riskList = ref([])
const highRiskCount = ref(0)
const activities = ref([])
const dashboardTrendChartRef = ref(null)
let dashboardTrendChart = null

const currentUserInfo = computed(() => getStoredUserInfo())
const isAdminView = computed(() => currentUserInfo.value?.role === 'admin')

const welcomeDescription = computed(() => {
  return isAdminView.value ? '当前待关注系统质控任务' : '今日待处理影像质控任务'
})

const quickAccessTitle = computed(() => {
  return isAdminView.value ? '系统治理工作台' : '影像质控工作台'
})

const quickAccessActionText = computed(() => {
  return '查看任务中心'
})

const recentPanelTitle = computed(() => {
  return isAdminView.value ? '近期系统动态' : '最近访问'
})

const recentEmptyText = computed(() => {
  return isAdminView.value ? '暂无系统动态' : '暂无最近访问'
})

const actionPanelTitle = computed(() => {
  return isAdminView.value ? '管理捷径' : '快捷操作'
})

/**
 * 首页趋势图默认数据结构。
 * 统一用于接口回填和空状态渲染，避免页面出现 undefined。
 *
 * @returns {Object} 趋势图默认数据
 */
const createDefaultTrendData = () => ({
  dates: [],
  passRates: [],
  totalCounts: [],
  abnormalCounts: [],
  averageScores: [],
  summary: {
    totalChecks: 0,
    averagePassRate: 0,
    averageScore: 0,
    abnormalPeak: 0,
    abnormalPeakDate: '--',
    bestPassRate: 0,
    bestPassRateDate: '--',
    hasData: false,
  },
})

const dashboardTrendData = ref(createDefaultTrendData())

/**
 * 当前趋势区是否存在可视化数据。
 */
const hasDashboardTrendData = computed(() => Boolean(dashboardTrendData.value?.summary?.hasData))

/**
 * 趋势区顶部概览指标。
 * 用更直观的摘要帮助用户快速把握当前周期质控运行状态。
 */
const trendSummaryItems = computed(() => {
  const summary = dashboardTrendData.value?.summary || createDefaultTrendData().summary
  const periodText = chartPeriod.value === 'month' ? '最近30天' : '最近7天'

  return [
    {
      label: '周期检查量',
      value: summary.totalChecks || 0,
      unit: '例',
      extra: `${periodText}累计完成检查`,
      type: 'primary',
    },
    {
      label: '平均合格率',
      value: Number(summary.averagePassRate || 0).toFixed(1),
      unit: '%',
      extra: summary.bestPassRateDate && summary.bestPassRateDate !== '--'
        ? `最佳表现 ${summary.bestPassRateDate} · ${Number(summary.bestPassRate || 0).toFixed(1)}%`
        : '等待首批趋势数据',
      type: 'success',
    },
    {
      label: '异常峰值',
      value: summary.abnormalPeak || 0,
      unit: '例',
      extra: summary.abnormalPeakDate && summary.abnormalPeakDate !== '--'
        ? `${summary.abnormalPeakDate} 风险最高`
        : '当前周期未出现异常峰值',
      type: 'danger',
    },
    {
      label: '平均质控分',
      value: Number(summary.averageScore || 0).toFixed(1),
      unit: '分',
      extra: '综合反映图像质量稳定度',
      type: 'warning',
    },
  ]
})

/**
 * 规范化首页趋势接口响应。
 *
 * @param {Object} response - 后端趋势接口响应
 * @returns {Object} 清洗后的趋势数据
 */
const normalizeDashboardTrendData = (response) => {
  const defaultTrend = createDefaultTrendData()
  const summary = response?.summary || {}

  return {
    dates: Array.isArray(response?.dates) ? response.dates : defaultTrend.dates,
    passRates: Array.isArray(response?.passRates) ? response.passRates : defaultTrend.passRates,
    totalCounts: Array.isArray(response?.totalCounts) ? response.totalCounts : defaultTrend.totalCounts,
    abnormalCounts: Array.isArray(response?.abnormalCounts) ? response.abnormalCounts : defaultTrend.abnormalCounts,
    averageScores: Array.isArray(response?.averageScores) ? response.averageScores : defaultTrend.averageScores,
    summary: {
      ...defaultTrend.summary,
      ...summary,
    },
  }
}

/**
 * 快捷入口配置
 * 配置系统各个子模块的路由跳转信息
 */
const doctorQuickAccessItems = [
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
    name: '质控任务中心',
    desc: '查看任务状态与结果',
    path: '/quality-tasks',
    icon: 'DataAnalysis',
    color: 'linear-gradient(135deg, #f6d365 0%, #fda085 100%)',
  },
]

const adminQuickAccessItems = [
  {
    name: '质控任务中心',
    desc: '查看全局任务状态与失败原因',
    path: '/quality-tasks',
    icon: 'Tickets',
    color: 'linear-gradient(135deg, #5b8ff9 0%, #69c0ff 100%)',
  },
  {
    name: '异常汇总',
    desc: '查看全局异常工单与处理状态',
    path: '/issues',
    icon: 'DataAnalysis',
    color: 'linear-gradient(135deg, #f6bd16 0%, #f6903d 100%)',
  },
  {
    name: '风险预警',
    desc: '聚焦高风险异常与待办事项',
    path: '/issues',
    icon: 'WarningFilled',
    color: 'linear-gradient(135deg, #ff7875 0%, #ff9c6e 100%)',
  },
  {
    name: '运行概览',
    desc: '查看系统质控趋势与活跃动态',
    path: '/dashboard',
    icon: 'TrendCharts',
    color: 'linear-gradient(135deg, #36cfc9 0%, #73d13d 100%)',
  },
]

const quickAccessItems = computed(() => {
  return isAdminView.value ? adminQuickAccessItems : doctorQuickAccessItems
})

/**
 * 最近访问记录
 * 展示用户最近完成的脑出血检测历史记录
 */
const recentVisits = ref([])

const sideActionButtons = computed(() => {
  if (isAdminView.value) {
    return [
      { label: '任务中心', path: '/quality-tasks', icon: 'Tickets', type: 'primary' },
      { label: '用户管理', path: '/admin/users', icon: 'User', type: 'warning' },
      { label: '异常工单', path: '/issues', icon: 'Warning', type: 'success' },
    ]
  }

  return [
    { label: '任务中心', path: '/quality-tasks', icon: 'Tickets', type: 'primary' },
    { label: '上传影像', path: '/hemorrhage', icon: 'Upload', type: 'success' },
    { label: '异常工单', path: '/issues', icon: 'Warning', type: 'warning' },
  ]
})

/**
 * 加载首页总览数据。
 * 该数据由后端统一聚合脑出血检测与异步质控任务。
 */
const loadDashboardOverview = async () => {
  const response = await getDashboardOverview()
  welcomeName.value = response?.welcomeName || '医生'
  pendingTaskCount.value = response?.pendingTaskCount || 0
  statsData.value = Array.isArray(response?.stats) ? response.stats : []
  riskList.value = Array.isArray(response?.riskList) ? response.riskList : []
  highRiskCount.value = response?.highRiskCount || 0
  activities.value = Array.isArray(response?.activities) ? response.activities : []
  recentVisits.value = Array.isArray(response?.recentVisits) ? response.recentVisits : []
}

const handleRecentVisitClick = (item) => {
  router.push({ path: item.path, query: item.query })
}

const handleQuickAccessHeaderClick = () => {
  router.push('/quality-tasks')
}

/**
 * 加载首页质控合格率趋势图数据。
 */
const loadDashboardTrend = async () => {
  try {
    const response = await getDashboardTrend(chartPeriod.value)
    dashboardTrendData.value = normalizeDashboardTrendData(response)
    await nextTick()

    if (hasDashboardTrendData.value) {
      initDashboardTrendChart(dashboardTrendData.value)
      return
    }

    dashboardTrendChart?.dispose()
    dashboardTrendChart = null
  } catch (error) {
    console.error('加载首页质控运行趋势失败', error)
    dashboardTrendData.value = createDefaultTrendData()
    dashboardTrendChart?.dispose()
    dashboardTrendChart = null
  }
}

/**
 * 初始化首页趋势图。
 *
 * @param {Object} data - 趋势图数据
 */
const initDashboardTrendChart = (data) => {
  if (!dashboardTrendChartRef.value) {
    return
  }

  if (!dashboardTrendChart) {
    dashboardTrendChart = echarts.init(dashboardTrendChartRef.value)
  }

  dashboardTrendChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: 'rgba(255, 255, 255, 0.96)',
      borderColor: '#ebeef5',
      borderWidth: 1,
      textStyle: { color: '#303133' },
      formatter: (params) => {
        const rows = params.map((item) => {
          const unit = item.seriesName.includes('率') ? '%' : item.seriesName.includes('评分') ? '分' : '例'
          return `${item.marker}${item.seriesName}：${item.value}${unit}`
        })
        return `${params?.[0]?.axisValue || '--'}<br/>${rows.join('<br/>')}`
      },
    },
    legend: {
      top: 0,
      itemWidth: 12,
      itemHeight: 8,
      textStyle: { color: '#606266', fontSize: 12 },
      data: ['检查量', '异常量', '合格率', '平均评分'],
    },
    grid: { left: '3%', right: '4%', bottom: '3%', top: 54, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data?.dates || [],
      axisLine: { lineStyle: { color: '#dcdfe6' } },
      axisLabel: { color: '#909399' },
    },
    yAxis: [
      {
        type: 'value',
        min: 0,
        max: 100,
        axisLabel: { formatter: '{value}%', color: '#909399' },
        splitLine: { lineStyle: { type: 'dashed', color: '#ebeef5' } },
      },
      {
        type: 'value',
        axisLabel: { formatter: '{value}例', color: '#909399' },
        splitLine: { show: false },
      },
    ],
    series: [
      {
        name: '检查量',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 18,
        itemStyle: {
          color: '#dbeafe',
          borderRadius: [4, 4, 0, 0],
        },
        emphasis: { focus: 'series' },
        data: data?.totalCounts || [],
      },
      {
        name: '异常量',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 14,
        itemStyle: {
          color: '#fca5a5',
          borderRadius: [4, 4, 0, 0],
        },
        emphasis: { focus: 'series' },
        data: data?.abnormalCounts || [],
      },
      {
        name: '合格率',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        symbol: 'circle',
        symbolSize: 8,
        data: data?.passRates || [],
        itemStyle: { color: '#67C23A' },
        lineStyle: { width: 3, color: '#67C23A' },
        areaStyle: {
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: 'rgba(103,194,58,0.28)' },
            { offset: 1, color: 'rgba(103,194,58,0.03)' },
          ]),
        },
      },
      {
        name: '平均评分',
        type: 'line',
        smooth: true,
        yAxisIndex: 0,
        symbol: 'circle',
        symbolSize: 7,
        data: data?.averageScores || [],
        itemStyle: { color: '#409EFF' },
        lineStyle: { width: 2, color: '#409EFF', type: 'dashed' },
      },
    ],
  })
}

/**
 * 响应窗口尺寸变化，保持图表自适应。
 */
const handleResize = () => {
  dashboardTrendChart?.resize()
}

/**
 * 页面挂载时加载最新历史记录。
 */
onMounted(async () => {
  const pendingTasks = [loadDashboardOverview(), loadDashboardTrend()]
  await Promise.allSettled(pendingTasks)
  window.addEventListener('resize', handleResize)
})

watch(chartPeriod, () => {
  loadDashboardTrend()
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  dashboardTrendChart?.dispose()
  dashboardTrendChart = null
})
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
  gap: 8px;
  font-size: 13px;
  color: #606266;
  cursor: pointer;
  padding: 4px 0;
}

.recent-image {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  border: 1px solid #ebeef5;
  flex: 0 0 34px;
}

.recent-image-placeholder {
  width: 34px;
  height: 34px;
  border-radius: 6px;
  border: 1px dashed #dcdfe6;
  background: #fafafa;
  color: #909399;
  font-size: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 34px;
}

.recent-item:hover {
  color: #409eff;
}

.recent-tag {
  min-width: 48px;
  text-align: center;
}

.recent-text {
  flex: 1;
  overflow: hidden;
  display: flex;
  align-items: center;
  margin-right: 8px;
  min-width: 0;
}

.recent-name {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex-shrink: 1;
}

.recent-issue {
  color: #f56c6c;
  margin-left: 6px;
  white-space: nowrap;
  flex-shrink: 0;
}

.recent-issue-normal {
  color: #909399;
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

/* 图表区域 */
.trend-summary-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
  margin-bottom: 18px;
}

.trend-summary-item {
  background: #f8fafc;
  border: 1px solid #ebeef5;
  border-radius: 10px;
  padding: 14px 16px;
  transition: all 0.3s;
}

.trend-summary-item:hover {
  transform: translateY(-1px);
  box-shadow: 0 6px 18px rgba(0, 0, 0, 0.05);
}

.trend-summary-item.is-primary {
  background: linear-gradient(180deg, rgba(64, 158, 255, 0.08), rgba(64, 158, 255, 0.02));
}

.trend-summary-item.is-success {
  background: linear-gradient(180deg, rgba(103, 194, 58, 0.10), rgba(103, 194, 58, 0.03));
}

.trend-summary-item.is-danger {
  background: linear-gradient(180deg, rgba(245, 108, 108, 0.10), rgba(245, 108, 108, 0.03));
}

.trend-summary-item.is-warning {
  background: linear-gradient(180deg, rgba(230, 162, 60, 0.10), rgba(230, 162, 60, 0.03));
}

.trend-summary-label {
  font-size: 12px;
  color: #909399;
  margin-bottom: 6px;
}

.trend-summary-value {
  font-size: 24px;
  font-weight: 700;
  color: #303133;
  line-height: 1.2;
}

.trend-summary-unit {
  font-size: 12px;
  color: #909399;
  margin-left: 2px;
}

.trend-summary-extra {
  margin-top: 6px;
  font-size: 12px;
  color: #606266;
  line-height: 1.4;
}

.trend-chart {
  height: 290px;
  background: #fcfcfc;
  border-radius: 8px;
}

.trend-empty-state {
  min-height: 290px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(180deg, #fbfdff 0%, #f7f9fc 100%);
  border-radius: 8px;
  border: 1px dashed #dcdfe6;
}

.trend-empty-desc {
  display: flex;
  flex-direction: column;
  gap: 6px;
  align-items: center;
}

.trend-empty-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.trend-empty-text {
  max-width: 420px;
  font-size: 13px;
  color: #909399;
  line-height: 1.6;
}
</style>
