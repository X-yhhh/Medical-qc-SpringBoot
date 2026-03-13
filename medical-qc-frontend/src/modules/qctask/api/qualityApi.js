import request from '@/utils/request'

const QUALITY_TASK_POLL_PREFIX = '/quality/tasks/'

const buildTaskFormData = ({ file, patientName, examId, sourceMode }) => {
  const formData = new FormData()
  if (file) {
    formData.append('file', file)
  }
  formData.append('patient_name', patientName || '')
  formData.append('exam_id', examId || '')
  formData.append('source_mode', sourceMode || 'local')
  return formData
}

const parseRequestError = (error, defaultMessage) => {
  console.error(defaultMessage, error)

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

export const detectHead = (payload) => submitQualityTask('/quality/head/detect', payload, '提交头部质控任务失败')

export const detectChestNonContrast = (payload) => submitQualityTask('/quality/chest-non-contrast/detect', payload, '提交胸部平扫质控任务失败')

export const detectChestContrast = (payload) => submitQualityTask('/quality/chest-contrast/detect', payload, '提交胸部增强质控任务失败')

export const detectCoronaryCTA = (payload) => submitQualityTask('/quality/coronary-cta/detect', payload, '提交冠脉 CTA 质控任务失败')

export const getQualityTask = async (taskId) => {
  try {
    return await request.get(`${QUALITY_TASK_POLL_PREFIX}${taskId}`)
  } catch (error) {
    throw parseRequestError(error, '查询质控任务失败')
  }
}

export const getQualityTaskPage = async (params = {}) => {
  try {
    return await request.get('/quality/tasks', { params })
  } catch (error) {
    throw parseRequestError(error, '查询质控任务列表失败')
  }
}

export const predictHemorrhage = async (file, metadata = {}) => {
  const formData = new FormData()
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

export const getHemorrhageHistory = async (limit = 20) => {
  try {
    return await request.get('/quality/hemorrhage/history', { params: { limit } })
  } catch (error) {
    console.error('获取历史记录失败', error)
    return { data: [] }
  }
}

export const getHemorrhageRecord = async (recordId) => {
  try {
    return await request.get(`/quality/hemorrhage/history/${recordId}`)
  } catch (error) {
    console.error('获取指定出血检测历史记录失败', error)
    throw error
  }
}

export const searchPacsStudies = async (params) => {
  try {
    return await request.get('/pacs/search', { params })
  } catch (error) {
    throw parseRequestError(error, 'PACS查询失败')
  }
}
