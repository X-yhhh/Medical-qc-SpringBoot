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

  const qcItems = ref([])
  const patientInfo = ref(cloneInitialState(initialPatientInfo))

  let progressTimer = null
  let destroyed = false
  let activeRunToken = 0
  let lastPolledStatus = ''

  const clearProgressTimer = () => {
    if (progressTimer) {
      clearInterval(progressTimer)
      progressTimer = null
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

  const applyTaskResult = (taskDetail) => {
    const result = taskDetail?.result || {}
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
      ElMessage.info(`正在通知后端从 PACS 拉取 ID: ${uploadForm.examId} 的数据...`)
    }

    uploadDialogVisible.value = false
    await runTask({ showSuccessToast: true })
  }

  const simulatePacsSelect = () => {
    ElMessage.success(pacsLoadingMessage)
    setTimeout(() => {
      uploadForm.patientName = pacsPreset.patientName
      uploadForm.examId = pacsPreset.examId
      selectedFile.value = null
      uploadMode.value = 'pacs'
      uploadDialogVisible.value = true
      ElMessage.success(pacsReadyMessage)
    }, 500)
  }

  const resetUpload = () => {
    openUploadDialog()
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
    openUploadDialog,
    handleDialogFileChange,
    handleDialogFileRemove,
    submitUpload,
    simulatePacsSelect,
    resetUpload,
    viewDetails,
    handleReanalyze,
    handleExport
  }
}
