import request from '@/utils/request'

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

export const getQualityPatients = (taskType, params) => {
  return request({
    url: `/patient-info/${taskType}`,
    method: 'get',
    params,
  })
}

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

export const deleteQualityPatient = (taskType, id) => {
  return request({
    url: `/patient-info/${taskType}/${id}`,
    method: 'delete',
  })
}

export const syncQualityPatientsFromPacs = (taskType) => {
  return request({
    url: `/patient-info/${taskType}/sync-from-pacs`,
    method: 'post',
  })
}
