<template>
  <div class="patient-management-page">
    <div class="page-header">
      <div>
        <h2>{{ pageTitle }}</h2>
        <p>{{ pageDescription }}</p>
      </div>
      <div class="page-actions">
        <el-button :loading="syncing" @click="handleSyncFromPacs">从PACS初始化</el-button>
        <el-button type="primary" @click="openCreateDialog">新增患者信息</el-button>
      </div>
    </div>

    <el-alert
      v-if="isWorkflowConnected"
      title="当前页面已与头部出血检测和对应 PACS 患者信息联动"
      type="success"
      :closable="false"
      show-icon
      class="page-alert"
    />
    <el-alert
      v-else
      title="当前页面已完成患者信息维护与表结构设计，但尚未接入对应质控分析流程"
      type="info"
      :closable="false"
      show-icon
      class="page-alert"
    />

    <el-card class="filter-card" shadow="never">
      <div class="toolbar">
        <el-input
          v-model="filters.keyword"
          placeholder="搜索患者姓名 / 患者ID / 检查号"
          clearable
          class="toolbar-item keyword-input"
          @keyup.enter="handleSearch"
          @clear="handleSearch"
        />
        <el-input
          v-model="filters.patientName"
          placeholder="患者姓名"
          clearable
          class="toolbar-item"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.patientId"
          placeholder="患者ID"
          clearable
          class="toolbar-item"
          @keyup.enter="handleSearch"
        />
        <el-input
          v-model="filters.accessionNumber"
          placeholder="检查号"
          clearable
          class="toolbar-item"
          @keyup.enter="handleSearch"
        />
        <el-button type="primary" @click="handleSearch">查询</el-button>
        <el-button @click="resetFilters">重置</el-button>
      </div>
    </el-card>

    <el-card class="table-card" shadow="never">
      <template #header>
        <div class="card-header">
          <span class="header-title">患者信息列表</span>
          <span class="header-extra">共 {{ pagination.total }} 条记录</span>
        </div>
      </template>

      <el-table :data="tableData" border stripe v-loading="loading" class="patient-table">
        <el-table-column label="影像图片" width="110" align="center">
          <template #default="{ row }">
            <el-image
              v-if="row.imagePath"
              :src="normalizeImageUrl(row.imagePath)"
              fit="cover"
              style="width: 56px; height: 56px; border-radius: 6px"
              :preview-src-list="[normalizeImageUrl(row.imagePath)]"
              preview-teleported
            />
            <span v-else>--</span>
          </template>
        </el-table-column>
        <el-table-column prop="patientName" label="患者姓名" min-width="120" />
        <el-table-column prop="patientId" label="患者ID" min-width="120">
          <template #default="{ row }">{{ row.patientId || '--' }}</template>
        </el-table-column>
        <el-table-column prop="accessionNumber" label="检查号" min-width="140" />
        <el-table-column prop="gender" label="性别" width="90" align="center">
          <template #default="{ row }">{{ row.gender || '--' }}</template>
        </el-table-column>
        <el-table-column prop="age" label="年龄" width="90" align="center">
          <template #default="{ row }">{{ row.age ?? '--' }}</template>
        </el-table-column>
        <el-table-column prop="studyDate" label="检查日期" width="130" align="center">
          <template #default="{ row }">{{ row.studyDate || '--' }}</template>
        </el-table-column>
        <el-table-column prop="remark" label="备注" min-width="180" show-overflow-tooltip>
          <template #default="{ row }">{{ row.remark || '--' }}</template>
        </el-table-column>
        <el-table-column prop="updatedAt" label="更新时间" width="180" align="center" />
        <el-table-column label="操作" width="180" align="center" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openEditDialog(row)">编辑</el-button>
            <el-button link type="danger" @click="handleDelete(row)">删除</el-button>
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

    <el-dialog v-model="dialogVisible" :title="dialogTitle" width="620px" destroy-on-close>
      <el-form ref="formRef" :model="form" :rules="formRules" label-width="100px" status-icon>
        <el-form-item label="患者姓名" prop="patientName">
          <el-input v-model="form.patientName" placeholder="请输入患者姓名" />
        </el-form-item>
        <el-form-item label="患者ID" prop="patientId">
          <el-input v-model="form.patientId" placeholder="请输入患者ID" />
        </el-form-item>
        <el-form-item label="检查号" prop="accessionNumber">
          <el-input v-model="form.accessionNumber" placeholder="请输入检查号" />
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="form.gender" placeholder="请选择性别" style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
            <el-option label="未说明" value="未说明" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="form.age" :min="0" :max="150" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="检查日期" prop="studyDate">
          <el-date-picker
            v-model="form.studyDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="请选择检查日期"
            style="width: 100%"
          />
        </el-form-item>
        <el-form-item label="影像图片" required>
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            :show-file-list="Boolean(selectedImageFile)"
            :limit="1"
            :on-change="handleImageChange"
            :on-remove="handleImageRemove"
            style="width: 100%"
            accept=".png,.jpg,.jpeg,.bmp"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽影像图片或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">必须上传 PNG/JPG/JPEG/BMP 图片；编辑时可重新上传以替换原图片</div>
            </template>
          </el-upload>
          <el-image
            v-if="imagePreviewUrl"
            :src="imagePreviewUrl"
            fit="cover"
            class="dialog-image-preview"
            :preview-src-list="[imagePreviewUrl]"
            preview-teleported
          />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
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
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { UploadFilled } from '@element-plus/icons-vue'
import {
  createQualityPatient,
  deleteQualityPatient,
  getQualityPatients,
  syncQualityPatientsFromPacs,
  updateQualityPatient,
} from '@/modules/patient/api/patientApi'

