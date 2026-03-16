import request from '@/utils/request'

// 轮询质控任务详情时统一复用的接口前缀。
const QUALITY_TASK_POLL_PREFIX = '/quality/tasks/'

// 将通用质控任务表单序列化为 multipart/form-data。
const buildTaskFormData = (payload = {}) => {
  const formData = new FormData()
  if (payload.file) {
    // 本地上传场景下才会有原始文件。
    formData.append('file', payload.file)
  }
  // 以下字段对应后端质控任务提交接口的表单参数。
  formData.append('patient_name', payload.patientName || '')
  formData.append('exam_id', payload.examId || '')
  if (payload.patientId) {
    formData.append('patient_id', payload.patientId)
  }
  if (payload.gender) {
    formData.append('gender', payload.gender)
  }
  if (payload.age !== null && payload.age !== undefined && payload.age !== '') {
    formData.append('age', payload.age)
  }
  if (payload.studyDate) {
    formData.append('study_date', payload.studyDate)
  }
  formData.append('source_mode', payload.sourceMode || 'local')
  if (payload.heartRate !== null && payload.heartRate !== undefined && payload.heartRate !== '') {
    formData.append('heart_rate', payload.heartRate)
  }
  if (payload.hrVariability !== null && payload.hrVariability !== undefined && payload.hrVariability !== '') {
    formData.append('hr_variability', payload.hrVariability)
  }
  if (payload.reconPhase) {
    formData.append('recon_phase', payload.reconPhase)
  }
  if (payload.kVp) {
    formData.append('kvp', payload.kVp)
  }
  if (payload.flowRate !== null && payload.flowRate !== undefined && payload.flowRate !== '') {
    formData.append('flow_rate', payload.flowRate)
  }
  if (payload.contrastVolume !== null && payload.contrastVolume !== undefined && payload.contrastVolume !== '') {
    formData.append('contrast_volume', payload.contrastVolume)
  }
  if (payload.injectionSite) {
    formData.append('injection_site', payload.injectionSite)
  }
  if (payload.sliceThickness !== null && payload.sliceThickness !== undefined && payload.sliceThickness !== '') {
    formData.append('slice_thickness', payload.sliceThickness)
  }
  if (payload.bolusTrackingHu !== null && payload.bolusTrackingHu !== undefined && payload.bolusTrackingHu !== '') {
    formData.append('bolus_tracking_hu', payload.bolusTrackingHu)
  }
  if (payload.scanDelaySec !== null && payload.scanDelaySec !== undefined && payload.scanDelaySec !== '') {
    formData.append('scan_delay_sec', payload.scanDelaySec)
  }
  return formData
}

// 统一把 Axios 错误转换为更贴近页面提示的 Error 对象。
const parseRequestError = (error, defaultMessage) => {
  console.error(defaultMessage, error)

  // 后端 detail 优先级最高，可直接展示业务错误。
  const detailMessage = error.response?.data?.detail
  if (detailMessage) {
    return new Error(detailMessage)
  }

  if (error.response) {
    return new Error(`后端返回错误: ${error.response.status} ${error.response.statusText}`)
  }
  if (error.request) {
    return new Error('网络错误：无法连接到后端服务')
  }
  return new Error(`${defaultMessage}: ${error.message}`)
}

