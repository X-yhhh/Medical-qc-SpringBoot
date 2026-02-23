<!--
  @file src/views/quality/Head.vue
  @description CT头部平扫质控主页面
  主要功能:
  1. 影像上传: 支持本地 DICOM 文件夹拖拽上传和 PACS 系统模拟拉取
  2. AI 智能分析: 展示分析进度、当前步骤和实时日志
  3. 结果展示: 患者信息、质控评分、质控项列表
  4. 交互: 重新分析、导出报告、查看详情

  @backend-api 对接接口:
  - [POST] /api/v1/quality/head/detect (模拟: detectHead) - 头部质控检测
  - [GET] /api/v1/quality/head/report/export (模拟) - 导出报告
-->
<template>
  <div class="head-qc-container">
    <!--
      顶部导航与操作栏
      功能: 显示页面路径、标题及全局操作按钮
    -->
    <div class="page-header">
      <div class="header-left">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>影像质控</el-breadcrumb-item>
          <el-breadcrumb-item>CT头部平扫</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 class="page-title">
          CT头部平扫智能质控
          <!-- 状态标签: 根据是否有质控结果显示不同状态 -->
          <el-tag v-if="qcItems.length > 0" type="primary" effect="plain" class="status-tag"
            >AI 自动分析完成</el-tag
          >
          <el-tag v-else type="info" effect="plain" class="status-tag">等待上传影像</el-tag>
        </h2>
      </div>
      <!-- 右侧操作按钮: 仅在有结果时显示 -->
      <div class="header-right" v-if="qcItems.length > 0">
        <el-button @click="resetUpload">
          <el-icon><Upload /></el-icon> 上传新案例
        </el-button>
        <el-button type="primary" :loading="analyzing" @click="handleReanalyze">
          <el-icon><Refresh /></el-icon> 重新分析
        </el-button>
        <el-button type="success" plain @click="handleExport">
          <el-icon><Download /></el-icon> 导出报告
        </el-button>
      </div>
    </div>

    <!--
      1. 上传与分析区域 (当没有质控数据时显示)
      包含: 正在分析的动画状态、上传方式选择(本地/PACS)
    -->
    <div v-if="qcItems.length === 0" class="upload-section">
      <div class="upload-wrapper">
        <transition name="fade" mode="out-in">
          <!-- 状态 A: AI 智能分析中 (显示进度条和日志) -->
          <div v-if="analyzing" class="analyzing-container" key="analyzing">
            <div class="scan-animation-box">
              <div class="scan-line"></div>
              <el-icon class="scan-icon"><Aim /></el-icon>
            </div>
            <div class="progress-info">
              <h3 class="analyzing-title">AI 智能分析中</h3>
              <el-progress
                :percentage="analyzeProgress"
                :stroke-width="12"
                striped
                striped-flow
                :duration="10"
              />
              <div class="step-display">
                <span class="step-text">{{ currentAnalysisStep }}</span>
                <span class="step-dots">...</span>
              </div>
              <!-- 实时日志显示窗口 -->
              <div class="log-window">
                <p v-for="(log, index) in analysisLogs" :key="index" class="log-item">
                  <span class="log-time">[{{ log.time }}]</span> {{ log.message }}
                </p>
              </div>
            </div>
          </div>

          <!-- 状态 B: 上传/选择入口 (默认初始状态) -->
          <div v-else class="upload-choices" key="upload">
            <el-row :gutter="40" justify="center">
              <!-- 本地上传卡片: 触发文件选择弹窗 -->
              <el-col :span="10">
                <div class="choice-card local-upload" @click="openUploadDialog('local')">
                  <div class="icon-wrapper">
                    <el-icon><FolderOpened /></el-icon>
                  </div>
                  <h3>本地影像上传</h3>
                  <p>支持 DICOM 文件夹拖拽上传</p>
                  <p class="sub-tip">自动解析 .dcm 序列文件</p>
                </div>
              </el-col>

              <!-- PACS 入口卡片: 模拟连接医院 PACS 系统 -->
              <el-col :span="10">
                <div class="choice-card pacs-select" @click="simulatePacsSelect">
                  <div class="icon-wrapper">
                    <el-icon><Connection /></el-icon>
                  </div>
                  <h3>PACS 系统调取</h3>
                  <p>连接医院内部影像归档系统</p>
                  <p class="sub-tip">支持按患者 ID / 检查号检索</p>
                </div>
              </el-col>
            </el-row>

            <div class="upload-footer">
              <p>
                <el-icon><InfoFilled /></el-icon>
                严禁上传含有敏感隐私的非脱敏数据，所有数据仅用于质控分析。
              </p>
            </div>
          </div>
        </transition>
      </div>
    </div>

    <!--
      2. 结果展示区域 (当有质控数据时显示)
      包含: 患者信息卡片、总体评分卡片、详细质控项列表
    -->
    <div v-else class="result-section">
      <el-row :gutter="20" class="info-section">
        <!-- 患者基本信息展示 -->
        <el-col :span="16">
          <el-card shadow="hover" class="patient-card">
            <template #header>
              <div class="card-header">
                <span
                  ><el-icon><User /></el-icon> 患者检查信息</span
                >
                <el-tag size="small" type="info"
                  >Accession No: {{ patientInfo.accessionNumber }}</el-tag
                >
              </div>
            </template>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="姓名">{{ patientInfo.name }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ patientInfo.gender }}</el-descriptions-item>
              <el-descriptions-item label="年龄">{{ patientInfo.age }}岁</el-descriptions-item>
              <el-descriptions-item label="检查ID">{{ patientInfo.studyId }}</el-descriptions-item>
              <el-descriptions-item label="检查日期">{{
                patientInfo.studyDate
              }}</el-descriptions-item>
              <el-descriptions-item label="设备型号">{{ patientInfo.device }}</el-descriptions-item>
              <el-descriptions-item label="扫描部位">Head Routine</el-descriptions-item>
              <el-descriptions-item label="图像层数"
                >{{ patientInfo.sliceCount }} 层</el-descriptions-item
              >
              <el-descriptions-item label="层厚"
                >{{ patientInfo.sliceThickness }} mm</el-descriptions-item
              >
            </el-descriptions>
          </el-card>
        </el-col>

        <!-- 总体评分仪表盘 -->
        <el-col :span="8">
          <el-card shadow="hover" class="score-card">
            <div class="score-content">
              <el-progress
                type="dashboard"
                :percentage="qualityScore"
                :color="scoreColor"
                :width="140"
              >
                <template #default="{ percentage }">
                  <span class="score-value">{{ percentage }}</span>
                  <span class="score-label">质控评分</span>
                </template>
              </el-progress>
              <div class="score-summary">
                <div class="summary-item">
                  <span class="label">检测项数</span>
                  <span class="value">{{ qcItems.length }}</span>
                </div>
                <div class="summary-item">
                  <span class="label">异常项</span>
                  <span class="value danger">{{ abnormalCount }}</span>
                </div>
                <div class="summary-result">
                  综合判定:
                  <el-tag :type="qualityScore >= 80 ? 'success' : 'danger'" effect="dark">
                    {{ qualityScore >= 80 ? '合格' : '不合格' }}
                  </el-tag>
                </div>
              </div>
            </div>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!--
      质控项详情列表
      功能: 展示每一项检测指标的状态，点击可查看详情
    -->
    <div v-if="qcItems.length > 0" class="qc-items-section">
      <div class="section-title">
        <h3>
          <el-icon><List /></el-icon> 质控检测详情
        </h3>
        <span class="subtitle"
          >系统自动检测 {{ qcItems.length }} 项关键指标，请重点关注异常项。</span
        >
      </div>

      <div class="qc-list">
        <div
          v-for="(item, index) in qcItems"
          :key="index"
          class="qc-list-item"
          :class="{ 'is-error': item.status === '不合格', 'is-success': item.status === '合格' }"
          @click="viewDetails(item)"
        >
          <!-- 左侧：图标与状态 -->
          <div class="list-item-left">
            <div class="status-icon">
              <el-icon v-if="item.status === '合格'"><CircleCheckFilled /></el-icon>
              <el-icon v-else><WarningFilled /></el-icon>
            </div>
          </div>

          <!-- 中间：信息主体 -->
          <div class="list-item-main">
            <div class="item-header">
              <span class="item-name">{{ item.name }}</span>
              <el-tag
                size="small"
                :type="item.status === '合格' ? 'success' : 'danger'"
                effect="light"
                class="item-tag"
              >
                {{ item.status }}
              </el-tag>
            </div>
            <div class="item-desc">
              {{ item.description }}
            </div>
            <!-- 异常详情提示 -->
            <div class="item-detail-text" v-if="item.status === '不合格'">
              <span class="error-text"
                ><el-icon><InfoFilled /></el-icon> {{ item.detail }}</span
              >
            </div>
          </div>

          <!-- 右侧：查看详情按钮 -->
          <div class="list-item-right">
            <el-button type="primary" link @click.stop="viewDetails(item)">
              查看详情 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 (Mock展示) -->
    <el-dialog v-model="dialogVisible" :title="currentItem?.name + ' - 详情分析'" width="50%">
      <div v-if="currentItem" class="dialog-content">
        <el-descriptions border :column="1">
          <el-descriptions-item label="检测项">{{ currentItem.name }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="currentItem.status === '合格' ? 'success' : 'danger'">{{
              currentItem.status
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="判定标准">{{
            currentItem.description
          }}</el-descriptions-item>
          <el-descriptions-item label="详细日志">
            {{
              currentItem.status === '合格'
                ? 'AI 算法扫描全序列，未检出异常特征值。'
                : currentItem.detail + '，建议技师检查扫描参数或重新扫描。'
            }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="mock-image-placeholder">
          <el-empty description="此处将显示相关层面的影像快照与AI标注" :image-size="120"></el-empty>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="dialogVisible = false">确认</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 新建案例/上传弹窗 -->
    <el-dialog v-model="uploadDialogVisible" title="新建质控案例" width="500px" destroy-on-close>
      <el-form
        ref="uploadFormRef"
        :model="uploadForm"
        :rules="uploadRules"
        label-width="100px"
        status-icon
      >
        <el-form-item label="患者姓名" prop="patientName">
          <el-input v-model="uploadForm.patientName" placeholder="请输入患者姓名" />
        </el-form-item>
        <el-form-item label="检查 ID" prop="examId">
          <el-input v-model="uploadForm.examId" placeholder="请输入检查/住院号" />
        </el-form-item>

        <!-- 本地上传模式 -->
        <el-form-item label="影像文件" required v-if="uploadMode === 'local'">
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleDialogFileChange"
            :on-remove="() => (selectedFile = null)"
            style="width: 100%"
            accept=".dcm"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽 DICOM 文件夹或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 .dcm 序列文件，单次最大 500MB</div>
            </template>
          </el-upload>
        </el-form-item>

        <!-- PACS 拉取模式 -->
        <el-form-item label="影像源" v-else>
          <el-alert title="已锁定 PACS 影像源" type="success" :closable="false" show-icon>
            <template #default> 系统将直接从服务器拉取 Accession No. 关联的影像序列。 </template>
          </el-alert>
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="uploadDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="submitUpload">开始智能分析</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
/**
 * @file Head.vue
 * @description CT头部平扫质控主页面逻辑
 * 包含影像上传（本地/PACS）、AI分析进度展示、质控结果报告展示。
 *
 * @api-dependencies
 * - src/api/quality.js: detectHead (Mock)
 */

import { ref, computed, onMounted, nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Upload, Refresh, Download, FolderOpened, Connection, InfoFilled, Aim, Picture, UploadFilled, List, CircleCheckFilled, WarningFilled, ArrowRight, ArrowLeft } from '@element-plus/icons-vue'

// ==========================================
// 状态定义
// ==========================================

// 分析状态控制
const analyzing = ref(false)
const analyzeProgress = ref(0) // 进度条百分比
const currentAnalysisStep = ref('准备就绪')
const analysisLogs = ref([]) // 实时日志队列

// 弹窗与详情展示
const dialogVisible = ref(false)
const currentItem = ref(null) // 当前查看的质控项详情

// 上传弹窗相关状态
const uploadDialogVisible = ref(false)
const uploadMode = ref('local') // 上传模式: 'local' | 'pacs'
const uploadFormRef = ref(null)
const uploadForm = reactive({
  patientName: '',
  examId: '',
})
const selectedFile = ref(null)

// 表单校验规则
const uploadRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  examId: [{ required: true, message: '请输入检查ID', trigger: 'blur' }],
}

// 质控结果数据 (模拟)
const qcItems = ref([])
const patientInfo = ref({
  name: '',
  gender: '',
  age: 0,
  studyId: '',
  accessionNumber: '',
  studyDate: '',
  device: '',
  sliceCount: 0,
  sliceThickness: 0,
})

// ==========================================
// 业务逻辑函数
// ==========================================

/**
 * 打开上传配置弹窗
 * @param {string} mode - 'local' 或 'pacs'
 */
const openUploadDialog = (mode = 'local') => {
  uploadMode.value = mode
  uploadForm.patientName = ''
  uploadForm.examId = ''
  selectedFile.value = null
  uploadDialogVisible.value = true
}

/**
 * 处理文件选择变更
 * @param {File} file - ElementPlus upload 组件返回的文件对象
 */
const handleDialogFileChange = (file) => {
  selectedFile.value = file
}

/**
 * 提交上传并启动分析流程
 * 验证表单 -> 模拟上传/PACS连接 -> 启动分析动画
 */
const submitUpload = async () => {
  if (!uploadFormRef.value) return

  await uploadFormRef.value.validate(async (valid) => {
    if (valid) {
      // 本地上传模式下，必须选择文件
      if (uploadMode.value === 'local' && !selectedFile.value) {
        ElMessage.warning('请上传影像文件')
        return
      }

      // PACS 模式下，模拟通知后端拉取
      if (uploadMode.value === 'pacs') {
        ElMessage.info(`正在通知后端从 PACS 拉取 ID: ${uploadForm.examId} 的数据...`)
      }

      // 关闭弹窗
      uploadDialogVisible.value = false
      // 开始分析流程
      await startAnalysisProcess()
    }
  })
}

/**
 * 模拟 PACS 选择操作
 * 演示自动填充患者信息并打开确认弹窗
 */
const simulatePacsSelect = () => {
  ElMessage.success('已连接 PACS 系统，正在检索今日检查列表...')

  setTimeout(() => {
    uploadForm.patientName = '王某某'
    uploadForm.examId = 'PACS_AUTO_20231024'
    selectedFile.value = null

    uploadMode.value = 'pacs'
    uploadDialogVisible.value = true

    ElMessage.success('已自动获取 PACS 影像信息，请确认')
  }, 500)
}

/**
 * 添加实时日志
 * @param {string} msg - 日志内容
 */
const addLog = (msg) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  analysisLogs.value.unshift({ time, message: msg })
  if (analysisLogs.value.length > 5) analysisLogs.value.pop()
}

/**
 * @function startAnalysisProcess
 * @description 模拟 AI 分析全流程
 * 包含：DICOM解析 -> 完整性校验 -> 元数据提取 -> 模型加载 -> 特征提取 -> 报告生成
 *
 * @backend-api [WebSocket] /ws/quality/head/progress (模拟) - 获取分析进度
 */
const startAnalysisProcess = async () => {
  // 1. 初始化状态
  qcItems.value = []
  patientInfo.value = { name: '' }
  analyzing.value = true
  analyzeProgress.value = 0
  analysisLogs.value = []

  // 2. 定义分析步骤序列
  const steps = [
    { progress: 10, msg: '正在读取 DICOM 文件头信息...', step: 'DICOM 解析' },
    { progress: 30, msg: '校验序列完整性 (240/240 slices)...', step: '完整性校验' },
    {
      progress: 45,
      msg: `提取患者元数据: ${uploadForm.patientName || '未知'}, ...`,
      step: '元数据提取',
    },
    { progress: 60, msg: 'AI 模型加载中 (Head_CT_QC_v2.1)...', step: '模型加载' },
    { progress: 80, msg: '正在检测运动伪影与金属伪影...', step: '特征提取' },
    { progress: 95, msg: '生成结构化质控报告...', step: '报告生成' },
    { progress: 100, msg: '分析完成', step: '完成' },
  ]

  // 3. 执行步骤模拟
  for (const step of steps) {
    await new Promise((resolve) => setTimeout(resolve, 200 + Math.random() * 200))
    analyzeProgress.value = step.progress
    currentAnalysisStep.value = step.step
    addLog(step.msg)

    // 在元数据提取阶段填充模拟患者数据
    if (step.progress === 45) {
      patientInfo.value = {
        name: uploadForm.patientName || '自动提取中...',
        gender: Math.random() > 0.5 ? '男' : '女',
        age: 40 + Math.floor(Math.random() * 40),
        studyId: uploadForm.examId || 'UNKNOWN',
        accessionNumber: 'ACC' + Math.floor(Math.random() * 100000),
        studyDate: new Date().toLocaleString(),
        device: 'GE Revolution CT',
        sliceCount: 240,
        sliceThickness: 5.0,
      }
    }
  }

  // 4. 获取最终质控结果 (模拟 API 调用)
  await fetchQCData()
}

/**
 * 计算逻辑
 */
// 异常项计数
const abnormalCount = computed(() => {
  return qcItems.value.filter((item) => item.status === '不合格').length
})

// 质控总分计算 (加权算法占位，目前为简单百分比)
const qualityScore = computed(() => {
  if (qcItems.value.length === 0) return 0
  const passed = qcItems.value.filter((item) => item.status === '合格').length
  return Math.round((passed / qcItems.value.length) * 100)
})

// 分数颜色映射
const scoreColor = computed(() => {
  if (qualityScore.value >= 90) return '#67C23A'
  if (qualityScore.value >= 60) return '#E6A23C'
  return '#F56C6C'
})

/**
 * @function fetchQCData
 * @description 获取质控检测结果数据
 * @returns {Promise<void>} 更新 qcItems 状态
 *
 * @backend-api [POST] /api/v1/quality/head/detect (对应前端 mock API: detectHead)
 */
const fetchQCData = async () => {
  analyzing.value = true
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300))

  // 模拟数据生成 logic moved here or imported
  // 在真实对接中，此处应调用:
  // const res = await detectHead(selectedFile.value)
  // qcItems.value = res.issues

  // 恢复之前的模拟数据逻辑 (为了保持演示效果，这里暂时硬编码生成)
  qcItems.value = [
    {
      name: '扫描覆盖范围',
      status: '合格',
      description: '扫描范围应覆盖从颅底至颅顶完整区域',
      detail: '',
    },
    {
      name: '体位不正',
      status: '合格',
      description: '正中矢状面应与扫描架中心线重合',
      detail: '',
    },
    {
      name: '运动伪影',
      status: Math.random() > 0.8 ? '不合格' : '合格',
      description: '图像中不应出现因患者运动导致的模糊或重影',
      detail: '检测到明显层间错位，建议固定头部后重新扫描',
    },
    {
      name: '金属伪影',
      status: Math.random() > 0.9 ? '不合格' : '合格',
      description: '应避免假牙、发卡等金属异物干扰',
      detail: '颅底区域存在放射状金属伪影，影响后颅窝观察',
    },
    {
      name: '层厚层间距',
      status: '合格',
      description: '常规扫描层厚应≤5mm',
      detail: '',
    },
    {
      name: '剂量控制 (CTDI)',
      status: '合格',
      description: 'CTDIvol 应低于参考水平 (60 mGy)',
      detail: '',
    },
  ]

  analyzing.value = false
}

