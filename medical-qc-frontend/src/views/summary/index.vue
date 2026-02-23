<template>
  <div class="summary-container">
    <!--
      顶部导航与标题
      功能：显示页面标题和数据更新时间
    -->
    <div class="page-header">
      <div class="header-content">
        <div class="title-section">
          <h2 class="page-title">异常数据汇总看板</h2>
          <span class="update-time">数据更新时间：{{ updateTime }}</span>
        </div>
      </div>
    </div>

    <!--
      核心指标统计卡片
      功能：展示总异常数、今日新增、待处理任务、处理解决率四个核心指标
      数据源：statsData (通过 getSummaryStats API 获取)
    -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="(item, index) in statsCards" :key="index">
        <el-card shadow="hover" class="stats-card">
          <div class="stats-icon-wrapper" :class="item.type">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="stats-content">
            <div class="stats-label">{{ item.title }}</div>
            <div class="stats-number">
              {{ item.value }}
              <span class="unit" v-if="item.unit">{{ item.unit }}</span>
            </div>
            <div class="stats-trend">
              <span :class="item.trend > 0 ? 'up' : 'down'">
                <el-icon><component :is="item.trend > 0 ? 'CaretTop' : 'CaretBottom'" /></el-icon>
                {{ Math.abs(item.trend) }}%
              </span>
              <span class="trend-text">较上周</span>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!--
      图表分析区域
      包含：
      1. 异常趋势分析 (折线图): 支持切换近7天/近30天
      2. 异常类型分布 (饼图): 展示不同类型异常的占比
    -->
    <el-row :gutter="20" class="charts-section">
      <!-- 趋势图 -->
      <el-col :span="16">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-title">
                <el-icon><TrendCharts /></el-icon>
                <span>异常趋势分析</span>
              </div>
              <el-radio-group v-model="trendRange" size="small" @change="fetchTrend">
                <el-radio-button label="7">近7天</el-radio-button>
                <el-radio-button label="30">近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
      <!-- 分布图 -->
      <el-col :span="8">
        <el-card shadow="never" class="chart-card">
          <template #header>
            <div class="card-header">
              <div class="header-title">
                <el-icon><PieChart /></el-icon>
                <span>异常类型分布</span>
              </div>
            </div>
          </template>
          <div ref="pieChartRef" class="chart-box"></div>
        </el-card>
      </el-col>
    </el-row>

    <!--
      详细数据列表
      功能：展示异常记录的详细表格，支持搜索、筛选、分页和导出
      操作：查看详情、处理异常
    -->
    <el-card shadow="never" class="list-card">
      <template #header>
        <div class="card-header list-header">
          <div class="header-left">
            <span class="header-title"><el-icon><List /></el-icon> 异常详细记录</span>
            <el-tag type="info" effect="plain" round class="count-tag">共 {{ total }} 条</el-tag>
          </div>
          <div class="header-actions">
            <el-input
              v-model="searchQuery"
              placeholder="搜索患者姓名/检查号"
              prefix-icon="Search"
              clearable
              class="search-input"
              @clear="handleSearch"
              @keyup.enter="handleSearch"
            />
            <el-select v-model="filterStatus" placeholder="状态筛选" clearable class="status-select" @change="handleSearch">
              <el-option label="待处理" value="待处理" />
              <el-option label="处理中" value="处理中" />
              <el-option label="已解决" value="已解决" />
            </el-select>
            <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
            <el-button icon="Download" @click="handleExport">导出</el-button>
          </div>
        </div>
      </template>

      <el-table
        :data="tableData"
        v-loading="loading"
        style="width: 100%"
        :header-cell-style="{ background: '#f8f9fa', color: '#606266', fontWeight: '600' }"
        row-key="id"
      >
        <el-table-column prop="id" label="异常ID" width="120" fixed />
        <el-table-column prop="date" label="发现时间" width="180" sortable>
          <template #default="{ row }">
            <span class="date-text">{{ row.date }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="patientName" label="患者信息" width="160">
          <template #default="{ row }">
            <div class="patient-cell">
              <el-avatar :size="28" class="patient-avatar">{{ row.patientName?.charAt(0) }}</el-avatar>
              <div class="patient-detail">
                <span class="name">{{ row.patientName }}</span>
                <span class="exam-id">{{ row.examId }}</span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="type" label="检查类型" width="140">
          <template #default="{ row }">
            <el-tag effect="light" type="info">{{ row.type }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="异常描述" min-width="200" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small" effect="dark">{{ row.priority || '普通' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="status" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getStatusType(row.status)" effect="plain" class="status-badge">
              {{ row.status }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="操作" width="160" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" size="small" @click="handleView(row)">查看详情</el-button>
            <el-button
              v-if="row.status !== '已解决'"
              link
              type="danger"
              size="small"
              @click="handleResolve(row)"
            >处理</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          v-model:current-page="currentPage"
          v-model:page-size="pageSize"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          :total="total"
          @size-change="handleSearch"
          @current-change="handleSearch"
          background
        />
      </div>
    </el-card>

    <!--
      详情弹窗
      功能：查看单条异常记录详情，并进行处理（填写处理备注）
    -->
    <el-dialog
      v-model="dialogVisible"
      title="异常详情处理"
      width="650px"
      destroy-on-close
      class="detail-dialog"
    >
      <div v-if="currentRow" class="detail-content">
        <div class="detail-header">
          <div class="detail-status">
            <span class="label">当前状态：</span>
            <el-tag :type="getStatusType(currentRow.status)">{{ currentRow.status }}</el-tag>
          </div>
          <div class="detail-id">ID: {{ currentRow.id }}</div>
        </div>

        <el-descriptions :column="2" border class="info-descriptions">
          <el-descriptions-item label="患者姓名">{{ currentRow.patientName }}</el-descriptions-item>
          <el-descriptions-item label="检查编号">{{ currentRow.examId }}</el-descriptions-item>
          <el-descriptions-item label="检查类型">{{ currentRow.type }}</el-descriptions-item>
          <el-descriptions-item label="发现时间">{{ currentRow.date }}</el-descriptions-item>
        </el-descriptions>

        <div class="section-block">
          <h4 class="section-title">异常描述</h4>
          <div class="text-content">{{ currentRow.description }}</div>
        </div>

        <div class="section-block" v-if="currentRow.imageUrl">
          <h4 class="section-title">影像快照</h4>
          <div class="image-wrapper">
            <el-image
              :src="currentRow.imageUrl"
              :preview-src-list="[currentRow.imageUrl]"
              fit="contain"
              class="snapshot-image"
            >
              <template #error>
                <div class="image-error">
                  <el-icon><Picture /></el-icon>
                  <span>暂无影像数据</span>
                </div>
              </template>
            </el-image>
          </div>
        </div>

        <div class="section-block input-block" v-if="currentRow.status !== '已解决'">
          <h4 class="section-title">处理备注</h4>
          <el-input
            v-model="resolveNote"
            type="textarea"
            :rows="3"
            placeholder="请输入处理意见、解决方案或备注信息..."
          />
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :disabled="currentRow?.status === '已解决'" @click="confirmResolve">
            {{ currentRow?.status === '已解决' ? '已完成' : '确认处理' }}
          </el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file summary/index.vue
 * @description 异常数据汇总看板
 * 展示全局的质控异常数据统计、趋势图表、分布分析以及详细记录列表。
 * 提供异常数据的查询、导出及处理功能。
 *
 * 对接API:
 * - getSummaryStats: 获取顶部核心指标数据
 * - getIssueTrend: 获取异常趋势折线图数据
 * - getIssueDistribution: 获取异常类型分布饼图数据
 * - getRecentIssues: 获取分页列表数据
 */
import { ref, onMounted, onUnmounted, computed, nextTick } from 'vue'
import dayjs from 'dayjs'
import * as echarts from 'echarts'
import {
  DataLine, Warning, Timer, CircleCheck,
  TrendCharts, PieChart, List, Search, Download,
  CaretTop, CaretBottom, Picture, FolderOpened
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getSummaryStats, getIssueTrend, getIssueDistribution, getRecentIssues } from '@/api/summary'

// --- 基础状态 ---
const updateTime = ref(dayjs().format('YYYY-MM-DD HH:mm'))
const loading = ref(false)
const trendRange = ref('7') // 趋势图时间范围: '7' | '30'
const searchQuery = ref('') // 列表搜索关键词
const filterStatus = ref('') // 列表状态筛选
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
const tableData = ref([])

// --- 统计卡片数据 ---
const statsData = ref({
  totalIssues: 0,
  todayIssues: 0,
  pendingIssues: 0,
  resolutionRate: 0
})

// 计算属性：生成卡片展示配置
const statsCards = computed(() => [
  { title: '总异常记录', value: statsData.value.totalIssues, unit: '条', icon: 'DataLine', trend: 5.2, type: 'primary' },
  { title: '今日新增', value: statsData.value.todayIssues, unit: '条', icon: 'Warning', trend: 2.1, type: 'danger' },
  { title: '待处理任务', value: statsData.value.pendingIssues, unit: '条', icon: 'Timer', trend: -12.5, type: 'warning' },
  { title: '处理解决率', value: statsData.value.resolutionRate, unit: '%', icon: 'CircleCheck', trend: 1.5, type: 'success' },
])

// --- 弹窗相关状态 ---
const dialogVisible = ref(false)
const currentRow = ref(null)
const resolveNote = ref('')

// --- ECharts 实例 ---
const trendChartRef = ref(null)
const pieChartRef = ref(null)
let trendChart = null
let pieChart = null

// --- 生命周期 ---
onMounted(async () => {
  await loadAllData()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  pieChart?.dispose()
})

/**
 * 加载所有初始数据
 * 并发请求以提高加载速度
 */
const loadAllData = async () => {
  await Promise.all([
    fetchStats(),
    fetchTrend(),
    fetchDistribution(),
    fetchList()
  ])
}

/**
 * 处理窗口大小调整，自适应图表
 */
const handleResize = () => {
  trendChart?.resize()
  pieChart?.resize()
}

// --- API 调用 ---

/**
 * 获取核心统计指标
 */
const fetchStats = async () => {
  try {
    const res = await getSummaryStats()
    if (res) statsData.value = { ...statsData.value, ...res }
  } catch (e) {
    console.error('获取统计数据失败', e)
  }
}

/**
 * 获取异常趋势数据并渲染图表
 */
const fetchTrend = async () => {
  try {
    const res = await getIssueTrend(trendRange.value)
    initTrendChart(res)
  } catch (e) {
    console.error('获取趋势数据失败', e)
  }
}

/**
 * 获取异常分布数据并渲染图表
 */
const fetchDistribution = async () => {
  try {
    const res = await getIssueDistribution()
    initPieChart(res)
  } catch (e) {
    console.error('获取分布数据失败', e)
  }
}

/**
 * 获取分页列表数据
 */
const fetchList = async () => {
  loading.value = true
  try {
    const res = await getRecentIssues({
      page: currentPage.value,
      limit: pageSize.value,
      query: searchQuery.value,
      status: filterStatus.value
    })

    // 兼容不同的后端返回格式
    if (res && Array.isArray(res.items)) {
      tableData.value = res.items
      total.value = res.total || res.items.length
    } else if (Array.isArray(res)) {
      tableData.value = res
      total.value = res.length
    } else {
      tableData.value = []
      total.value = 0
    }
  } catch (error) {
    console.error('获取列表失败', error)
    ElMessage.error('获取数据列表失败')
  } finally {
    loading.value = false
  }
}

// --- 交互处理 ---

/**
 * 处理列表搜索/筛选
 */
const handleSearch = () => {
  currentPage.value = 1
  fetchList()
}

/**
 * 导出数据
 */
const handleExport = () => {
  ElMessage.success('正在导出数据，请稍候...')
  // 实际项目中这里会调用导出API
}

// --- 图表初始化逻辑 ---

/**
 * 初始化趋势折线图
 * @param {Object} data - { dates: [], values: [] }
 */
const initTrendChart = (data) => {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)

  const option = {
    tooltip: { trigger: 'axis' },
    grid: { left: '3%', right: '4%', bottom: '3%', containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: false,
      data: data?.dates || ['周一', '周二', '周三', '周四', '周五', '周六', '周日'],
      axisLine: { lineStyle: { color: '#909399' } }
    },
    yAxis: {
      type: 'value',
      axisLine: { show: false },
      axisTick: { show: false },
      splitLine: { lineStyle: { type: 'dashed', color: '#ebeef5' } }
    },
    series: [{
      name: '异常数量',
      type: 'line',
      smooth: true,
      data: data?.values || [5, 12, 8, 15, 10, 7, 9],
      itemStyle: { color: '#409EFF' },
      areaStyle: {
        color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
          { offset: 0, color: 'rgba(64,158,255,0.3)' },
          { offset: 1, color: 'rgba(64,158,255,0.01)' }
        ])
      }
    }]
  }
  trendChart.setOption(option)
}