// 当前路由的 meta 决定页面绑定的质控类型、标题和是否已接入业务流程。
const route = useRoute()

/**
 * 当前页面绑定的质控任务类型。
 */
const taskType = computed(() => route.meta.taskType || 'hemorrhage')

/**
 * 当前页面标题。
 */
const pageTitle = computed(() => route.meta.pageTitle || '患者信息管理')

/**
 * 当前页面描述文案。
 */
const pageDescription = computed(() => route.meta.pageDescription || '维护患者基础信息与检查号档案。')

/**
 * 当前页面是否已接入真实业务流程。
 */
const isWorkflowConnected = computed(() => Boolean(route.meta.workflowConnected))

// loading 控制列表加载态；submitting 控制弹窗保存态；syncing 控制 PACS 同步按钮状态。
const loading = ref(false)
const submitting = ref(false)
const syncing = ref(false)
// dialogVisible 和 editingId 一起决定当前是新增还是编辑模式。
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref(null)
// 选中的新图片文件和预览地址在编辑/新增场景都复用。
const selectedImageFile = ref(null)
const imagePreviewUrl = ref('')

// 顶部筛选栏状态。
const filters = reactive({
  keyword: '',
  patientId: '',
  patientName: '',
  accessionNumber: '',
})

// 分页状态与后端分页参数一一对应。
const pagination = reactive({
  page: 1,
  limit: 10,
  total: 0,
})

// 表格数据直接承接后端返回的患者记录数组。
const tableData = ref([])

const createDefaultForm = () => ({
  patientName: '',
  patientId: '',
  accessionNumber: '',
  gender: '未说明',
  age: null,
  studyDate: '',
  imagePath: '',
  remark: '',
})

// 表单对象既用于新增，也用于编辑时的回填。
const form = reactive(createDefaultForm())

const formRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  accessionNumber: [{ required: true, message: '请输入检查号', trigger: 'blur' }],
}

/**
 * 当前弹窗标题。
 */
const dialogTitle = computed(() => (editingId.value ? '编辑患者信息' : '新增患者信息'))

/**
 * 规范化图片地址，便于直接在前端预览 uploads 静态资源。
 *
 * @param {string} rawUrl - 原始图片路径
 * @returns {string} 可直接访问的图片地址
 */
const normalizeImageUrl = (rawUrl) => {
  if (!rawUrl) {
    return ''
  }

  const normalizedUrl = String(rawUrl).trim().replaceAll('\\', '/')
  if (normalizedUrl.startsWith('http://') || normalizedUrl.startsWith('https://') || normalizedUrl.startsWith('data:')) {
    return normalizedUrl
  }

  return normalizedUrl.startsWith('/') ? normalizedUrl : `/${normalizedUrl}`
}

/**
 * 将表单重置为初始状态。
 */
const resetForm = () => {
  // 每次打开弹窗前都重置表单，避免不同患者之间的数据串联。
  Object.assign(form, createDefaultForm())
  editingId.value = null
  selectedImageFile.value = null
  imagePreviewUrl.value = ''
}

/**
 * 加载患者信息列表。
 */