/**
 * @function handleReanalyze
 * @description 触发重新分析流程
 * 调用 fetchQCData 获取最新的质控结果
 *
 * @backend-api [POST] /api/v1/quality/head/detect (复用检测接口)
 */
const handleReanalyze = () => {
  ElMessage.info('正在请求云端 AI 重新分析...')
  fetchQCData().then(() => {
    ElMessage.success('分析完成，数据已更新')
  })
}

/**
 * @function handleExport
 * @description 导出质控报告
 *
 * @backend-api [GET] /api/v1/quality/head/report/export (模拟)
 */
const handleExport = () => {
  ElMessage.success('质控报告已生成并开始下载')
}

/**
 * @function viewDetails
 * @description 查看质控项详情
 * @param {Object} item - 选中的质控项数据
 */
const viewDetails = (item) => {
  currentItem.value = item
  dialogVisible.value = true
}

/**
 * @function ignoreIssue
 * @description 忽略特定的质控异常
 * @param {Object} item - 质控项
 *
 * @backend-api [POST] /api/v1/quality/head/issue/ignore (计划中)
 */
const ignoreIssue = (item) => {
  item.status = '合格'
  ElMessage.success(`已忽略 "${item.name}" 的异常标记`)
}

/**
 * @function resetUpload
 * @description 重置页面状态至初始上传界面
 */
