import request from '@/utils/request'

// 把患者表单序列化成 multipart/form-data，便于同时上传结构化字段和影像文件。
const buildPatientFormData = (data = {}) => {
  const formData = new FormData()
  // 以下字段名与后端 QualityPatientInfoController 的 @RequestParam 保持一致。
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

// 获取患者信息分页列表。
export const getQualityPatients = (taskType, params) => {
  return request({
    url: `/patient-info/${taskType}`,
    method: 'get',
    params,
  })
}

// 新增患者信息，同时上传患者预览图。
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

// 更新患者信息；若传入 imageFile，则后端会替换旧图片。
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

// 删除单条患者档案。
export const deleteQualityPatient = (taskType, id) => {
  return request({
    url: `/patient-info/${taskType}/${id}`,
    method: 'delete',
  })
}

// 从 PACS 缓存批量初始化当前质控项对应的患者主数据。
export const syncQualityPatientsFromPacs = (taskType) => {
  return request({
    url: `/patient-info/${taskType}/sync-from-pacs`,
    method: 'post',
  })
}
