<template>
  <div class="quality-task-page">
    <div class="page-header">
      <div>
        <h2>质控任务中心</h2>
        <p>统一查看异步质控任务的提交状态、质控评分、失败原因与异常项详情。</p>
      </div>
      <el-tag :type="isAdminView ? 'warning' : 'primary'" effect="dark">
        {{ isAdminView ? '管理员全局视图' : '医生个人视图' }}
      </el-tag>
    </div>

    <el-row :gutter="20" class="summary-row">
      <el-col :span="6" v-for="item in summaryCards" :key="item.label">
        <el-card class="summary-card" shadow="hover">
          <div class="summary-icon" :class="item.type">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div class="summary-content">
            <div class="summary-label">{{ item.label }}</div>
            <div class="summary-value">{{ item.value }}</div>
            <div class="summary-extra">{{ item.extra }}</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="filter-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.query"
          placeholder="搜索患者姓名、检查号、任务 ID 或主异常项"
          clearable
          class="toolbar-item keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filters.taskType" clearable placeholder="任务类型" class="toolbar-item" @change="handleSearch">
          <el-option v-for="option in TASK_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.status" clearable placeholder="执行状态" class="toolbar-item" @change="handleSearch">
          <el-option v-for="option in STATUS_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.sourceMode" clearable placeholder="来源模式" class="toolbar-item" @change="handleSearch">
          <el-option v-for="option in SOURCE_MODE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">任务列表</span>
          <span class="header-extra">共 {{ pagination.total }} 条任务记录</span>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" row-key="taskId" class="task-table">
        <el-table-column prop="taskId" label="任务ID" min-width="220" show-overflow-tooltip />
        <el-table-column prop="taskTypeName" label="任务类型" min-width="150" />
        <el-table-column prop="patientName" label="患者姓名" width="120">
          <template #default="{ row }">{{ row.patientName || '--' }}</template>
        </el-table-column>
        <el-table-column prop="examId" label="检查号" min-width="140">
          <template #default="{ row }">{{ row.examId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="sourceModeLabel" label="来源" width="110" align="center" />
        <el-table-column prop="status" label="执行状态" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="getTaskStatusType(row.status)" effect="plain">{{ getTaskStatusLabel(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="qcStatus" label="质控结论" width="110" align="center">
          <template #default="{ row }">
            <el-tag v-if="row.qcStatus" :type="row.qcStatus === '合格' ? 'success' : 'danger'" effect="light">
              {{ row.qcStatus }}
            </el-tag>
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="qualityScore" label="质控分" width="90" align="center">
          <template #default="{ row }">{{ formatScore(row.qualityScore) }}</template>
        </el-table-column>
        <el-table-column prop="abnormalCount" label="异常项" width="90" align="center">
          <template #default="{ row }">{{ row.abnormalCount ?? 0 }}</template>
        </el-table-column>
        <el-table-column prop="primaryIssue" label="主异常项" min-width="160" show-overflow-tooltip>
          <template #default="{ row }">{{ row.primaryIssue || '--' }}</template>
        </el-table-column>
        <el-table-column prop="submittedAt" label="提交时间" width="170" align="center" />
        <el-table-column prop="completedAt" label="完成时间" width="170" align="center" />
        <el-table-column label="操作" width="120" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openTaskDetail(row.taskId)">查看详情</el-button>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrapper">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next, jumper"
          :current-page="pagination.page"
          :page-size="pagination.limit"
          :page-sizes="[10, 20, 30]"
          :total="pagination.total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </el-card>

    <el-dialog v-model="dialogVisible" title="任务详情" width="860px" destroy-on-close>
      <div v-if="currentTask" v-loading="detailLoading" class="detail-content">
        <div class="detail-header">
          <div class="detail-title">
            <span class="task-name">{{ currentTask.taskTypeName }}</span>
            <el-tag :type="getTaskStatusType(currentTask.status)" effect="plain">{{ getTaskStatusLabel(currentTask.status) }}</el-tag>
            <el-tag v-if="currentTask.qcStatus" :type="currentTask.qcStatus === '合格' ? 'success' : 'danger'" effect="light">
              {{ currentTask.qcStatus }}
            </el-tag>
          </div>
          <div class="detail-id">{{ currentTask.taskId }}</div>
        </div>

        <el-alert
          v-if="currentTask.errorMessage"
          :title="currentTask.errorMessage"
          type="error"
          :closable="false"
          show-icon
          class="detail-alert"
        />

        <el-row :gutter="16" class="detail-summary-row">
          <el-col :span="8">
            <div class="summary-box">
              <div class="summary-box__label">质控评分</div>
              <div class="summary-box__value">{{ formatScore(detailSummary.qualityScore ?? currentTask.qualityScore) }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-box">
              <div class="summary-box__label">异常项数量</div>
              <div class="summary-box__value">{{ detailSummary.abnormalCount ?? currentTask.abnormalCount ?? 0 }}</div>
            </div>
          </el-col>
          <el-col :span="8">
            <div class="summary-box">
              <div class="summary-box__label">主异常项</div>
              <div class="summary-box__value summary-box__value--small">{{ currentTask.primaryIssue || '未见明显异常' }}</div>
            </div>
          </el-col>
        </el-row>

        <el-descriptions :column="3" border class="info-descriptions">
          <el-descriptions-item label="患者姓名">{{ currentTask.patientName || detailPatientInfo.name || '--' }}</el-descriptions-item>
          <el-descriptions-item label="检查号">{{ currentTask.examId || detailPatientInfo.studyId || '--' }}</el-descriptions-item>
          <el-descriptions-item label="来源模式">{{ currentTask.sourceModeLabel || '--' }}</el-descriptions-item>
          <el-descriptions-item label="提交时间">{{ currentTask.submittedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="开始时间">{{ currentTask.startedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="完成时间">{{ currentTask.completedAt || '--' }}</el-descriptions-item>
          <el-descriptions-item label="Accession No">{{ detailPatientInfo.accessionNumber || '--' }}</el-descriptions-item>
          <el-descriptions-item label="设备">{{ detailPatientInfo.device || '--' }}</el-descriptions-item>
          <el-descriptions-item label="原始文件">{{ currentTask.originalFilename || '--' }}</el-descriptions-item>
        </el-descriptions>

        <div v-if="detailPatientInfo && Object.keys(detailPatientInfo).length" class="section-block">
          <h4 class="section-title">患者与采集信息</h4>
          <el-descriptions :column="3" border class="info-descriptions">
            <el-descriptions-item label="姓名">{{ detailPatientInfo.name || '--' }}</el-descriptions-item>
            <el-descriptions-item label="性别">{{ detailPatientInfo.gender || '--' }}</el-descriptions-item>
            <el-descriptions-item label="年龄">{{ detailPatientInfo.age ?? '--' }}</el-descriptions-item>
            <el-descriptions-item label="检查ID">{{ detailPatientInfo.studyId || '--' }}</el-descriptions-item>
            <el-descriptions-item label="检查日期">{{ detailPatientInfo.studyDate || '--' }}</el-descriptions-item>
            <el-descriptions-item label="来源">{{ detailPatientInfo.sourceLabel || '--' }}</el-descriptions-item>
          </el-descriptions>
        </div>

        <div v-if="detailQcItems.length" class="section-block">
          <h4 class="section-title">质控项详情</h4>
          <div class="qc-item-list">
            <div
              v-for="(item, index) in detailQcItems"
              :key="`${item.name}-${index}`"
              :class="['qc-item', item.status === '不合格' ? 'is-error' : 'is-success']"
            >
              <div class="qc-item__header">
                <span class="qc-item__name">{{ item.name }}</span>
                <el-tag :type="item.status === '合格' ? 'success' : 'danger'" effect="light">{{ item.status }}</el-tag>
              </div>
              <div class="qc-item__desc">{{ item.description || '--' }}</div>
              <div v-if="item.detail" class="qc-item__detail">{{ item.detail }}</div>
            </div>
          </div>
        </div>

        <el-empty v-else-if="!currentTask.errorMessage" description="当前任务暂无明细结果" :image-size="88" />
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file quality/QualityTaskCenter.vue
 * @description 质控任务中心页面。
 * 提供异步质控任务的统一列表、状态追踪和结果详情查看能力。
 */

import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import { getQualityTask, getQualityTaskPage } from '@/modules/qctask/api/qualityApi'
import { getStoredUserInfo } from '@/utils/auth'

const route = useRoute()

// 任务类型筛选项与后端 task_type 编码保持一致。
const TASK_TYPE_OPTIONS = [
  { label: 'CT头部平扫质控', value: 'head' },
  { label: 'CT胸部平扫质控', value: 'chest-non-contrast' },
  { label: 'CT胸部增强质控', value: 'chest-contrast' },
  { label: '冠脉CTA质控', value: 'coronary-cta' },
]

// 任务执行状态筛选项。
const STATUS_OPTIONS = [
  { label: '等待执行', value: 'PENDING' },
  { label: '执行中', value: 'PROCESSING' },
  { label: '已完成', value: 'SUCCESS' },
  { label: '执行失败', value: 'FAILED' },
]

// 来源模式筛选项。
const SOURCE_MODE_OPTIONS = [
  { label: '本地上传', value: 'local' },
  { label: 'PACS 调取', value: 'pacs' },
]

// 列表加载态、详情加载态和详情弹窗状态。
const loading = ref(false)
const detailLoading = ref(false)
const dialogVisible = ref(false)
const tableData = ref([])
const currentTask = ref(null)

// 顶部筛选条件直接透传给分页接口。
const filters = ref({
  query: '',
  taskType: '',
  status: '',
  sourceMode: '',
})

// 分页状态与后端分页结构一一对应。
const pagination = ref({
  page: 1,
  limit: 10,
  total: 0,
})

// 顶部统计卡片的原始数据源。
const summary = ref({
  totalTasks: 0,
  runningTasks: 0,
  abnormalTasks: 0,
  failedTasks: 0,
  averageQualityScore: 0,
  todayTasks: 0,
})

// 管理员可看全局任务，医生看到个人任务，因此页面头部标签依赖当前角色。
const isAdminView = computed(() => getStoredUserInfo()?.role === 'admin')
// 详情弹窗中的 patientInfo / summary / qcItems 都来自后端 result 字段的不同片段。
const detailResult = computed(() => currentTask.value?.result || {})
const detailPatientInfo = computed(() => detailResult.value?.patientInfo || {})
const detailSummary = computed(() => detailResult.value?.summary || {})
const detailQcItems = computed(() => (Array.isArray(detailResult.value?.qcItems) ? detailResult.value.qcItems : []))

// 顶部统计卡片根据 summary 聚合值直接派生展示结构。
const summaryCards = computed(() => [
  {
    label: '任务总数',
    value: summary.value.totalTasks,
    extra: `今日新增 ${summary.value.todayTasks || 0} 条`,
    icon: 'Tickets',
    type: 'primary',
  },
  {
    label: '运行中',
    value: summary.value.runningTasks,
    extra: '含等待执行与处理中任务',
    icon: 'Timer',
    type: 'warning',
  },
  {
    label: '异常任务',
    value: summary.value.abnormalTasks,
    extra: `失败任务 ${summary.value.failedTasks || 0} 条`,
    icon: 'Warning',
    type: 'danger',
  },
  {
    label: '平均质控分',
    value: formatScore(summary.value.averageQualityScore),
    extra: '仅统计已生成评分的任务',
    icon: 'DataAnalysis',
    type: 'success',
  },
])

// 分页加载任务列表，并同步刷新顶部摘要。
const loadTasks = async () => {
  loading.value = true
  try {
    const response = await getQualityTaskPage({
      page: pagination.value.page,
      limit: pagination.value.limit,
      query: filters.value.query || undefined,
      task_type: filters.value.taskType || undefined,
      status: filters.value.status || undefined,
      source_mode: filters.value.sourceMode || undefined,
    })

    // 新接口返回 items + total + summary，页面按结构化字段回填。
    tableData.value = Array.isArray(response?.items) ? response.items : []
    pagination.value.total = Number(response?.total || 0)
    pagination.value.page = Number(response?.page || pagination.value.page)
    pagination.value.limit = Number(response?.limit || pagination.value.limit)
    summary.value = { ...summary.value, ...(response?.summary || {}) }
  } catch (error) {
    console.error('加载质控任务列表失败', error)
    ElMessage.error(error.message || '加载质控任务列表失败')
  } finally {
    loading.value = false
  }
}

// 打开任务详情弹窗，并按 taskId 拉取结构化结果。
const openTaskDetail = async (taskId) => {
  if (!taskId) {
    return
  }

  // 先打开弹窗再拉取详情，用户能立即感知到操作已触发。
  dialogVisible.value = true
  detailLoading.value = true
  try {
    currentTask.value = await getQualityTask(taskId)
  } catch (error) {
    console.error('加载任务详情失败', error)
    ElMessage.error(error.message || '加载任务详情失败')
    dialogVisible.value = false
  } finally {
    detailLoading.value = false
  }
}

// 切换筛选条件时统一回到第一页。
const handleSearch = () => {
  pagination.value.page = 1
  loadTasks()
}

// 重置全部筛选项。
const resetFilters = () => {
  filters.value = {
    query: '',
    taskType: '',
    status: '',
    sourceMode: '',
  }
  handleSearch()
}

// 翻页后重新拉取列表。
const handlePageChange = (page) => {
  pagination.value.page = page
  loadTasks()
}

// 修改分页大小后回到第一页。
const handlePageSizeChange = (size) => {
  pagination.value.limit = size
  pagination.value.page = 1
  loadTasks()
}

// 将任务执行状态映射到 Element Plus Tag 类型。
const getTaskStatusType = (status) => {
  const mapping = {
    PENDING: 'info',
    PROCESSING: 'warning',
    SUCCESS: 'success',
    FAILED: 'danger',
  }
  return mapping[status] || 'info'
}

// 将任务状态码转换为中文展示文案。
const getTaskStatusLabel = (status) => {
  const mapping = {
    PENDING: '等待执行',
    PROCESSING: '执行中',
    SUCCESS: '已完成',
    FAILED: '执行失败',
  }
  return mapping[status] || status || '--'
}

// 质控分无效时统一显示为 --。
function formatScore(score) {
  const numericScore = Number(score)
  if (!Number.isFinite(numericScore) || numericScore <= 0) {
    return '--'
  }
  // 整数分不补小数，非整数保留 1 位，保持列表展示紧凑。
  return numericScore % 1 === 0 ? String(numericScore) : numericScore.toFixed(1)
}

onMounted(async () => {
  // 首屏加载列表；若路由带 taskId，则直接打开对应详情。
  await loadTasks()
  if (typeof route.query.taskId === 'string' && route.query.taskId) {
    openTaskDetail(route.query.taskId)
  }
})
</script>

<style scoped>
/* 页面整体容器。 */
.quality-task-page {
  min-height: calc(100vh - 60px);
  padding: 24px;
  background: #f5f7fa;
}

/* 顶部标题区。 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
  padding: 20px 24px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 2px 12px rgba(0, 0, 0, 0.05);
}

.page-header h2 {
  margin: 0 0 8px;
  font-size: 24px;
  color: #303133;
}

.page-header p {
  margin: 0;
  color: #606266;
  font-size: 14px;
}

.summary-row {
  margin-bottom: 20px;
}

/* 统计卡片、筛选卡片与表格卡片共用外观。 */
.summary-card,
.filter-card,
.table-card {
  border-radius: 12px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 12px 30px rgba(15, 23, 42, 0.06),
    0 3px 10px rgba(15, 23, 42, 0.04);
}

/* 统计卡片内部布局。 */
.summary-card :deep(.el-card__body) {
  display: flex;
  align-items: center;
  gap: 16px;
}

/* 左侧图标块。 */
.summary-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 56px;
  height: 56px;
  border-radius: 16px;
  font-size: 24px;
}

.summary-icon.primary {
  color: #409eff;
  background: rgba(64, 158, 255, 0.12);
}

.summary-icon.warning {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.12);
}

.summary-icon.danger {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.12);
}

.summary-icon.success {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.12);
}

/* 统计文案区。 */
.summary-label {
  font-size: 14px;
  color: #909399;
}

.summary-value {
  margin-top: 6px;
  font-size: 28px;
  font-weight: 700;
  color: #303133;
}

.summary-extra {
  margin-top: 8px;
  font-size: 13px;
  color: #909399;
}

/* 顶部筛选工具栏。 */
.filter-card {
  margin-bottom: 20px;
}

.toolbar {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}

.toolbar-item {
  width: 180px;
}

.keyword-input {
  width: 360px;
}

/* 列表卡片头部。 */
.card-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.header-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.header-extra {
  color: #909399;
  font-size: 13px;
}

/* 分页区域。 */
.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

/* 详情弹窗内部排版。 */
.detail-content {
  padding: 0 8px;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
  padding-bottom: 12px;
  border-bottom: 1px solid #eef2f7;
}

.detail-title {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.task-name {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
}

.detail-id {
  color: #909399;
  font-family: Consolas, Monaco, monospace;
  font-size: 13px;
}

.detail-alert {
  margin-bottom: 16px;
}

/* 详情顶部摘要卡片。 */
.detail-summary-row {
  margin-bottom: 18px;
}

.summary-box {
  padding: 16px 18px;
  border-radius: 12px;
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.98), rgba(241, 245, 249, 0.95));
  border: 1px solid #e5e7eb;
}

.summary-box__label {
  font-size: 13px;
  color: #909399;
}

.summary-box__value {
  margin-top: 10px;
  font-size: 26px;
  font-weight: 700;
  color: #111827;
}

.summary-box__value--small {
  font-size: 18px;
}

/* 公共区块。 */
.info-descriptions {
  margin-bottom: 20px;
}

/* 详情区块外边距。 */
.section-block {
  margin-bottom: 22px;
}

.section-title {
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 4px solid #409eff;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

/* 质控项详情列表。 */
.qc-item-list {
  display: grid;
  gap: 12px;
}

/* 单个质控项卡片。 */
.qc-item {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

/* 异常结果卡片。 */
.qc-item.is-error {
  border-color: rgba(245, 108, 108, 0.24);
  background: rgba(254, 242, 242, 0.66);
}

/* 正常结果卡片。 */
.qc-item.is-success {
  border-color: rgba(103, 194, 58, 0.22);
  background: rgba(240, 253, 244, 0.72);
}

/* 质控项头部。 */
.qc-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

/* 质控项名称。 */
.qc-item__name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

/* 质控项描述。 */
.qc-item__desc {
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
}

/* 质控项异常详情。 */
.qc-item__detail {
  margin-top: 8px;
  color: #b42318;
  line-height: 1.6;
}

@media (max-width: 768px) {
  .page-header,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .toolbar-item,
  .keyword-input {
    width: 100%;
  }
}
</style>
