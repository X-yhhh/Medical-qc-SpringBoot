/**
 * PACS 演示模式静态结果构建工具。
 *
 * 说明：
 * - 仅供四个尚未对接真实后端模型的质控页面使用。
 * - 头部出血检测页面不使用本文件，仍走真实后端 + 数据库 + 模型流程。
 * - 所有结果均为固定静态数据，避免继续触发 mock API、数据库或任务轮询。
 */

/**
 * 生成当前时间的中文展示文本。
 *
 * @returns {string} 格式化后的日期时间字符串
 */
const nowText = () => new Date().toLocaleString('zh-CN', { hour12: false })

/**
 * 计算静态质控结果摘要。
 *
 * @param {Array<Object>} qcItems - 质控项列表
 * @returns {{ totalItems: number, abnormalCount: number, qualityScore: number, result: string }} 统计摘要
 */
const buildSummary = (qcItems = []) => {
  const abnormalCount = qcItems.filter((item) => item.status === '不合格').length
  const totalItems = qcItems.length
  const qualityScore = totalItems === 0 ? 0 : Math.round(((totalItems - abnormalCount) / totalItems) * 100)

  return {
    totalItems,
    abnormalCount,
    qualityScore,
    result: qualityScore >= 80 ? '合格' : '不合格',
  }
}

/**
 * 构建统一的质控项对象。
 *
 * @param {string} name - 质控项名称
 * @param {string} status - 质控状态
 * @param {string} description - 质控说明
 * @param {string} detail - 异常详情
 * @param {string} phase - 所属时相或分组
 * @returns {Object} 页面可直接渲染的质控项
 */
const createQcItem = (name, status, description, detail = '', phase = '') => {
  const item = {
    name,
    status,
    description,
    detail: status === '合格' ? '' : detail,
  }

  if (phase) {
    item.phase = phase
  }

  return item
}

/**
 * 构建 PACS 演示模式的患者基本信息。
 *
 * @param {Object} options - 页面传入的动态信息与预置模板
 * @param {string} options.patientName - 当前患者姓名
 * @param {string} options.examId - 当前检查号
 * @param {Object} template - 各质控页面对应的静态模板
 * @returns {Object} 页面右侧患者信息卡片所需数据
 */
const createBasePatientInfo = (options, template) => {
  return {
    name: options.patientName || template.patientName,
    gender: template.gender,
    age: template.age,
    studyId: options.examId || template.examId,
    accessionNumber: options.examId || template.examId,
    studyDate: nowText(),
    sourceMode: 'pacs',
    sourceLabel: 'PACS 调取',
    originalFilename: 'PACS 静态演示',
  }
}

/**
 * 将 patientInfo 与 qcItems 组装成页面统一结果结构。
 *
 * @param {string} taskType - 质控任务类型
 * @param {string} taskTypeName - 页面标题名称
 * @param {Object} patientInfo - 患者信息
 * @param {Array<Object>} qcItems - 质控项列表
 * @param {number} duration - 静态展示耗时
 * @returns {Object} 通用结果对象
 */
const createResultEnvelope = (taskType, taskTypeName, patientInfo, qcItems, duration) => {
  return {
    taskType,
    taskTypeName,
    mock: true,
    staticDisplay: true,
    duration,
    patientInfo,
    qcItems,
    summary: buildSummary(qcItems),
  }
}

/**
 * 构建 CT 头部平扫页面的 PACS 静态结果。
 *
 * @param {Object} options - 页面传入的患者信息
 * @returns {Object} 静态结果
 */
export const buildHeadStaticPacsResult = (options = {}) => {
  const patientInfo = createBasePatientInfo(options, {
    patientName: '王某某',
    examId: 'PACS_AUTO_20231024',
    gender: '男',
    age: 52,
  })

  patientInfo.device = 'GE Revolution CT'
  patientInfo.sliceCount = 240
  patientInfo.sliceThickness = 5.0

  const qcItems = [
    createQcItem('扫描覆盖范围', '合格', '扫描范围应覆盖从颅底至颅顶完整区域'),
    createQcItem('体位不正', '不合格', '正中矢状面应与扫描架中心线重合', '头部存在轻度偏斜，建议重新摆位'),
    createQcItem('运动伪影', '合格', '图像中不应出现因患者运动导致的模糊或重影'),
    createQcItem('金属伪影', '合格', '应避免假牙、发卡等金属异物干扰'),
    createQcItem('层厚层间距', '合格', '常规扫描层厚应≤5mm'),
    createQcItem('剂量控制 (CTDI)', '合格', 'CTDIvol 应低于参考水平 (60 mGy)'),
  ]

  return createResultEnvelope('head', 'CT头部平扫质控', patientInfo, qcItems, 980)
}

/**
 * 构建 CT 胸部平扫页面的 PACS 静态结果。
 *
 * @param {Object} options - 页面传入的患者信息
 * @returns {Object} 静态结果
 */
