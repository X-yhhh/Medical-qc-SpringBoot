<!--
  @file HeadQualityPage.vue
  @description CT头部平扫质控主页面
  主要功能:
  1. 影像上传: 支持本地影像上传和 PACS 调取。
  2. 异步分析: 提交后端质控任务，前端自动轮询任务状态并展示进度。
  3. 结果展示: 患者信息、质控评分、质控项列表。
  4. 交互: 重新分析、导出报告、查看详情。

  @backend-api 对接接口:
  - [POST] /api/v1/quality/head/detect - 提交头部质控异步任务
  - [GET] /api/v1/quality/tasks/{taskId} - 轮询任务结果
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
          <el-tag v-if="qcItems.length > 0" :type="pageStatusType" effect="plain" class="status-tag"
            >{{ pageStatusLabel }}</el-tag
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
    <div v-if="taskError && !analyzing" class="task-error-section">
      <el-result
        icon="error"
        title="头部平扫真实模型推理失败"
        :sub-title="taskError"
      >
        <template #extra>
          <el-button type="primary" @click="handleReanalyze">重新分析</el-button>
          <el-button @click="resetUpload">重新上传</el-button>
        </template>
      </el-result>
    </div>
    <div v-else-if="qcItems.length === 0" class="upload-section">
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
                  <p>支持 DICOM / NIfTI / ZIP 影像上传</p>
                  <p class="sub-tip">支持 .dcm / .nii / .nii.gz / .zip 头部平扫数据</p>
                </div>
              </el-col>

              <!-- PACS 入口卡片: 模拟连接医院 PACS 系统 -->
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
                  >Accession No: {{ patientInfo.accessionNumber || '--' }}</el-tag
                >
              </div>
            </template>
            <el-descriptions :column="3" border>
              <el-descriptions-item label="姓名">{{ patientInfo.name || '--' }}</el-descriptions-item>
              <el-descriptions-item label="患者编号">{{ patientInfo.patientId || '--' }}</el-descriptions-item>
              <el-descriptions-item label="性别">{{ patientInfo.gender || '--' }}</el-descriptions-item>
              <el-descriptions-item label="年龄">{{ formatPatientAge(patientInfo.age) }}</el-descriptions-item>
              <el-descriptions-item label="检查ID">{{ patientInfo.studyId || '--' }}</el-descriptions-item>
              <el-descriptions-item label="检查日期">{{
                patientInfo.studyDate || '--'
              }}</el-descriptions-item>
              <el-descriptions-item label="设备型号">{{ patientInfo.device || '--' }}</el-descriptions-item>
              <el-descriptions-item label="扫描部位">Head Routine</el-descriptions-item>
              <el-descriptions-item label="图像层数"
                >{{ patientInfo.sliceCount || '--' }} 层</el-descriptions-item
              >
              <el-descriptions-item label="层厚"
                >{{ patientInfo.sliceThickness || '--' }} mm</el-descriptions-item
              >
              <el-descriptions-item label="分析模式">{{ analysisLabel || '真实模型推理' }}</el-descriptions-item>
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
                  <el-tag :type="abnormalCount === 0 ? (qualityScore >= 80 ? 'success' : 'warning') : 'danger'" effect="dark">
                    {{ abnormalCount > 0 ? '需复核/异常' : qualityScore >= 80 ? '合格' : '待人工确认' }}
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
          :class="{ 'is-error': item.status !== '合格', 'is-success': item.status === '合格' }"
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
                :type="item.status === '合格' ? 'success' : item.status === '待人工确认' ? 'warning' : 'danger'"
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
            <div class="item-detail-text" v-if="item.status !== '合格'">
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

    <!-- 详情弹窗，优先展示真实模型链路返回的说明信息。 -->
    <el-dialog v-model="dialogVisible" :title="currentItem?.name + ' - 详情分析'" width="50%">
      <div v-if="currentItem" class="dialog-content">
        <el-descriptions border :column="1">
          <el-descriptions-item label="检测项">{{ currentItem.name }}</el-descriptions-item>
          <el-descriptions-item label="当前状态">
            <el-tag :type="currentItem.status === '合格' ? 'success' : currentItem.status === '待人工确认' ? 'warning' : 'danger'">{{
              currentItem.status
            }}</el-tag>
          </el-descriptions-item>
          <el-descriptions-item label="判定标准">{{
            currentItem.description
          }}</el-descriptions-item>
          <el-descriptions-item label="详细日志">
            {{
              currentItem.status === '合格'
                ? '真实模型推理未检出异常特征。'
                : currentItem.detail + '，建议结合原始影像、扫描参数与患者信息人工复核。'
            }}
          </el-descriptions-item>
        </el-descriptions>

        <div class="mock-image-placeholder">
          <el-empty description="当前版本暂未输出关键切片快照，后续将在此挂接真实模型定位结果。" :image-size="120"></el-empty>
        </div>
      </div>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">关闭</el-button>
          <el-button type="primary" @click="dialogVisible = false">确认</el-button>
        </span>
      </template>
    </el-dialog>

    <!--
      选择上传方式弹窗
      功能: 在结果页继续创建新案例时，允许用户再次选择本地上传或 PACS 调取。
    -->
    <el-dialog
      v-model="uploadMethodDialogVisible"
      title="选择上传方式"
      width="600px"
      :close-on-click-modal="false"
      destroy-on-close
    >
      <el-row :gutter="20" justify="center">
        <el-col :span="11">
          <div class="choice-card local-upload" @click="uploadMethodDialogVisible = false; openUploadDialog('local')">
            <div class="icon-wrapper">
              <el-icon><FolderOpened /></el-icon>
            </div>
            <h3>本地影像上传</h3>
            <p>继续创建本地上传质控案例</p>
          </div>
        </el-col>
        <el-col :span="11">
          <div class="choice-card pacs-select" @click="uploadMethodDialogVisible = false; openPacsSearch()">
            <div class="icon-wrapper">
              <el-icon><Connection /></el-icon>
            </div>
            <h3>PACS 系统调取</h3>
            <p>继续创建 PACS 调取质控案例</p>
          </div>
        </el-col>
      </el-row>
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
        <el-form-item label="患者编号" prop="patientId">
          <el-input v-model="uploadForm.patientId" placeholder="请输入患者编号（选填）" />
        </el-form-item>
        <el-form-item label="检查 ID" prop="examId">
          <el-input v-model="uploadForm.examId" placeholder="请输入检查/住院号" />
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
            :on-remove="handleDialogFileRemove"
            style="width: 100%"
            accept=".dcm,.dicom,.nii,.nii.gz,.zip"
          >
            <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
            <div class="el-upload__text">拖拽影像文件或 <em>点击上传</em></div>
            <template #tip>
              <div class="el-upload__tip">支持 .dcm / .dicom / .nii / .nii.gz / .zip 文件，单次最大 500MB</div>
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

    <PacsSearchDialog
      v-model:visible="pacsSearchDialogVisible"
      task-type="head"
      @select="handlePacsSelect"
    />
  </div>