const resetUpload = () => {
  openUploadDialog()
}
</script>

<style scoped>
.head-qc-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 84px); /* 减去顶部导航栏高度 */
}

/* 页面头部 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
}

.header-left .el-breadcrumb {
  margin-bottom: 12px;
}

.page-title {
  margin: 0;
  font-size: 24px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 12px;
}

.status-tag {
  font-weight: normal;
}

/* 1. 上传区域样式 */
.upload-section {
  display: flex;
  flex-direction: column;
  /* 固定高度，确保所有页面一致 */
  height: calc(100vh - 180px);
  min-height: 600px;
  background: #fff;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
  overflow: hidden;
}

.upload-wrapper {
  width: 100%;
  height: 100%;
  padding: 40px;
  display: flex;
  flex-direction: column;
}

.analyzing-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

.upload-choices {
  flex: 1;
  display: flex;
  flex-direction: column;
  /* 内容垂直居中 */
  justify-content: center;
}

/* 卡片行容器 */
.upload-choices .el-row {
  flex: 1; /* 占据中间空间 */
  display: flex;
  align-items: center; /* 垂直居中 */
  width: 100%;
  max-width: 900px;
  margin: 0 auto !important;
}

.upload-footer {
  margin-top: auto; /* 推到底部 */
  padding-top: 20px;
  border-top: 1px solid #eee;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

/* 卡片选择样式 */
.choice-card {
  background: #f8f9fb;
  border: 2px solid #e4e7ed;
  border-radius: 12px;
  padding: 32px 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  height: 220px; /* 固定卡片高度 */
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.choice-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.08);
  border-color: #409eff;
}

