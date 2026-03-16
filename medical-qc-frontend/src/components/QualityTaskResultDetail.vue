<template>
  <div class="quality-task-result-detail">
    <div v-if="showSummaryPanel" class="section-block">
      <h4 class="section-title">结果摘要</h4>
      <div class="summary-panel">
        <div class="summary-panel__status" :class="resolveStatusClass(normalizedSummary.overallStatus)">
          <div class="summary-panel__label">综合结论</div>
          <div class="summary-panel__status-main">
            <span class="summary-panel__status-text">{{ normalizedSummary.overallStatus }}</span>
            <el-tag :type="resolveStatusTagType(normalizedSummary.overallStatus)" effect="light">
              {{ normalizedSummary.primaryIssue }}
            </el-tag>
          </div>
        </div>
        <div class="summary-metrics">
          <div class="summary-metric-card">
            <span class="summary-metric-card__label">质控评分</span>
            <span class="summary-metric-card__value">{{ formatScore(normalizedSummary.qualityScore) }}</span>
          </div>
          <div class="summary-metric-card">
            <span class="summary-metric-card__label">非通过项</span>
            <span class="summary-metric-card__value">{{ normalizedSummary.abnormalCount }}</span>
          </div>
          <div class="summary-metric-card">
            <span class="summary-metric-card__label">待人工确认</span>
            <span class="summary-metric-card__value">{{ normalizedSummary.reviewCount }}</span>
          </div>
          <div class="summary-metric-card">
            <span class="summary-metric-card__label">不合格项</span>
            <span class="summary-metric-card__value">{{ normalizedSummary.failCount }}</span>
          </div>
        </div>
      </div>
    </div>

    <div v-if="normalizedSourceFields.length" class="section-block">
      <h4 class="section-title">{{ sourceTitle }}</h4>
      <el-descriptions :column="sourceColumns" border class="info-descriptions">
        <el-descriptions-item
          v-for="field in normalizedSourceFields"
          :key="field.key"
          :label="field.label"
          :span="field.span || 1"
        >
          <span :class="{ 'path-text': field.pathLike }">{{ field.display }}</span>
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="hasPatientInfo" class="section-block">
      <h4 class="section-title">{{ patientTitle }}</h4>
      <el-descriptions :column="patientColumns" border class="info-descriptions">
        <el-descriptions-item label="姓名">{{ patientInfo.name || '--' }}</el-descriptions-item>
        <el-descriptions-item label="性别">{{ patientInfo.gender || '--' }}</el-descriptions-item>
        <el-descriptions-item label="年龄">{{ patientInfo.age ?? '--' }}</el-descriptions-item>
        <el-descriptions-item label="检查ID">{{ patientInfo.studyId || '--' }}</el-descriptions-item>
        <el-descriptions-item label="Accession No">{{ patientInfo.accessionNumber || '--' }}</el-descriptions-item>
        <el-descriptions-item label="检查日期">{{ patientInfo.studyDate || '--' }}</el-descriptions-item>
      </el-descriptions>

      <el-descriptions v-if="extraPatientInfoItems.length" :column="patientColumns" border class="info-descriptions">
        <el-descriptions-item
          v-for="item in extraPatientInfoItems"
          :key="item.key"
          :label="item.label"
        >
          {{ item.display }}
        </el-descriptions-item>
      </el-descriptions>
    </div>

    <div v-if="normalizedQcItems.length" class="section-block">
      <h4 class="section-title">{{ qcTitle }}</h4>
      <div class="qc-item-list">
        <div
          v-for="(item, index) in normalizedQcItems"
          :key="`${item.name}-${index}`"
          :class="['qc-item', resolveStatusClass(item.status)]"
        >
          <div class="qc-item__header">
            <span class="qc-item__name">{{ item.name }}</span>
            <el-tag :type="resolveStatusTagType(item.status)" effect="light">{{ item.status }}</el-tag>
          </div>
          <div class="qc-item__desc">{{ item.description || '--' }}</div>
          <div v-if="item.detail" class="qc-item__detail">{{ item.detail }}</div>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const EXTRA_PATIENT_INFO_FIELDS = [
  { key: 'sourceLabel', label: '来源' },
  { key: 'device', label: '设备' },
  { key: 'sliceCount', label: '图像层数', formatter: (value) => `${value} 层` },
  { key: 'sliceThickness', label: '层厚', formatter: (value) => `${value} mm` },
  { key: 'pixelSpacing', label: '像素间距', formatter: formatPixelSpacing },
  { key: 'heartRate', label: '平均心率', formatter: (value) => `${value} bpm` },
  { key: 'hrVariability', label: '心率波动', formatter: (value) => `${value} bpm` },
  { key: 'reconPhase', label: '重建相位' },
  { key: 'kVp', label: '管电压' },
  { key: 'flowRate', label: '造影剂流速', formatter: (value) => `${value} mL/s` },
  { key: 'contrastVolume', label: '造影剂总量', formatter: (value) => `${value} mL` },
  { key: 'injectionSite', label: '注射部位' },
  { key: 'bolusTrackingHu', label: '追踪阈值', formatter: (value) => `${value} HU` },
  { key: 'scanDelaySec', label: '扫描延迟', formatter: (value) => `${value} s` },
  { key: 'originalFilename', label: '原始文件' },
]