/**
 * 初始化分布饼图
 * @param {Array} data - [{ value, name }]
 */
const initPieChart = (data) => {
  if (!pieChartRef.value) return
  if (!pieChart) pieChart = echarts.init(pieChartRef.value)

  const option = {
    tooltip: { trigger: 'item' },
    legend: { bottom: '0%', left: 'center' },
    series: [{
      name: '异常类型',
      type: 'pie',
      radius: ['40%', '70%'],
      center: ['50%', '45%'],
      avoidLabelOverlap: false,
      itemStyle: {
        borderRadius: 10,
        borderColor: '#fff',
        borderWidth: 2
      },
      label: { show: false },
      data: data || [
        { value: 1048, name: '伪影' },
        { value: 735, name: '体位不正' },
        { value: 580, name: '参数错误' },
        { value: 484, name: '其他' }
      ]
    }]
  }
  pieChart.setOption(option)
}

// --- 业务辅助函数 ---

const getStatusType = (status) => {
  const map = {
    '待处理': 'danger',
    '处理中': 'warning',
    '已解决': 'success'
  }
  return map[status] || 'info'
}

const getPriorityType = (p) => {
  const map = {
    '高': 'danger',
    '中': 'warning',
    '低': 'info'
  }
  return map[p] || ''
}

/**
 * 查看详情
 */