.icon-wrapper {
  width: 80px;
  height: 80px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 20px;
  font-size: 36px;
  transition: all 0.3s;
}

.local-upload .icon-wrapper {
  background: #ecf5ff;
  color: #409eff;
}

.pacs-select .icon-wrapper {
  background: #f0f9eb;
  color: #67c23a;
}

.choice-card:hover .icon-wrapper {
  transform: scale(1.1);
}

.choice-card h3 {
  margin: 0 0 10px;
  font-size: 18px;
  color: #303133;
}

.choice-card p {
  margin: 0 0 5px;
  color: #606266;
  font-size: 14px;
}

.choice-card .sub-tip {
  color: #909399;
  font-size: 12px;
}

.upload-footer p {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6px;
}

/* 分析动画样式 */
.analyzing-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

.scan-animation-box {
  width: 120px;
  height: 120px;
  border: 4px solid #409eff;
  border-radius: 50%;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 30px;
  overflow: hidden;
  box-shadow: 0 0 15px rgba(64, 158, 255, 0.4);
}

.scan-icon {
  font-size: 48px;
  color: #409eff;
  z-index: 2;
}

.scan-line {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 4px;
  background: #67c23a;
  box-shadow: 0 0 10px #67c23a;
  animation: scanMove 1.5s linear infinite;
  z-index: 1;
}