const props = defineProps({
  sourceTitle: {
    type: String,
    default: '来源信息',
  },
  patientTitle: {
    type: String,
    default: '患者与采集信息',
  },
  qcTitle: {
    type: String,
    default: '质控项详情',
  },
  sourceColumns: {
    type: Number,
    default: 2,
  },
  patientColumns: {
    type: Number,
    default: 3,
  },
  sourceFields: {
    type: Array,
    default: () => [],
  },
  patientInfo: {
    type: Object,
    default: () => ({}),
  },
  summary: {
    type: Object,
    default: () => ({}),
  },
  overallStatus: {
    type: String,
    default: '',
  },
  primaryIssue: {
    type: String,
    default: '',
  },
  qcItems: {
    type: Array,
    default: () => [],
  },
})

const normalizedSourceFields = computed(() => {
  return (Array.isArray(props.sourceFields) ? props.sourceFields : []).map((field, index) => ({
    key: field?.key || `${field?.label || 'field'}-${index}`,
    label: field?.label || '--',
    display: normalizeDisplay(field?.value),
    span: field?.span,
    pathLike: Boolean(field?.pathLike),
  }))
})

const hasPatientInfo = computed(() => {
  return props.patientInfo && Object.keys(props.patientInfo).length > 0
})

const normalizedQcItems = computed(() => {
  return Array.isArray(props.qcItems) ? props.qcItems : []
})

const normalizedSummary = computed(() => {
  const qcItems = normalizedQcItems.value
  const reviewCount = resolveNumericValue(
    props.summary?.reviewCount,
    qcItems.filter((item) => item?.status === '待人工确认').length,
  )
  const failCount = resolveNumericValue(
    props.summary?.failCount,
    qcItems.filter((item) => item?.status && !['合格', '待人工确认'].includes(item.status)).length,
  )
  const abnormalCount = resolveNumericValue(props.summary?.abnormalCount, reviewCount + failCount)
  const passCount = resolveNumericValue(props.summary?.passCount, Math.max(qcItems.length - abnormalCount, 0))
  const qualityScore = props.summary?.qualityScore
  const primaryIssue = normalizeDisplay(
    props.primaryIssue || props.summary?.primaryIssue,
    null,
    resolvePrimaryIssue(qcItems),
  )
  const overallStatus = resolveOverallStatus(props.overallStatus || props.summary?.result, failCount, reviewCount, qcItems.length > 0)

  return {
    qualityScore,
    passCount,
    abnormalCount,
    reviewCount,
    failCount,
    primaryIssue,
    overallStatus,
  }
})

const showSummaryPanel = computed(() => {
  return normalizedQcItems.value.length > 0
    || Object.keys(props.summary || {}).length > 0
    || hasSummaryValue(props.overallStatus)
    || hasSummaryValue(props.primaryIssue)
})

const extraPatientInfoItems = computed(() => {
  return EXTRA_PATIENT_INFO_FIELDS.map((field) => {
    const rawValue = props.patientInfo?.[field.key]
    return {
      key: field.key,
      label: field.label,
      display: normalizeDisplay(rawValue, field.formatter),
    }
  }).filter((item) => item.display !== '--')
})

function normalizeDisplay(value, formatter, fallback = '--') {
  if (value === null || value === undefined || value === '') {
    return fallback
  }
  return formatter ? formatter(value) : String(value)
}

function formatPixelSpacing(value) {
  if (!Array.isArray(value) || value.length === 0) {
    return '--'
  }
  return `${value.join(' x ')} mm`
}

function resolveStatusTagType(status) {
  if (status === '合格') {
    return 'success'
  }
  if (status === '待人工确认') {
    return 'warning'
  }
  return 'danger'
}

function resolveStatusClass(status) {
  if (status === '合格') {
    return 'is-success'
  }
  if (status === '待人工确认') {
    return 'is-warning'
  }
  return 'is-error'
}