const handleView = (row) => {
  currentRow.value = { ...row }
  resolveNote.value = ''
  dialogVisible.value = true
}

/**
 * 打开处理弹窗
 */
const handleResolve = (row) => {
  currentRow.value = { ...row }
  resolveNote.value = ''
  dialogVisible.value = true
}

/**
 * 确认处理
 * 提交处理备注并更新状态
 */
const confirmResolve = () => {
  if (!resolveNote.value && currentRow.value.status !== '已解决') {
    ElMessage.warning('请填写处理备注')
    return
  }

  // 模拟提交
  ElMessage.success('处理成功')
  dialogVisible.value = false

  // 更新本地数据状态 (实际应重新拉取列表)
  const index = tableData.value.findIndex(item => item.id === currentRow.value.id)
  if (index !== -1) {
    tableData.value[index].status = '已解决'
  }
}
</script>

<style scoped>
.summary-container {
  padding: 20px;
  background-color: #f5f7fa;
  min-height: 100vh;
}

/* 顶部 Header */
.page-header {
  margin-bottom: 24px;
  background: #fff;
  padding: 16px 24px;
  border-radius: 8px;
  box-shadow: 0 1px 4px rgba(0,0,0,0.05);
}

.title-section {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-top: 12px;
}

.page-title {
  font-size: 24px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.update-time {
  font-size: 13px;
  color: #909399;
}

/* 统计卡片 */
.stats-row {
  margin-bottom: 24px;
}

.stats-card {
  border: none;
  border-radius: 12px;
  overflow: hidden;
  transition: transform 0.3s, box-shadow 0.3s;
}

.stats-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 12px 24px rgba(0,0,0,0.1);
}