@keyframes scanMove {
  0% {
    top: 0;
    opacity: 0;
  }
  10% {
    opacity: 1;
  }
  90% {
    opacity: 1;
  }
  100% {
    top: 100%;
    opacity: 0;
  }
}

.progress-info {
  width: 100%;
  max-width: 500px;
  text-align: center;
}

.analyzing-title {
  font-size: 20px;
  color: #303133;
  margin-bottom: 20px;
}

.step-display {
  margin-top: 15px;
  font-size: 14px;
  color: #409eff;
  font-weight: 500;
}

.log-window {
  margin-top: 20px;
  height: 120px;
  background: #2b2b2b;
  border-radius: 4px;
  padding: 10px 15px;
  text-align: left;
  overflow-y: hidden;
  font-family: 'Consolas', monospace;
  font-size: 12px;
  color: #a6a9ad;
}

.log-item {
  margin: 4px 0;
  line-height: 1.4;
  animation: fadeIn 0.3s ease;
}

.log-time {
  color: #67c23a;
  margin-right: 8px;
}

/* 2. 结果展示样式 */
.info-section {
  margin-bottom: 24px;
}

.patient-card,
.score-card {
  height: 100%;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.score-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 10px 0;
}

.score-value {
  display: block;
  font-size: 28px;
  font-weight: bold;
  color: #303133;
}

