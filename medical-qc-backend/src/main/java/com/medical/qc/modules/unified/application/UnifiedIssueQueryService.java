package com.medical.qc.modules.unified.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueActionLog;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueCapaRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueTicket;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueActionLogMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueCapaRecordMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueTicketMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultItemMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.shared.JsonObjectMapReader;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.TaskScopedSourceTableSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 统一模型异常工单查询服务。
 */
@Service
public class UnifiedIssueQueryService {
    // 状态与时间格式常量分别服务于摘要统计和前端时间文案。
    private static final String STATUS_RESOLVED = "已解决";
    private static final DateTimeFormatter SUMMARY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter ALERT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    private final UnifiedIssueTicketMapper unifiedIssueTicketMapper;
    private final UnifiedIssueActionLogMapper unifiedIssueActionLogMapper;
    private final UnifiedIssueCapaRecordMapper unifiedIssueCapaRecordMapper;
    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedQcResultItemMapper unifiedQcResultItemMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;
    private final TaskScopedSourceTableSupport taskScopedSourceTableSupport;

    public UnifiedIssueQueryService(UnifiedIssueTicketMapper unifiedIssueTicketMapper,
                                    UnifiedIssueActionLogMapper unifiedIssueActionLogMapper,
                                    UnifiedIssueCapaRecordMapper unifiedIssueCapaRecordMapper,
                                    UnifiedQcTaskMapper unifiedQcTaskMapper,
                                    UnifiedQcResultMapper unifiedQcResultMapper,
                                    UnifiedQcResultItemMapper unifiedQcResultItemMapper,
                                    UnifiedStudyMapper unifiedStudyMapper,
                                    UnifiedPatientMapper unifiedPatientMapper,
                                    UserMapper userMapper,
                                    ObjectMapper objectMapper,
                                    TaskScopedSourceTableSupport taskScopedSourceTableSupport) {
        this.unifiedIssueTicketMapper = unifiedIssueTicketMapper;
        this.unifiedIssueActionLogMapper = unifiedIssueActionLogMapper;
        this.unifiedIssueCapaRecordMapper = unifiedIssueCapaRecordMapper;
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedQcResultItemMapper = unifiedQcResultItemMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
        this.taskScopedSourceTableSupport = taskScopedSourceTableSupport;
    }

    /**
     * 统计待处理工单数量。
     */
    public long countPendingIssues(Long scopedUserId) {
        return loadScopedTickets(scopedUserId).stream().filter(this::isUnresolved).count();
    }

    /**
     * 统计高优先级未解决工单数量。
     */
    public long countHighRiskIssues(Long scopedUserId) {
        return loadScopedTickets(scopedUserId).stream()
                .filter(this::isUnresolved)
                .filter(ticket -> Objects.equals("高", ticket.getPriority()))
                .count();
    }