const loadPatients = async () => {
  loading.value = true
  try {
    const response = await getQualityPatients(taskType.value, {
      // 空字符串统一转成 undefined，避免无效参数污染查询。
      keyword: filters.keyword || undefined,
      patient_id: filters.patientId || undefined,
      patient_name: filters.patientName || undefined,
      accession_number: filters.accessionNumber || undefined,
      page: pagination.page,
      limit: pagination.limit,
    })
    // 后端返回 data + pagination 结构，前端分别回填表格和分页器。
    tableData.value = Array.isArray(response?.data) ? response.data : []
    pagination.total = response?.pagination?.total || 0
  } catch (error) {
    console.error('加载患者信息失败', error)
    ElMessage.error(error.response?.data?.detail || '加载患者信息失败')
  } finally {
    loading.value = false
  }
}

/**
 * 打开新增弹窗。
 */
const openCreateDialog = () => {
  resetForm()
  dialogVisible.value = true
}

/**
 * 打开编辑弹窗，并回填当前行数据。
 *
 * @param {Object} row - 当前患者信息行
 */
const openEditDialog = (row) => {
  resetForm()
  // 编辑场景需要把后端行数据逐项回填到可编辑表单。
  editingId.value = row.id
  form.patientName = row.patientName || ''
  form.patientId = row.patientId || ''
  form.accessionNumber = row.accessionNumber || ''
  form.gender = row.gender || '未说明'
  form.age = row.age ?? null
  form.studyDate = row.studyDate || ''
  form.imagePath = row.imagePath || ''
  form.remark = row.remark || ''
  imagePreviewUrl.value = normalizeImageUrl(row.imagePath)
  dialogVisible.value = true
}

/**
 * 处理患者影像图片选择。
 *
 * @param {Object} file - 上传组件返回的文件对象
 */
const handleImageChange = (file) => {
  const rawFile = file?.raw || null
  const filename = rawFile?.name || ''
  // 这里只接受图片预览格式，和后端验证规则保持一致。
  const isSupportedImage = /\.(png|jpg|jpeg|bmp)$/i.test(filename)

  if (rawFile && !isSupportedImage) {
    selectedImageFile.value = null
    imagePreviewUrl.value = editingId.value ? normalizeImageUrl(form.imagePath) : ''
    ElMessage.warning('患者影像图片仅支持 PNG/JPG/JPEG/BMP 格式')
    return
  }

  selectedImageFile.value = rawFile
  // 新选择的图片优先显示本地 Object URL 预览。
  imagePreviewUrl.value = rawFile ? URL.createObjectURL(rawFile) : ''
}

/**
 * 处理患者影像图片移除。
 */
const handleImageRemove = () => {
  selectedImageFile.value = null
  // 编辑场景移除新图后回退显示已有图片；新增场景则清空预览。
  imagePreviewUrl.value = editingId.value ? normalizeImageUrl(form.imagePath) : ''
}

/**
 * 提交新增或编辑请求。
 */
const handleSubmit = async () => {
  await formRef.value.validate()
  submitting.value = true

  try {
    // 新增患者必须有图片；编辑患者如果已有旧图则允许不重新上传。
    if (!selectedImageFile.value && !form.imagePath) {
      ElMessage.warning('请上传患者对应的影像图片')
      submitting.value = false
      return
    }

    const payload = {
      patientName: form.patientName,
      patientId: form.patientId,
      accessionNumber: form.accessionNumber,
      gender: form.gender,
      age: form.age,
      studyDate: form.studyDate,
      imageFile: selectedImageFile.value,
      remark: form.remark,
    }

    if (editingId.value) {
      // 编辑场景走 PUT，后端会保留或替换现有图片。
      await updateQualityPatient(taskType.value, editingId.value, payload)
      ElMessage.success('患者信息已更新')
    } else {
      // 新增场景走 POST，由后端创建患者、检查和预览文件记录。
      await createQualityPatient(taskType.value, payload)
      ElMessage.success('患者信息已新增')
    }

    dialogVisible.value = false
    await loadPatients()
  } catch (error) {
    console.error('保存患者信息失败', error)
    ElMessage.error(error.response?.data?.detail || '保存患者信息失败')
  } finally {
    submitting.value = false
  }
}

/**
 * 删除患者信息。
 *
 * @param {Object} row - 当前患者信息行
 */
const handleDelete = async (row) => {
  try {
    await ElMessageBox.confirm(
      `确认删除患者“${row.patientName}”的档案信息吗？`,
      '删除确认',
      { type: 'warning' },
    )

    await deleteQualityPatient(taskType.value, row.id)
    ElMessage.success('患者信息已删除')

    if (tableData.value.length === 1 && pagination.page > 1) {
      // 删除当前页最后一条记录时，自动退回上一页避免空页。
      pagination.page -= 1
    }
    await loadPatients()
  } catch (error) {
    if (error === 'cancel' || error === 'close') {
      return
    }
    console.error('删除患者信息失败', error)
    ElMessage.error(error.response?.data?.detail || '删除患者信息失败')
  }
}