.score-label {
  display: block;
  font-size: 12px;
  color: #909399;
  margin-top: 4px;
}

.score-summary {
  margin-top: 20px;
  width: 100%;
  padding: 0 20px;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
  font-size: 14px;
}

.summary-item .label {
  color: #606266;
}

.summary-item .value {
  font-weight: bold;
}

.summary-item .value.danger {
  color: #f56c6c;
}

.summary-result {
  margin-top: 15px;
  padding-top: 15px;
  border-top: 1px dashed #dcdfe6;
  text-align: center;
  font-size: 14px;
  color: #606266;
}

/* 质控项详情样式 */
.section-title {
  margin-bottom: 20px;
  display: flex;
  align-items: baseline;
  gap: 12px;
}

.section-title h3 {
  margin: 0;
  font-size: 18px;
  color: #303133;
  display: flex;
  align-items: center;
  gap: 8px;
}

.section-title .subtitle {
  font-size: 13px;
  color: #909399;
}

.qc-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.qc-list-item {
  display: flex;
  align-items: center;
  background: #fff;
  border: 1px solid #ebeef5;
  border-radius: 8px;
  padding: 20px 24px;
  transition: all 0.3s;
  cursor: pointer;
}

.qc-list-item:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.08);
  transform: translateY(-2px);
}