</template>

<script setup>
import { computed } from 'vue'
import {
  User,
  Upload,
  Refresh,
  Download,
  FolderOpened,
  Connection,
  InfoFilled,
  Aim,
  Picture,
  UploadFilled,
  List,
  CircleCheckFilled,
  WarningFilled,
  ArrowRight,
  ArrowLeft,
} from '@element-plus/icons-vue'
import { detectHead } from '@/modules/qctask/api/qualityApi'
import { useAsyncQualityTaskPage } from '@/composables/useAsyncQualityTaskPage'
import PacsSearchDialog from '@/components/PacsSearchDialog.vue'

// 上传弹窗校验规则，对齐后端 head 质控任务的最小输入要求。
const uploadRules = {
  patientName: [{ required: true, message: '请输入患者姓名', trigger: 'blur' }],
  examId: [{ required: true, message: '请输入检查ID', trigger: 'blur' }],
  gender: [{ required: true, message: '请选择性别', trigger: 'change' }],
  age: [{ required: true, message: '请输入年龄', trigger: 'change' }],
  studyDate: [{ required: true, message: '请选择检查日期', trigger: 'change' }],
}

// 当前页面的大部分上传、轮询和结果回填逻辑都复用通用异步质控组合函数。
const {
  analyzing,
  analyzeProgress,
  currentAnalysisStep,
  analysisLogs,
  dialogVisible,
  currentItem,
  uploadMethodDialogVisible,
  uploadDialogVisible,
  uploadMode,
  uploadFormRef,
  uploadForm,
  selectedFile,
  qcItems,
  patientInfo,
  taskError,
  analysisLabel,
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
  handleExport,
} = useAsyncQualityTaskPage({
  // 指定本页使用的任务提交接口。
  submitTask: detectHead,
  // 头部平扫页面的默认患者信息结构。
  initialPatientInfo: {
    patientId: '',
    name: '',
    gender: '',
    age: null,
    studyId: '',
    accessionNumber: '',
    studyDate: '',
    device: '',
    sliceCount: 0,
    sliceThickness: 0,
  },
  // 上传表单与后端 multipart 字段保持一致，确保真实链路提交时患者字段完整。
  initialUploadForm: {
    patientId: '',
    patientName: '',
    examId: '',
    gender: '',
    age: null,
    studyDate: '',
  },
  // PACS 演示模式下预填的示例检查号。
  // 头部平扫任务专属的前端进度步骤文案。
  analysisSteps: (form) => [
    { progress: 10, msg: '正在读取影像序列头信息...', step: '影像解析' },
    { progress: 30, msg: '校验三维体数据尺寸与 spacing / DICOM 元数据...', step: '完整性校验' },
    { progress: 45, msg: `提取患者元数据: ${form.patientName || '未知'}, ...`, step: '元数据提取', prefillPatientInfo: true },
    { progress: 60, msg: 'AI 模型加载中 (Head_CT_QC_v2.1)...', step: '模型加载' },
    { progress: 80, msg: '正在检测运动伪影与金属伪影...', step: '特征提取' },
    { progress: 95, msg: '生成结构化质控报告...', step: '报告生成' },
  ],
  pacsLoadingMessage: '已连接 PACS 系统，正在检索今日检查列表...',
  pacsReadyMessage: '已自动获取 PACS 影像信息，请确认',
  reanalyzeStartMessage: '正在请求云端 AI 重新分析...',
  reanalyzeSuccessMessage: '分析完成，数据已更新',
  exportMessage: '质控报告已生成并开始下载',
})

