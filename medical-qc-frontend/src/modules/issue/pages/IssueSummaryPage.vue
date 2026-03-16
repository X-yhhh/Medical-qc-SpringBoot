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
              <el-avatar v-if="row.imageUrl" :size="28" :src="row.imageUrl" class="patient-avatar" />
              <el-avatar v-else :size="28" class="patient-avatar">{{ row.patientName?.charAt(0) }}</el-avatar>
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
        <el-table-column prop="description" label="异常描述" min-width="260">
          <template #default="{ row }">
            <div class="issue-description-cell">
              <div class="issue-description-text">{{ row.description || '请及时复核并推进工单流转' }}</div>
              <div class="issue-description-meta">
                <span class="issue-description-meta__item">{{ row.type || '质控任务' }}</span>
                <span class="issue-description-meta__divider">·</span>
                <span
                  :class="['issue-description-meta__item', row.overdue ? 'is-danger' : 'is-warning']"
                >
                  {{ row.overdue ? '已超期，建议优先处理' : '存在待复核风险' }}
                </span>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="issueType" label="主异常项" width="140" align="center">
          <template #default="{ row }">
            <el-tag :type="row.priority === '高' ? 'danger' : row.priority === '中' ? 'warning' : 'info'" effect="light">
              {{ row.issueType || '未见明显异常' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="priority" label="优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="getPriorityType(row.priority)" size="small" effect="dark">{{ row.priority || '普通' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responsibleRoleLabel" label="责任角色" width="110" align="center">
          <template #default="{ row }">{{ row.responsibleRoleLabel || '--' }}</template>
        </el-table-column>
        <el-table-column prop="dueAt" label="SLA截止" width="180" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.overdue" type="danger" effect="dark">已超期</el-tag>
            <span>{{ row.dueAt || '--' }}</span>
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
          @size-change="handlePageSizeChange"
          @current-change="handlePageChange"
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
      <div v-if="currentRow" v-loading="detailLoading" class="detail-content">
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
          <el-descriptions-item label="主异常项">{{ currentRow.issueType || '未见明显异常' }}</el-descriptions-item>
          <el-descriptions-item label="优先级">{{ currentRow.priority || '低' }}</el-descriptions-item>
          <el-descriptions-item label="责任角色">{{ currentRow.responsibleRoleLabel || '--' }}</el-descriptions-item>
          <el-descriptions-item label="SLA截止">
            <span>{{ currentRow.dueAt || '--' }}</span>
            <el-tag v-if="currentRow.overdue" type="danger" effect="dark" class="due-tag">已超期</el-tag>
          </el-descriptions-item>
        </el-descriptions>

        <div class="section-block">
          <h4 class="section-title">异常描述</h4>
          <div class="text-content">{{ currentRow.description }}</div>
        </div>

        <div class="section-block" v-if="currentSourceDetail">
          <h4 class="section-title">原始检测记录</h4>
          <template v-if="isHemorrhageSourceDetail">
            <el-descriptions :column="2" border class="info-descriptions">
              <el-descriptions-item label="记录ID">{{ currentSourceDetail.recordId }}</el-descriptions-item>
              <el-descriptions-item label="质控结论">{{ currentSourceDetail.qcStatus || '--' }}</el-descriptions-item>
              <el-descriptions-item label="AI判定">{{ currentSourceDetail.prediction || '--' }}</el-descriptions-item>
              <el-descriptions-item label="出血风险">{{ formatProbability(currentSourceDetail.hemorrhageProbability) }}</el-descriptions-item>
              <el-descriptions-item label="置信度">{{ currentSourceDetail.confidenceLevel || '--' }}</el-descriptions-item>
              <el-descriptions-item label="推理设备">{{ currentSourceDetail.device || '--' }}</el-descriptions-item>
              <el-descriptions-item label="检测时间">{{ currentSourceDetail.createdAt || '--' }}</el-descriptions-item>
              <el-descriptions-item label="检测模型">{{ currentSourceDetail.modelName || '--' }}</el-descriptions-item>
              <el-descriptions-item label="中线偏移">{{ currentSourceDetail.midlineShift ? (currentSourceDetail.midlineDetail || '存在中线偏移') : '未见异常' }}</el-descriptions-item>
              <el-descriptions-item label="脑室结构">{{ currentSourceDetail.ventricleIssue ? (currentSourceDetail.ventricleDetail || '脑室结构异常') : '未见异常' }}</el-descriptions-item>
            </el-descriptions>
          </template>
          <template v-else>
            <QualityTaskResultDetail
              source-title="原始检测记录"
              :source-fields="qualityTaskSourceFields"
              :patient-info="qualityTaskPatientInfo"
              :summary="qualityTaskSummary"
              :overall-status="qualityTaskSummary.result"
              :primary-issue="qualityTaskSummary.primaryIssue || currentRow.issueType"
              :qc-items="qualityTaskQcItems"
            />
          </template>
        </div>

        <div class="section-block" v-if="detailImageUrl">
          <h4 class="section-title">影像快照</h4>
          <div class="image-wrapper">
            <el-image
              :src="detailImageUrl"
              :preview-src-list="detailImageUrl ? [detailImageUrl] : []"
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

        <div class="section-block input-block">
          <h4 class="section-title">工单流转与 CAPA</h4>
          <el-form :model="workflowForm" label-width="100px" class="workflow-form">
            <el-form-item label="当前状态">
              <el-select v-model="workflowForm.status" style="width: 100%">
                <el-option label="待处理" value="待处理" />
                <el-option label="处理中" value="处理中" />
                <el-option label="已解决" value="已解决" />
              </el-select>
            </el-form-item>
            <el-form-item label="处理人">
              <el-select v-model="workflowForm.assigneeUserId" clearable filterable placeholder="请选择处理人" style="width: 100%">
                <el-option
                  v-for="user in assignableUsers"
                  :key="user.id"
                  :label="`${user.displayName}（${user.roleLabel}）`"
                  :value="user.id"
                />
              </el-select>
            </el-form-item>
            <el-form-item label="根因分类">
              <el-select v-model="workflowForm.rootCauseCategory" clearable placeholder="请选择根因分类" style="width: 100%">
                <el-option v-for="option in ROOT_CAUSE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
              </el-select>
            </el-form-item>
            <el-form-item label="根因说明">
              <el-input v-model="workflowForm.rootCauseDetail" type="textarea" :rows="2" placeholder="请说明异常产生的根因" />
            </el-form-item>
            <el-form-item label="纠正措施">
              <el-input v-model="workflowForm.correctiveAction" type="textarea" :rows="3" placeholder="请输入本次纠正措施" />
            </el-form-item>
            <el-form-item label="预防措施">
              <el-input v-model="workflowForm.preventiveAction" type="textarea" :rows="3" placeholder="请输入后续预防措施" />
            </el-form-item>
            <el-form-item label="验证备注">
              <el-input v-model="workflowForm.verificationNote" type="textarea" :rows="2" placeholder="请输入验证结果或复盘说明" />
            </el-form-item>
            <el-form-item label="处理备注">
              <el-input
                v-model="workflowForm.remark"
                type="textarea"
                :rows="3"
                placeholder="请输入处理意见、流转说明或备注信息..."
              />
            </el-form-item>
          </el-form>
        </div>

        <div class="section-block" v-if="currentHandleLogs.length">
          <h4 class="section-title">处理日志</h4>
          <el-timeline class="handle-log-timeline">
            <el-timeline-item
              v-for="log in currentHandleLogs"
              :key="log.id"
              :timestamp="log.createdAt"
              size="large"
            >
              <div class="handle-log-item">
                <div class="handle-log-item__title">{{ log.actionTypeLabel }} · {{ log.operatorName || '--' }}</div>
                <div class="handle-log-item__meta">
                  <span v-if="log.beforeStatus || log.afterStatus">{{ log.beforeStatus || '--' }} → {{ log.afterStatus || '--' }}</span>
                </div>
                <div v-if="log.remark" class="handle-log-item__remark">{{ log.remark }}</div>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button v-if="currentRow?.sourceType === 'hemorrhage' && currentRow?.sourceRecordId" type="primary" plain @click="openSourceRecord">查看原始记录</el-button>
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="confirmResolve">
            保存流转
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
import { useRouter } from 'vue-router'
import dayjs from 'dayjs'
import { echarts } from '@/utils/echarts'
import {
  DataLine, Warning, Timer, CircleCheck,
  TrendCharts, PieChart, List, Search, Download,
  CaretTop, CaretBottom, Picture, FolderOpened
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import QualityTaskResultDetail from '@/components/QualityTaskResultDetail.vue'
import {
  exportIssues,
  getSummaryStats,
  getIssueTrend,
  getIssueDistribution,
  getRecentIssues,
  getIssueDetail,
  getAssignableUsers,
  updateIssueWorkflow,
} from '@/modules/issue/api/issueApi'

const ROOT_CAUSE_OPTIONS = [
  { label: '采集问题', value: '采集问题' },
  { label: '设备问题', value: '设备问题' },
  { label: '协议问题', value: '协议问题' },
  { label: '流程问题', value: '流程问题' },
  { label: '患者配合', value: '患者配合' },
  { label: '数据同步', value: '数据同步' },
  { label: '其他', value: '其他' },
]

// --- 基础状态 ---
// 顶部更新时间用于提示用户当前看板数据的刷新时刻。
const updateTime = ref(dayjs().format('YYYY-MM-DD HH:mm'))
// 列表加载态，覆盖表格和弹窗内详情加载之外的主区域请求。
const loading = ref(false)
// 趋势图时间范围切换值，直接映射后端 days 参数。
const trendRange = ref('7') // 趋势图时间范围: '7' | '30'
// 模糊查询关键字，可匹配患者姓名和检查号。
const searchQuery = ref('') // 列表搜索关键词
// 状态筛选值，与工单状态枚举保持一致。
const filterStatus = ref('') // 列表状态筛选
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(0)
// 表格行数据直接来自 getRecentIssues 返回的 items。
const tableData = ref([])

// --- 统计卡片数据 ---
// statsData 是顶部四张统计卡片的原始数据源，接口刷新后整体回填。
const statsData = ref({
  totalIssues: 0,
  todayIssues: 0,
  pendingIssues: 0,
  resolutionRate: 0,
  totalIssuesTrend: 0,
  todayIssuesTrend: 0,
  pendingIssuesTrend: 0,
  resolutionRateTrend: 0
})

// 计算属性：生成卡片展示配置
const statsCards = computed(() => [
  { title: '总异常记录', value: statsData.value.totalIssues, unit: '条', icon: 'DataLine', trend: statsData.value.totalIssuesTrend, type: 'primary' },
  { title: '今日新增', value: statsData.value.todayIssues, unit: '条', icon: 'Warning', trend: statsData.value.todayIssuesTrend, type: 'danger' },
  { title: '待处理任务', value: statsData.value.pendingIssues, unit: '条', icon: 'Timer', trend: statsData.value.pendingIssuesTrend, type: 'warning' },
  { title: '处理解决率', value: statsData.value.resolutionRate, unit: '%', icon: 'CircleCheck', trend: statsData.value.resolutionRateTrend, type: 'success' },
])

// 详情弹窗中不同来源类型共用 currentRow，以下计算属性负责拆出子视图需要的数据片段。
const currentSourceDetail = computed(() => currentRow.value?.sourceDetail || null)
const isHemorrhageSourceDetail = computed(() => currentSourceDetail.value?.detailType === 'hemorrhage')
const qualityTaskPatientInfo = computed(() => currentSourceDetail.value?.patientInfo || {})
const qualityTaskSummary = computed(() => ({
  ...(currentSourceDetail.value?.summary || {}),
  result: currentSourceDetail.value?.qcStatus || currentSourceDetail.value?.summary?.result,
  primaryIssue: currentSourceDetail.value?.primaryIssue || currentRow.value?.issueType,
}))
const qualityTaskQcItems = computed(() => (Array.isArray(currentSourceDetail.value?.qcItems) ? currentSourceDetail.value.qcItems : []))
const qualityTaskSourceFields = computed(() => ([
  { key: 'recordId', label: '记录ID', value: currentSourceDetail.value?.recordId },
  { key: 'taskId', label: '任务ID', value: currentSourceDetail.value?.taskId },
  { key: 'taskTypeName', label: '任务类型', value: currentSourceDetail.value?.taskTypeName },
  { key: 'qcStatus', label: '质控结论', value: currentSourceDetail.value?.qcStatus },
  { key: 'sourceModeLabel', label: '来源模式', value: currentSourceDetail.value?.sourceModeLabel },
  { key: 'sourceCacheTable', label: '来源表', value: currentSourceDetail.value?.sourceCacheTable },
  { key: 'createdAt', label: '检测时间', value: currentSourceDetail.value?.createdAt },
  { key: 'qualityScore', label: '质控评分', value: currentSourceDetail.value?.qualityScore },
  { key: 'abnormalCount', label: '异常项数', value: currentSourceDetail.value?.abnormalCount ?? 0 },
  { key: 'primaryIssue', label: '主异常项', value: currentSourceDetail.value?.primaryIssue },
  { key: 'device', label: '设备', value: currentSourceDetail.value?.device || qualityTaskPatientInfo.value?.device },
  { key: 'modelCode', label: '模型代码', value: currentSourceDetail.value?.modelCode },
  { key: 'modelVersion', label: '模型版本', value: currentSourceDetail.value?.modelVersion },
]))
const currentHandleLogs = computed(() => (Array.isArray(currentRow.value?.handleLogs) ? currentRow.value.handleLogs : []))
const detailImageUrl = computed(() => currentSourceDetail.value?.imageUrl || currentRow.value?.imageUrl || '')

// --- 弹窗相关状态 ---
// 弹窗开关和当前选中行共同决定详情区域渲染内容。
const dialogVisible = ref(false)
const currentRow = ref(null)
// 可分派人员在页面初始化时拉取一次，供所有工单复用。
const assignableUsers = ref([])
// workflowForm 对应详情弹窗中的状态、指派人和 CAPA 表单。
const workflowForm = ref(createDefaultWorkflowForm())
const submitting = ref(false)
const detailLoading = ref(false)

// --- ECharts 实例 ---
const trendChartRef = ref(null)
const pieChartRef = ref(null)
let trendChart = null
let pieChart = null

function createDefaultWorkflowForm() {
  // 每次打开新工单时都重置为默认表单，避免上一次编辑内容残留。
  return {
    status: '待处理',
    remark: '',
    assigneeUserId: null,
    rootCauseCategory: '',
    rootCauseDetail: '',
    correctiveAction: '',
    preventiveAction: '',
    verificationNote: '',
  }
}

// --- 生命周期 ---
onMounted(async () => {
  // 首屏并发拉取统计、图表、列表和指派人，减少页面可交互等待时间。
  await Promise.all([loadAllData(), loadAssignableUsers()])
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  // 页面销毁时清理事件监听和 ECharts 实例，避免重复初始化导致的内存泄漏。
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
  // 所有核心数据刷新完成后统一更新页面时间戳。
  updateTime.value = dayjs().format('YYYY-MM-DD HH:mm')
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
 * 获取可分派处理人员列表。
 */
const loadAssignableUsers = async () => {
  try {
    const response = await getAssignableUsers()
    // 接口异常时回退到空数组，避免 el-select 渲染时报错。
    assignableUsers.value = Array.isArray(response) ? response : []
  } catch (error) {
    console.error('获取可分派人员失败', error)
    assignableUsers.value = []
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

    if (res && Array.isArray(res.items)) {
      const totalPages = Number(res.pages || 0)
      if (totalPages > 0 && currentPage.value > totalPages) {
        // 过滤条件变化后当前页可能越界，此时回退到最后一页重新查询。
        currentPage.value = totalPages
        await fetchList()
        return
      }

      // 新分页接口返回 items + total + page，表格和分页器都按该结构回填。
      tableData.value = res.items
      total.value = Number(res.total || res.items.length)
      currentPage.value = Number(res.page || currentPage.value)
      updateTime.value = dayjs().format('YYYY-MM-DD HH:mm')
      return
    }

    if (Array.isArray(res)) {
      // 兼容旧接口直接返回数组的情况。
      tableData.value = res
      total.value = res.length
      updateTime.value = dayjs().format('YYYY-MM-DD HH:mm')
      return
    }

    tableData.value = []
    total.value = 0
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
  // 新查询必须回到第一页，否则可能留在一个已经不存在的页码上。
  currentPage.value = 1
  fetchList()
}

/**
 * 处理分页页码变化。
 * 切页时不能重置回第一页，否则会导致分页看起来“失效”。
 *
 * @param {number} page - 目标页码
 */
const handlePageChange = (page) => {
  currentPage.value = page
  fetchList()
}

/**
 * 处理每页条数变化。
 * 修改分页大小后回到第一页，避免当前页超出新分页范围。
 *
 * @param {number} size - 每页条数
 */
const handlePageSizeChange = (size) => {
  pageSize.value = size
  currentPage.value = 1
  fetchList()
}

/**
 * 导出数据
 */
const handleExport = async () => {
  try {
    const blob = await exportIssues({
      query: searchQuery.value,
      status: filterStatus.value
    })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `异常汇总_${dayjs().format('YYYYMMDD_HHmmss')}.csv`
    link.click()
    window.URL.revokeObjectURL(url)
    ElMessage.success('导出成功')
  } catch (error) {
    console.error('导出异常记录失败', error)
    ElMessage.error('导出失败，请稍后重试')
  }
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
      data: Array.isArray(data?.dates) ? data.dates : [],
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
      data: Array.isArray(data?.values) ? data.values : [],
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
      data: Array.isArray(data) ? data : []
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
 * 格式化概率展示。
 *
 * @param {number|string|null} probability - 概率值（0-1）
 * @returns {string} 百分比文本
 */
const formatProbability = (probability) => {
  const numericValue = Number(probability)
  if (!Number.isFinite(numericValue)) {
    return '--'
  }

  return `${(numericValue * 100).toFixed(1)}%`
}

/**
 * 打开异常工单详情，并回源查询对应的原始检测记录。
 *
 * @param {Object} row - 异常工单摘要
 */
const openIssueDetail = async (row) => {
  currentRow.value = { ...row }
  workflowForm.value = {
    ...createDefaultWorkflowForm(),
    status: row.status || '待处理',
    remark: row.remark || '',
    assigneeUserId: row.assigneeUserId ?? null,
  }
  dialogVisible.value = true
  detailLoading.value = true

  try {
    const detail = await getIssueDetail(row.id)
    currentRow.value = { ...currentRow.value, ...detail }
    workflowForm.value = {
      ...createDefaultWorkflowForm(),
      status: detail?.status || row.status || '待处理',
      remark: detail?.remark || row.remark || '',
      assigneeUserId: detail?.assigneeUserId ?? row.assigneeUserId ?? null,
      rootCauseCategory: detail?.capa?.rootCauseCategory || '',
      rootCauseDetail: detail?.capa?.rootCauseDetail || '',
      correctiveAction: detail?.capa?.correctiveAction || '',
      preventiveAction: detail?.capa?.preventiveAction || '',
      verificationNote: detail?.capa?.verificationNote || '',
    }
  } catch (error) {
    console.error('获取异常工单详情失败', error)
    ElMessage.error('获取异常详情失败')
  } finally {
    detailLoading.value = false
  }
}

/**
 * 查看详情
 */
const handleView = (row) => {
  openIssueDetail(row)
}

/**
 * 打开处理弹窗
 */
const handleResolve = (row) => {
  openIssueDetail(row)
}

/**
 * 跳转到对应的出血检测原始记录页面。
 */
const openSourceRecord = () => {
  if (!currentRow.value?.sourceRecordId) {
    return
  }

  dialogVisible.value = false
  router.push({ path: '/hemorrhage', query: { recordId: currentRow.value.sourceRecordId } })
}

/**
 * 确认处理
 * 提交处理备注并更新状态
 */
const confirmResolve = async () => {
  if (!workflowForm.value.remark && workflowForm.value.status !== '已解决') {
    ElMessage.warning('请填写处理备注')
    return
  }

  submitting.value = true
  try {
    await updateIssueWorkflow(currentRow.value.id, {
      status: workflowForm.value.status,
      remark: workflowForm.value.remark,
      assigneeUserId: workflowForm.value.assigneeUserId,
      rootCauseCategory: workflowForm.value.rootCauseCategory,
      rootCauseDetail: workflowForm.value.rootCauseDetail,
      correctiveAction: workflowForm.value.correctiveAction,
      preventiveAction: workflowForm.value.preventiveAction,
      verificationNote: workflowForm.value.verificationNote,
    })

    ElMessage.success('工单流转已更新')
    dialogVisible.value = false
    await Promise.all([
      fetchStats(),
      fetchList(),
    ])
  } catch (error) {
    console.error('处理异常工单失败', error)
    ElMessage.error('处理失败，请稍后重试')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.summary-container {
  padding: 20px;
  background: transparent;
  min-height: 100vh;
}

/* 顶部 Header */
.page-header {
  margin-bottom: 24px;
  background: linear-gradient(135deg, #ffffff 0%, #f7fbff 100%);
  padding: 16px 24px;
  border-radius: 18px;
  border: 1px solid var(--app-border);
  box-shadow: var(--app-shadow-sm);
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
  border: 1px solid var(--app-border);
  border-radius: 16px;
  overflow: hidden;
  transition: transform 0.3s, box-shadow 0.3s;
}

.stats-card:hover {
  transform: translateY(-4px);
  box-shadow: var(--app-shadow-md);
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
  border: 1px solid var(--app-border);
  border-radius: 16px;
  box-shadow: var(--app-shadow-sm);
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
  border: 1px solid var(--app-border);
  border-radius: 18px;
  box-shadow: var(--app-shadow-sm);
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

.issue-description-cell {
  display: flex;
  flex-direction: column;
  gap: 6px;
  padding: 6px 0;
}

.issue-description-text {
  color: #374151;
  line-height: 1.6;
}

.issue-description-meta {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 6px;
  font-size: 12px;
}

.issue-description-meta__item {
  color: #6b7280;
}

.issue-description-meta__item.is-warning {
  color: #b45309;
}

.issue-description-meta__item.is-danger {
  color: #b42318;
}

.issue-description-meta__divider {
  color: #d0d5dd;
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
  gap: 10px;
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

.due-tag {
  margin-left: 8px;
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

.workflow-form {
  background: #f8fafc;
  padding: 16px 16px 4px;
  border-radius: 8px;
  border: 1px solid #eef2f7;
}

.task-qc-list {
  display: grid;
  gap: 12px;
}

.task-qc-item {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.task-qc-item.is-error {
  border-color: rgba(245, 108, 108, 0.24);
  background: rgba(254, 242, 242, 0.66);
}

.task-qc-item.is-success {
  border-color: rgba(103, 194, 58, 0.22);
  background: rgba(240, 253, 244, 0.72);
}

.task-qc-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.task-qc-item__name {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.task-qc-item__desc {
  margin-top: 8px;
  color: #606266;
  line-height: 1.6;
}

.task-qc-item__detail {
  margin-top: 8px;
  color: #b42318;
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

.handle-log-timeline {
  padding-left: 8px;
}

.handle-log-item__title {
  font-weight: 600;
  color: #303133;
}

.handle-log-item__meta {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
}

.handle-log-item__remark {
  margin-top: 6px;
  color: #606266;
  line-height: 1.6;
}
</style>