.qc-list-item.is-error {
  border-left: 4px solid #f56c6c;
  background: #fff5f5;
}

.qc-list-item.is-success {
  border-left: 4px solid #67c23a;
}

.list-item-left {
  margin-right: 24px;
}

.status-icon {
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48px;
  height: 48px;
  border-radius: 50%;
  background: #f2f6fc;
}

.is-success .status-icon {
  color: #67c23a;
  background: #f0f9eb;
}
.is-error .status-icon {
  color: #f56c6c;
  background: #fef0f0;
}

.list-item-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.item-header {
  display: flex;
  align-items: center;
  gap: 12px;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.item-desc {
  font-size: 14px;
  color: #606266;
  line-height: 1.5;
}

.item-detail-text {
  font-size: 13px;
  color: #f56c6c;
  display: flex;
  align-items: center;
  gap: 6px;
  margin-top: 4px;
  background: rgba(245, 108, 108, 0.1);
  padding: 4px 8px;
  border-radius: 4px;
  width: fit-content;
}

.list-item-right {
  margin-left: 20px;
  display: flex;
  align-items: center;
}

/* Dialog 内容 */
.dialog-content {
  display: flex;
  flex-direction: column;
  gap: 20px;
}

.mock-image-placeholder {
  height: 200px;
  background: #000;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

/* 过渡动画 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

@keyframes fadeIn {
  from {
    opacity: 0;
    transform: translateY(5px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
