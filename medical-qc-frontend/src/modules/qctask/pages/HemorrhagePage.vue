<template>
  <div class="hemorrhage-qc-container">
    <!--
      顶部导航与操作栏
      功能：显示页面路径、标题、状态标签以及全局操作按钮
    -->
    <div class="page-header">
      <div class="header-left">
        <el-breadcrumb separator="/">
          <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
          <el-breadcrumb-item>影像质控</el-breadcrumb-item>
          <el-breadcrumb-item>头部出血检测</el-breadcrumb-item>
        </el-breadcrumb>
        <h2 class="page-title">
          头部出血 AI 智能检测
          <!-- 状态展示：根据是否有出血检测结果动态显示 -->
          <el-tag v-if="qcItems.length > 0" :type="hasHemorrhage ? 'danger' : 'success'" effect="plain" class="status-tag">
            {{ hasHemorrhage ? '检测到出血' : 'AI 自动分析完成' }}
          </el-tag>
          <el-tag v-else type="info" effect="plain" class="status-tag">等待上传影像</el-tag>
        </h2>
      </div>
      <!-- 操作按钮区：仅在有分析结果时显示 -->
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
      1. 上传区域 (当没有数据时显示)
      包含：
      - 正在分析时的进度条动画
      - 本地文件上传入口
      - PACS 系统检索入口
    -->
    <div v-if="qcItems.length === 0" class="upload-section">
      <div class="upload-wrapper">
        <!-- 正在分析的状态：显示进度条与日志 -->
        <transition name="fade" mode="out-in">
          <div v-if="analyzing" class="analyzing-container" key="analyzing">
            <div class="scan-animation-box">
              <div class="scan-line"></div>
              <el-icon class="scan-icon"><Aim /></el-icon>
            </div>
            <div class="progress-info">
              <h3 class="analyzing-title">AI 智能分析中</h3>
              <!-- 模拟进度条：通过定时器控制进度增长 -->
              <el-progress
                :percentage="Math.floor(analyzeProgress)"
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

          <!-- 初始状态：上传/选择入口 -->
          <div v-else class="upload-choices" key="upload">
            <el-row :gutter="40" justify="center">
              <!-- 本地上传卡片：触发文件选择弹窗 -->
              <el-col :span="10">
                <div class="choice-card local-upload" @click="openUploadDialog('local')">
                  <div class="icon-wrapper">
                    <el-icon><FolderOpened /></el-icon>
                  </div>
                  <h3>本地影像上传</h3>
                  <p>支持 PNG / JPG / BMP 影像上传</p>
                  <p class="sub-tip">当前模型暂不支持 .dcm 序列解析</p>
                </div>
              </el-col>

              <!-- PACS 入口卡片：当前环境未接入真实 PACS，提示改用本地上传 -->
              <el-col :span="10">
                <div class="choice-card pacs-select" @click="openPacsSearch">
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
      2. 结果展示区域 (列表模式)
      包含：
      - 患者基本信息卡片
      - 出血风险评分仪表盘
      - 检测详情列表（点击可查看影像与 BBox）
    -->
    <div v-else class="result-section">
      <!-- 患者信息与总体评分 -->
      <el-row :gutter="20" class="info-section">
        <el-col :span="16">
          <el-card shadow="hover" class="patient-card">
            <template #header>
              <div class="card-header">
                <span><el-icon><User /></el-icon> 患者检查信息</span>
                <el-tag size="small" type="info">检查编号：{{ patientInfo.examId }}</el-tag>
              </div>
            </template>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="姓名">{{ patientInfo.name }}</el-descriptions-item>
              <el-descriptions-item label="患者编号">{{ patientInfo.patientCode }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ patientInfo.gender }}</el-descriptions-item>
              <el-descriptions-item label="年龄">{{ formatAge(patientInfo.age) }}</el-descriptions-item>
              <el-descriptions-item label="检查编号">{{ patientInfo.examId }}</el-descriptions-item>
              <el-descriptions-item label="检查日期">{{ patientInfo.studyDate }}</el-descriptions-item>
              <el-descriptions-item label="设备型号">{{ patientInfo.device }}</el-descriptions-item>
              <el-descriptions-item label="扫描部位">{{ scanRegion }}</el-descriptions-item>
              <el-descriptions-item label="检测模型">
                 <el-tag size="small" effect="plain">{{ modelName }}</el-tag>
              </el-descriptions-item>
              <el-descriptions-item label="推理设备">
                <span style="color: #409EFF; font-weight: bold;">{{ inferenceDevice }}</span>
              </el-descriptions-item>
            </el-descriptions>
          </el-card>
        </el-col>
        <!-- 风险评分仪表盘 -->
        <el-col :span="8">
          <el-card shadow="hover" class="score-card">
            <div class="score-content">
               <el-progress
                 type="dashboard"
                 :percentage="hemorrhageProb"
                 :color="scoreColor"
                 :width="140"
               >
                 <template #default="{ percentage }">
                   <span class="score-value" :style="{ color: scoreColor }">{{ percentage }}%</span>
                   <span class="score-label">出血风险</span>
                 </template>
               </el-progress>
               <div class="score-summary">
                 <div class="summary-result">
                   AI 判定:
                   <el-tag :type="hasHemorrhage ? 'danger' : 'success'" effect="dark" size="large">
                     {{ hasHemorrhage ? '疑似出血' : '未见异常' }}
                   </el-tag>
                 </div>
                 <div class="summary-primary-issue">
                   主要异常：
                   <span :class="['primary-issue-text', hasHemorrhage ? 'is-danger' : 'is-normal']">
                     {{ primaryIssue }}
                   </span>
                 </div>
               </div>
             </div>
           </el-card>
        </el-col>
      </el-row>

      <!-- 质控检测列表 -->
      <div class="qc-items-section">
        <div class="section-title">
          <h3><el-icon><List /></el-icon> 检测详情报告</h3>
          <span class="subtitle">点击列表项查看详细影像与 AI 标注</span>
        </div>

        <div class="qc-list">
          <div
            v-for="(item, index) in qcItems"
            :key="index"
            class="qc-list-item"
            :class="{ 'is-error': item.status === '异常', 'is-success': item.status === '正常' }"
            @click="viewDetails(item)"
          >
            <!-- 左侧：图标与状态 -->
            <div class="list-item-left">
              <div class="status-icon">
                <el-icon v-if="item.status === '正常'"><CircleCheckFilled /></el-icon>
                <el-icon v-else><WarningFilled /></el-icon>
              </div>
            </div>

            <!-- 中间：信息主体 -->
            <div class="list-item-main">
              <div class="item-header">
                <span class="item-name">{{ item.name }}</span>
                <el-tag
                  size="small"
                  :type="item.status === '正常' ? 'success' : 'danger'"
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
              <div class="item-detail-text" v-if="item.status === '异常'">
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
    </div>

    <!--
      上传弹窗
      功能：录入患者信息，选择本地文件或确认 PACS 影像源
    -->
    <el-dialog
      v-model="uploadDialogVisible"
      :title="uploadMode === 'local' ? '本地影像上传' : 'PACS 系统检索'"
      width="500px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-form :model="uploadForm" :rules="uploadRules" ref="uploadFormRef" label-width="100px">
        <el-form-item label="患者姓名" prop="patientName">
          <el-input v-model="uploadForm.patientName" placeholder="请输入患者姓名"></el-input>
        </el-form-item>
        <el-form-item label="患者编号" prop="patientCode">
          <el-input v-model="uploadForm.patientCode" placeholder="请输入患者编号"></el-input>
        </el-form-item>
        <el-form-item label="检查编号" prop="examId">
          <el-input v-model="uploadForm.examId" placeholder="请输入检查编号"></el-input>
        </el-form-item>
        <el-form-item label="性别" prop="gender">
          <el-select v-model="uploadForm.gender" placeholder="请选择性别" style="width: 100%">
            <el-option label="男" value="男" />
            <el-option label="女" value="女" />
            <el-option label="未说明" value="未说明" />
          </el-select>
        </el-form-item>
        <el-form-item label="年龄" prop="age">
          <el-input-number v-model="uploadForm.age" :min="0" :max="150" controls-position="right" style="width: 100%" />
        </el-form-item>
        <el-form-item label="检查日期" prop="studyDate">
          <el-date-picker
            v-model="uploadForm.studyDate"
            type="date"
            value-format="YYYY-MM-DD"
            format="YYYY-MM-DD"
            placeholder="请选择检查日期"
            style="width: 100%"
          />
        </el-form-item>

        <!-- 本地模式：显示文件上传控件 -->
        <el-form-item label="影像文件" required v-if="uploadMode === 'local'">
          <el-upload
            class="upload-demo"
            drag
            action="#"
            :auto-upload="false"
            :show-file-list="true"
            :limit="1"
            :on-change="handleDialogFileChange"
            :on-remove="handleDialogFileRemove"
            style="width: 100%"
            accept=".png,.jpg,.jpeg,.bmp"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽影像文件或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 PNG、JPG、JPEG、BMP 格式</div>
            </template>
          </el-upload>
        </el-form-item>

        <!-- PACS 模式：显示锁定提示 -->
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

    <!--
      详情弹窗 (带影像与 BBox 标注)
      功能：查看特定检测项的详细信息和影像标注结果
    -->
    <el-dialog v-model="dialogVisible" :title="currentItem?.name + ' - 详情'" width="800px" class="detail-dialog">
      <div v-if="currentItem" class="detail-content">
         <el-row :gutter="20">
           <!-- 左侧：影像预览与标注层 -->
           <el-col :span="14">
             <div class="image-preview-wrapper">
                <div class="image-container">
                  <img v-if="imageUrl" :src="imageUrl" class="preview-image" alt="CT Scan" />
                  <div v-else class="image-placeholder">
                    <el-icon><Picture /></el-icon>
                    <span>无预览图像</span>
                  </div>
                  <!-- 动态 BBox 标注 (仅在出血检测项且有结果时显示) -->
                  <template v-if="currentItem.type === 'hemorrhage' && bboxes && bboxes.length > 0 && hasHemorrhage">
                    <div
                      v-for="(box, index) in bboxes"
                      :key="index"
                      class="bbox-overlay"
                      :style="getBboxStyle(box)"
                    >
                      <span class="bbox-label" v-if="index === 0">出血灶 {{ hemorrhageProb }}%</span>
                    </div>
                  </template>
                </div>
             </div>
           </el-col>
           <!-- 右侧：详细文本信息 -->
           <el-col :span="10">
             <div class="detail-info">
               <el-descriptions :column="1" border direction="vertical">
                 <el-descriptions-item label="检测项">{{ currentItem.name }}</el-descriptions-item>
                 <el-descriptions-item label="状态">
                    <el-tag :type="currentItem.status === '正常' ? 'success' : 'danger'">
                      {{ currentItem.status }}
                    </el-tag>
                 </el-descriptions-item>
                 <el-descriptions-item label="详细说明">
                    {{ currentItem.detail }}
                 </el-descriptions-item>
                 <el-descriptions-item label="AI 置信度" v-if="currentItem.type === 'hemorrhage'">
                    {{ confidenceLevel }}
                 </el-descriptions-item>
               </el-descriptions>
             </div>
           </el-col>
         </el-row>
      </div>
    </el-dialog>

    <!-- 选择上传方式对话框 -->
    <el-dialog
      v-model="uploadMethodDialogVisible"
      title="选择上传方式"
      width="600px"
      :close-on-click-modal="false"
    >
      <el-row :gutter="20" justify="center">
        <el-col :span="11">
          <div class="choice-card local-upload" @click="openUploadDialog('local'); uploadMethodDialogVisible = false">
            <div class="icon-wrapper">
              <el-icon><FolderOpened /></el-icon>
            </div>
            <h3>本地影像上传</h3>
            <p>支持 PNG / JPG / BMP 影像上传</p>
          </div>
        </el-col>
        <el-col :span="11">
          <div class="choice-card pacs-select" @click="openPacsSearch(); uploadMethodDialogVisible = false">
            <div class="icon-wrapper">
              <el-icon><Connection /></el-icon>
            </div>
            <h3>PACS 系统调取</h3>
            <p>连接医院内部影像归档系统</p>
          </div>
        </el-col>
      </el-row>
    </el-dialog>

    <!-- PACS查询对话框 -->
    <PacsSearchDialog
      v-model:visible="pacsSearchDialogVisible"
      task-type="hemorrhage"
      @select="handlePacsSelect"
    />
  </div>