    /**
     * 获取风险预警列表。
     */
    public List<Map<String, Object>> getRiskAlerts(Long scopedUserId, int limit) {
        List<UnifiedIssueTicket> tickets = loadScopedTickets(scopedUserId);
        Map<Long, UnifiedQcTask> taskMap = loadTaskMap(tickets);
        Map<Long, UnifiedStudy> studyMap = loadStudyMap(taskMap.values());
        Map<Long, UnifiedPatient> patientMap = loadPatientMap(studyMap.values());

        return tickets.stream()
                .filter(this::isUnresolved)
                .sorted(Comparator
                        .comparingInt((UnifiedIssueTicket ticket) -> resolvePriorityRank(ticket.getPriority())).reversed()
                        .thenComparing(UnifiedIssueTicket::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(Math.max(1, Math.min(limit, 10)))
                .map(ticket -> toRiskAlertItem(ticket, taskMap.get(ticket.getTaskId()), studyMap, patientMap))
                .toList();
    }

    /**
     * 获取异常汇总顶部统计卡片。
     */
    public Map<String, Object> getSummaryStats(Long scopedUserId) {
        List<UnifiedIssueTicket> tickets = loadScopedTickets(scopedUserId);
        LocalDate today = LocalDate.now();

        long totalIssues = tickets.size();
        long todayIssues = tickets.stream()
                .filter(item -> item.getCreatedAt() != null && today.equals(item.getCreatedAt().toLocalDate()))
                .count();
        long pendingIssues = tickets.stream().filter(this::isUnresolved).count();
        long resolvedIssues = tickets.stream().filter(item -> STATUS_RESOLVED.equals(item.getStatus())).count();
        double resolutionRate = totalIssues == 0 ? 0D : roundOneDecimal(resolvedIssues * 100.0D / totalIssues);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIssues", totalIssues);
        stats.put("todayIssues", todayIssues);
        stats.put("pendingIssues", pendingIssues);
        stats.put("resolutionRate", resolutionRate);
        stats.put("totalIssuesTrend", 0D);
        stats.put("todayIssuesTrend", 0D);
        stats.put("pendingIssuesTrend", 0D);
        stats.put("resolutionRateTrend", 0D);
        return stats;
    }

    /**
     * 获取异常趋势图数据。
     */
    public Map<String, Object> getIssueTrend(Long scopedUserId, int days) {
        int normalizedDays = Math.max(1, days);
        List<UnifiedIssueTicket> tickets = loadScopedTickets(scopedUserId);
        LocalDate startDate = LocalDate.now().minusDays(normalizedDays - 1L);
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < normalizedDays; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            dates.add(currentDate.format(DateTimeFormatter.ofPattern("MM-dd")));
            int count = (int) tickets.stream()
                    .filter(item -> item.getCreatedAt() != null && currentDate.equals(item.getCreatedAt().toLocalDate()))
                    .count();
            values.add(count);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dates", dates);
        response.put("values", values);
        return response;
    }

    /**
     * 获取异常类型分布。
     */
    public List<Map<String, Object>> getIssueDistribution(Long scopedUserId) {
        return loadScopedTickets(scopedUserId).stream()
                .collect(Collectors.groupingBy(item -> firstNonBlank(item.getIssueName(), "未见明显异常"), Collectors.counting()))
                .entrySet()
                .stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .map(entry -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("name", entry.getKey());
                    item.put("value", entry.getValue());
                    return item;
                })
                .toList();
    }

    /**
     * 获取工单分页列表。
     */
    public Map<String, Object> getIssuePage(Long scopedUserId, int page, int limit, String query, String status) {
        // 先按数据范围加载工单，再在内存中做关键字和状态过滤。
        List<UnifiedIssueTicket> tickets = loadScopedTickets(scopedUserId).stream()
                .filter(ticket -> matchesQuery(ticket, query))
                .filter(ticket -> matchesStatus(ticket, status))
                .toList();
        Map<Long, UnifiedQcTask> taskMap = loadTaskMap(tickets);
        Map<Long, UnifiedStudy> studyMap = loadStudyMap(taskMap.values());
        Map<Long, UnifiedPatient> patientMap = loadPatientMap(studyMap.values());

        int normalizedPage = Math.max(page, 1);
        int normalizedLimit = Math.max(1, Math.min(limit, 100));
        int total = tickets.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedLimit, total);
        int toIndex = Math.min(fromIndex + normalizedLimit, total);

        Map<String, Object> response = new HashMap<>();
        response.put("items", tickets.subList(fromIndex, toIndex).stream()
                .map(ticket -> toSummaryItem(ticket, taskMap.get(ticket.getTaskId()), studyMap, patientMap))
                .toList());
        response.put("total", total);
        response.put("page", normalizedPage);
        response.put("limit", normalizedLimit);
        response.put("pages", total == 0 ? 0 : (long) Math.ceil(total * 1.0D / normalizedLimit));
        return response;
    }

