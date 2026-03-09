// ----------------------------------------------------------------------------------
// 质控项患者信息前端 API
// ----------------------------------------------------------------------------------
// @file src/api/patient.js
// @description 为五个质控项的患者信息管理页面提供统一 CRUD 能力。
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

/**
 * 构建患者信息上传表单。
 *
 * @param {Object} data - 患者信息与图片文件
 * @returns {FormData} 可直接提交给后端的表单对象
 */
const buildPatientFormData = (data = {}) => {
  const formData = new FormData()
  formData.append('patient_name', data.patientName || '')
  formData.append('patient_id', data.patientId || '')
  formData.append('accession_number', data.accessionNumber || '')
  formData.append('gender', data.gender || '')

  if (data.age !== null && data.age !== undefined && data.age !== '') {
    formData.append('age', data.age)
  }
  if (data.studyDate) {
    formData.append('study_date', data.studyDate)
  }
  if (data.remark) {
    formData.append('remark', data.remark)
  }
  if (data.imageFile) {
    formData.append('image_file', data.imageFile)
  }

  return formData
}

/**
 * 查询指定质控项的患者信息列表。
 *
 * @param {string} taskType - 质控任务类型
 * @param {Object} params - 查询参数
 * @returns {Promise<Object>} 列表、分页信息与统计摘要
 */
export const getQualityPatients = (taskType, params) => {
  return request({
    url: `/patient-info/${taskType}`,
    method: 'get',
    params,
  })
}

/**
 * 新增患者信息。
 *
 * @param {string} taskType - 质控任务类型
 * @param {Object} data - 患者信息
 * @returns {Promise<Object>} 新增结果
 */
export const createQualityPatient = (taskType, data) => {
  return request({
    url: `/patient-info/${taskType}`,
    method: 'post',
    data: buildPatientFormData(data),
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

/**
 * 更新患者信息。
 *
 * @param {string} taskType - 质控任务类型
 * @param {number|string} id - 患者信息主键
 * @param {Object} data - 患者信息
 * @returns {Promise<Object>} 更新结果
 */
export const updateQualityPatient = (taskType, id, data) => {
  return request({
    url: `/patient-info/${taskType}/${id}`,
    method: 'put',
    data: buildPatientFormData(data),
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
}

/**
 * 删除患者信息。
 *
 * @param {string} taskType - 质控任务类型
 * @param {number|string} id - 患者信息主键
 * @returns {Promise<Object>} 删除结果
 */
export const deleteQualityPatient = (taskType, id) => {
  return request({
    url: `/patient-info/${taskType}/${id}`,
    method: 'delete',
  })
}

/**
 * 从 PACS 缓存批量初始化当前质控项患者信息表。
 *
 * @param {string} taskType - 质控任务类型
 * @returns {Promise<Object>} 同步统计结果
 */
export const syncQualityPatientsFromPacs = (taskType) => {
  return request({
    url: `/patient-info/${taskType}/sync-from-pacs`,
    method: 'post',
  })
}
