<!--
  @file src/views/quality/CoronaryCTA.vue
  @description 冠脉CTA智能质控视图
  主要功能：
  1. 影像上传：支持本地DICOM文件夹拖拽上传和PACS系统模拟拉取。
  2. AI智能分析：展示分析进度、当前步骤和实时日志（模拟）。
  3. 结果展示：
     - 患者检查信息卡片（包含冠脉CTA特有参数：心率、心率波动、重建相位等）
     - 质控评分仪表盘
     - 异常项汇总
     - 详细质控检测项列表（心率控制、血管强化、血管显示等）
  4. 详情查看：点击质控项查看详细分析结果和影像快照（Mock）。

  @backend-api
  - [MOCK] detectCoronaryCTA: 模拟冠脉质控检测 (src/api/quality.js)
  - (Planned) POST /api/v1/quality/coronary-cta/detect: 真实后端检测接口
  - (Simulated) WebSocket /ws/quality/coronary-cta/progress: 实时分析进度推送
-->
<template>
  <div class="coronary-qc-container">
    <!-- 顶部导航与操作栏 -->
    <div class="page-header">
      <div class="header-left">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>影像质控</el-breadcrumb-item>
          <el-breadcrumb-item>冠脉CTA</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 class="page-title">
          冠脉CTA智能质控
          <el-tag v-if="qcItems.length > 0" type="primary" effect="plain" class="status-tag"
            >AI 自动分析完成</el-tag
          >
          <el-tag v-else type="info" effect="plain" class="status-tag">等待上传影像</el-tag>
        </h2>
      </div>
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

    <!-- 1. 上传区域 (当没有数据时显示) -->
    <div v-if="qcItems.length === 0" class="upload-section">
      <div class="upload-wrapper">
        <!-- 正在分析的状态 (覆盖在上传区域之上，或者替换它) -->
        <transition name="fade" mode="out-in">
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
              <div class="log-window">
                <p v-for="(log, index) in analysisLogs" :key="index" class="log-item">
                  <span class="log-time">[{{ log.time }}]</span> {{ log.message }}
                </p>
              </div>
            </div>
          </div>

          <!-- 上传/选择入口 -->
          <div v-else class="upload-choices" key="upload">
            <el-row :gutter="40" justify="center">
              <!-- 本地上传卡片 -->
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

              <!-- PACS 入口卡片 -->
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

    <!-- 2. 结果展示区域 (当有数据时显示) -->
    <div v-else class="result-section">
      <!-- 患者信息与总体评分 -->
      <el-row :gutter="20" class="info-section">
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
              <el-descriptions-item label="扫描协议">Coronary CTA</el-descriptions-item>

              <!-- 冠脉CTA特有参数 -->
              <el-descriptions-item label="平均心率">
                <span
                  :style="{
                    color: patientInfo.heartRate > 75 ? '#F56C6C' : '#67C23A',
                    fontWeight: 'bold',
                  }"
                >
                  {{ patientInfo.heartRate }} bpm
                </span>
              </el-descriptions-item>
              <el-descriptions-item label="心率波动">
                {{ patientInfo.hrVariability }} bpm
              </el-descriptions-item>
              <el-descriptions-item label="重建相位">
                {{ patientInfo.reconPhase }}
              </el-descriptions-item>
              <el-descriptions-item label="管电压">
                {{ patientInfo.kVp }}
              </el-descriptions-item>
              <el-descriptions-item label="层厚">
                {{ patientInfo.sliceThickness }} mm
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
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

    <!-- 质控项详情 (列表模式) -->
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
            <div class="item-detail-text" v-if="item.status === '不合格'">
              <span class="error-text"
                ><el-icon><InfoFilled /></el-icon> {{ item.detail }}</span
              >
            </div>
          </div>

          <!-- 右侧：操作与分数 (可选) -->
          <div class="list-item-right">
            <el-button type="primary" link @click.stop="viewDetails(item)">
              查看详情 <el-icon class="el-icon--right"><ArrowRight /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 详情弹窗 (Mock) -->
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
          <el-empty description="此处将显示相关层面的 MPR/CPR 重建图" :image-size="120"></el-empty>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="dialogVisible = false">确认</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- 新建案例弹窗 -->
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
 * @section Imports
 */
