// ----------------------------------------------------------------------------------
// 质控模块前端 API (Quality API)
// ----------------------------------------------------------------------------------
// @file src/api/quality.js
// @description 封装质控模块所有与后端交互的 API 请求。
//              包含真实 AI 算法接口（脑出血）和用于演示的模拟接口（其他质控项）。
// @module API/Quality
//
// 对应后端服务:
// - Base URL: /api/v1/quality
// - 路由文件: app/api/v1/quality.py (Python Backend)
// ----------------------------------------------------------------------------------

import request from '@/utils/request'

// ==================================================================================
// 1. 模拟接口 (Mock APIs)
// 作用：用于前端演示和开发，暂未接入真实后端算法服务。
// ==================================================================================

/**
 * @function detectHead
 * @description [MOCK] 模拟 CT 头部平扫质控检测
 * @param {File} file - 上传的影像文件 (虽然是模拟，但保留接口签名一致性)
 * @returns {Promise<Object>} 返回包含质控问题列表和耗时的 Promise
 *
 * @backend-api (Pending) POST /api/v1/quality/head/detect
 */
export const detectHead = (file) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        issues: [
          { item: '运动伪影', status: Math.random() > 0.7 ? '不合格' : '合格' },
          { item: '金属伪影', status: Math.random() > 0.8 ? '不合格' : '合格' },
          { item: 'FOV过大', status: Math.random() > 0.6 ? '不合格' : '合格' },
          { item: 'FOV过小', status: Math.random() > 0.5 ? '不合格' : '合格' },
          { item: '层厚不当', status: Math.random() > 0.4 ? '不合格' : '合格' }
        ],
        duration: Math.floor(800 + Math.random() * 500)
      })
    }, 1200)
  })
}

/**
 * @function detectChestNonContrast
 * @description [MOCK] 模拟 CT 胸部平扫质控检测
 * @param {File} file - 上传的影像文件
 * @returns {Promise<Object>} 返回包含质控问题列表和耗时的 Promise
 *
 * @backend-api (Pending) POST /api/v1/quality/chest-non-contrast/detect
 */
export const detectChestNonContrast = (file) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        issues: [
          { item: '呼吸伪影', status: Math.random() > 0.6 ? '不合格' : '合格' },
          { item: '体外金属', status: Math.random() > 0.8 ? '不合格' : '合格' },
          { item: '扫描范围不全', status: Math.random() > 0.5 ? '不合格' : '合格' }
        ],
        duration: Math.floor(900 + Math.random() * 400)
      })
    }, 1300)
  })
}

/**
 * @function detectChestContrast
 * @description [MOCK] 模拟 CT 胸部增强质控检测
 * @param {File} file - 上传的影像文件
 * @returns {Promise<Object>} 返回包含质控问题列表和耗时的 Promise
 *
 * @backend-api (Pending) POST /api/v1/quality/chest-contrast/detect
 */
export const detectChestContrast = (file) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        issues: [
          { item: '分期错误', status: Math.random() > 0.7 ? '不合格' : '合格' },
          { item: '增强时机不当', status: Math.random() > 0.6 ? '不合格' : '合格' },
          { item: 'FOV过小', status: Math.random() > 0.4 ? '不合格' : '合格' }
        ],
        duration: Math.floor(1000 + Math.random() * 600)
      })
    }, 1400)
  })
}

/**
 * @function detectCoronaryCTA
 * @description [MOCK] 模拟冠脉 CTA 质控检测
 * @param {File} file - 上传的影像文件
 * @returns {Promise<Object>} 返回包含质控问题列表和耗时的 Promise
 *
 * @backend-api (Pending) POST /api/v1/quality/coronary-cta/detect
 */
export const detectCoronaryCTA = (file) => {
  return new Promise((resolve) => {
    setTimeout(() => {
      resolve({
        issues: [
          { item: '血管强化不足', status: Math.random() > 0.5 ? '不合格' : '合格' },
          { item: '噪声过大', status: Math.random() > 0.6 ? '不合格' : '合格' },
          { item: '心电门控失败', status: Math.random() > 0.7 ? '不合格' : '合格' }
        ],
        duration: Math.floor(1200 + Math.random() * 800)
      })
    }, 1600)
  })
}

// ==================================================================================
// 2. 真实接口 (Real APIs)
// 作用：对接真实后端 AI 算法服务
// ==================================================================================

/**
 * @function predictHemorrhage
 * @description 脑出血智能检测 (Real AI Service)
 * @param {File} file - DICOM/图片文件
 * @param {Object} metadata - 额外的元数据 (如患者姓名、检查ID)
 * @returns {Promise<Object>} 后端返回的检测结果
 *
 * @backend-api POST /api/v1/quality/hemorrhage
 * @note 使用 multipart/form-data 格式上传
 */
export const predictHemorrhage = async (file, metadata = {}) => {
  const formData = new FormData()
  formData.append('file', file)
  if (metadata.patientName) formData.append('patient_name', metadata.patientName)
  if (metadata.examId) formData.append('exam_id', metadata.examId)

  try {
    const response = await request.post('/quality/hemorrhage', formData, {
      headers: {
        'Content-Type': 'multipart/form-data'
      }
    })
    return response // request.js 响应拦截器已处理 data
  } catch (error) {
    console.error('脑出血检测失败:', error)

    if (error.response) {
      throw new Error(`后端返回错误: ${error.response.status} ${error.response.statusText}`)
    } else if (error.request) {
      throw new Error('网络错误：无法连接到后端服务')
    } else {
      throw new Error(`请求配置错误: ${error.message}`)
    }
  }
}

/**
 * @function getHemorrhageHistory
 * @description 获取脑出血质控历史记录
 * @param {number} limit - 获取记录的数量限制
 * @returns {Promise<Object>} 历史记录列表
 *
 * @backend-api (Planned) GET /api/v1/quality/hemorrhage/history
 */
export const getHemorrhageHistory = async (limit = 20) => {
  try {
    // TODO: 后端暂未实现此接口，预留位置
    // const response = await request.get('/quality/hemorrhage/history', { params: { limit } })
    return { data: [] } // 临时返回空数据
  } catch (error) {
    console.error('获取历史记录失败', error)
    return { data: [] }
  }
}