/**
 * 从 PACS 缓存批量初始化当前质控项患者主数据。
 */
const handleSyncFromPacs = async () => {
  syncing.value = true
  try {
    const response = await syncQualityPatientsFromPacs(taskType.value)
    // 后端返回的是一次批量同步的统计结果，直接拼成成功提示。
    const result = response?.data || {}
    ElMessage.success(
      `PACS初始化完成：匹配 ${result.matchedStudies || 0} 条，新增 ${result.createdCount || 0} 条，更新 ${result.updatedCount || 0} 条`,
    )
    await loadPatients()
  } catch (error) {
    console.error('从PACS初始化患者信息失败', error)
    ElMessage.error(error.response?.data?.detail || '从PACS初始化患者信息失败')
  } finally {
    syncing.value = false
  }
}

/**
 * 执行查询。
 */
const handleSearch = () => {
  // 切换搜索条件时统一回到第一页。
  pagination.page = 1
  loadPatients()
}

/**
 * 重置筛选条件。
 */
const resetFilters = () => {
  filters.keyword = ''
  filters.patientId = ''
  filters.patientName = ''
  filters.accessionNumber = ''
  handleSearch()
}

/**
 * 处理页码切换。
 *
 * @param {number} page - 新页码
 */
const handlePageChange = (page) => {
  pagination.page = page
  loadPatients()
}

/**
 * 处理分页大小切换。
 *
 * @param {number} size - 新分页大小
 */
const handlePageSizeChange = (size) => {
  // 修改分页大小后返回第一页，避免请求页码越界。
  pagination.limit = size
  pagination.page = 1
  loadPatients()
}

onMounted(() => {
  // 首屏根据当前 taskType 拉取对应患者档案。
  loadPatients()
})

/**
 * 监听质控项切换，确保复用同一组件时能重新加载对应页面数据。
 */
watch(taskType, () => {
  // 复用同一页面组件切换不同 taskType 时，重置表单和筛选条件后重新加载。
  resetForm()
  resetFilters()
})
</script>

<style scoped>
.patient-management-page {
  min-height: calc(100vh - 60px);
  padding: 24px;
  background: #f5f7fa;
}

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

.page-alert {
  margin-bottom: 20px;
}

.filter-card,
.table-card {
  border-radius: 12px;
  margin-bottom: 20px;
  border: 1px solid rgba(15, 23, 42, 0.06);
  background: rgba(255, 255, 255, 0.98);
  box-shadow:
    0 12px 30px rgba(15, 23, 42, 0.06),
    0 3px 10px rgba(15, 23, 42, 0.04);
}

.filter-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
}

.table-card {
  overflow: visible;
}

.table-card :deep(.el-card__header) {
  border-bottom: 1px solid #eef2f7;
}

.table-card :deep(.el-card__body) {
  position: relative;
  z-index: 1;
  overflow: hidden;
  border-radius: 0 0 12px 12px;
}

.patient-table :deep(.el-table__inner-wrapper) {
  border-radius: 10px;
}

.patient-table :deep(.el-table__body-wrapper),
.patient-table :deep(.el-scrollbar__wrap) {
  overflow: auto;
}

.patient-table :deep(.el-table__fixed),
.patient-table :deep(.el-table__fixed-right),
.patient-table :deep(.el-table-fixed-column--left.is-first-column),
.patient-table :deep(.el-table-fixed-column--right.is-last-column) {
  box-shadow: none !important;
}

.patient-table :deep(.el-table__fixed::before),
.patient-table :deep(.el-table__fixed-right::before) {
  width: 10px;
  background: linear-gradient(90deg, rgba(15, 23, 42, 0.06), rgba(15, 23, 42, 0));
}

.patient-table :deep(.el-table__fixed-right::before) {
  left: 0;
}

.patient-table :deep(.el-table__fixed::before) {
  right: 0;
  left: auto;
  background: linear-gradient(270deg, rgba(15, 23, 42, 0.06), rgba(15, 23, 42, 0));
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

.pagination-wrapper {
  display: flex;
  justify-content: flex-end;
  margin-top: 20px;
}

.dialog-image-preview {
  width: 120px;
  height: 120px;
  margin-top: 12px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

@media (max-width: 768px) {
  .page-header,
  .toolbar {
    flex-direction: column;
    align-items: stretch;
  }

  .page-actions {
    width: 100%;
    justify-content: stretch;
    flex-direction: column;
  }

  .toolbar-item,
  .keyword-input {
    width: 100%;
  }
}
</style>