// 异常项数量直接由质控项列表中“不合格”项计数得出。
const abnormalCount = computed(() => qcItems.value.filter((item) => item.status !== '合格').length)
const failCount = computed(() => qcItems.value.filter((item) => item.status === '不合格').length)
const reviewCount = computed(() => qcItems.value.filter((item) => item.status === '待人工确认').length)

// 页面总评分按“合格项占比”计算，作为展示层简化分值。
const qualityScore = computed(() => {
  if (qcItems.value.length === 0) return 0
  const passed = qcItems.value.filter((item) => item.status === '合格').length
  return Math.round((passed / qcItems.value.length) * 100)
})

const pageStatusLabel = computed(() => {
  if (!qcItems.value.length) return '等待上传影像'
  if (failCount.value > 0) return '存在异常项'
  if (reviewCount.value > 0) return '待人工确认'
  return '质控合格'
})

const pageStatusType = computed(() => {
  if (failCount.value > 0) return 'danger'
  if (reviewCount.value > 0) return 'warning'
  return 'success'
})

// 仪表盘颜色随评分档位变化。
const scoreColor = computed(() => {
  if (qualityScore.value >= 90) return '#67C23A'
  if (qualityScore.value >= 60) return '#E6A23C'
  return '#F56C6C'
})

// 年龄为空时统一回退为占位文本，避免结果页出现“null岁”。
const formatPatientAge = (age) => (age === null || age === undefined || age === '' ? '--' : `${age}岁`)
</script>

<style scoped>
.head-qc-container {
  padding: 24px;
  background-color: #f5f7fa;
  min-height: calc(100vh - 84px); /* 减去顶部导航栏高度 */
}

.task-error-section {
  margin-bottom: 24px;
  padding: 12px;
  border-radius: 20px;
  background: linear-gradient(180deg, #fff6f6 0%, #ffffff 100%);
  border: 1px solid #fbd2d5;
  box-shadow: 0 14px 32px rgba(245, 108, 108, 0.12);
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

/* 页面标题与状态标签。 */
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

/* 分析中的居中布局。 */
.analyzing-container {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
}

/* 上传入口默认态。 */
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

/* 扫描动画外框。 */
/* 分析动画样式 */
.analyzing-container {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 20px;
}

/* 扫描动画圆环。 */
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

/* 2. 结果展示总览区。 */
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

/* 质控项列表与标题区。 */
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