export const buildChestNonContrastStaticPacsResult = (options = {}) => {
  const patientInfo = createBasePatientInfo(options, {
    patientName: '王某某',
    examId: 'PACS_CHEST_20231024',
    gender: '女',
    age: 46,
  })

  patientInfo.device = 'GE Revolution CT'
  patientInfo.sliceCount = 300
  patientInfo.sliceThickness = 1.25

  const qcItems = [
    createQcItem('扫描范围', '合格', '肺尖至肺底完整覆盖'),
    createQcItem('呼吸伪影', '不合格', '无明显呼吸运动伪影', '屏气配合一般，双下肺局部轻度模糊'),
    createQcItem('体位不正', '合格', '患者居中，无倾斜'),
    createQcItem('金属伪影', '合格', '无明显金属伪影干扰'),
    createQcItem('图像噪声', '合格', '噪声指数符合诊断要求'),
    createQcItem('肺窗设置', '合格', '窗宽窗位适宜观察肺纹理'),
    createQcItem('纵隔窗设置', '合格', '窗宽窗位适宜观察纵隔结构'),
    createQcItem('心影干扰', '合格', '心脏搏动伪影在可接受范围内'),
  ]

  return createResultEnvelope('chest-non-contrast', 'CT胸部平扫质控', patientInfo, qcItems, 1120)
}

/**
 * 构建 CT 胸部增强页面的 PACS 静态结果。
 *
 * @param {Object} options - 页面传入的患者信息
 * @returns {Object} 静态结果
 */
export const buildChestContrastStaticPacsResult = (options = {}) => {
  const patientInfo = createBasePatientInfo(options, {
    patientName: '王某某',
    examId: 'PACS_ENH_20231024',
    gender: '男',
    age: 58,
  })

  patientInfo.device = 'Siemens Somatom Force'
  patientInfo.sliceCount = 320
  patientInfo.sliceThickness = 1.0
  patientInfo.flowRate = 4.5
  patientInfo.contrastVolume = 80
  patientInfo.injectionSite = '右侧肘正中静脉'

  const qcItems = [
    createQcItem('定位像范围', '合格', '包含肺尖至肺底完整范围', '', '定位片'),
    createQcItem('呼吸配合', '合格', '无明显呼吸运动伪影', '', '平扫期'),
    createQcItem('金属伪影', '合格', '无明显金属植入物伪影', '', '平扫期'),
    createQcItem('主动脉强化值', '合格', '主动脉弓CT值应 > 250 HU', '实测平均值 320 HU', '增强I期'),
    createQcItem('肺动脉强化', '合格', '肺动脉主干CT值应 > 200 HU', '实测平均值 280 HU', '增强I期'),
    createQcItem('静脉污染', '不合格', '上腔静脉无明显高密度伪影', '上腔静脉见明显条束状硬化伪影，建议生理盐水冲刷', '增强I期'),
    createQcItem('实质强化均匀度', '合格', '肝脏实质强化均匀', '', '增强II期'),
  ]

  return createResultEnvelope('chest-contrast', 'CT胸部增强质控', patientInfo, qcItems, 1260)
}

/**
 * 构建冠脉 CTA 页面 PACS 静态结果。
 *
 * @param {Object} options - 页面传入的患者信息
 * @returns {Object} 静态结果
 */
export const buildCoronaryCtaStaticPacsResult = (options = {}) => {
  const patientInfo = createBasePatientInfo(options, {
    patientName: '张某某',
    examId: 'PACS_CTA_20231024',
    gender: '男',
    age: 61,
  })

  patientInfo.device = 'Philips iCT 256'
  patientInfo.sliceThickness = 0.6
  patientInfo.heartRate = 64
  patientInfo.hrVariability = 2
  patientInfo.reconPhase = '75% (Diastolic)'
  patientInfo.kVp = '100 kV'

  const qcItems = [
    createQcItem('心率控制', '合格', '检查扫描期间平均心率是否符合重建要求', '平均心率 64 bpm (<=75 bpm)'),
    createQcItem('心率稳定性', '合格', '检查扫描期间心率波动情况', '心率波动 < 5 bpm'),
    createQcItem('呼吸配合', '合格', '检查是否存在呼吸运动伪影'),
    createQcItem('血管强化 (AO)', '合格', '升主动脉根部 CT 值', 'CT值 380 HU (>=300 HU)'),
    createQcItem('血管强化 (LAD)', '合格', '左前降支远端 CT 值', 'CT值 280 HU (>=250 HU)'),
    createQcItem('血管强化 (RCA)', '不合格', '右冠状动脉远端 CT 值', 'CT值 210 HU (<250 HU)，强化稍显不足'),
    createQcItem('噪声水平', '合格', '主动脉根部图像噪声 (SD)', 'SD = 22 HU (<=30 HU)'),
    createQcItem('钙化积分影响', '合格', '严重钙化斑块导致的伪影干扰'),
    createQcItem('台阶伪影', '合格', '因心率不齐或屏气不佳导致的层面错位', '血管连续性良好'),
    createQcItem('心电门控', '合格', 'ECG 信号同步状态', 'R波触发准确'),
    createQcItem('扫描范围', '合格', '覆盖气管分叉至心脏膈面下', '心脏包络完整'),
    createQcItem('金属/线束伪影', '合格', '上腔静脉高浓度对比剂或电极片伪影'),
  ]

  return createResultEnvelope('coronary-cta', '冠脉CTA质控', patientInfo, qcItems, 1380)
}
