<template>
  <div class="qc-rule-page">
    <div class="page-header">
      <div>
        <h2>质控规则中心</h2>
        <p>按模块和异常项维护优先级、责任角色、SLA 与自动建单策略。</p>
      </div>
      <div class="page-actions">
        <el-tag type="warning" effect="dark">管理员配置面板</el-tag>
        <el-button type="primary" @click="openCreateDialog">新增规则</el-button>
      </div>
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
          v-model="filters.keyword"
          placeholder="搜索模块、异常项或规则说明"
          clearable
          class="toolbar-item keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-select v-model="filters.taskType" clearable placeholder="模块类型" class="toolbar-item" @change="handleSearch">
          <el-option v-for="option in TASK_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
        </el-select>
        <el-select v-model="filters.enabled" clearable placeholder="启用状态" class="toolbar-item" @change="handleSearch">
          <el-option label="已启用" :value="true" />
          <el-option label="已停用" :value="false" />
        </el-select>
        <el-button type="primary" icon="Search" @click="handleSearch">查询</el-button>
        <el-button icon="Refresh" @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">规则列表</span>
          <span class="header-extra">共 {{ pagination.total }} 条规则</span>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" class="rule-table">
        <el-table-column prop="taskTypeName" label="模块" min-width="150" />
        <el-table-column prop="issueType" label="异常项" min-width="180" show-overflow-tooltip />
        <el-table-column prop="priority" label="优先级" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.priority === '高' ? 'danger' : row.priority === '中' ? 'warning' : 'info'" effect="light">
              {{ row.priority }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="responsibleRoleLabel" label="责任角色" width="110" align="center" />
        <el-table-column prop="slaHours" label="SLA" width="90" align="center">
          <template #default="{ row }">{{ row.slaHours }}h</template>
        </el-table-column>
        <el-table-column prop="autoCreateIssue" label="自动建单" width="110" align="center">
          <template #default="{ row }">
            <el-tag :type="row.autoCreateIssue ? 'success' : 'info'" effect="plain">{{ row.autoCreateIssue ? '开启' : '关闭' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="enabled" label="状态" width="100" align="center">
          <template #default="{ row }">
            <el-tag :type="row.enabled ? 'success' : 'danger'" effect="plain">{{ row.enabled ? '启用中' : '已停用' }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="description" label="规则说明" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">{{ row.description || '--' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="170" align="center" />
        <el-table-column label="操作" width="100" fixed="right" align="center">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="640px" destroy-on-close>
      <el-form ref="formRef" :model="editForm" :rules="rules" label-width="110px">
        <el-form-item label="模块类型" prop="taskType">
          <el-select v-model="editForm.taskType" placeholder="请选择模块类型" style="width: 100%">
            <el-option v-for="option in TASK_TYPE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="异常项" prop="issueType">
          <el-input v-model="editForm.issueType" placeholder="请输入异常项名称，兜底规则请填 DEFAULT" />
        </el-form-item>
        <el-form-item label="优先级" prop="priority">
          <el-radio-group v-model="editForm.priority">
            <el-radio-button label="高">高</el-radio-button>
            <el-radio-button label="中">中</el-radio-button>
            <el-radio-button label="低">低</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="责任角色" prop="responsibleRole">
          <el-select v-model="editForm.responsibleRole" placeholder="请选择责任角色" style="width: 100%">
            <el-option v-for="option in ROLE_OPTIONS" :key="option.value" :label="option.label" :value="option.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="SLA时限" prop="slaHours">
          <el-input-number v-model="editForm.slaHours" :min="1" :max="168" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="自动建单">
          <el-switch v-model="editForm.autoCreateIssue" inline-prompt active-text="开" inactive-text="关" />
        </el-form-item>
        <el-form-item label="启用状态">
          <el-switch v-model="editForm.enabled" inline-prompt active-text="启用" inactive-text="停用" />
        </el-form-item>
        <el-form-item label="规则说明">
          <el-input v-model="editForm.description" type="textarea" :rows="3" placeholder="请输入规则说明" />
        </el-form-item>
      </el-form>

      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">保存</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file admin/QcRuleCenter.vue
 * @description 管理员质控规则中心。
 */

import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { createAdminQcRule, getAdminQcRules, updateAdminQcRule } from '@/modules/admin-user/api/adminUserApi'
import { ROLE_OPTIONS } from '@/utils/auth'

// 模块类型选项与后端规则配置中的 taskType 保持一致。
const TASK_TYPE_OPTIONS = [
  { value: 'hemorrhage', label: '头部出血检测' },
  { value: 'head', label: 'CT头部平扫质控' },
  { value: 'chest-non-contrast', label: 'CT胸部平扫质控' },
  { value: 'chest-contrast', label: 'CT胸部增强质控' },
  { value: 'coronary-cta', label: '冠脉CTA质控' },
]

// 列表加载态、提交态和弹窗状态。
const loading = ref(false)
const submitting = ref(false)
const dialogVisible = ref(false)
const formRef = ref(null)
const editingId = ref(null)
const tableData = ref([])

// 顶部筛选项直接传给规则分页接口。
const filters = ref({
  keyword: '',
  taskType: '',
  enabled: undefined,
})

// 分页状态与后端分页结构对应。
const pagination = ref({
  page: 1,
  limit: 10,
  total: 0,
})

// 顶部规则统计摘要。
const summary = ref({
  totalRules: 0,
  enabledRules: 0,
  disabledRules: 0,
  autoIssueRules: 0,
  averageSlaHours: 0,
})

// 新增/编辑表单默认值。
const createDefaultForm = () => ({
  taskType: 'head',
  issueType: '',
  priority: '中',
  responsibleRole: 'doctor',
  slaHours: 24,
  autoCreateIssue: true,
  enabled: true,
  description: '',
})

// 当前编辑表单对象。
const editForm = ref(createDefaultForm())

const rules = {
  taskType: [{ required: true, message: '请选择模块类型', trigger: 'change' }],
  issueType: [{ required: true, message: '请输入异常项名称', trigger: 'blur' }],
  priority: [{ required: true, message: '请选择优先级', trigger: 'change' }],
  responsibleRole: [{ required: true, message: '请选择责任角色', trigger: 'change' }],
}

const dialogTitle = computed(() => (editingId.value ? '编辑规则' : '新增规则'))

// 顶部统计卡片直接根据 summary 派生展示结构。
const summaryCards = computed(() => [
  {
    label: '规则总数',
    value: summary.value.totalRules,
    extra: `${summary.value.disabledRules || 0} 条规则当前停用`,
    icon: 'SetUp',
    type: 'primary',
  },
  {
    label: '启用规则',
    value: summary.value.enabledRules,
    extra: '参与实时工单生成',
    icon: 'CircleCheck',
    type: 'success',
  },
  {
    label: '自动建单',
    value: summary.value.autoIssueRules,
    extra: '符合规则时自动触发工单',
    icon: 'Bell',
    type: 'warning',
  },
  {
    label: '平均SLA',
    value: `${summary.value.averageSlaHours || 0}h`,
    extra: '全平台默认处置时限',
    icon: 'Timer',
    type: 'danger',
  },
])

// 加载规则列表与统计摘要。
const loadRules = async () => {
  loading.value = true
  try {
    const response = await getAdminQcRules({
      page: pagination.value.page,
      limit: pagination.value.limit,
      keyword: filters.value.keyword || undefined,
      task_type: filters.value.taskType || undefined,
      enabled: filters.value.enabled,
    })

    // 分页接口返回 items + total + summary，页面分别回填表格、分页器和统计卡片。
    tableData.value = Array.isArray(response?.items) ? response.items : []
    pagination.value.total = Number(response?.total || 0)
    pagination.value.page = Number(response?.page || pagination.value.page)
    pagination.value.limit = Number(response?.limit || pagination.value.limit)
    summary.value = { ...summary.value, ...(response?.summary || {}) }
  } catch (error) {
    console.error('加载质控规则失败', error)
    ElMessage.error(error.response?.data?.detail || '加载质控规则失败')
  } finally {
    loading.value = false
  }
}

// 查询时统一回到第一页。
const handleSearch = () => {
  pagination.value.page = 1
  loadRules()
}

// 清空全部筛选项。
const resetFilters = () => {
  filters.value = {
    keyword: '',
    taskType: '',
    enabled: undefined,
  }
  handleSearch()
}

// 翻页后重新拉取列表。
const handlePageChange = (page) => {
  pagination.value.page = page
  loadRules()
}

// 修改分页大小后回到第一页。
const handlePageSizeChange = (size) => {
  pagination.value.limit = size
  pagination.value.page = 1
  loadRules()
}

// 打开新增规则弹窗。
const openCreateDialog = () => {
  editingId.value = null
  editForm.value = createDefaultForm()
  dialogVisible.value = true
}

// 打开编辑弹窗，并把当前行数据回填到表单。
const openEditDialog = (row) => {
  editingId.value = row.id
  editForm.value = {
    taskType: row.taskType,
    issueType: row.issueType,
    priority: row.priority,
    responsibleRole: row.responsibleRole,
    slaHours: row.slaHours,
    autoCreateIssue: row.autoCreateIssue,
    enabled: row.enabled,
    description: row.description || '',
  }
  dialogVisible.value = true
}

// 保存规则；若有 editingId 则更新，否则新增。
const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true
  try {
    // 直接提交表单结构，字段名与后端 QcRuleConfigSaveReq 保持一致。
    const payload = { ...editForm.value }
    if (editingId.value) {
      await updateAdminQcRule(editingId.value, payload)
      ElMessage.success('规则已更新')
    } else {
      await createAdminQcRule(payload)
      ElMessage.success('规则已新增')
    }

    dialogVisible.value = false
    await loadRules()
  } catch (error) {
    console.error('保存质控规则失败', error)
    ElMessage.error(error.response?.data?.detail || '保存质控规则失败')
  } finally {
    submitting.value = false
  }
}

onMounted(() => {
  // 页面进入时先加载规则列表。
  loadRules()
})
</script>

<style scoped>
/* 页面整体容器。 */
.qc-rule-page {
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

.page-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}

/* 统计卡片行。 */
.summary-row {
  margin-bottom: 20px;
}

/* 统计卡片、筛选卡片和表格卡片共用外观。 */
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

/* 卡片左侧图标块。 */
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

.summary-icon.success {
  color: #67c23a;
  background: rgba(103, 194, 58, 0.12);
}

.summary-icon.warning {
  color: #e6a23c;
  background: rgba(230, 162, 60, 0.12);
}

.summary-icon.danger {
  color: #f56c6c;
  background: rgba(245, 108, 108, 0.12);
}

/* 卡片文案区。 */
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
  width: 320px;
}

/* 表格头部。 */
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

@media (max-width: 768px) {
  .page-header,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .page-actions,
  .toolbar-item,
  .keyword-input {
    width: 100%;
  }
}
</style>