</template>

<!--
  @file HemorrhagePage.vue
  @description 头部出血 AI 智能检测视图
  功能：
  1. 提供影像上传（本地/PACS）界面。
  2. 展示 AI 分析进度与实时日志。
  3. 显示脑出血检测结果、置信度及风险评分。
  4. 渲染可疑出血区域的 BBox 标注。
  5. 展示中线偏移与脑室结构的分析详情。

  @backend-api 对接接口:
  - [POST] /api/v1/quality/hemorrhage (真实: predictHemorrhage) - 脑出血检测
  - [GET] /api/v1/quality/hemorrhage/history (真实) - 获取历史记录
-->
<script setup>
/**
 * @component Hemorrhage
 * @description 头部出血检测页面逻辑控制器
 */
import { ref, computed, reactive, onMounted, watch } from 'vue'
import { ElMessage } from 'element-plus'
import { User, Upload, Refresh, Download, FolderOpened, Connection, InfoFilled, Aim, Picture, UploadFilled, List, CircleCheckFilled, WarningFilled, ArrowRight } from '@element-plus/icons-vue'
import { useRoute, useRouter } from 'vue-router'
import { predictHemorrhage, getHemorrhageHistory, getHemorrhageRecord } from '@/modules/qctask/api/qualityApi'
import PacsSearchDialog from '@/components/PacsSearchDialog.vue'
import '@/assets/css/hemorrhage-scan.css'

