import { reactive, ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { exportQualityTaskReport, getQualityTask } from '@/modules/qctask/api/qualityApi'

// 轮询频率与超时统一在组合函数层收敛，供各质控页面复用。
const POLL_INTERVAL_MS = 1000
const POLL_TIMEOUT_MS = 60000

// 异步休眠工具，配合任务轮询使用。
const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

// 克隆初始患者信息，避免不同页面实例共享同一个对象引用。
const cloneInitialState = (initialState) => ({ ...initialState })

// 兼容 Element Plus Upload 返回的包装对象与原生 File。
const extractRawFile = (selectedFile) => {
  if (!selectedFile) {
    return null
  }
  return selectedFile.raw || selectedFile
}

// 统一生成中文时间文本，用于页面上的“当前时间/检查时间”预填。
const nowText = () => new Date().toLocaleString('zh-CN', { hour12: false })

/**
 * 通用异步质控页面组合函数。
 *
 * 负责处理：
 * - 上传弹窗状态
 * - 本地上传 / PACS 模式切换
 * - 任务提交
 * - 自动轮询
 * - 前端模拟进度与日志
 */
export const useAsyncQualityTaskPage = (options) => {
  const {
    submitTask,
    initialPatientInfo,
    initialUploadForm = {
      patientName: '',
      examId: ''
    },
    analysisSteps,
    exportMessage,
    reanalyzeStartMessage,
    reanalyzeSuccessMessage,
    pendingStatusMessage = '质控任务已提交，等待云端处理...',
    processingStatusMessage = '云端任务处理中，正在等待最终报告...'
  } = options

  // analyzing 与 progress 负责驱动上传页的加载动画和步骤展示。
  const analyzing = ref(false)
  const analyzeProgress = ref(0)
  const currentAnalysisStep = ref('准备就绪')
  const analysisLogs = ref([])

  // 详情弹窗状态。
  const dialogVisible = ref(false)
  const currentItem = ref(null)

  // 上传弹窗和上传模式状态，本地上传与 PACS 调取共用这一套表单。
  const uploadDialogVisible = ref(false)
  const uploadMode = ref('local')
  const uploadFormRef = ref(null)
  const uploadForm = reactive(cloneInitialState(initialUploadForm))
  const selectedFile = ref(null)
  const uploadMethodDialogVisible = ref(false)

  // 页面主结果区状态。
  const qcItems = ref([])
  const patientInfo = ref(cloneInitialState(initialPatientInfo))
  const latestTaskId = ref('')
  const taskStatus = ref('')
  const taskError = ref('')
  const analysisMode = ref('')
  const analysisLabel = ref('')
  const isMockResult = ref(false)

  // 以下内部变量不参与渲染，用于控制定时器、组件销毁和并发运行保护。
  let progressTimer = null
  let destroyed = false
  let activeRunToken = 0
  let lastPolledStatus = ''

  // 清理进度条定时器，避免重复开多个 interval。
  const clearProgressTimer = () => {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
  }

  const resetResultState = () => {
    qcItems.value = []
    patientInfo.value = cloneInitialState(initialPatientInfo)
    latestTaskId.value = ''
    taskStatus.value = ''
    taskError.value = ''
    analysisMode.value = ''
    analysisLabel.value = ''
    isMockResult.value = false
  }

  // 统一重置上传表单，确保任务专属扩展字段也能被正确清空。
  const resetUploadFormValues = (overrides = {}) => {
    const nextValues = {
      ...cloneInitialState(initialUploadForm),
      ...overrides
    }
    Object.keys(uploadForm).forEach((key) => {
      delete uploadForm[key]
    })
    Object.entries(nextValues).forEach(([key, value]) => {
      uploadForm[key] = value
    })
  }

  // 向页面日志区域插入最新一条消息，并限制展示条数。
  const addLog = (message) => {
    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    analysisLogs.value.unshift({ time, message })
    if (analysisLogs.value.length > 6) {
      analysisLogs.value.pop()
    }
  }

  // 在结果返回前先把患者姓名和检查号回填到预览卡片，避免页面空白。
  const patchPatientInfoPreview = () => {
    patientInfo.value = {
      ...cloneInitialState(initialPatientInfo),
      ...patientInfo.value,
      patientId: uploadForm.patientId || patientInfo.value.patientId,
      name: uploadForm.patientName || patientInfo.value.name,
      gender: uploadForm.gender || patientInfo.value.gender,
      age: uploadForm.age ?? patientInfo.value.age,
      studyId: uploadForm.examId || patientInfo.value.studyId,
      accessionNumber: uploadForm.examId || patientInfo.value.accessionNumber,
      studyDate: uploadForm.studyDate || patientInfo.value.studyDate || nowText()
    }
  }

  // 启动前端视觉进度条，不代表后端真实进度，只用于优化交互体验。
  const startVisualProgress = (runToken) => {
    const steps = typeof analysisSteps === 'function' ? analysisSteps(uploadForm) : analysisSteps
    let stepIndex = 0

    clearProgressTimer()

    const applyStep = (step) => {
      if (!step || destroyed || runToken !== activeRunToken) {
        return
      }

      analyzeProgress.value = Math.max(analyzeProgress.value, step.progress)
      currentAnalysisStep.value = step.step
      addLog(step.msg)

      if (step.prefillPatientInfo) {
        patchPatientInfoPreview()
      }
    }

    // 先立即应用第一步，再按固定节奏推进后续演示步骤。
    applyStep(steps[stepIndex])
    stepIndex += 1

    progressTimer = setInterval(() => {
      if (destroyed || runToken !== activeRunToken) {
        clearProgressTimer()
        return
      }

      if (stepIndex >= steps.length) {
        clearProgressTimer()
        return
      }

      applyStep(steps[stepIndex])
      stepIndex += 1
    }, 700)
  }

  /**
   * 将统一格式的结果载荷回填到页面状态。
   *
   * @param {Object} result - 质控结果对象，需包含 patientInfo 与 qcItems
   */
  const applyResultPayload = (result = {}) => {
    const nextPatientInfo = result.patientInfo || {}
    const nextQcItems = Array.isArray(result.qcItems) ? result.qcItems : []

    patientInfo.value = {
      ...cloneInitialState(initialPatientInfo),
      ...nextPatientInfo,
      name: nextPatientInfo.name || uploadForm.patientName,
      studyId: nextPatientInfo.studyId || uploadForm.examId
    }
    qcItems.value = nextQcItems
    taskStatus.value = 'SUCCESS'
    taskError.value = ''
    analysisMode.value = result.analysisMode || ''
    analysisLabel.value = result.analysisLabel || ''
    isMockResult.value = Boolean(result.mock)
    analyzeProgress.value = 100
    currentAnalysisStep.value = '完成'
    addLog('分析完成，已获取最终质控报告。')
  }

  /**
   * 将轮询接口返回的任务详情转换为页面可消费的结果结构。
   *
   * @param {Object} taskDetail - 后端返回的任务详情
   */
  const applyTaskResult = (taskDetail) => {
    applyResultPayload(taskDetail?.result || {})
  }

  // 轮询后端任务详情接口，直到 SUCCESS / FAILED / TIMEOUT。
  const pollTaskUntilComplete = async (taskId, runToken) => {
    const startTime = Date.now()
    lastPolledStatus = ''

    while (!destroyed && runToken === activeRunToken) {
      // 每轮都从服务端取最新任务状态与结果，避免只依赖前端本地状态。
      const taskDetail = await getQualityTask(taskId)
      if (destroyed || runToken !== activeRunToken) {
        return false
      }

      const currentStatus = taskDetail?.status || ''
      taskStatus.value = currentStatus
      if (currentStatus !== lastPolledStatus) {
        // 状态变化时才追加日志，避免轮询日志刷屏。
        if (currentStatus === 'PENDING') {
          addLog(pendingStatusMessage)
        } else if (currentStatus === 'PROCESSING') {
          addLog(processingStatusMessage)
          currentAnalysisStep.value = '云端处理'
          analyzeProgress.value = Math.max(analyzeProgress.value, 92)
        }
        lastPolledStatus = currentStatus
      }

      if (currentStatus === 'SUCCESS') {
        applyTaskResult(taskDetail)
        return true
      }

      if (currentStatus === 'FAILED') {
        taskError.value = taskDetail?.errorMessage || '任务处理失败'
        currentAnalysisStep.value = '失败'
        addLog(taskError.value)
        throw new Error(taskDetail?.errorMessage || '任务处理失败')
      }

      if (Date.now() - startTime >= POLL_TIMEOUT_MS) {
        throw new Error('任务处理超时')
      }

      // 未到终态时按固定间隔继续轮询。
      await sleep(POLL_INTERVAL_MS)
    }

    return false
  }

  // 统一执行一次质控任务提交流程。
  const runTask = async ({ showSuccessToast = true } = {}) => {
    if (analyzing.value) {
      return
    }

    // runToken 用于防止多次点击或组件卸载后旧请求回写页面状态。
    const runToken = ++activeRunToken
    const rawFile = extractRawFile(selectedFile.value)

    resetResultState()
    analysisLogs.value = []
    analyzing.value = true
    analyzeProgress.value = 0
    currentAnalysisStep.value = '任务提交'
    taskError.value = ''
    taskStatus.value = ''
    patchPatientInfoPreview()
    startVisualProgress(runToken)

    try {
      // 各页面通过 options.submitTask 注入具体提交逻辑，这里只负责公共流程。
      const submitResult = await submitTask({
        file: uploadMode.value === 'local' ? rawFile : null,
        ...uploadForm,
        patientName: uploadForm.patientName,
        examId: uploadForm.examId,
        sourceMode: uploadMode.value
      })

      const taskId = submitResult?.taskId
      if (!taskId) {
        throw new Error('提交任务成功，但未获取到任务 ID')
      }
      latestTaskId.value = taskId

      // 任务提交成功后进入轮询阶段。
      addLog(submitResult.message || pendingStatusMessage)
      analyzeProgress.value = Math.max(analyzeProgress.value, 20)
      currentAnalysisStep.value = '等待云端处理'

      const completed = await pollTaskUntilComplete(taskId, runToken)
      if (completed && showSuccessToast) {
        ElMessage.success(reanalyzeSuccessMessage)
      }
    } catch (error) {
      taskStatus.value = 'FAILED'
      taskError.value = error.message || '分析失败'
      currentAnalysisStep.value = '失败'
      addLog(taskError.value)
      ElMessage.error(taskError.value)
    } finally {
      // 只有当前活跃任务才能收尾并关闭加载态。
      if (runToken === activeRunToken) {
        clearProgressTimer()
        analyzing.value = false
      }
    }
  }

  // 打开上传弹窗时重置本次任务的基础输入。
  const openUploadDialog = (mode = 'local') => {
    uploadMode.value = mode
    resetUploadFormValues()
    selectedFile.value = null
    uploadDialogVisible.value = true
  }

  // 本地上传模式下记录选择的文件。
  const handleDialogFileChange = (file) => {
    selectedFile.value = file
  }

  // 清空已选择文件。
  const handleDialogFileRemove = () => {
    selectedFile.value = null
  }

  // 校验表单后真正发起提交。
  const submitUpload = async () => {
    if (!uploadFormRef.value) {
      return
    }

    try {
      await uploadFormRef.value.validate()
    } catch {
      return
    }

    if (uploadMode.value === 'local' && !extractRawFile(selectedFile.value)) {
      ElMessage.warning('请上传影像文件')
      return
    }

    if (uploadMode.value === 'pacs') {
      ElMessage.info(`正在提交 PACS 检查 ${uploadForm.examId} 的质控任务...`)
    }

    // 关闭弹窗后进入统一任务执行流程。
    uploadDialogVisible.value = false
    await runTask({ showSuccessToast: true })
  }

  // 真实 PACS 检索对话框显示状态。
  const pacsSearchDialogVisible = ref(false)

  const openPacsSearch = () => {
    pacsSearchDialogVisible.value = true
  }

  // 从 PACS 查询结果中选中检查后，把关键字段回填到上传表单。
  const handlePacsSelect = (selectedStudy) => {
    resetUploadFormValues({
      patientId: selectedStudy.patientId || '',
      patientName: selectedStudy.patientName,
      examId: selectedStudy.accessionNumber,
      gender: selectedStudy.gender || '',
      age: selectedStudy.age ?? null,
      studyDate: selectedStudy.studyDate || '',
      flowRate: selectedStudy.flowRate ?? '',
      contrastVolume: selectedStudy.contrastVolume ?? '',
      injectionSite: selectedStudy.injectionSite ?? '',
      sliceThickness: selectedStudy.sliceThickness ?? '',
      bolusTrackingHu: selectedStudy.bolusTrackingHu ?? '',
      scanDelaySec: selectedStudy.scanDelaySec ?? '',
      heartRate: selectedStudy.heartRate ?? '',
      hrVariability: selectedStudy.hrVariability ?? '',
      reconPhase: selectedStudy.reconPhase ?? '',
      kVp: selectedStudy.kVp ?? ''
    })
    selectedFile.value = null
    uploadMode.value = 'pacs'
    uploadDialogVisible.value = true
    pacsSearchDialogVisible.value = false
    ElMessage.success('已选择PACS检查记录，请确认信息后提交')
  }

  // 上传新案例时先弹出“本地/PACS”二选一入口。
  const resetUpload = () => {
    uploadMethodDialogVisible.value = true
  }

  // 点击结果项时打开详情弹窗。
  const viewDetails = (item) => {
    currentItem.value = item
    dialogVisible.value = true
  }

  // 基于当前案例配置重新提交一次质控任务。
  const handleReanalyze = async () => {
    if (!uploadForm.patientName || !uploadForm.examId) {
      ElMessage.warning('请先创建质控案例')
      return
    }

    if (uploadMode.value === 'local' && !extractRawFile(selectedFile.value)) {
      ElMessage.warning('当前案例缺少影像文件，请重新上传')
      return
    }

    ElMessage.info(reanalyzeStartMessage)
    await runTask({ showSuccessToast: true })
  }

  // 当前导出逻辑由各页面通过文案控制，组合函数只统一成功提示。
  const handleExport = async () => {
    if (!latestTaskId.value) {
      ElMessage.warning('当前案例尚未生成可导出的任务报告')
      return
    }
    if (taskStatus.value !== 'SUCCESS') {
      ElMessage.warning('当前任务尚未成功完成，暂无可导出的任务报告')
      return
    }

    try {
      const blob = await exportQualityTaskReport(latestTaskId.value)
      const url = window.URL.createObjectURL(blob)
      const link = document.createElement('a')
      link.href = url
      link.download = `quality-task-${latestTaskId.value}.docx`
      link.click()
      window.URL.revokeObjectURL(url)
      ElMessage.success(exportMessage)
    } catch (error) {
      ElMessage.error(error.message || '导出报告失败')
    }
  }

  onBeforeUnmount(() => {
    // 组件卸载时终止旧轮询/旧定时器，避免异步回写已销毁页面。
    destroyed = true
    activeRunToken += 1
    clearProgressTimer()
  })

  return {
    analyzing,
    analyzeProgress,
    currentAnalysisStep,
    analysisLogs,
    dialogVisible,
    currentItem,
    uploadDialogVisible,
    uploadMode,
    uploadFormRef,
    uploadForm,
    selectedFile,
    qcItems,
    patientInfo,
    latestTaskId,
    taskStatus,
    taskError,
    analysisMode,
    analysisLabel,
    isMockResult,
    uploadMethodDialogVisible,
    openUploadDialog,
    handleDialogFileChange,
    handleDialogFileRemove,
    submitUpload,
    pacsSearchDialogVisible,
    openPacsSearch,
    handlePacsSelect,
    resetUpload,
    viewDetails,
    handleReanalyze,
    handleExport
  }
}
