package com.medical.qc.modules.unified.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.unified.application.support.UnifiedQualityTaskWriteService;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultAuditLog;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultAuditLogMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultItemMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.shared.JsonObjectMapReader;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.TaskScopedSourceTableSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一模型质控任务查询服务。
 */
@Service
public class UnifiedQcTaskQueryService {
    // 统一格式化任务列表与详情中的时间字段。
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedQcResultAuditLogMapper unifiedQcResultAuditLogMapper;
    private final UnifiedQcResultItemMapper unifiedQcResultItemMapper;
    private final UnifiedQualityTaskWriteService unifiedQualityTaskWriteService;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final TaskScopedSourceTableSupport taskScopedSourceTableSupport;

    public UnifiedQcTaskQueryService(UnifiedQcTaskMapper unifiedQcTaskMapper,
                                     UnifiedQcResultMapper unifiedQcResultMapper,
                                     UnifiedQcResultAuditLogMapper unifiedQcResultAuditLogMapper,
                                     UnifiedQcResultItemMapper unifiedQcResultItemMapper,
                                     UnifiedQualityTaskWriteService unifiedQualityTaskWriteService,
                                     UserMapper userMapper,
                                     ObjectMapper objectMapper,
                                     TaskScopedSourceTableSupport taskScopedSourceTableSupport) {
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedQcResultAuditLogMapper = unifiedQcResultAuditLogMapper;
        this.unifiedQcResultItemMapper = unifiedQcResultItemMapper;
        this.unifiedQualityTaskWriteService = unifiedQualityTaskWriteService;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.taskScopedSourceTableSupport = taskScopedSourceTableSupport;
    }

    /**
     * 分页查询任务列表。
     */
    public Map<String, Object> getTaskPage(Long scopedUserId,
                                           int page,
                                           int limit,
                                           String query,
                                           String taskType,
                                           String status,
                                           String sourceMode) {
        // 先加载满足条件的任务记录，再在内存中按关键字做补充过滤。
        List<QcTaskRecord> taskRecords = loadTaskRecords(scopedUserId, taskType, status, sourceMode).stream()
                .filter(taskRecord -> matchesQuery(taskRecord, query))
                .toList();

        // 统一做分页边界保护，避免前端传入极端值。
        int normalizedPage = Math.max(page, 1);
        int normalizedLimit = Math.max(1, Math.min(limit, 50));
        int total = taskRecords.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedLimit, total);
        int toIndex = Math.min(fromIndex + normalizedLimit, total);