const route = useRoute()
const router = useRouter()

// --- 状态定义 ---
const analyzing = ref(false)
const analyzeProgress = ref(0)
const uploadDialogVisible = ref(false)
const uploadMode = ref('local')
const uploadFormRef = ref(null)
const selectedFile = ref(null)
const dialogVisible = ref(false)
const currentItem = ref(null)
const pacsSearchDialogVisible = ref(false)
const uploadMethodDialogVisible = ref(false)

// --- 结果数据 ---
const qcItems = ref([])
const hasHemorrhage = ref(false)
const hemorrhageProb = ref(0)
const primaryIssue = ref('未见明显异常')
const inferenceDevice = ref('--')
const modelName = ref('AdvancedHemorrhageModel')
const imageUrl = ref('')
const bboxes = ref([])
const confidenceLevel = ref('--')
const imageMeta = ref({ width: 0, height: 0 })
const recordCreatedAt = ref('')
const scanRegion = '头颅平扫'
const scannerModel = '头颅 CT 标准采集设备'

// --- 日志与进度 ---
const currentAnalysisStep = ref('准备就绪')
const analysisLogs = ref([])

// --- 表单数据 ---
const uploadForm = reactive({
  patientName: '',
  patientCode: '',
  examId: '',
  gender: '未说明',
  age: null,
  studyDate: '',
})

const uploadRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  patientCode: [{ required: true, message: '请输入患者编号', trigger: 'blur' }],
  examId: [{ required: true, message: '请输入检查编号', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'change' }],
  studyDate: [{ required: true, message: '请选择检查日期', trigger: 'change' }],
}

/**
 * 构造患者信息默认值，避免页面在缺少字段时出现写死数据。
 *
 * @returns {Object} 患者信息默认对象
 */
const createDefaultPatientInfo = () => ({
  name: '--',
  patientCode: '--',
  gender: '--',
  age: null,
  examId: '--',
  studyDate: '--',
  device: scannerModel,
})

/**
 * 统一规范脑出血检测页面的推理设备显示。
 * 当前产品要求全局固定展示为 cuda，不再暴露具体 GPU 型号。
 *
 * @returns {string} 统一后的推理设备文案
 */
const normalizeInferenceDevice = () => 'cuda'

// --- 患者信息（展示用） ---
const patientInfo = ref(createDefaultPatientInfo())

// --- 计算属性 ---
const scoreColor = computed(() => {
  if (hasHemorrhage.value) return '#F56C6C'
  if (hemorrhageProb.value > 50) return '#E6A23C'
  return '#67C23A'
})

// --- 通用辅助方法 ---

/**
 * 判断字符串是否有实际内容。
 *
 * @param {unknown} value - 待判断值
 * @returns {boolean} 是否为非空字符串
 */
const hasText = (value) => typeof value === 'string' && value.trim().length > 0

/**
 * 从多个候选值中提取第一个非空文本。
 *
 * @param {...unknown} candidates - 候选值列表
 * @returns {string} 清洗后的文本
 */
const resolveText = (...candidates) => {
  for (const candidate of candidates) {
    if (typeof candidate === 'string' && candidate.trim().length > 0) {
      return candidate.trim()
    }

    if (candidate !== null && candidate !== undefined && candidate !== '' && typeof candidate !== 'object') {
      return String(candidate)
    }
  }

  return ''
}

/**
 * 从多个候选值中提取第一个合法数字。
 *
 * @param {...unknown} candidates - 候选值列表
 * @returns {number} 数值结果
 */
const resolveNumber = (...candidates) => {
  for (const candidate of candidates) {
    const parsedValue = Number(candidate)
    if (Number.isFinite(parsedValue)) {
      return parsedValue
    }
  }

  return 0
}

/**
 * 从多个候选值中提取可选数字；若不存在则返回 null。
 *
 * @param {...unknown} candidates - 候选值列表
 * @returns {number|null} 数值结果
 */