import { ref, computed, onMounted, nextTick, reactive } from 'vue'
import { ElMessage } from 'element-plus'
import {
  Upload,
  Download,
  Refresh,
  FolderOpened,
  Connection,
  Aim,
  User,
  List,
  CircleCheckFilled,
  WarningFilled,
  InfoFilled,
  ArrowRight,
  UploadFilled,
  ArrowLeft,
} from '@element-plus/icons-vue'

/**
 * @section State Definitions
 * @description 定义页面所需的状态变量
 */
// 模拟状态
const analyzing = ref(false)
const analyzeProgress = ref(0) // 进度条百分比
const dialogVisible = ref(false)
const currentItem = ref(null)

// 新增：上传弹窗相关状态
const uploadDialogVisible = ref(false)
const uploadMode = ref('local') // 'local' | 'pacs'
const uploadFormRef = ref(null)
const uploadForm = reactive({
  patientName: '',
  examId: '',
})
const selectedFile = ref(null)

// 表单验证规则
const uploadRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  examId: [{ required: true, message: '请输入检查ID', trigger: 'blur' }],
}

/**
 * @function openUploadDialog
 * @description 打开上传对话框，重置表单状态
 * @param {string} mode - 上传模式 'local' (本地文件) 或 'pacs' (PACS拉取)
 */
const openUploadDialog = (mode = 'local') => {
  uploadMode.value = mode
  uploadForm.patientName = ''
  uploadForm.examId = ''
  selectedFile.value = null
  uploadDialogVisible.value = true
}

/**
 * @function handleDialogFileChange
 * @description 处理文件选择变更，更新 selectedFile 状态
 * @param {File} file - Element Plus 上传组件选中的文件对象
 */
const handleDialogFileChange = (file) => {
  selectedFile.value = file
}

/**
 * @function submitUpload
 * @description 提交上传表单并触发分析流程
 *
 * 逻辑:
 * 1. 验证表单信息 (患者姓名、检查ID)
 * 2. 检查文件是否已选择 (Local 模式)
 * 3. 关闭弹窗并调用 startAnalysisProcess 开始分析
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

      // PACS 模式下，不需要文件，因为是后端拉取
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

// 分析过程相关状态
const currentAnalysisStep = ref('准备就绪')
const analysisLogs = ref([])

/**
 * @function simulatePacsSelect
 * @description 模拟从 PACS 系统选择影像
 * 
 * 逻辑:
 * 1. 模拟与 PACS 系统建立连接
 * 2. 模拟检索患者列表
 * 3. 自动填充患者信息到上传表单
 * 4. 切换到 PACS 模式并打开确认弹窗
 */
const simulatePacsSelect = () => {
  ElMessage.success('已连接 PACS 系统，正在检索今日检查列表...')
  // 模拟从 PACS 获取到数据，弹出对话框让用户确认
  setTimeout(() => {
    uploadForm.patientName = '张某某'
    uploadForm.examId = 'PACS_CTA_20231024'
    // PACS 模式不需要 selectedFile
    selectedFile.value = null

    // 打开弹窗，指定为 pacs 模式
    uploadMode.value = 'pacs'
    uploadDialogVisible.value = true

    ElMessage.success('已自动获取 PACS 影像信息，请确认')
  }, 500) // 缩短模拟时间
}

/**
 * @section Mock Data
 * @description 模拟患者和质控结果数据
 */
// 模拟患者数据
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
  // Coronary specific
  heartRate: 0,
  hrVariability: 0,
  reconPhase: '',
  kVp: '',
})

// 质控项数据
const qcItems = ref([])

/**
 * @function resetUpload
 * @description 重置上传状态，重新打开上传对话框以开始新案例
 */
const resetUpload = () => {
  openUploadDialog()
}

/**
 * @function addLog
 * @description 向分析日志中添加一条新记录，保持最新的 5 条日志
 * @param {string} msg - 日志消息内容
 */
const addLog = (msg) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  analysisLogs.value.unshift({ time, message: msg })
  // 保持日志最多 5 条
  if (analysisLogs.value.length > 5) analysisLogs.value.pop()
}

/**
 * @function startAnalysisProcess
 * @description 模拟完整的 AI 分析流程
 * 包含多个步骤：DICOM解析 -> 完整性校验 -> 元数据提取 -> 模型加载 -> 血管提取 -> 特征分析 -> 报告生成
 *
 * @backend-api (Simulated) WebSocket /ws/quality/coronary-cta/progress
 * @note 实际项目中，此函数将建立 WebSocket 连接监听后端进度推送
 */