function resolveNumericValue(value, fallback) {
  const numericValue = Number(value)
  return Number.isFinite(numericValue) ? Math.max(numericValue, 0) : fallback
}

function resolvePrimaryIssue(qcItems) {
  if (!Array.isArray(qcItems) || qcItems.length === 0) {
    return '结果不完整'
  }
  const firstAbnormalItem = qcItems.find((item) => item?.status && item.status !== '合格')
  return firstAbnormalItem?.name || '未见明显异常'
}

function resolveOverallStatus(explicitStatus, failCount, reviewCount, hasQcItems) {
  if (explicitStatus) {
    return explicitStatus
  }
  if (failCount > 0) {
    return '不合格'
  }
  if (reviewCount > 0) {
    return '待人工确认'
  }
  if (!hasQcItems) {
    return '结果不完整'
  }
  return '合格'
}

function formatScore(score) {
  const numericScore = Number(score)
  if (!Number.isFinite(numericScore) || numericScore < 0) {
    return '--'
  }
  return numericScore % 1 === 0 ? String(numericScore) : numericScore.toFixed(1)
}

function hasSummaryValue(value) {
  return value !== null && value !== undefined && value !== ''
}
</script>

<style scoped>
.section-block {
  margin-bottom: 22px;
}

.section-title {
  margin: 0 0 12px;
  padding-left: 10px;
  border-left: 4px solid #409eff;
  font-size: 15px;
  font-weight: 600;
  color: #303133;
}

.info-descriptions {
  margin-bottom: 20px;
}

.summary-panel {
  display: grid;
  grid-template-columns: minmax(220px, 1fr) 2fr;
  gap: 14px;
}

.summary-panel__status {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 12px;
  min-height: 124px;
  padding: 18px 20px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: linear-gradient(135deg, rgba(248, 250, 252, 0.96), rgba(255, 255, 255, 0.98));
}

.summary-panel__status.is-success {
  border-color: rgba(103, 194, 58, 0.22);
  background: linear-gradient(135deg, rgba(240, 253, 244, 0.95), rgba(255, 255, 255, 0.98));
}

.summary-panel__status.is-warning {
  border-color: rgba(230, 162, 60, 0.26);
  background: linear-gradient(135deg, rgba(255, 247, 237, 0.94), rgba(255, 255, 255, 0.98));
}

.summary-panel__status.is-error {
  border-color: rgba(245, 108, 108, 0.24);
  background: linear-gradient(135deg, rgba(254, 242, 242, 0.95), rgba(255, 255, 255, 0.98));
}

.summary-panel__label {
  font-size: 13px;
  color: #6b7280;
}

.summary-panel__status-main {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.summary-panel__status-text {
  font-size: 24px;
  font-weight: 700;
  color: #111827;
  letter-spacing: 0.5px;
}

.summary-metrics {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.summary-metric-card {
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 8px;
  min-height: 124px;
  padding: 16px 18px;
  border-radius: 14px;
  border: 1px solid rgba(15, 23, 42, 0.08);
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.summary-metric-card__label {
  font-size: 13px;
  color: #6b7280;
}

.summary-metric-card__value {
  font-size: 26px;
  font-weight: 700;
  color: #111827;
  line-height: 1;
}

.path-text {
  display: inline-block;
  max-width: 100%;
  word-break: break-all;
  line-height: 1.6;
}

.qc-item-list {
  display: grid;
  gap: 12px;
}

.qc-item {
  padding: 14px 16px;
  border-radius: 10px;
  border: 1px solid #e5e7eb;
  background: #fff;
}

.qc-item.is-error {
  border-color: rgba(245, 108, 108, 0.24);
  background: rgba(254, 242, 242, 0.66);
}

.qc-item.is-warning {
  border-color: rgba(230, 162, 60, 0.28);
  background: rgba(255, 247, 237, 0.78);
}

.qc-item.is-success {
  border-color: rgba(103, 194, 58, 0.22);
  background: rgba(240, 253, 244, 0.72);
}

.qc-item__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
}

.qc-item__name {
  font-size: 15px;
  font-weight: 600;
  color: #1f2937;
}

.qc-item__desc {
  margin-top: 8px;
  color: #4b5563;
  line-height: 1.6;
}

.qc-item__detail {
  margin-top: 8px;
  color: #b42318;
  line-height: 1.6;
}

@media (max-width: 960px) {
  .summary-panel {
    grid-template-columns: 1fr;
  }

  .summary-metrics {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 640px) {
  .summary-metrics {
    grid-template-columns: 1fr;
  }
}
</style>