const resolveOptionalNumber = (...candidates) => {
  for (const candidate of candidates) {
    const parsedValue = Number(candidate)
    if (Number.isFinite(parsedValue)) {
      return parsedValue
    }
  }

  return null
}

/**
 * 兼容后端布尔字段的不同表现形式。
 *
 * @param {unknown} value - 原始值
 * @returns {boolean} 布尔结果
 */
const toBoolean = (value) => {
  if (typeof value === 'boolean') return value
  if (value === 1 || value === '1' || value === 'true') return true
  if (value === 0 || value === '0' || value === 'false') return false
  return Boolean(value)
}

/**
 * 将时间值格式化为页面展示字符串。
 *
 * @param {string|Date} value - 时间值
 * @returns {string} 格式化时间
 */
const formatDateTime = (value) => {
  if (!value) return ''

  const parsedDate = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(parsedDate.getTime())) {
    return ''
  }

  const pad = (number) => String(number).padStart(2, '0')
  const year = parsedDate.getFullYear()
  const month = pad(parsedDate.getMonth() + 1)
  const day = pad(parsedDate.getDate())
  const hour = pad(parsedDate.getHours())
  const minute = pad(parsedDate.getMinutes())
  const second = pad(parsedDate.getSeconds())

  return `${year}-${month}-${day} ${hour}:${minute}:${second}`
}

/**
 * 将检查时间格式化为“检查日期”字段。
 *
 * @param {string|Date} value - 检查时间
 * @returns {string} 日期文本
 */
const formatStudyDate = (value) => {
  const dateTimeText = formatDateTime(value)
  return dateTimeText ? dateTimeText.slice(0, 10) : '--'
}

/**
 * 格式化年龄展示，避免空值时出现“岁”后缀异常。
 *
 * @param {number|string|null} age - 年龄值
 * @returns {string} 年龄展示文案
 */
const formatAge = (age) => {
  const numericAge = resolveOptionalNumber(age)
  return numericAge === null ? '--' : `${numericAge}岁`
}

/**
 * 解析历史记录中的原始分析结果 JSON。
 *
 * @param {string} rawResultJson - 数据库保存的原始结果
 * @returns {Object} 解析后的结果对象
 */
const safeParseRawResult = (rawResultJson) => {
  if (!hasText(rawResultJson)) {
    return {}
  }

  try {
    return JSON.parse(rawResultJson)
  } catch (error) {
    console.warn('解析脑出血历史原始结果失败', error)
    return {}
  }
}

/**
 * 统一处理后端返回的图片路径。
 *
 * @param {string} rawUrl - 原始图片路径
 * @returns {string} 标准化后的图片路径
 */
const normalizeImageUrl = (rawUrl) => {
  if (!hasText(rawUrl)) {
    return ''
  }

  const normalizedUrl = rawUrl.trim().replaceAll('\\', '/')
  if (normalizedUrl.startsWith('http') || normalizedUrl.startsWith('data:')) {
    return normalizedUrl
  }

  return normalizedUrl.startsWith('/') ? normalizedUrl : `/${normalizedUrl}`
}

/**
 * 根据结果中的来源字段或图片路径推断当前案例来源。
 *
 * 作用：
 * 1. 让历史记录、刷新后的结果页仍能识别本案例是否来自 PACS。
 * 2. 使“重新分析”按钮可以直接复用当前 PACS 案例，而不是退回本地上传。
 *
 * @param {string} explicitSourceMode - 后端显式返回的来源模式
 * @param {string} rawImageUrl - 原始图片路径
 * @returns {string} `pacs` 或 `local`
 */
const resolveSourceMode = (explicitSourceMode, rawImageUrl) => {
  const normalizedSourceMode = resolveText(explicitSourceMode, '').toLowerCase()
  if (normalizedSourceMode === 'pacs' || normalizedSourceMode === 'local') {
    return normalizedSourceMode
  }

  const normalizedImageUrl = normalizeImageUrl(rawImageUrl)
  if (normalizedImageUrl.includes('/uploads/pacs/') || normalizedImageUrl.includes('/pacs/')) {
    return 'pacs'
  }

  return 'local'
}

/**
 * 兼容后端返回的单个 bbox 或 bbox 数组。
 *
 * @param {unknown} rawBboxes - 原始 bbox 数据
 * @returns {Array<Array<number>>} 标准化后的 bbox 列表
 */
const normalizeBboxes = (rawBboxes) => {
  if (!Array.isArray(rawBboxes)) {
    return []
  }

  if (rawBboxes.length === 4 && rawBboxes.every((item) => Number.isFinite(Number(item)))) {
    return [rawBboxes.map((item) => Number(item))]
  }

  return rawBboxes
    .filter((item) => Array.isArray(item) && item.length === 4)
    .map((item) => item.map((value) => Number(value)))
}

/**
 * 清空当前分析结果，回到待上传状态。
 */
const clearHemorrhageResult = () => {
  qcItems.value = []
  hasHemorrhage.value = false
  hemorrhageProb.value = 0
  primaryIssue.value = '未见明显异常'
  inferenceDevice.value = '--'
  modelName.value = 'AdvancedHemorrhageModel'
  imageUrl.value = ''
  bboxes.value = []
  confidenceLevel.value = '--'
  imageMeta.value = { width: 0, height: 0 }
  patientInfo.value = createDefaultPatientInfo()
  recordCreatedAt.value = ''
  currentItem.value = null
}

/**
 * 根据后端分析结果生成主要异常文案。
 * 与首页、异常汇总页保持同一严重度优先级。
 *
 * @param {Object} result - 后端响应数据或历史记录数据
 * @returns {string} 主异常项
 */