        Map<String, Object> response = new HashMap<>();
        response.put("items", taskRecords.subList(fromIndex, toIndex).stream().map(this::toTaskListItem).toList());
        response.put("total", total);
        response.put("page", normalizedPage);
        response.put("limit", normalizedLimit);
        response.put("pages", total == 0 ? 0 : (long) Math.ceil(total * 1.0D / normalizedLimit));
        response.put("summary", buildSummary(taskRecords));
        return response;
    }

    /**
     * 查询单个任务详情。
     */
    public Map<String, Object> getTaskDetail(String taskNo, Long scopedUserId) {
        UnifiedQcTask task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                .eq("task_no", taskNo)
                .last("LIMIT 1"));
        if (task == null) {
            throw new IllegalArgumentException("质控任务不存在或已过期");
        }
        if (scopedUserId != null && task.getSubmittedBy() != null && !Objects.equals(scopedUserId, task.getSubmittedBy())) {
            throw new IllegalArgumentException("无权访问该质控任务");
        }

        // 统一把新模型任务实体转换成前端仍在消费的旧视图结构。
        QcTaskRecord taskRecord = unifiedQualityTaskWriteService.toLegacyTaskRecord(task);
        if (taskRecord == null) {
            throw new IllegalArgumentException("质控任务不存在或已过期");
        }

        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));

        Map<String, Object> parsedRawResult = parseJson(taskRecord.getResultJson());
        if (parsedRawResult.isEmpty()) {
            // 若原始结果 JSON 缺失，则退化为根据结果项表拼装一个兜底结构。
            parsedRawResult = buildFallbackResult(taskRecord, result == null ? null : result.getId());
        }

        Map<String, Object> response = new HashMap<>(toTaskListItem(taskRecord));
        response.put("originalFilename", taskRecord.getOriginalFilename());
        response.put("storedFilePath", taskRecord.getStoredFilePath());
        response.put("result", parsedRawResult);
        response.put("auditLogs", buildAuditLogs(task.getId()));
        return response;
    }

    /**
     * 获取指定范围内的全部任务记录，供仪表盘等聚合场景复用。
     */
    public List<QcTaskRecord> getTaskRecords(Long scopedUserId) {
        return loadTaskRecords(scopedUserId, null, null, null);
    }

    /**
     * 按范围和结构化筛选条件加载任务记录。
     */
    private List<QcTaskRecord> loadTaskRecords(Long scopedUserId,
                                               String taskType,
                                               String status,
                                               String sourceMode) {
        QueryWrapper<UnifiedQcTask> queryWrapper = new QueryWrapper<>();
        if (scopedUserId != null) {
            queryWrapper.eq("submitted_by", scopedUserId);
        }
        if (StringUtils.hasText(taskType)) {
            queryWrapper.eq("task_type_code", taskType.trim());
        }
        if (StringUtils.hasText(status)) {
            queryWrapper.eq("task_status", status.trim());
        }
        if (StringUtils.hasText(sourceMode)) {
            queryWrapper.eq("source_mode", sourceMode.trim());
        }
        queryWrapper.orderByDesc("requested_at").orderByDesc("created_at");

        return unifiedQcTaskMapper.selectList(queryWrapper).stream()
                .map(unifiedQualityTaskWriteService::toLegacyTaskRecord)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * 组装任务列表单行数据。
     */
    private Map<String, Object> toTaskListItem(QcTaskRecord taskRecord) {
        Map<String, Object> item = new HashMap<>();
        item.put("recordId", taskRecord.getId());
        item.put("taskId", taskRecord.getTaskId());
        item.put("taskType", taskRecord.getTaskType());
        item.put("taskTypeName", firstNonBlank(
                taskRecord.getTaskTypeName(),
                MockQualityAnalysisSupport.resolveTaskTypeName(taskRecord.getTaskType())));
        item.put("patientName", taskRecord.getPatientName());
        item.put("examId", taskRecord.getExamId());
        item.put("sourceMode", taskRecord.getSourceMode());
        item.put("sourceModeLabel", resolveSourceModeLabel(taskRecord.getSourceMode()));
        item.put("sourceCacheTable", resolveSourceCacheTable(taskRecord.getTaskType(), taskRecord.getSourceMode()));
        item.put("status", taskRecord.getTaskStatus());
        item.put("mock", Boolean.TRUE.equals(taskRecord.getMock()));
        item.put("analysisMode", resolveAnalysisMode(taskRecord));
        item.put("analysisLabel", resolveAnalysisLabel(taskRecord));
        item.put("qcStatus", taskRecord.getQcStatus());
        item.put("qualityScore", taskRecord.getQualityScore() == null ? null : taskRecord.getQualityScore().doubleValue());
        item.put("abnormalCount", taskRecord.getAbnormalCount());
        item.put("primaryIssue", taskRecord.getPrimaryIssue());
        item.put("reviewStatus", firstNonBlank(taskRecord.getReviewStatus(), "PENDING"));
        item.put("reviewComment", taskRecord.getReviewComment());
        item.put("reviewedBy", taskRecord.getReviewedBy());
        item.put("reviewedByName", resolveUserDisplayName(taskRecord.getReviewedBy()));
        item.put("reviewedAt", formatDateTime(taskRecord.getReviewedAt()));
        item.put("lockedAt", formatDateTime(taskRecord.getLockedAt()));
        item.put("device", taskRecord.getDevice());
        item.put("externalRef", taskRecord.getExternalRef());
        item.put("submittedAt", formatDateTime(taskRecord.getSubmittedAt()));
        item.put("startedAt", formatDateTime(taskRecord.getStartedAt()));
        item.put("completedAt", formatDateTime(taskRecord.getCompletedAt()));
        item.put("errorMessage", taskRecord.getErrorMessage());
        return item;
    }

    /**
     * 汇总任务中心顶部摘要数据。
     */
    private Map<String, Object> buildSummary(List<QcTaskRecord> taskRecords) {
        long pendingTasks = taskRecords.stream().filter(record -> "PENDING".equals(record.getTaskStatus())).count();
        long processingTasks = taskRecords.stream().filter(record -> "PROCESSING".equals(record.getTaskStatus())).count();
        long successTasks = taskRecords.stream().filter(record -> "SUCCESS".equals(record.getTaskStatus())).count();
        long failedTasks = taskRecords.stream().filter(record -> "FAILED".equals(record.getTaskStatus())).count();
        long abnormalTasks = taskRecords.stream()
                .filter(this::isAbnormalTaskRecord)
                .count();
        long todayTasks = taskRecords.stream()
                .filter(record -> record.getSubmittedAt() != null && LocalDate.now().equals(record.getSubmittedAt().toLocalDate()))
                .count();
        double averageQualityScore = roundOneDecimal(taskRecords.stream()
                .filter(record -> record.getQualityScore() != null)
                .map(QcTaskRecord::getQualityScore)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTasks", (long) taskRecords.size());
        summary.put("pendingTasks", pendingTasks);
        summary.put("processingTasks", processingTasks);
        summary.put("runningTasks", pendingTasks + processingTasks);
        summary.put("successTasks", successTasks);
        summary.put("failedTasks", failedTasks);
        summary.put("abnormalTasks", abnormalTasks);
        summary.put("todayTasks", todayTasks);
        summary.put("averageQualityScore", averageQualityScore);
        return summary;
    }

    /**
     * 当 rawResultJson 缺失时，根据结果项表构造前端可消费的兜底结果。
     */
    private Map<String, Object> buildFallbackResult(QcTaskRecord taskRecord, Long resultId) {
        List<UnifiedQcResultItem> items = resultId == null
                ? List.of()
                : unifiedQcResultItemMapper.selectList(new QueryWrapper<UnifiedQcResultItem>()
                        .eq("result_id", resultId)
                        .orderByAsc("sort_order"));

        List<Map<String, Object>> qcItems = items.stream().map(item -> {
            Map<String, Object> qcItem = new HashMap<>();
            qcItem.put("name", item.getItemName());
            qcItem.put("status", item.getItemStatus());
            qcItem.put("detail", item.getDetailText());
            qcItem.put("description", item.getDetailText());
            return qcItem;
        }).toList();

        long reviewCount = items.stream()
                .filter(item -> "待人工确认".equals(item.getItemStatus()))
                .count();
        long failCount = items.stream()
                .filter(item -> item.getItemStatus() != null
                        && !"合格".equals(item.getItemStatus())
                        && !"待人工确认".equals(item.getItemStatus()))
                .count();
        String primaryIssue = items.stream()
                .filter(item -> item.getItemStatus() != null && !"合格".equals(item.getItemStatus()))
                .map(UnifiedQcResultItem::getItemName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(taskRecord.getPrimaryIssue());

        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("name", taskRecord.getPatientName());
        patientInfo.put("studyId", taskRecord.getExamId());
        patientInfo.put("sourceLabel", resolveSourceModeLabel(taskRecord.getSourceMode()));
        patientInfo.put("sourceCacheTable", resolveSourceCacheTable(taskRecord.getTaskType(), taskRecord.getSourceMode()));
        patientInfo.put("accessionNumber", taskRecord.getExamId());

        Map<String, Object> summary = new HashMap<>();
        summary.put("qualityScore", taskRecord.getQualityScore() == null ? null : taskRecord.getQualityScore().doubleValue());
        summary.put("abnormalCount", taskRecord.getAbnormalCount() == null ? 0 : taskRecord.getAbnormalCount());
        summary.put("reviewCount", reviewCount);
        summary.put("failCount", failCount);
        summary.put("primaryIssue", primaryIssue);
        summary.put("result", taskRecord.getQcStatus());

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("patientInfo", patientInfo);
        fallback.put("summary", summary);
        fallback.put("qcItems", qcItems);
        fallback.put("mock", Boolean.TRUE.equals(taskRecord.getMock()));
        fallback.put("analysisMode", resolveAnalysisMode(taskRecord));
        fallback.put("analysisLabel", resolveAnalysisLabel(taskRecord));
        return fallback;
    }

    /**
     * 安全解析原始结果 JSON。
     */
    private Map<String, Object> parseJson(String rawJson) {
        return JsonObjectMapReader.read(objectMapper, rawJson);
    }

    /**
     * 关键字查询匹配患者姓名、检查号、任务 ID 和主异常项。
     */
    private boolean matchesQuery(QcTaskRecord taskRecord, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String normalizedQuery = query.trim();
        return contains(taskRecord.getPatientName(), normalizedQuery)
                || contains(taskRecord.getExamId(), normalizedQuery)
                || contains(taskRecord.getTaskId(), normalizedQuery)
                || contains(taskRecord.getPrimaryIssue(), normalizedQuery);
    }

    /**
     * 安全做字符串包含判断。
     */
    private boolean contains(Object value, String query) {
        return value != null && String.valueOf(value).contains(query);
    }

    /**
     * 判断任务是否属于成功但异常的记录。
     */
    private boolean isAbnormalTaskRecord(QcTaskRecord taskRecord) {
        return taskRecord != null
                && "SUCCESS".equals(taskRecord.getTaskStatus())
                && ("不合格".equals(taskRecord.getQcStatus())
                || (taskRecord.getAbnormalCount() != null && taskRecord.getAbnormalCount() > 0));
    }

    /**
     * 把来源模式编码转换为中文展示文案。
     */
    private String resolveSourceModeLabel(String sourceMode) {
        return "pacs".equals(sourceMode) ? "PACS 调取" : "本地上传";
    }

    /**
     * 解析任务分析模式，供任务中心列表与详情显式区分 real/mock。
     */
    private String resolveAnalysisMode(QcTaskRecord taskRecord) {
        if (taskRecord == null) {
            return null;
        }
        if (Boolean.TRUE.equals(taskRecord.getMock())) {
            return "mock-rule-based";
        }
        if (MockQualityAnalysisSupport.TASK_TYPE_HEAD.equals(taskRecord.getTaskType())
                || MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST.equals(taskRecord.getTaskType())) {
            return "real-model";
        }
        return null;
    }

    /**
     * 解析任务分析模式中文标签。
     */
    private String resolveAnalysisLabel(QcTaskRecord taskRecord) {
        String analysisMode = resolveAnalysisMode(taskRecord);
        if ("mock-rule-based".equals(analysisMode)) {
            return "模拟分析（规则辅助）";
        }
        if ("real-model".equals(analysisMode)) {
            return "真实模型推理";
        }
        return null;
    }

    /**
     * 组装任务审计日志列表。
     */
    private List<Map<String, Object>> buildAuditLogs(Long taskId) {
        if (taskId == null) {
            return List.of();
        }
        return unifiedQcResultAuditLogMapper.selectList(new QueryWrapper<UnifiedQcResultAuditLog>()
                        .eq("task_id", taskId)
                        .orderByDesc("created_at"))
                .stream()
                .map(log -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", log.getId());
                    item.put("actionType", log.getActionType());
                    item.put("comment", log.getActionComment());
                    item.put("operatorId", log.getOperatorId());
                    item.put("operatorName", resolveUserDisplayName(log.getOperatorId()));
                    item.put("createdAt", formatDateTime(log.getCreatedAt()));
                    item.put("payload", parseJson(log.getPayloadJson()));
                    return item;
                })
                .toList();
    }

    /**
     * 解析任务对应的来源缓存表。
     */
    private String resolveSourceCacheTable(String taskType, String sourceMode) {
        if (!StringUtils.hasText(taskType)) {
            return null;
        }
        return "pacs".equals(sourceMode)
                ? taskScopedSourceTableSupport.resolvePacsTableLabel(taskType)
                : taskScopedSourceTableSupport.resolvePatientInfoTableLabel(taskType);
    }

    /**
     * 统一格式化时间字段。
     */
    private String formatDateTime(LocalDateTime value) {
        return value == null ? "--" : value.format(DATE_TIME_FORMATTER);
    }

    /**
     * 从多个候选文本中返回第一个非空值。
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    /**
     * 解析用户展示名。
     */
    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "--";
        }
        User user = userMapper.selectById(userId);
        if (user == null) {
            return "--";
        }
        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : "--";
    }

    /**
     * 保留一位小数，供列表摘要展示。
     */
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