const startAnalysisProcess = async () => {
  // 清除旧数据，切换到分析视图
  qcItems.value = []
  patientInfo.value = { name: '' }

  analyzing.value = true
  analyzeProgress.value = 0
  analysisLogs.value = []

  // 模拟分析流程 (冠脉CTA特有)
  const steps = [
    { progress: 10, msg: '正在读取 DICOM 文件头信息...', step: 'DICOM 解析' },
    { progress: 30, msg: '校验心电门控与序列完整性...', step: '完整性校验' },
    {
      progress: 45,
      msg: `提取患者元数据: ${uploadForm.patientName || '未知'}, ...`,
      step: '元数据提取',
    },
    { progress: 55, msg: 'AI 模型加载中 (Coronary_Seg_v3.0)...', step: '模型加载' },
    { progress: 70, msg: '自动提取冠状动脉血管树...', step: '血管提取' },
    { progress: 85, msg: '分析血管对比剂强化CT值与伪影...', step: '特征分析' },
    { progress: 95, msg: '生成结构化质控报告...', step: '报告生成' },
    { progress: 100, msg: '分析完成', step: '完成' },
  ]

  for (const step of steps) {
    // 缩短时间：200ms ~ 400ms
    await new Promise((resolve) => setTimeout(resolve, 200 + Math.random() * 200))
    analyzeProgress.value = step.progress
    currentAnalysisStep.value = step.step
    addLog(step.msg)

    // 在中间某个时刻填充患者数据
    if (step.progress === 45) {
      // 使用表单数据 + 模拟数据
      patientInfo.value = {
        name: uploadForm.patientName || '自动提取中...',
        gender: Math.random() > 0.5 ? '男' : '女', // 模拟自动获取
        age: 50 + Math.floor(Math.random() * 30), // 模拟自动获取
        studyId: uploadForm.examId || 'UNKNOWN',
        accessionNumber: 'ACC' + Math.floor(Math.random() * 100000), // 模拟自动获取
        studyDate: new Date().toLocaleString(), // 模拟自动获取
        device: 'Siemens Somatom Force', // 模拟自动获取
        sliceCount: 400, // 模拟自动获取
        sliceThickness: 0.6, // 模拟自动获取
        // CTA Specific
        heartRate: 60 + Math.floor(Math.random() * 20), // 60-80
        hrVariability: Math.floor(Math.random() * 4), // 0-3
        reconPhase: '75% (Diastolic)',
        kVp: '100 kV',
      }
    }
  }

  await fetchQCData()
}

/**
 * @section Computed Properties
 */
// 计算异常数量
const abnormalCount = computed(() => {
  return qcItems.value.filter((item) => item.status === '不合格').length
})

// 计算质控分数 (简单逻辑：每个合格项得 100/总数 分，向下取整)
const qualityScore = computed(() => {
  if (qcItems.value.length === 0) return 0
  const passed = qcItems.value.filter((item) => item.status === '合格').length
  return Math.round((passed / qcItems.value.length) * 100)
})

// 分数颜色
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
 * @backend-api [MOCK] detectCoronaryCTA (src/api/quality.js)
 * @backend-api (Planned) POST /api/v1/quality/coronary-cta/detect
 * @note 该函数模拟了向后端发送检测请求并获取完整报告的过程
 */
const fetchQCData = async () => {
  analyzing.value = true
  // 模拟网络延迟
  await new Promise((resolve) => setTimeout(resolve, 300))

  // 模拟数据生成
  // 真实场景: const res = await detectCoronaryCTA(selectedFile.value)

  // 模拟返回数据 - 冠脉CTA质控项 (12项)
  qcItems.value = [
    {
      name: '心率控制',
      description: '检查扫描期间平均心率是否符合重建要求',
      status: '合格',
      detail: '平均心率 62 bpm (<=75 bpm)',
    },
    {
      name: '心率稳定性',
      description: '检查扫描期间心率波动情况',
      status: '合格',
      detail: '心率波动 < 5 bpm',
    },
    {
      name: '呼吸配合',
      description: '检查是否存在呼吸运动伪影',
      status: '合格',
      detail: '膈肌位置稳定',
    },
    {
      name: '血管强化 (AO)',
      description: '升主动脉根部 CT 值',
      status: '合格',
      detail: 'CT值 380 HU (>=300 HU)',
    },
    {
      name: '血管强化 (LAD)',
      description: '左前降支远端 CT 值',
      status: '合格',
      detail: 'CT值 280 HU (>=250 HU)',
    },
    {
      name: '血管强化 (RCA)',
      description: '右冠状动脉远端 CT 值',
      status: '不合格',
      detail: 'CT值 210 HU (<250 HU)，强化稍显不足',
    },
    {
      name: '噪声水平',
      description: '主动脉根部图像噪声 (SD)',
      status: '合格',
      detail: 'SD = 22 HU (<=30 HU)',
    },
    {
      name: '钙化积分影响',
      description: '严重钙化斑块导致的伪影干扰',
      status: '合格',
      detail: '无严重钙化干扰',
    },
    {
      name: '台阶伪影',
      description: '因心率不齐或屏气不佳导致的层面错位',
      status: '合格',
      detail: '血管连续性良好',
    },
    {
      name: '心电门控',
      description: 'ECG 信号同步状态',
      status: '合格',
      detail: 'R波触发准确',
    },
    {
      name: '扫描范围',
      description: '覆盖气管分叉至心脏膈面下',
      status: '合格',
      detail: '心脏包络完整',
    },
    {
      name: '金属/线束伪影',
      description: '上腔静脉高浓度对比剂或电极片伪影',
      status: '不合格',
      detail: '上腔静脉硬化伪影干扰 RCA 近段观察',
    },
  ]

  analyzing.value = false
}