const resolvePrimaryIssue = (result) => {
  const explicitPrimaryIssue = resolveText(result?.primary_issue, result?.primaryIssue)
  if (explicitPrimaryIssue) {
    return explicitPrimaryIssue
  }

  if (result?.prediction === '出血') {
    return '脑出血'
  }

  if (toBoolean(result?.midline_shift ?? result?.midlineShift)) {
    return resolveText(result?.midline_detail, result?.midlineDetail, '中线偏移')
  }

  if (toBoolean(result?.ventricle_issue ?? result?.ventricleIssue)) {
    return resolveText(result?.ventricle_detail, result?.ventricleDetail, '脑室结构异常')
  }

  return '未见明显异常'
}

/**
 * 组装页面展示用的质控明细列表。
 *
 * @param {Object} resultPayload - 标准化后的结果载荷
 * @param {number} probabilityPercent - 出血风险百分比
 * @returns {Array<Object>} 展示列表
 */
const buildQcItems = (resultPayload, probabilityPercent) => [
  {
    name: '脑出血检测',
    type: 'hemorrhage',
    description: '检测是否存在脑实质内高密度出血灶',
    status: resultPayload.prediction === '出血' ? '异常' : '正常',
    detail: resultPayload.prediction === '出血'
      ? `检测到疑似出血区域 (置信度: ${probabilityPercent.toFixed(1)}%)`
      : '未检测到明显出血灶',
  },
  {
    name: '中线偏移',
    type: 'midline',
    description: '检测脑中线结构是否发生位移',
    status: resultPayload.midline_shift ? '异常' : '正常',
    detail: resultPayload.midline_detail || (resultPayload.midline_shift ? '检测到中线偏移' : '中线结构居中'),
  },
  {
    name: '脑室结构',
    type: 'ventricle',
    description: '检测脑室系统形态及密度是否正常',
    status: resultPayload.ventricle_issue ? '异常' : '正常',
    detail: resultPayload.ventricle_detail || (resultPayload.ventricle_issue ? '脑室形态异常' : '脑室系统形态正常'),
  },
]

/**
 * 将后端实时响应或数据库历史记录统一标准化为页面可消费的数据结构。
 *
 * @param {Object} source - 原始数据源
 * @returns {Object} 标准化结果
 */
const normalizeHemorrhagePayload = (source = {}) => {
  const rawResult = source?.rawResultJson ? safeParseRawResult(source.rawResultJson) : source
  const prediction = resolveText(source?.prediction, rawResult?.prediction)
  const midlineShift = typeof source?.midlineShift === 'boolean'
    ? source.midlineShift
    : toBoolean(rawResult?.midline_shift)
  const ventricleIssue = typeof source?.ventricleIssue === 'boolean'
    ? source.ventricleIssue
    : toBoolean(rawResult?.ventricle_issue)

  const normalizedPayload = {
    prediction,
    qc_status: resolveText(source?.qcStatus, rawResult?.qc_status),
    source_mode: resolveSourceMode(
      resolveText(source?.sourceMode, rawResult?.source_mode),
      resolveText(rawResult?.image_url, source?.imagePath),
    ),
    confidence_level: resolveText(source?.confidenceLevel, rawResult?.confidence_level, rawResult?.confidence, '--'),
    hemorrhage_probability: resolveNumber(
      source?.hemorrhageProbability,
      rawResult?.hemorrhage_probability,
      rawResult?.probability?.hemorrhage,
    ),
    no_hemorrhage_probability: resolveNumber(
      source?.noHemorrhageProbability,
      rawResult?.no_hemorrhage_probability,
      rawResult?.probability?.no_hemorrhage,
    ),
    analysis_duration: resolveNumber(source?.analysisDuration, rawResult?.analysis_duration, rawResult?.duration),
    midline_shift: midlineShift,
    shift_score: resolveNumber(source?.shiftScore, rawResult?.shift_score),
    midline_detail: resolveText(source?.midlineDetail, rawResult?.midline_detail),
    ventricle_issue: ventricleIssue,
    ventricle_detail: resolveText(source?.ventricleDetail, rawResult?.ventricle_detail),
    device: resolveText(source?.device, rawResult?.device, '--') || '--',
    scanner_model: resolveText(rawResult?.scanner_model, scannerModel) || scannerModel,
    scan_region: resolveText(rawResult?.scan_region, scanRegion) || scanRegion,
    model_name: resolveText(rawResult?.model_name),
    image_url: normalizeImageUrl(resolveText(rawResult?.image_url, source?.imagePath)),
    image_base64: resolveText(rawResult?.image_base64),
    image_width: resolveNumber(rawResult?.image_width, rawResult?.imageWidth, 512),
    image_height: resolveNumber(rawResult?.image_height, rawResult?.imageHeight, 512),
    bboxes: normalizeBboxes(rawResult?.bboxes ?? rawResult?.bbox),
    patient_name: resolveText(source?.patientName, rawResult?.patient_name),
    patient_code: resolveText(source?.patientCode, rawResult?.patient_code),
    exam_id: resolveText(source?.examId, rawResult?.exam_id),
    gender: resolveText(source?.gender, rawResult?.gender),
    age: resolveOptionalNumber(source?.age, rawResult?.age),
    study_date: resolveText(source?.studyDate, rawResult?.study_date),
    accession_number: resolveText(rawResult?.accession_number, source?.examId, rawResult?.exam_id),
    created_at: source?.createdAt || rawResult?.created_at,
    updated_at: source?.updatedAt || rawResult?.updated_at,
  }

  normalizedPayload.primary_issue = resolvePrimaryIssue({
    primary_issue: resolveText(source?.primaryIssue, rawResult?.primary_issue),
    prediction: normalizedPayload.prediction,
    midline_shift: normalizedPayload.midline_shift,
    midline_detail: normalizedPayload.midline_detail,
    ventricle_issue: normalizedPayload.ventricle_issue,
    ventricle_detail: normalizedPayload.ventricle_detail,
  })

  return normalizedPayload
}

/**
 * 将标准化结果回填到页面状态中。
 *
 * @param {Object} resultPayload - 标准化后的结果数据
 */
