<template>
  <el-dialog
    :model-value="visible"
    @update:model-value="$emit('update:visible', $event)"
    title="PACS系统检索"
    width="900px"
    :close-on-click-modal="false"
    destroy-on-close
  >
    <!-- 查询表单 -->
    <el-form :model="searchForm" inline class="search-form">
      <el-form-item label="患者姓名">
        <el-input v-model="searchForm.patientName" placeholder="支持模糊查询" clearable style="width: 150px" />
      </el-form-item>
      <el-form-item label="患者ID">
        <el-input v-model="searchForm.patientId" placeholder="精确匹配" clearable style="width: 150px" />
      </el-form-item>
      <el-form-item label="检查号">
        <el-input v-model="searchForm.accessionNumber" placeholder="精确匹配" clearable style="width: 150px" />
      </el-form-item>
      <el-form-item label="检查日期">
        <el-date-picker
          v-model="dateRange"
          type="daterange"
          range-separator="至"
          start-placeholder="开始日期"
          end-placeholder="结束日期"
          value-format="YYYY-MM-DD"
          style="width: 240px"
        />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="handleSearch" :loading="searching">
          <el-icon><Search /></el-icon> 查询
        </el-button>
        <el-button @click="handleReset">重置</el-button>
      </el-form-item>
    </el-form>

    <!-- 查询结果列表 -->
    <div v-loading="searching" class="result-container">
      <el-empty v-if="!searched" description="请输入查询条件后点击查询" />
      <el-empty v-else-if="studies.length === 0" description="未找到匹配的检查记录" />
      <div v-else class="study-list">
        <div
          v-for="study in studies"
          :key="study.id"
          class="study-item"
          @click="handleSelect(study)"
        >
          <div class="study-preview">
            <el-image
              v-if="study.patientImagePath"
              :src="normalizeImageUrl(study.patientImagePath)"
              fit="cover"
              class="study-preview-image"
              :preview-src-list="[normalizeImageUrl(study.patientImagePath)]"
              preview-teleported
              @click.stop
            />
            <div v-else class="study-preview-placeholder">无图</div>
          </div>
          <div class="study-main">
            <div class="study-header">
              <span class="patient-name">{{ study.patientName }}</span>
              <el-tag size="small" type="primary">{{ study.modality }}</el-tag>
            </div>
            <div class="study-info">
              <div class="info-row">
                <span class="label">患者ID:</span>
                <span class="value">{{ study.patientId }}</span>
                <span class="label">检查号:</span>
                <span class="value">{{ study.accessionNumber }}</span>
              </div>
              <div class="info-row">
                <span class="label">检查日期:</span>
                <span class="value">{{ study.studyDate }}</span>
                <span class="label">检查描述:</span>
                <span class="value">{{ study.studyDescription }}</span>
              </div>
              <div class="info-row">
                <span class="label">性别:</span>
                <span class="value">{{ study.gender || '--' }}</span>
                <span class="label">年龄:</span>
                <span class="value">{{ study.age ?? '--' }}</span>
              </div>
              <div class="info-row">
                <span class="label">序列数:</span>
                <span class="value">{{ study.seriesCount }}</span>
                <span class="label">图像数:</span>
                <span class="value">{{ study.imageCount }}</span>
                <span class="label">检查部位:</span>
                <span class="value">{{ study.bodyPart }}</span>
              </div>
              <div class="info-row">
                <span class="label">设备:</span>
                <span class="value">{{ study.manufacturer }} {{ study.modelName }}</span>
              </div>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-dialog>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { searchPacsStudies } from '@/api/quality'

/**
 * PACS查询对话框组件
 * 提供PACS检查记录查询和选择功能
 */

const props = defineProps({
  visible: Boolean,
  taskType: {
    type: String,
    default: '',
  },
})

const emit = defineEmits(['update:visible', 'select'])

// 查询表单
const searchForm = reactive({
  patientName: '',
  patientId: '',
  accessionNumber: ''
})

// 日期范围
const dateRange = ref([])

// 查询状态
const searching = ref(false)
const searched = ref(false)

// 查询结果
const studies = ref([])

/**
 * 规范化后端返回的图片路径，便于直接预览患者影像缩略图。
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
 * 处理查询操作
 */
const handleSearch = async () => {
  searching.value = true
  searched.value = true

  try {
    const params = {
      task_type: props.taskType || undefined,
      patient_name: searchForm.patientName || undefined,
      patient_id: searchForm.patientId || undefined,
      accession_number: searchForm.accessionNumber || undefined,
      start_date: dateRange.value?.[0] || undefined,
      end_date: dateRange.value?.[1] || undefined
    }

    const response = await searchPacsStudies(params)
    studies.value = response.data || []

    if (studies.value.length === 0) {
      ElMessage.info('未找到匹配的检查记录')
    }
  } catch (error) {
    ElMessage.error(error.message || 'PACS查询失败')
    studies.value = []
  } finally {
    searching.value = false
  }
}

/**
 * 重置查询条件
 */
const handleReset = () => {
  searchForm.patientName = ''
  searchForm.patientId = ''
  searchForm.accessionNumber = ''
  dateRange.value = []
  studies.value = []
  searched.value = false
}

/**
 * 选择检查记录
 */
const handleSelect = (study) => {
  emit('select', study)
}
</script>

<style scoped>
.search-form {
  margin-bottom: 20px;
  padding-bottom: 20px;
  border-bottom: 1px solid #ebeef5;
}

.result-container {
  min-height: 300px;
  max-height: 500px;
  overflow-y: auto;
}

.study-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.study-item {
  display: flex;
  gap: 16px;
  padding: 16px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  cursor: pointer;
  transition: all 0.3s;
}

.study-item:hover {
  border-color: #409eff;
  background-color: #f0f9ff;
  box-shadow: 0 2px 8px rgba(64, 158, 255, 0.1);
}

.study-preview {
  flex: 0 0 88px;
  display: flex;
  align-items: center;
  justify-content: center;
}

.study-preview-image {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  border: 1px solid #ebeef5;
}

.study-preview-placeholder {
  width: 88px;
  height: 88px;
  border-radius: 8px;
  border: 1px dashed #dcdfe6;
  background: #fafafa;
  color: #909399;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
}

.study-main {
  flex: 1;
  min-width: 0;
}

.study-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.patient-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.study-info {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
}

.label {
  color: #909399;
  min-width: 70px;
}

.value {
  color: #606266;
  margin-right: 16px;
}
</style>
