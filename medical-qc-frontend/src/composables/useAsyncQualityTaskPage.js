import { reactive, ref, onBeforeUnmount } from 'vue'
import { ElMessage } from 'element-plus'
import { getQualityTask } from '@/api/quality'

const POLL_INTERVAL_MS = 1000
const POLL_TIMEOUT_MS = 60000

const sleep = (ms) => new Promise((resolve) => setTimeout(resolve, ms))

const cloneInitialState = (initialState) => ({ ...initialState })

const extractRawFile = (selectedFile) => {
  if (!selectedFile) {
    return null
  }
  return selectedFile.raw || selectedFile
}

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
    pacsPreset,
    buildStaticPacsResult,
    analysisSteps,
    exportMessage,
    reanalyzeStartMessage,
    reanalyzeSuccessMessage,
    pacsLoadingMessage,
    pacsReadyMessage,
    pendingStatusMessage = '质控任务已提交，等待云端处理...',
    processingStatusMessage = '云端任务处理中，正在等待最终报告...'
  } = options

  const analyzing = ref(false)
  const analyzeProgress = ref(0)
  const currentAnalysisStep = ref('准备就绪')
  const analysisLogs = ref([])

  const dialogVisible = ref(false)
  const currentItem = ref(null)

  const uploadDialogVisible = ref(false)
  const uploadMode = ref('local')
  const uploadFormRef = ref(null)
  const uploadForm = reactive({
    patientName: '',
    examId: ''
  })
  const selectedFile = ref(null)
  const uploadMethodDialogVisible = ref(false)

  const qcItems = ref([])
  const patientInfo = ref(cloneInitialState(initialPatientInfo))

  let progressTimer = null
  let pacsMessageTimer = null
  let destroyed = false
  let activeRunToken = 0
  let lastPolledStatus = ''

  const clearProgressTimer = () => {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
    }
  }

  /**
   * 清理 PACS 演示提示定时器，避免组件卸载后继续弹提示。
   */
  const clearPacsMessageTimer = () => {
    if (pacsMessageTimer) {
      clearTimeout(pacsMessageTimer)
      pacsMessageTimer = null
    }
  }

  const resetResultState = () => {
    qcItems.value = []
    patientInfo.value = cloneInitialState(initialPatientInfo)
  }

  const addLog = (message) => {
    const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
    analysisLogs.value.unshift({ time, message })
    if (analysisLogs.value.length > 6) {
      analysisLogs.value.pop()
    }
  }

  const patchPatientInfoPreview = () => {
    patientInfo.value = {
      ...cloneInitialState(initialPatientInfo),
      ...patientInfo.value,
      name: uploadForm.patientName || patientInfo.value.name,
      studyId: uploadForm.examId || patientInfo.value.studyId,
      studyDate: patientInfo.value.studyDate || nowText()
    }
  }

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

  /**
   * PACS 演示模式下直接使用前端静态结果，不再请求后端或数据库。
   *
   * @param {number} runToken - 当前运行令牌，用于避免旧任务串扰
   * @param {boolean} showSuccessToast - 是否展示成功提示
   */
  const runStaticPacsTask = async (runToken, showSuccessToast) => {
    addLog('当前质控项的 PACS 调取尚未对接后端，开始使用静态结果回显。')
    currentAnalysisStep.value = '演示回显'
    analyzeProgress.value = Math.max(analyzeProgress.value, 35)

    await sleep(1600)
    if (destroyed || runToken !== activeRunToken) {
      return false
    }

    const staticResult = buildStaticPacsResult({
      patientName: uploadForm.patientName,
      examId: uploadForm.examId,
      sourceMode: uploadMode.value,
    })

    applyResultPayload(staticResult)
    addLog('静态质控结果已生成，当前未对接后端任务与数据库。')

    if (showSuccessToast) {
      ElMessage.success(reanalyzeSuccessMessage)
    }
    return true
  }

  const pollTaskUntilComplete = async (taskId, runToken) => {
    const startTime = Date.now()
    lastPolledStatus = ''

    while (!destroyed && runToken === activeRunToken) {
      const taskDetail = await getQualityTask(taskId)
      if (destroyed || runToken !== activeRunToken) {
        return false
      }

      const currentStatus = taskDetail?.status || ''
      if (currentStatus !== lastPolledStatus) {
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
        throw new Error(taskDetail?.errorMessage || '任务处理失败')
      }

      if (Date.now() - startTime >= POLL_TIMEOUT_MS) {
        throw new Error('任务处理超时')
      }

      await sleep(POLL_INTERVAL_MS)
    }

    return false
  }

  const runTask = async ({ showSuccessToast = true } = {}) => {
    if (analyzing.value) {
      return
    }

    const runToken = ++activeRunToken
    const rawFile = extractRawFile(selectedFile.value)

    resetResultState()
    analysisLogs.value = []
    analyzing.value = true
    analyzeProgress.value = 0
    currentAnalysisStep.value = '任务提交'
    patchPatientInfoPreview()
    startVisualProgress(runToken)

    try {
      if (uploadMode.value === 'pacs' && typeof buildStaticPacsResult === 'function') {
        await runStaticPacsTask(runToken, showSuccessToast)
        return
      }

      const submitResult = await submitTask({
        file: uploadMode.value === 'local' ? rawFile : null,
        patientName: uploadForm.patientName,
        examId: uploadForm.examId,
        sourceMode: uploadMode.value
      })

      const taskId = submitResult?.taskId
      if (!taskId) {
        throw new Error('提交任务成功，但未获取到任务 ID')
      }

      addLog(submitResult.message || pendingStatusMessage)
      analyzeProgress.value = Math.max(analyzeProgress.value, 20)
      currentAnalysisStep.value = '等待云端处理'

      const completed = await pollTaskUntilComplete(taskId, runToken)
      if (completed && showSuccessToast) {
        ElMessage.success(reanalyzeSuccessMessage)
      }
    } catch (error) {
      currentAnalysisStep.value = '失败'
      addLog(error.message || '分析失败')
      ElMessage.error(error.message || '分析失败')
    } finally {
      if (runToken === activeRunToken) {
        clearProgressTimer()
        analyzing.value = false
      }
    }
  }

  const openUploadDialog = (mode = 'local') => {
    clearPacsMessageTimer()
    uploadMode.value = mode
    uploadForm.patientName = ''
    uploadForm.examId = ''
    selectedFile.value = null
    uploadDialogVisible.value = true
  }

  const handleDialogFileChange = (file) => {
    selectedFile.value = file
  }

  const handleDialogFileRemove = () => {
    selectedFile.value = null
  }

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
      if (typeof buildStaticPacsResult === 'function') {
        ElMessage.info(`当前为演示模式，PACS 检查 ${uploadForm.examId} 将直接静态展示结果`)
      } else {
        ElMessage.info(`正在通知后端从 PACS 拉取 ID: ${uploadForm.examId} 的数据...`)
      }
    }

    uploadDialogVisible.value = false
    await runTask({ showSuccessToast: true })
  }

  /**
   * 模拟 PACS 选片入口。
   *
   * 当前四个 mock 质控页面不走真实 PACS 检索，因此直接使用页面配置的预置患者信息。
   */
  const simulatePacsSelect = () => {
    clearPacsMessageTimer()
    uploadMode.value = 'pacs'
    uploadForm.patientName = pacsPreset?.patientName || ''
    uploadForm.examId = pacsPreset?.examId || ''
    selectedFile.value = null
    uploadDialogVisible.value = true
    ElMessage.info(pacsLoadingMessage)

    pacsMessageTimer = setTimeout(() => {
      if (!destroyed) {
        ElMessage.success(pacsReadyMessage)
      }
    }, 500)
  }

  const pacsSearchDialogVisible = ref(false)

  const openPacsSearch = () => {
    pacsSearchDialogVisible.value = true
  }

  const handlePacsSelect = (selectedStudy) => {
    uploadForm.patientName = selectedStudy.patientName
    uploadForm.examId = selectedStudy.accessionNumber
    selectedFile.value = null
    uploadMode.value = 'pacs'
    uploadDialogVisible.value = true
    pacsSearchDialogVisible.value = false
    ElMessage.success('已选择PACS检查记录，请确认信息后提交')
  }

  const resetUpload = () => {
    uploadMethodDialogVisible.value = true
  }

  const viewDetails = (item) => {
    currentItem.value = item
    dialogVisible.value = true
  }

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

  const handleExport = () => {
    ElMessage.success(exportMessage)
  }

  onBeforeUnmount(() => {
    destroyed = true
    activeRunToken += 1
    clearProgressTimer()
    clearPacsMessageTimer()
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
    uploadMethodDialogVisible,
    openUploadDialog,
    handleDialogFileChange,
    handleDialogFileRemove,
    submitUpload,
    simulatePacsSelect,
    pacsSearchDialogVisible,
    openPacsSearch,
    handlePacsSelect,
    resetUpload,
    viewDetails,
    handleReanalyze,
    handleExport
  }
}