const applyHemorrhageResult = (resultPayload) => {
  if (!resultPayload) {
    clearHemorrhageResult()
    return
  }

  const probabilityPercent = Number((resolveNumber(resultPayload.hemorrhage_probability) * 100).toFixed(1))
  const previewUrl = resultPayload.image_base64
    ? `data:image/png;base64,${resultPayload.image_base64}`
    : normalizeImageUrl(resultPayload.image_url)

  analyzing.value = false
  hasHemorrhage.value = resultPayload.prediction === '出血'
  hemorrhageProb.value = Number.isFinite(probabilityPercent) ? probabilityPercent : 0
  primaryIssue.value = resolvePrimaryIssue(resultPayload)
  inferenceDevice.value = normalizeInferenceDevice()
  modelName.value = resolveText(resultPayload.model_name, 'AdvancedHemorrhageModel') || 'AdvancedHemorrhageModel'
  imageUrl.value = previewUrl
  bboxes.value = resultPayload.bboxes
  confidenceLevel.value = resolveText(resultPayload.confidence_level, '--') || '--'
  imageMeta.value = {
    width: resolveNumber(resultPayload.image_width, 512),
    height: resolveNumber(resultPayload.image_height, 512),
  }
  patientInfo.value = {
    name: resolveText(resultPayload.patient_name, '--') || '--',
    patientCode: resolveText(resultPayload.patient_code, '--') || '--',
    gender: resolveText(resultPayload.gender, '--') || '--',
    age: resolveOptionalNumber(resultPayload.age),
    examId: resolveText(resultPayload.exam_id, '--') || '--',
    studyDate: formatStudyDate(resultPayload.study_date || resultPayload.created_at),
    device: resolveText(resultPayload.scanner_model, scannerModel) || scannerModel,
  }
  recordCreatedAt.value = formatDateTime(resultPayload.created_at)
  uploadForm.patientName = patientInfo.value.name === '--' ? '' : patientInfo.value.name
  uploadForm.patientCode = patientInfo.value.patientCode === '--' ? '' : patientInfo.value.patientCode
  uploadForm.examId = patientInfo.value.examId === '--' ? '' : patientInfo.value.examId
  uploadForm.gender = patientInfo.value.gender === '--' ? '未说明' : patientInfo.value.gender
  uploadForm.age = patientInfo.value.age
  uploadForm.studyDate = patientInfo.value.studyDate === '--' ? '' : patientInfo.value.studyDate
  uploadMode.value = resultPayload.source_mode === 'pacs' ? 'pacs' : 'local'
  qcItems.value = buildQcItems(resultPayload, hemorrhageProb.value)
}

/**
 * 从数据库加载最近一次脑出血检测记录。
 * 当网络瞬时波动时，允许使用本次分析结果作为兜底，保证页面回显稳定。
 *
 * @param {Object|null} fallbackPayload - 可选的兜底结果
 */
const loadLatestHemorrhageRecord = async (fallbackPayload = null) => {
  try {
    const response = await getHemorrhageHistory(1)
    const latestRecord = Array.isArray(response?.data) ? response.data[0] : null

    if (latestRecord) {
      applyHemorrhageResult(normalizeHemorrhagePayload(latestRecord))
      return
    }
  } catch (error) {
    console.error('加载脑出血历史记录失败', error)
  }

  if (fallbackPayload) {
    applyHemorrhageResult(fallbackPayload)
    return
  }

  clearHemorrhageResult()
}

// --- 方法定义 ---

/**
 * 计算 BBox 在预览图中的相对位置样式。
 * 将后端返回的绝对坐标转换为百分比，以适应响应式布局。
 *
 * @param {Array<number>} box - [x, y, w, h]
 * @returns {Object} style object
 */
/**
 * 解析当前路由中的历史记录 ID。
 *
 * @returns {number|null} 历史记录 ID
 */
const resolveRouteRecordId = () => {
  const routeRecordId = Number(route.query.recordId)
  return Number.isInteger(routeRecordId) && routeRecordId > 0 ? routeRecordId : null
}

/**
 * 根据记录 ID 加载指定的出血检测历史结果，接口异常时回退到兜底数据。
 *
 * @param {number|string} recordId - 历史记录 ID
 * @param {Object|null} fallbackPayload - 兜底结果
 */
const loadHemorrhageRecordById = async (recordId, fallbackPayload = null) => {
  try {
    const response = await getHemorrhageRecord(recordId)
    const record = response?.data

    if (record) {
      applyHemorrhageResult(normalizeHemorrhagePayload(record))
      return
    }
  } catch (error) {
    console.error('加载指定历史记录失败', error)
  }

  if (fallbackPayload) {
    applyHemorrhageResult(fallbackPayload)
    return
  }

  clearHemorrhageResult()
}

const getBboxStyle = (box) => {
  if (!box || box.length !== 4) return {}
  if (!imageMeta.value.width || !imageMeta.value.height) return {}

  const [x, y, w, h] = box
  const left = (x / imageMeta.value.width) * 100
  const top = (y / imageMeta.value.height) * 100
  const width = (w / imageMeta.value.width) * 100
  const height = (h / imageMeta.value.height) * 100

  return {
    left: `${left}%`,
    top: `${top}%`,
    width: `${width}%`,
    height: `${height}%`,
    position: 'absolute',
    border: '2px solid #F56C6C',
    boxShadow: '0 0 8px rgba(245, 108, 108, 0.6)',
    zIndex: 10,
  }
}

/**
 * 打开上传配置弹窗。
 *
 * @param {string} mode - local 或 pacs
 */
const openUploadDialog = (mode = 'local') => {
  uploadMode.value = mode
  uploadForm.patientName = ''
  uploadForm.patientCode = ''
  uploadForm.examId = ''
  uploadForm.gender = '未说明'
  uploadForm.age = null
  uploadForm.studyDate = ''
  selectedFile.value = null
  uploadDialogVisible.value = true
}

/**
 * 处理文件选择变更。
 *
 * @param {Object} file - 上传组件返回的文件对象
 */