    /**
     * 获取符合筛选条件的全部异常工单摘要，供服务端导出使用。
     */
    public List<Map<String, Object>> getIssueItems(Long scopedUserId, String query, String status) {
        List<UnifiedIssueTicket> tickets = loadScopedTickets(scopedUserId).stream()
                .filter(ticket -> matchesQuery(ticket, query))
                .filter(ticket -> matchesStatus(ticket, status))
                .toList();
        Map<Long, UnifiedQcTask> taskMap = loadTaskMap(tickets);
        Map<Long, UnifiedStudy> studyMap = loadStudyMap(taskMap.values());
        Map<Long, UnifiedPatient> patientMap = loadPatientMap(studyMap.values());
        return tickets.stream()
                .map(ticket -> toSummaryItem(ticket, taskMap.get(ticket.getTaskId()), studyMap, patientMap))
                .toList();
    }

    /**
     * 获取单条工单摘要。
     */
    public Map<String, Object> getIssueSummary(Long scopedUserId, Long issueId) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        UnifiedIssueTicket ticket = unifiedIssueTicketMapper.selectById(issueId);
        if (ticket == null) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        UnifiedQcTask task = ticket.getTaskId() == null ? null : unifiedQcTaskMapper.selectById(ticket.getTaskId());
        if (scopedUserId != null && task != null && task.getSubmittedBy() != null && !Objects.equals(scopedUserId, task.getSubmittedBy())) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        Map<Long, UnifiedStudy> studyMap = loadStudyMap(task == null ? List.of() : List.of(task));
        Map<Long, UnifiedPatient> patientMap = loadPatientMap(studyMap.values());
        return toSummaryItem(ticket, task, studyMap, patientMap);
    }

    /**
     * 获取单条工单详情。
     */
    public Map<String, Object> getIssueDetail(Long scopedUserId, Long issueId) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        UnifiedIssueTicket ticket = unifiedIssueTicketMapper.selectById(issueId);
        if (ticket == null) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        UnifiedQcTask task = ticket.getTaskId() == null ? null : unifiedQcTaskMapper.selectById(ticket.getTaskId());
        if (scopedUserId != null && task != null && task.getSubmittedBy() != null && !Objects.equals(scopedUserId, task.getSubmittedBy())) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        Map<Long, UnifiedStudy> studyMap = loadStudyMap(task == null ? List.of() : List.of(task));
        Map<Long, UnifiedPatient> patientMap = loadPatientMap(studyMap.values());

        Map<String, Object> detail = new HashMap<>(toSummaryItem(ticket, task, studyMap, patientMap));
        detail.put("sourceDetail", buildSourceDetail(ticket, task, studyMap, patientMap));
        detail.put("capa", buildCapaDetail(ticket.getId()));
        detail.put("handleLogs", buildHandleLogs(ticket.getId()));
        return detail;
    }

    /**
     * 根据 scopedUserId 加载当前可见工单。
     */
    private List<UnifiedIssueTicket> loadScopedTickets(Long scopedUserId) {
        List<UnifiedIssueTicket> tickets = unifiedIssueTicketMapper.selectList(new QueryWrapper<UnifiedIssueTicket>()
                .orderByDesc("created_at"));
        if (scopedUserId == null) {
            return tickets;
        }

        // 医生视角仅保留自己提交任务所产生的工单。
        Map<Long, UnifiedQcTask> taskMap = loadTaskMap(tickets);
        return tickets.stream()
                .filter(ticket -> {
                    UnifiedQcTask task = taskMap.get(ticket.getTaskId());
                    return task != null && Objects.equals(scopedUserId, task.getSubmittedBy());
                })
                .toList();
    }

    /**
     * 批量加载任务映射。
     */
    private Map<Long, UnifiedQcTask> loadTaskMap(List<UnifiedIssueTicket> tickets) {
        Set<Long> taskIds = tickets.stream()
                .map(UnifiedIssueTicket::getTaskId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (taskIds.isEmpty()) {
            return Map.of();
        }
        return unifiedQcTaskMapper.selectBatchIds(taskIds).stream()
                .collect(Collectors.toMap(UnifiedQcTask::getId, item -> item));
    }

    /**
     * 批量加载检查映射。
     */
    private Map<Long, UnifiedStudy> loadStudyMap(java.util.Collection<UnifiedQcTask> tasks) {
        Set<Long> studyIds = tasks.stream()
                .map(UnifiedQcTask::getStudyId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (studyIds.isEmpty()) {
            return Map.of();
        }
        return unifiedStudyMapper.selectBatchIds(studyIds).stream()
                .collect(Collectors.toMap(UnifiedStudy::getId, item -> item));
    }

    /**
     * 批量加载患者映射。
     */
    private Map<Long, UnifiedPatient> loadPatientMap(java.util.Collection<UnifiedStudy> studies) {
        Set<Long> patientIds = studies.stream()
                .map(UnifiedStudy::getPatientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (patientIds.isEmpty()) {
            return Map.of();
        }
        return unifiedPatientMapper.selectBatchIds(patientIds).stream()
                .collect(Collectors.toMap(UnifiedPatient::getId, item -> item));
    }

    /**
     * 组装工单摘要项。
     */
    private Map<String, Object> toSummaryItem(UnifiedIssueTicket ticket,
                                              UnifiedQcTask task,
                                              Map<Long, UnifiedStudy> studyMap,
                                              Map<Long, UnifiedPatient> patientMap) {
        UnifiedStudy study = task == null ? null : studyMap.get(task.getStudyId());
        UnifiedPatient patient = study == null ? null : patientMap.get(study.getPatientId());

        Map<String, Object> item = new HashMap<>();
        item.put("id", ticket.getId());
        item.put("patientName", patient == null ? null : patient.getPatientName());
        item.put("examId", study == null ? null : study.getAccessionNumber());
        item.put("type", task == null ? "--" : MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
        item.put("sourceType", task == null ? null : task.getTaskTypeCode());
        item.put("sourceRecordId", task == null ? null : task.getId());
        item.put("sourceCacheTable", task == null ? null : resolveSourceCacheTable(task.getTaskTypeCode(), task.getSourceMode()));
        item.put("issueType", ticket.getIssueName());
        item.put("description", ticket.getDescription());
        item.put("date", formatDateTime(ticket.getCreatedAt()));
        item.put("status", ticket.getStatus());
        item.put("priority", ticket.getPriority());
        item.put("assigneeUserId", ticket.getAssigneeUserId());
        item.put("assigneeName", resolveUserDisplayName(ticket.getAssigneeUserId()));
        item.put("responsibleRole", ticket.getResponsibleRole());
        item.put("responsibleRoleLabel", resolveResponsibleRoleLabel(ticket.getResponsibleRole()));
        item.put("slaHours", ticket.getSlaHours());
        item.put("dueAt", formatDateTime(ticket.getDueAt()));
        item.put("overdue", isUnresolved(ticket) && ticket.getDueAt() != null && ticket.getDueAt().isBefore(LocalDateTime.now()));
        item.put("imageUrl", null);
        item.put("remark", ticket.getLastRemark());
        item.put("resolvedAt", formatDateTime(ticket.getResolvedAt()));
        item.put("timestamp", ticket.getCreatedAt());
        return item;
    }

    /**
     * 组装工单来源详情。
     * 优先使用 rawResultJson；缺失时再根据结果项表拼装兜底结构。
     */
    private Map<String, Object> buildSourceDetail(UnifiedIssueTicket ticket,
                                                  UnifiedQcTask task,
                                                  Map<Long, UnifiedStudy> studyMap,
                                                  Map<Long, UnifiedPatient> patientMap) {
        if (task == null) {
            return null;
        }

        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));
        Map<String, Object> rawResult = MockQualityAnalysisSupport.normalizeResultPayload(
                parseJson(result == null ? null : result.getRawResultJson()));
        if (!rawResult.isEmpty()) {
            Map<String, Object> enrichedRawResult = new HashMap<>(rawResult);
            enrichedRawResult.putIfAbsent("detailType", "qualityTask");
            enrichedRawResult.put("taskId", task.getTaskNo());
            enrichedRawResult.put("taskType", task.getTaskTypeCode());
            enrichedRawResult.put("taskTypeName", MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
            enrichedRawResult.put("sourceMode", task.getSourceMode());
            enrichedRawResult.put("sourceModeLabel", "pacs".equals(task.getSourceMode()) ? "PACS 调取" : "本地上传");
            enrichedRawResult.put("sourceCacheTable", resolveSourceCacheTable(task.getTaskTypeCode(), task.getSourceMode()));
            enrichedRawResult.put("qcStatus", MockQualityAnalysisSupport.resolveQcStatus(enrichedRawResult));
            String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(enrichedRawResult);
            enrichedRawResult.put("primaryIssue", "未见明显异常".equals(primaryIssue) ? null : primaryIssue);
            return enrichedRawResult;
        }

        UnifiedStudy study = studyMap.get(task.getStudyId());
        UnifiedPatient patient = study == null ? null : patientMap.get(study.getPatientId());
        List<UnifiedQcResultItem> resultItems = result == null ? List.of() : unifiedQcResultItemMapper.selectList(
                new QueryWrapper<UnifiedQcResultItem>().eq("result_id", result.getId()).orderByAsc("sort_order"));

        Map<String, Object> sourceDetail = new HashMap<>();
        sourceDetail.put("detailType", "qualityTask");
        sourceDetail.put("taskId", task.getTaskNo());
        sourceDetail.put("taskType", task.getTaskTypeCode());
        sourceDetail.put("taskTypeName", MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
        sourceDetail.put("sourceMode", task.getSourceMode());
        sourceDetail.put("sourceModeLabel", "pacs".equals(task.getSourceMode()) ? "PACS 调取" : "本地上传");
        sourceDetail.put("sourceCacheTable", resolveSourceCacheTable(task.getTaskTypeCode(), task.getSourceMode()));
        sourceDetail.put("patientInfo", buildQualityTaskPatientInfo(task, patient, study));
        sourceDetail.put("summary", buildQualityTaskSummary(task, result, resultItems));
        sourceDetail.put("qcItems", buildQualityTaskQcItems(resultItems));
        sourceDetail.put("qcStatus", result == null ? null : result.getQcStatus());
        sourceDetail.put("primaryIssue", result == null ? null : result.getPrimaryIssueName());
        sourceDetail.put("errorMessage", task.getErrorMessage());
        return sourceDetail;
    }

    /**
     * 组装质控任务详情中的患者与采集信息，避免 Map.of 在空值场景抛出异常。
     */
    private Map<String, Object> buildQualityTaskPatientInfo(UnifiedQcTask task,
                                                            UnifiedPatient patient,
                                                            UnifiedStudy study) {
        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("name", patient == null ? null : patient.getPatientName());
        patientInfo.put("studyId", study == null ? null : study.getAccessionNumber());
        patientInfo.put("gender", patient == null ? null : patient.getGender());
        patientInfo.put("age", patient == null ? null : patient.getAgeText());
        patientInfo.put("studyDate", study == null ? null : study.getStudyDate());
        patientInfo.put("sourceLabel", "pacs".equals(task.getSourceMode()) ? "PACS 调取" : "本地上传");
        patientInfo.put("device", study == null ? null : study.getDeviceModel());
        return patientInfo;
    }

    /**
     * 组装质控任务摘要，补齐复核项和主异常项，供问题工单详情页直接展示。
     */
    private Map<String, Object> buildQualityTaskSummary(UnifiedQcTask task,
                                                        UnifiedQcResult result,
                                                        List<UnifiedQcResultItem> resultItems) {
        long reviewCount = resultItems.stream()
                .filter(item -> "待人工确认".equals(item.getItemStatus()))
                .count();
        long failCount = resultItems.stream()
                .filter(item -> item.getItemStatus() != null
                        && !"合格".equals(item.getItemStatus())
                        && !"待人工确认".equals(item.getItemStatus()))
                .count();
        String primaryIssue = resultItems.stream()
                .filter(item -> item.getItemStatus() != null && !"合格".equals(item.getItemStatus()))
                .map(UnifiedQcResultItem::getItemName)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(result == null ? null : result.getPrimaryIssueName());

        Map<String, Object> summary = new HashMap<>();
        summary.put("qualityScore", result == null || result.getQualityScore() == null
                ? null
                : result.getQualityScore().doubleValue());
        summary.put("abnormalCount", result == null || result.getAbnormalCount() == null ? 0 : result.getAbnormalCount());
        summary.put("reviewCount", reviewCount);
        summary.put("failCount", failCount);
        summary.put("result", result == null ? null : result.getQcStatus());
        summary.put("primaryIssue", primaryIssue);
        return summary;
    }

    /**
     * 组装质控任务原始质控项列表，兼容 detailText 为空的场景。
     */
    private List<Map<String, Object>> buildQualityTaskQcItems(List<UnifiedQcResultItem> resultItems) {
        return resultItems.stream().map(item -> {
            Map<String, Object> qcItem = new HashMap<>();
            qcItem.put("name", item.getItemName());
            qcItem.put("status", item.getItemStatus());
            qcItem.put("detail", item.getDetailText());
            qcItem.put("description", item.getDetailText());
            return qcItem;
        }).toList();
    }

    /**
     * 组装 CAPA 详情。
     */
    private Map<String, Object> buildCapaDetail(Long ticketId) {
        UnifiedIssueCapaRecord capaRecord = unifiedIssueCapaRecordMapper.selectOne(new QueryWrapper<UnifiedIssueCapaRecord>()
                .eq("ticket_id", ticketId)
                .last("LIMIT 1"));
        if (capaRecord == null) {
            return null;
        }

        Map<String, Object> item = new HashMap<>();
        item.put("rootCauseCategory", capaRecord.getRootCauseCategory());
        item.put("rootCauseDetail", capaRecord.getRootCauseDetail());
        item.put("correctiveAction", capaRecord.getCorrectiveAction());
        item.put("preventiveAction", capaRecord.getPreventiveAction());
        item.put("verificationNote", capaRecord.getVerificationNote());
        item.put("updatedBy", capaRecord.getUpdatedBy());
        item.put("updatedByName", resolveUserDisplayName(capaRecord.getUpdatedBy()));
        item.put("createdAt", formatDateTime(capaRecord.getCreatedAt()));
        item.put("updatedAt", formatDateTime(capaRecord.getUpdatedAt()));
        return item;
    }

    /**
     * 组装处理日志时间轴。
     */
    private List<Map<String, Object>> buildHandleLogs(Long ticketId) {
        return unifiedIssueActionLogMapper.selectList(new QueryWrapper<UnifiedIssueActionLog>()
                        .eq("ticket_id", ticketId)
                        .orderByDesc("created_at"))
                .stream()
                .map(log -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("id", log.getId());
                    item.put("actionType", log.getActionType());
                    item.put("actionTypeLabel", resolveActionTypeLabel(log.getActionType()));
                    item.put("beforeStatus", log.getBeforeStatus());
                    item.put("afterStatus", log.getAfterStatus());
                    item.put("remark", log.getRemark());
                    item.put("operatorId", log.getOperatorId());
                    item.put("operatorName", resolveUserDisplayName(log.getOperatorId()));
                    item.put("createdAt", formatDateTime(log.getCreatedAt()));
                    return item;
                })
                .toList();
    }

    private Map<String, Object> toRiskAlertItem(UnifiedIssueTicket ticket,
                                                UnifiedQcTask task,
                                                Map<Long, UnifiedStudy> studyMap,
                                                Map<Long, UnifiedPatient> patientMap) {
        UnifiedStudy study = task == null ? null : studyMap.get(task.getStudyId());
        UnifiedPatient patient = study == null ? null : patientMap.get(study.getPatientId());

        // 风险提示文案需要同时携带患者名、检查号、任务类型和是否超期。
        String patientName = patient == null || !StringUtils.hasText(patient.getPatientName())
                ? "匿名患者"
                : patient.getPatientName().trim();
        String examId = study == null || !StringUtils.hasText(study.getAccessionNumber())
                ? "未知检查号"
                : study.getAccessionNumber().trim();
        String issueType = firstNonBlank(ticket.getIssueName(), "异常质控项");
        String taskLabel = task == null ? "质控任务" : MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode());
        boolean overdue = ticket.getDueAt() != null && ticket.getDueAt().isBefore(LocalDateTime.now());
        String priority = firstNonBlank(ticket.getPriority(), "中");

        Map<String, Object> item = new HashMap<>();
        item.put("title", issueType);
        item.put("patientName", patientName);
        item.put("examId", examId);
        item.put("taskTypeName", taskLabel);
        item.put("priority", priority);
        item.put("overdue", overdue);
        item.put("status", ticket.getStatus());
        item.put("content", patientName + "（" + examId + "）" + taskLabel + "存在“" + issueType + "”");
        item.put("description", overdue ? "已超过 SLA 时限，请优先处理" : "请及时复核并推进工单流转");
        item.put("time", ticket.getCreatedAt() == null ? "--" : ticket.getCreatedAt().format(ALERT_TIME_FORMATTER));
        item.put("targetRoute", "/issues");
        return item;
    }

    /**
     * 工单列表关键字匹配。
     */
    private boolean matchesQuery(UnifiedIssueTicket ticket, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }
        String normalizedQuery = query.trim();
        return contains(ticket.getIssueName(), normalizedQuery)
                || contains(ticket.getDescription(), normalizedQuery)
                || contains(ticket.getTicketNo(), normalizedQuery);
    }

    /**
     * 工单状态匹配。
     */
    private boolean matchesStatus(UnifiedIssueTicket ticket, String status) {
        return !StringUtils.hasText(status) || Objects.equals(ticket.getStatus(), status.trim());
    }

    /**
     * 安全做字符串包含判断。
     */
    private boolean contains(String value, String query) {
        return value != null && value.contains(query);
    }

    /**
     * 判断工单是否尚未解决。
     */
    private boolean isUnresolved(UnifiedIssueTicket ticket) {
        return ticket != null && !STATUS_RESOLVED.equals(ticket.getStatus());
    }

    /**
     * 解析用户展示名。
     * 优先真实姓名，缺失时回退到用户名。
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
     * 解析责任角色中文标签。
     */
    private String resolveResponsibleRoleLabel(String role) {
        if ("admin".equals(role)) {
            return "管理员";
        }
        if ("doctor".equals(role)) {
            return "医生";
        }
        return "--";
    }

    /**
     * 解析动作类型中文标签。
     */
    private String resolveActionTypeLabel(String actionType) {
        if ("create".equals(actionType)) return "创建工单";
        if ("update_status".equals(actionType)) return "状态流转";
        if ("assign".equals(actionType)) return "处理人指派";
        if ("update_capa".equals(actionType)) return "更新 CAPA";
        return actionType;
    }

    /**
     * 安全解析原始结果 JSON。
     */
    private Map<String, Object> parseJson(String rawJson) {
        return JsonObjectMapReader.read(objectMapper, rawJson);
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
     * 将优先级映射为排序权重。
     */
    private int resolvePriorityRank(String priority) {
        if ("高".equals(priority)) {
            return 3;
        }
        if ("中".equals(priority)) {
            return 2;
        }
        if ("低".equals(priority)) {
            return 1;
        }
        return 0;
    }

    /**
     * 统一格式化时间字段。
     */
    private String formatDateTime(LocalDateTime value) {
        return value == null ? "--" : value.format(SUMMARY_TIME_FORMATTER);
    }

    /**
     * 解析任务关联的来源缓存表名。
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
     * 保留一位小数。
     */
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