/**
 * @function handleReanalyze
 * @description 触发重新分析流程
 * @backend-api (Planned) POST /api/v1/quality/coronary-cta/reanalyze
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
 * @backend-api (Planned) GET /api/v1/quality/coronary-cta/report/export
 */
const handleExport = () => {
  ElMessage.success('质控报告已生成并开始下载')
}

/**
 * @function viewDetails
 * @description 查看单个质控项的详情，打开详情弹窗
 * @param {Object} item - 选中的质控项对象，包含名称、状态、描述等信息
 */
const viewDetails = (item) => {
  currentItem.value = item
  dialogVisible.value = true
}
</script>

<style scoped>
/* 容器与整体布局 */
.coronary-qc-container {
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

/* 1. 上传区域样式 - 保持一致 */
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
  animation: scan-move 1.5s infinite linear;
  z-index: 1;
}

@keyframes scan-move {
  0% {
    top: -10%;
  }
  100% {
    top: 110%;
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
  font-size: 16px;
  color: #409eff;
  font-weight: 500;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 5px;
}

.step-dots {
  animation: blink 1.5s infinite;
}

@keyframes blink {
  0%,
  100% {
    opacity: 0;
  }
  50% {
    opacity: 1;
  }
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
  animation: slide-up 0.3s ease-out;
}

.log-time {
  color: #67c23a;
  margin-right: 8px;
}

@keyframes slide-up {
  from {
    opacity: 0;
    transform: translateY(10px);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

/* 2. 结果区域样式 */
.result-section {
  animation: fade-in 0.5s ease;
}

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
  font-weight: bold;
}

.score-content {
  display: flex;
  align-items: center;
  justify-content: space-around;
  height: 100%;
  padding: 10px 0;
}

.score-value {
  display: block;
  font-size: 28px;
  font-weight: bold;
}

.score-label {
  font-size: 12px;
  color: #909399;
}

.score-summary {
  display: flex;
  flex-direction: column;
  gap: 12px;
  padding-left: 20px;
  border-left: 1px solid #eee;
}

.summary-item {
  display: flex;
  justify-content: space-between;
  width: 140px;
  font-size: 14px;
  color: #606266;
}

.summary-item .value {
  font-weight: bold;
}

.summary-item .value.danger {
  color: #f56c6c;
}

.summary-result {
  margin-top: 8px;
  font-size: 14px;
}

/* 列表模式的质控项 */
.qc-items-section {
  background: #fff;
  border-radius: 8px;
  padding: 24px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

.section-title {
  margin-bottom: 24px;
  padding-bottom: 16px;
  border-bottom: 1px solid #ebeef5;
}

.section-title h3 {
  margin: 0 0 8px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 18px;
  color: #303133;
}

.section-title .subtitle {
  color: #909399;
  font-size: 13px;
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
}

.item-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.item-name {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}

.item-desc {
  font-size: 14px;
  color: #606266;
  margin-bottom: 6px;
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
}

/* 弹窗样式 */
.dialog-content {
  padding: 10px;
}

.mock-image-placeholder {
  margin-top: 20px;
  height: 200px;
  background-color: #f0f2f5;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
}

/* 动画通用类 */
.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.3s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}
</style>