// 提交通用异步质控任务，并统一处理 multipart 头与错误转换。
const submitQualityTask = async (endpoint, payload, actionLabel) => {
  try {
    return await request.post(endpoint, buildTaskFormData(payload), {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  } catch (error) {
    throw parseRequestError(error, actionLabel)
  }
}

// CT 头部平扫质控任务提交入口。
export const detectHead = (payload) => submitQualityTask('/quality/head/detect', payload, '提交头部质控任务失败')

// CT 胸部平扫质控任务提交入口。
export const detectChestNonContrast = (payload) => submitQualityTask('/quality/chest-non-contrast/detect', payload, '提交胸部平扫质控任务失败')

// CT 胸部增强质控任务提交入口。
export const detectChestContrast = (payload) => submitQualityTask('/quality/chest-contrast/detect', payload, '提交胸部增强质控任务失败')

// 冠脉 CTA 质控任务提交入口。
export const detectCoronaryCTA = (payload) => submitQualityTask('/quality/coronary-cta/detect', payload, '提交冠脉 CTA 质控任务失败')

// 根据任务 ID 轮询异步质控结果。
export const getQualityTask = async (taskId) => {
  try {
    return await request.get(`${QUALITY_TASK_POLL_PREFIX}${taskId}`)
  } catch (error) {
    throw parseRequestError(error, '查询质控任务失败')
  }
}

// 获取质控任务中心分页列表。
export const getQualityTaskPage = async (params = {}) => {
  try {
    return await request.get('/quality/tasks', { params })
  } catch (error) {
    throw parseRequestError(error, '查询质控任务列表失败')
  }
}

// 更新单条任务的人工复核状态。
export const updateQualityTaskReview = async (taskId, payload = {}) => {
  try {
    return await request.patch(`/quality/tasks/${taskId}/review`, payload)
  } catch (error) {
    throw parseRequestError(error, '更新任务复核状态失败')
  }
}

// 批量更新任务的人工复核状态。
export const batchUpdateQualityTaskReview = async (payload = {}) => {
  try {
    return await request.patch('/quality/tasks/batch/review', payload)
  } catch (error) {
    throw parseRequestError(error, '批量更新任务复核状态失败')
  }
}

// 批量重跑历史任务。
export const batchRetryQualityTasks = async (payload = {}) => {
  try {
    return await request.post('/quality/tasks/batch/retry', payload)
  } catch (error) {
    throw parseRequestError(error, '批量重跑任务失败')
  }
}

// 修复历史任务中的质控结论、异常项和 mock 标记。
export const repairQualityTasks = async (payload = {}) => {
  try {
    return await request.post('/quality/tasks/repair', payload)
  } catch (error) {
    throw parseRequestError(error, '修复历史任务失败')
  }
}

// 导出单条任务的 DOCX 报告。
export const exportQualityTaskReport = async (taskId) => {
  try {
    return await request.get(`/quality/tasks/${taskId}/report`, { responseType: 'blob' })
  } catch (error) {
    throw parseRequestError(error, '导出任务报告失败')
  }
}

// 导出多条任务的 CSV 摘要。
export const exportQualityTasksCsv = async (taskIds = []) => {
  try {
    return await request.post('/quality/tasks/export', { taskIds }, { responseType: 'blob' })
  } catch (error) {
    throw parseRequestError(error, '导出任务摘要失败')
  }
}

// 获取任务中心概览指标。
export const getQualityTaskMetrics = async () => {
  try {
    return await request.get('/quality/tasks/metrics')
  } catch (error) {
    throw parseRequestError(error, '获取任务指标失败')
  }
}

// 提交头部出血检测任务，兼容本地上传与 PACS 来源两种模式。
export const predictHemorrhage = async (file, metadata = {}) => {
  const formData = new FormData()
  // PACS 模式下后端直接复用已有预览图，因此无需重复上传文件。
  if (metadata.sourceMode !== 'pacs' && file) {
    formData.append('file', file)
  }
  if (metadata.patientName) formData.append('patient_name', metadata.patientName)
  if (metadata.patientCode) formData.append('patient_code', metadata.patientCode)
  if (metadata.examId) formData.append('exam_id', metadata.examId)
  if (metadata.gender) formData.append('gender', metadata.gender)
  if (metadata.age !== null && metadata.age !== undefined && metadata.age !== '') {
    formData.append('age', metadata.age)
  }
  if (metadata.studyDate) formData.append('study_date', metadata.studyDate)
  if (metadata.sourceMode) formData.append('source_mode', metadata.sourceMode)

  try {
    return await request.post('/quality/hemorrhage', formData, {
      headers: {
        'Content-Type': 'multipart/form-data',
      },
    })
  } catch (error) {
    throw parseRequestError(error, '脑出血检测失败')
  }
}

// 查询脑出血检测历史记录。
export const getHemorrhageHistory = async (limit = 20) => {
  try {
    return await request.get('/quality/hemorrhage/history', { params: { limit } })
  } catch (error) {
    console.error('获取历史记录失败', error)
    return { data: [] }
  }
}

// 查询指定脑出血检测记录详情。
export const getHemorrhageRecord = async (recordId) => {
  try {
    return await request.get(`/quality/hemorrhage/history/${recordId}`)
  } catch (error) {
    console.error('获取指定出血检测历史记录失败', error)
    throw error
  }
}

// 导出单条脑出血检测历史记录的 DOCX 报告。
export const exportHemorrhageReport = async (recordId) => {
  try {
    return await request.get(`/quality/hemorrhage/history/${recordId}/report`, { responseType: 'blob' })
  } catch (error) {
    throw parseRequestError(error, '导出脑出血检测报告失败')
  }
}

// 从 PACS 缓存中检索检查列表。
export const searchPacsStudies = async (params) => {
  try {
    return await request.get('/pacs/search', { params })
  } catch (error) {
    throw parseRequestError(error, 'PACS查询失败')
  }
}