const handleDialogFileChange = (file) => {
  const rawFile = file?.raw || null
  const isSupportedImage = rawFile && /\.(png|jpg|jpeg|bmp)$/i.test(rawFile.name || '')

  if (rawFile && !isSupportedImage) {
    selectedFile.value = null
    ElMessage.warning('当前脑出血模型仅支持 PNG/JPG/JPEG/BMP 图片，暂不支持 DICOM 文件')
    return
  }

  selectedFile.value = rawFile
}

/**
 * 处理文件移除。
 */
const handleDialogFileRemove = () => {
  selectedFile.value = null
}

/**
 * 打开PACS查询对话框
 */
const openPacsSearch = () => {
  pacsSearchDialogVisible.value = true
}

/**
 * 处理PACS记录选择
 * 将选中的PACS检查记录信息填充到上传表单
 */
const handlePacsSelect = (selectedStudy) => {
  uploadForm.patientName = selectedStudy.patientName
  uploadForm.patientCode = selectedStudy.patientId
  uploadForm.examId = selectedStudy.accessionNumber
  uploadForm.gender = selectedStudy.gender || '未说明'
  uploadForm.age = selectedStudy.age || null
  uploadForm.studyDate = selectedStudy.studyDate || ''
  selectedFile.value = null
  uploadMode.value = 'pacs'
  uploadDialogVisible.value = true
  pacsSearchDialogVisible.value = false
  ElMessage.success('已选择PACS检查记录，请确认信息后提交')
}

/**
 * 重置上传状态，弹出选择上传方式对话框。
 */
const resetUpload = () => {
  uploadMethodDialogVisible.value = true
}

/**
 * @function handleReanalyze
 * @description 重新分析当前案例
 *
 * @backend-api [POST] /api/v1/quality/hemorrhage（复用分析接口）
 */
const handleReanalyze = () => {
  if (uploadMode.value === 'pacs') {
    if (!uploadForm.examId) {
      ElMessage.warning('当前 PACS 案例缺少检查号，请重新选择 PACS 记录')
      openPacsSearch()
      return
    }

    startAnalysisProcess()
    return
  }

  if (!selectedFile.value) {
    ElMessage.warning('当前仅支持基于本地文件重新分析，请重新上传影像')
    openUploadDialog('local')
    return
  }

  startAnalysisProcess()
}

/**
 * @function handleExport
 * @description 导出当前案例的真实检测报告
 */
const handleExport = () => {
  if (!qcItems.value.length) {
    ElMessage.warning('暂无可导出的检测报告')
    return
  }

  const reportLines = [
    '头部出血 AI 智能检测报告',
    `导出时间：${formatDateTime(new Date())}`,
    '',
    `患者姓名：${patientInfo.value.name}`,
    `患者编号：${patientInfo.value.patientCode}`,
    `检查编号：${patientInfo.value.examId}`,
    `性别：${patientInfo.value.gender}`,
    `年龄：${formatAge(patientInfo.value.age)}`,
    `检查日期：${patientInfo.value.studyDate}`,
    `设备型号：${patientInfo.value.device}`,
    `推理设备：${normalizeInferenceDevice()}`,
    `检测模型：${modelName.value}`,
    `结果时间：${recordCreatedAt.value || '--'}`,
    '',
    `AI 判定：${hasHemorrhage.value ? '疑似出血' : '未见异常'}`,
    `主要异常：${primaryIssue.value}`,
    `出血风险：${hemorrhageProb.value.toFixed(1)}%`,
    `置信度：${confidenceLevel.value}`,
    '',
    '检测详情：',
    ...qcItems.value.map((item, index) => `${index + 1}. ${item.name} - ${item.status} - ${item.detail}`),
  ]

  const blob = new Blob([reportLines.join('\r\n')], { type: 'text/plain;charset=utf-8;' })
  const url = window.URL.createObjectURL(blob)
  const link = document.createElement('a')
  link.href = url
  link.download = `脑出血检测报告_${patientInfo.value.examId || 'report'}_${Date.now()}.txt`
  link.click()
  window.URL.revokeObjectURL(url)
  ElMessage.success('报告导出成功')
}

/**
 * 查看详情。
 *
 * @param {Object} item - 当前质控项
 */
const viewDetails = (item) => {
  currentItem.value = item
  dialogVisible.value = true
}

/**
 * 添加分析日志。
 *
 * @param {string} message - 日志内容
 */
const addLog = (message) => {
  const time = new Date().toLocaleTimeString('zh-CN', { hour12: false })
  analysisLogs.value.unshift({ time, message })

  if (analysisLogs.value.length > 5) {
    analysisLogs.value.pop()
  }
}

/**
 * 提交表单并开始分析流程。
 */
const submitUpload = async () => {
  if (!uploadFormRef.value) return

  try {
    await uploadFormRef.value.validate()
  } catch {
    return
  }

  // 本地模式需要检查文件
  if (uploadMode.value === 'local' && !selectedFile.value) {
    ElMessage.warning('请上传影像文件')
    return
  }

  uploadDialogVisible.value = false
  await startAnalysisProcess()
}

/**
 * @function startAnalysisProcess
 * @description 启动 AI 分析流程
 * 包含进度条动画、真实 API 调用和数据库结果回显。
 *
 * @backend-api [POST] /api/v1/quality/hemorrhage（通过 predictHemorrhage 调用）
 */