.stats-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  padding: 20px;
}

.stats-icon-wrapper {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  font-size: 28px;
  margin-right: 20px;
}

.stats-icon-wrapper.primary { background: rgba(64,158,255,0.1); color: #409EFF; }
.stats-icon-wrapper.success { background: rgba(103,194,58,0.1); color: #67C23A; }
.stats-icon-wrapper.warning { background: rgba(230,162,60,0.1); color: #E6A23C; }
.stats-icon-wrapper.danger { background: rgba(245,108,108,0.1); color: #F56C6C; }

.stats-content {
  flex: 1;
}

.stats-label {
  font-size: 14px;
  color: #909399;
  margin-bottom: 8px;
}

.stats-number {
  font-size: 28px;
  font-weight: bold;
  color: #303133;
  line-height: 1;
  margin-bottom: 8px;
}

.unit {
  font-size: 14px;
  font-weight: normal;
  color: #909399;
  margin-left: 4px;
}

.stats-trend {
  font-size: 13px;
  display: flex;
  align-items: center;
}

.up { color: #F56C6C; display: flex; align-items: center; }
.down { color: #67C23A; display: flex; align-items: center; }
.trend-text { color: #909399; margin-left: 6px; }

/* 图表区域 */
.charts-section {
  margin-bottom: 24px;
}

.chart-card {
  border: none;
  border-radius: 8px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.header-title {
  display: flex;
  align-items: center;
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-title .el-icon {
  margin-right: 8px;
  font-size: 18px;
  color: #409EFF;
}

.chart-box {
  height: 320px;
  width: 100%;
}

/* 列表区域 */
.list-card {
  border: none;
  border-radius: 8px;
}

.list-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
}

.header-left {
  display: flex;
  align-items: center;
}

.count-tag {
  margin-left: 12px;
}

.header-actions {
  display: flex;
  gap: 12px;
}

.search-input {
  width: 240px;
}

.status-select {
  width: 120px;
}

/* 表格样式 */
.patient-cell {
  display: flex;
  align-items: center;
}

.patient-avatar {
  margin-right: 12px;
  background: #409EFF;
}

.patient-detail {
  display: flex;
  flex-direction: column;
}

.patient-detail .name {
  font-weight: 500;
  color: #303133;
}

.patient-detail .exam-id {
  font-size: 12px;
  color: #909399;
}

.status-badge {
  min-width: 60px;
}

/* 分页 */
.pagination-wrapper {
  margin-top: 24px;
  display: flex;
  justify-content: flex-start; /* 明确左对齐 */
}

/* 详情弹窗 */
.detail-content {
  padding: 0 10px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.detail-status .label {
  color: #606266;
  margin-right: 8px;
}

.detail-id {
  color: #909399;
  font-family: monospace;
}

.info-descriptions {
  margin-bottom: 24px;
}

.section-block {
  margin-bottom: 24px;
}

.section-title {
  font-size: 15px;
  font-weight: 600;
  color: #303133;
  margin: 0 0 12px 0;
  padding-left: 10px;
  border-left: 4px solid #409EFF;
}

.text-content {
  background: #f8f9fa;
  padding: 12px;
  border-radius: 4px;
  color: #606266;
  line-height: 1.6;
}

.image-wrapper {
  background: #f5f7fa;
  border-radius: 8px;
  overflow: hidden;
  border: 1px solid #ebeef5;
  height: 300px;
  display: flex;
  justify-content: center;
  align-items: center;
}

.snapshot-image {
  width: 100%;
  height: 100%;
}

.image-error {
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #909399;
}
</style>
