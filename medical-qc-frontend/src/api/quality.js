// ----------------------------------------------------------------------------------
// 质控模块前端 API (Quality API)
// ----------------------------------------------------------------------------------
// @file src/api/quality.js
// @description 封装质控模块所有与后端交互的 API 请求。
//              包含真实 AI 算法接口（脑出血）以及基于 ActiveMQ 的异步质控任务接口。
// @module API/Quality
// ----------------------------------------------------------------------------------

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
        'Content-Type': 'multipart/form-data'
      }
    })
  } catch (error) {
    throw parseRequestError(error, actionLabel)
  }
}

/**
 * 提交 CT 头部平扫异步质控任务。
 */
export const detectHead = (payload) => {
  return submitQualityTask('/quality/head/detect', payload, '提交头部质控任务失败')
}

/**
 * 提交 CT 胸部平扫异步质控任务。
 */
export const detectChestNonContrast = (payload) => {
  return submitQualityTask('/quality/chest-non-contrast/detect', payload, '提交胸部平扫质控任务失败')
}

/**
 * 提交 CT 胸部增强异步质控任务。
 */
export const detectChestContrast = (payload) => {
  return submitQualityTask('/quality/chest-contrast/detect', payload, '提交胸部增强质控任务失败')
}

/**
 * 提交冠脉 CTA 异步质控任务。
 */
export const detectCoronaryCTA = (payload) => {
  return submitQualityTask('/quality/coronary-cta/detect', payload, '提交冠脉 CTA 质控任务失败')
}

/**
 * 查询异步质控任务状态与结果。
 */
export const getQualityTask = async (taskId) => {
  try {
    return await request.get(`${QUALITY_TASK_POLL_PREFIX}${taskId}`)
  } catch (error) {
    throw parseRequestError(error, '查询质控任务失败')
  }
}

/**
 * 查询质控任务中心分页列表。
 */
export const getQualityTaskPage = async (params = {}) => {
  try {
    return await request.get('/quality/tasks', { params })
  } catch (error) {
    throw parseRequestError(error, '查询质控任务列表失败')
  }
}

// ==================================================================================
// 真实接口 (Real APIs)
// ==================================================================================

/**
 * 脑出血智能检测 (Real AI Service)
 * PACS模式下file为null，通过examId从数据库获取图片路径
 */
export const predictHemorrhage = async (file, metadata = {}) => {
  const formData = new FormData()
  // PACS模式下不传file，本地模式必须传file
  if (metadata.sourceMode === 'pacs') {
    // PACS模式：不添加file字段
  } else if (file) {
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
        'Content-Type': 'multipart/form-data'
      }
    })
  } catch (error) {
    throw parseRequestError(error, '脑出血检测失败')
  }
}

/**
 * 获取脑出血质控历史记录。
 */
export const getHemorrhageHistory = async (limit = 20) => {
  try {
    return await request.get('/quality/hemorrhage/history', { params: { limit } })
  } catch (error) {
    console.error('获取历史记录失败', error)
    return { data: [] }
  }
}

/**
 * 获取指定的出血检测历史记录详情。
 */
export const getHemorrhageRecord = async (recordId) => {
  try {
    return await request.get(`/quality/hemorrhage/history/${recordId}`)
  } catch (error) {
    console.error('获取指定出血检测历史记录失败', error)
    throw error
  }
}

/**
 * 查询PACS检查记录
 * 支持多条件组合查询：患者ID、患者姓名、检查号、日期范围
 */
export const searchPacsStudies = async (params) => {
  try {
    return await request.get('/pacs/search', { params })
  } catch (error) {
    throw parseRequestError(error, 'PACS查询失败')
  }
}