const startAnalysisProcess = async () => {
  // 本地模式需要检查文件
  if (uploadMode.value === 'local' && !selectedFile.value) {
    ElMessage.warning('请先上传影像文件')
    return
  }

  // PACS模式需要检查examId
  if (uploadMode.value === 'pacs' && !uploadForm.examId) {
    ElMessage.warning('PACS模式下必须提供检查号')
    return
  }

  qcItems.value = []
  analyzing.value = true
  analyzeProgress.value = 0
  analysisLogs.value = []
  currentItem.value = null
  addLog('正在初始化 AI 引擎...')
  currentAnalysisStep.value = '初始化'

  const progressTimer = setInterval(() => {
    if (analyzeProgress.value < 90) {
      analyzeProgress.value += Math.random() * 2

      if (analyzeProgress.value > 10 && analyzeProgress.value < 30) {
        currentAnalysisStep.value = '图像预处理'
      } else if (analyzeProgress.value > 40 && analyzeProgress.value < 70) {
        currentAnalysisStep.value = '神经网络推理'
      } else if (analyzeProgress.value > 80) {
        currentAnalysisStep.value = '生成报告'
      }
    }
  }, 100)

  try {
    addLog('正在上传影像并请求分析...')
    const response = await predictHemorrhage(selectedFile.value, {
      patientName: uploadForm.patientName,
      patientCode: uploadForm.patientCode,
      examId: uploadForm.examId,
      gender: uploadForm.gender,
      age: uploadForm.age,
      studyDate: uploadForm.studyDate,
      sourceMode: uploadMode.value
    })

    clearInterval(progressTimer)
    analyzeProgress.value = 100
    currentAnalysisStep.value = '分析完成'

    const normalizedPayload = normalizeHemorrhagePayload({
      ...response,
      patientName: resolveText(response?.patient_name, uploadForm.patientName),
      patientCode: resolveText(response?.patient_code, uploadForm.patientCode),
      examId: resolveText(response?.exam_id, uploadForm.examId),
      gender: resolveText(response?.gender, uploadForm.gender),
      age: resolveOptionalNumber(response?.age, uploadForm.age),
      studyDate: resolveText(response?.study_date, uploadForm.studyDate),
    })
    const duration = resolveNumber(response?.analysis_duration, response?.duration)

    addLog(`AI 分析完成，耗时: ${duration} ms`)
    addLog(`推理设备: ${normalizeInferenceDevice()}`)

    if (resolveRouteRecordId()) {
      await router.replace({ path: route.path, query: {} })
    }

    await loadLatestHemorrhageRecord(normalizedPayload)
  } catch (error) {
    clearInterval(progressTimer)
    analyzing.value = false

    const errorMessage = error?.message || '分析失败'
    ElMessage.error(errorMessage)
    addLog(`错误: ${errorMessage}`)
  }
}

/**
 * 页面挂载时仅在带有历史记录 ID 的情况下回显对应记录。
 * 默认进入页面时保持空白上传态，与其他质控页面一致。
 */
onMounted(() => {
  const routeRecordId = resolveRouteRecordId()
  if (routeRecordId) {
    loadHemorrhageRecordById(routeRecordId)
    return
  }

  clearHemorrhageResult()
})

watch(() => route.query.recordId, () => {
  const routeRecordId = resolveRouteRecordId()
  if (routeRecordId) {
    loadHemorrhageRecordById(routeRecordId)
    return
  }

  if (!analyzing.value) {
    clearHemorrhageResult()
  }
})
</script>

<style scoped>
/* 容器与整体布局 */
.hemorrhage-qc-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 84px);
}

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

/* 上传区域样式 */
.upload-section {
  display: flex;
  flex-direction: column;
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
  justify-content: center;
}

.upload-choices .el-row {
  flex: 1;
  display: flex;
  align-items: center;
  width: 100%;
  max-width: 900px;
  margin: 0 auto !important;
}

.upload-footer {
  margin-top: auto;
  padding-top: 20px;
  border-top: 1px solid #eee;
  color: #909399;
  font-size: 13px;
  text-align: center;
}

.choice-card {
  background: #f8f9fb;
  border: 2px solid #e4e7ed;
  border-radius: 12px;
  padding: 32px 20px;
  cursor: pointer;
  transition: all 0.3s ease;
  height: 220px;
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
  0% { top: 0; opacity: 0; }
  10% { opacity: 1; }
  90% { opacity: 1; }
  100% { top: 100%; opacity: 0; }
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

/* 结果展示样式 */
.info-section {
  margin-bottom: 24px;
}

.patient-card, .score-card {
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
}

.score-label {
  display: block;
  font-size: 12px;
  color: #909399;
}

.score-summary {
  margin-top: 20px;
  text-align: center;
}

.summary-result {
  margin-top: 10px;
  font-size: 14px;
  color: #606266;
  display: flex;
  align-items: center;
  gap: 8px;
}

.summary-primary-issue {
  margin-top: 10px;
  font-size: 13px;
  color: #606266;
}

.primary-issue-text {
  font-weight: 600;
}

.primary-issue-text.is-danger {
  color: #f56c6c;
}

.primary-issue-text.is-normal {
  color: #909399;
}

/* 列表样式 */
.qc-items-section {
  background: #fff;
  padding: 24px;
  border-radius: 8px;
  box-shadow: 0 2px 12px 0 rgba(0, 0, 0, 0.05);
}

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

.subtitle {
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
}

.item-header {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-bottom: 6px;
}

.item-name {
  font-size: 16px;
  font-weight: bold;
  color: #303133;
}

.item-desc {
  font-size: 13px;
  color: #606266;
  margin-bottom: 8px;
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
  margin-left: 16px;
  display: flex;
  align-items: center;
}

/* 详情弹窗样式 */
.detail-content {
  padding: 10px;
}

.image-preview-wrapper {
  background: #000;
  height: 400px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 4px;
  overflow: hidden;
}

.image-container {
  position: relative;
  display: inline-block;
  line-height: 0; /* Remove extra space for inline-block */
}

.preview-image {
  max-width: 100%;
  max-height: 400px; /* Limit height to match wrapper */
  width: auto;
  height: auto;
  display: block;
}

.image-placeholder {
  color: #909399;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10px;
}

.bbox-overlay {
  pointer-events: none;
}

.bbox-label {
  position: absolute;
  top: -24px;
  left: -2px;
  background: #F56C6C;
  color: #fff;
  padding: 2px 6px;
  font-size: 12px;
  border-radius: 2px;
  white-space: nowrap;
}

.detail-info {
  height: 100%;
}
</style>
