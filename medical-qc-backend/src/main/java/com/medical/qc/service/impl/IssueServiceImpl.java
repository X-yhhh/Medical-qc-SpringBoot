package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.bean.IssueWorkflowUpdateReq;
import com.medical.qc.common.AuthRole;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.QcIssueCapaRecord;
import com.medical.qc.entity.QcIssueHandleLog;
import com.medical.qc.entity.QcIssueRecord;
import com.medical.qc.entity.QcRuleConfig;
import com.medical.qc.entity.QcTaskRecord;
import com.medical.qc.entity.User;
import com.medical.qc.mapper.HemorrhageRecordMapper;
import com.medical.qc.mapper.QcIssueCapaRecordMapper;
import com.medical.qc.mapper.QcIssueHandleLogMapper;
import com.medical.qc.mapper.QcIssueRecordMapper;
import com.medical.qc.mapper.QcTaskRecordMapper;
import com.medical.qc.mapper.UserMapper;
import com.medical.qc.service.IssueService;
import com.medical.qc.service.QcRuleConfigService;
import com.medical.qc.service.QualityPatientInfoService;
import com.medical.qc.support.HemorrhageIssueSupport;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 异常工单服务实现。
 *
 * <p>当前先同步脑出血检测模块的异常记录；为兼容历史数据，查询前会按用户执行一次增量回填。</p>
 */
@Service
public class IssueServiceImpl implements IssueService {
    private static final String SOURCE_TYPE_HEMORRHAGE = "hemorrhage";
    private static final String STATUS_PENDING = "待处理";
    private static final String STATUS_PROCESSING = "处理中";
    private static final String STATUS_RESOLVED = "已解决";
    private static final DateTimeFormatter SUMMARY_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter RISK_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Autowired
    private QcIssueRecordMapper qcIssueRecordMapper;

    @Autowired
    private QcIssueHandleLogMapper qcIssueHandleLogMapper;

    @Autowired
    private QcIssueCapaRecordMapper qcIssueCapaRecordMapper;

    @Autowired
    private HemorrhageRecordMapper hemorrhageRecordMapper;

    @Autowired
    private QcTaskRecordMapper qcTaskRecordMapper;

    @Autowired
    private QualityPatientInfoService qualityPatientInfoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private QcRuleConfigService qcRuleConfigService;

    @Autowired
    private UserMapper userMapper;

    @Override
    public void syncHemorrhageIssue(HemorrhageRecord record) {
        if (!isAbnormalRecord(record) || record.getUserId() == null || record.getId() == null) {
            return;
        }

        upsertHemorrhageIssue(record);
    }

    @Override
    public void syncQualityTaskIssue(QcTaskRecord taskRecord) {
        if (!isAbnormalQualityTask(taskRecord) || taskRecord.getUserId() == null || taskRecord.getId() == null) {
            return;
        }

        upsertQualityTaskIssue(taskRecord);
    }

    @Override
    public void syncHemorrhageIssues(Long userId) {
        List<HemorrhageRecord> records = userId == null
                ? hemorrhageRecordMapper.selectList(new QueryWrapper<HemorrhageRecord>().orderByDesc("created_at"))
                : hemorrhageRecordMapper.findByUserId(userId);
        records.stream()
                .filter(this::isAbnormalRecord)
                .forEach(this::upsertHemorrhageIssue);
    }

    @Override
    public void syncQualityTaskIssues(Long userId) {
        QueryWrapper<QcTaskRecord> queryWrapper = new QueryWrapper<QcTaskRecord>()
                .eq("task_status", "SUCCESS")
                .orderByDesc("submitted_at");
        if (userId != null) {
            queryWrapper.eq("user_id", userId);
        }

        qcTaskRecordMapper.selectList(queryWrapper).stream()
                .filter(this::isAbnormalQualityTask)
                .forEach(this::upsertQualityTaskIssue);
    }

    @Override
    public long countPendingIssues(Long userId) {
        syncAllIssues(userId);
        return listIssuesByUserId(userId).stream()
                .filter(this::isUnresolvedIssue)
                .count();
    }

    @Override
    public long countHighRiskIssues(Long userId) {
        syncAllIssues(userId);
        return listIssuesByUserId(userId).stream()
                .filter(this::isUnresolvedIssue)
                .filter(issue -> Objects.equals("高", issue.getPriority()))
                .count();
    }

    @Override
    public List<Map<String, Object>> getRiskAlerts(Long userId, int limit) {
        syncAllIssues(userId);
        int normalizedLimit = Math.max(1, Math.min(limit, 10));

        return listIssuesByUserId(userId).stream()
                .filter(this::isUnresolvedIssue)
                .sorted(Comparator
                        .comparingInt((QcIssueRecord issue) -> resolvePriorityRank(issue.getPriority())).reversed()
                        .thenComparing(QcIssueRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(normalizedLimit)
                .map(this::toRiskAlertItem)
                .toList();
    }

    @Override
    public Map<String, Object> getSummaryStats(Long userId) {
        syncAllIssues(userId);
        List<QcIssueRecord> records = listIssuesByUserId(userId);
        LocalDate today = LocalDate.now();

        long totalIssues = records.size();
        long todayIssues = records.stream()
                .filter(issue -> issue.getCreatedAt() != null && today.equals(issue.getCreatedAt().toLocalDate()))
                .count();
        long pendingIssues = records.stream().filter(this::isUnresolvedIssue).count();
        long resolvedIssues = records.stream().filter(issue -> STATUS_RESOLVED.equals(issue.getStatus())).count();
        double resolutionRate = totalIssues == 0 ? 0D : roundOneDecimal(resolvedIssues * 100.0D / totalIssues);

        LocalDate currentWeekStart = today.minusDays(6);
        LocalDate previousWeekStart = today.minusDays(13);
        LocalDate previousWeekEnd = today.minusDays(7);

        long currentWeekIssues = countIssuesBetween(records, currentWeekStart, today);
        long previousWeekIssues = countIssuesBetween(records, previousWeekStart, previousWeekEnd);
        long currentWeekPending = countUnresolvedIssuesBetween(records, currentWeekStart, today);
        long previousWeekPending = countUnresolvedIssuesBetween(records, previousWeekStart, previousWeekEnd);
        double currentWeekResolutionRate = calculateResolutionRate(records, currentWeekStart, today);
        double previousWeekResolutionRate = calculateResolutionRate(records, previousWeekStart, previousWeekEnd);

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalIssues", totalIssues);
        stats.put("todayIssues", todayIssues);
        stats.put("pendingIssues", pendingIssues);
        stats.put("resolutionRate", resolutionRate);
        stats.put("totalIssuesTrend", calculateTrend(currentWeekIssues, previousWeekIssues));
        stats.put("todayIssuesTrend", calculateTrend(todayIssues, countIssuesBetween(records, today.minusDays(1), today.minusDays(1))));
        stats.put("pendingIssuesTrend", calculateTrend(currentWeekPending, previousWeekPending));
        stats.put("resolutionRateTrend", calculateTrend(currentWeekResolutionRate, previousWeekResolutionRate));
        return stats;
    }

    @Override
    public Map<String, Object> getIssueTrend(Long userId, int days) {
        syncAllIssues(userId);
        int normalizedDays = Math.max(1, days);
        List<QcIssueRecord> records = listIssuesByUserId(userId);
        Map<LocalDate, Long> issueCountByDate = records.stream()
                .filter(issue -> issue.getCreatedAt() != null)
                .collect(Collectors.groupingBy(issue -> issue.getCreatedAt().toLocalDate(), Collectors.counting()));

        LocalDate startDate = LocalDate.now().minusDays(normalizedDays - 1L);
        List<String> dates = new ArrayList<>();
        List<Integer> values = new ArrayList<>();
        for (int i = 0; i < normalizedDays; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            dates.add(currentDate.format(DateTimeFormatter.ofPattern("MM-dd")));
            values.add(issueCountByDate.getOrDefault(currentDate, 0L).intValue());
        }

        Map<String, Object> response = new HashMap<>();
        response.put("dates", dates);
        response.put("values", values);
        return response;
    }

    @Override
    public List<Map<String, Object>> getIssueDistribution(Long userId) {
        syncAllIssues(userId);
        return listIssuesByUserId(userId).stream()
                .collect(Collectors.groupingBy(
                        issue -> StringUtils.hasText(issue.getIssueType()) ? issue.getIssueType() : "未见明显异常",
                        Collectors.counting()))
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

    @Override
    public Map<String, Object> getIssuePage(Long userId, int page, int limit, String query, String status) {
        syncAllIssues(userId);
        int normalizedPage = normalizePage(page);
        int normalizedLimit = normalizeLimit(limit);
        List<QcIssueRecord> filteredRecords = listIssuesByUserId(userId).stream()
                .filter(issue -> matchesQuery(issue, query))
                .filter(issue -> matchesStatus(issue, status))
                .toList();

        int total = filteredRecords.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedLimit, total);
        int toIndex = Math.min(fromIndex + normalizedLimit, total);
        long totalPages = total == 0 ? 0 : (long) Math.ceil(total * 1.0D / normalizedLimit);

        Map<String, Object> response = new HashMap<>();
        response.put("items", filteredRecords.subList(fromIndex, toIndex).stream().map(this::toSummaryItem).toList());
        response.put("total", total);
        response.put("page", normalizedPage);
        response.put("limit", normalizedLimit);
        response.put("pages", totalPages);
        return response;
    }

    @Override
    public Map<String, Object> getIssueDetail(Long userId, Long issueId) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        syncAllIssues(userId);

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectById(issueId);
        if (issueRecord == null || (userId != null && !Objects.equals(issueRecord.getUserId(), userId))) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        Map<String, Object> detail = new HashMap<>(toSummaryItem(issueRecord));
        detail.put("sourceDetail", buildSourceDetail(issueRecord));
        detail.put("capa", buildCapaDetail(issueRecord.getId()));
        detail.put("handleLogs", buildHandleLogs(issueRecord.getId()));
        return detail;
    }

    @Override
    public List<Map<String, Object>> getAssignableUsers() {
        return userMapper.selectList(new QueryWrapper<User>()
                        .eq("is_active", true)
                        .orderByAsc("role_id")
                        .orderByAsc("username"))
                .stream()
                .map(this::toAssignableUserItem)
                .toList();
    }

    @Override
    public Map<String, Object> updateIssueStatus(Long userId, Long operatorId, Long issueId, String status, String remark) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        if (!isSupportedStatus(status)) {
            throw new IllegalArgumentException("不支持的工单状态");
        }

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectById(issueId);
        if (issueRecord == null || (userId != null && !Objects.equals(issueRecord.getUserId(), userId))) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        String beforeStatus = issueRecord.getStatus();
        LocalDateTime now = LocalDateTime.now();
        issueRecord.setStatus(status);
        if (StringUtils.hasText(remark)) {
            issueRecord.setLastRemark(remark.trim());
        }
        issueRecord.setUpdatedAt(now);
        issueRecord.setResolvedAt(STATUS_RESOLVED.equals(status) ? now : null);
        qcIssueRecordMapper.updateById(issueRecord);

        insertHandleLog(issueRecord.getId(), operatorId, "update_status", beforeStatus, status, remark, now);
        return toSummaryItem(qcIssueRecordMapper.selectById(issueId));
    }

    @Override
    public Map<String, Object> updateIssueWorkflow(Long userId, Long operatorId, Long issueId, IssueWorkflowUpdateReq request) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectById(issueId);
        if (issueRecord == null || (userId != null && !Objects.equals(issueRecord.getUserId(), userId))) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        IssueWorkflowUpdateReq normalizedRequest = request == null ? new IssueWorkflowUpdateReq() : request;
        LocalDateTime now = LocalDateTime.now();
        String nextStatus = StringUtils.hasText(normalizedRequest.getStatus())
                ? normalizedRequest.getStatus().trim()
                : issueRecord.getStatus();
        if (!isSupportedStatus(nextStatus)) {
            throw new IllegalArgumentException("不支持的工单状态");
        }

        Long assigneeUserId = resolveAssigneeUserId(normalizedRequest.getAssigneeUserId());
        String remark = trimToNull(normalizedRequest.getRemark());
        String beforeStatus = issueRecord.getStatus();
        Long beforeAssigneeUserId = issueRecord.getAssigneeUserId();

        issueRecord.setStatus(nextStatus);
        issueRecord.setAssigneeUserId(assigneeUserId);
        if (remark != null) {
            issueRecord.setLastRemark(remark);
        }
        issueRecord.setUpdatedAt(now);
        issueRecord.setResolvedAt(STATUS_RESOLVED.equals(nextStatus) ? now : null);
        qcIssueRecordMapper.updateById(issueRecord);

        upsertCapaRecord(issueId, operatorId, normalizedRequest);

        if (!Objects.equals(beforeAssigneeUserId, assigneeUserId)) {
            String assignmentRemark = buildAssignmentRemark(beforeAssigneeUserId, assigneeUserId);
            insertHandleLog(issueId, operatorId, "assign", beforeStatus, issueRecord.getStatus(), assignmentRemark, now);
        }

        if (!Objects.equals(beforeStatus, nextStatus) || remark != null) {
            insertHandleLog(issueId, operatorId, "update_status", beforeStatus, nextStatus, remark, now);
        }

        if (hasCapaContent(normalizedRequest)) {
            insertHandleLog(issueId, operatorId, "update_capa", issueRecord.getStatus(), issueRecord.getStatus(), "更新 CAPA 整改记录", now);
        }

        return getIssueDetail(userId, issueId);
    }

    /**
     * 查询前统一回填所有来源的异常工单，避免历史数据因接入顺序不同而遗漏。
     */
    private void syncAllIssues(Long userId) {
        syncHemorrhageIssues(userId);
        syncQualityTaskIssues(userId);
    }

    /**
     * 将脑出血历史记录上卷为异常工单。
     * 为避免覆盖人工处理结果，仅同步来源字段与异常展示字段，保留状态与处理备注。
     */
    private void upsertHemorrhageIssue(HemorrhageRecord record) {
        String issueType = HemorrhageIssueSupport.resolvePrimaryIssue(record);
        var appliedRule = resolveAppliedRule(SOURCE_TYPE_HEMORRHAGE, issueType);
        if (shouldSkipIssueCreation(appliedRule)) {
            return;
        }

        String priority = resolveIssuePriority(issueType, appliedRule);
        String description = HemorrhageIssueSupport.buildIssueDescription(issueType);
        String responsibleRole = resolveResponsibleRole(appliedRule);
        Integer slaHours = resolveSlaHours(appliedRule);
        LocalDateTime detectedAt = record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt();
        LocalDateTime dueAt = calculateDueAt(detectedAt, slaHours);

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectOne(new QueryWrapper<QcIssueRecord>()
                .eq("user_id", record.getUserId())
                .eq("source_type", SOURCE_TYPE_HEMORRHAGE)
                .eq("source_record_id", record.getId()));

        if (issueRecord == null) {
            QcIssueRecord newIssueRecord = new QcIssueRecord();
            newIssueRecord.setUserId(record.getUserId());
            newIssueRecord.setSourceType(SOURCE_TYPE_HEMORRHAGE);
            newIssueRecord.setSourceRecordId(record.getId());
            newIssueRecord.setPatientName(record.getPatientName());
            newIssueRecord.setExamId(record.getExamId());
            newIssueRecord.setIssueType(issueType);
            newIssueRecord.setDescription(description);
            newIssueRecord.setPriority(priority);
            newIssueRecord.setResponsibleRole(responsibleRole);
            newIssueRecord.setSlaHours(slaHours);
            newIssueRecord.setDueAt(dueAt);
            newIssueRecord.setStatus(STATUS_PENDING);
            newIssueRecord.setImageUrl(resolvePreferredImageUrl(record));
            newIssueRecord.setCreatedAt(detectedAt);
            newIssueRecord.setUpdatedAt(record.getUpdatedAt() == null ? newIssueRecord.getCreatedAt() : record.getUpdatedAt());
            qcIssueRecordMapper.insert(newIssueRecord);

            insertHandleLog(
                    newIssueRecord.getId(),
                    record.getUserId(),
                    "create",
                    null,
                    STATUS_PENDING,
                    "由脑出血检测结果自动生成异常工单",
                    newIssueRecord.getCreatedAt());
            return;
        }

        boolean changed = false;
        changed |= updateStringField(issueRecord.getPatientName(), record.getPatientName(), issueRecord::setPatientName);
        changed |= updateStringField(issueRecord.getExamId(), record.getExamId(), issueRecord::setExamId);
        changed |= updateStringField(issueRecord.getIssueType(), issueType, issueRecord::setIssueType);
        changed |= updateStringField(issueRecord.getDescription(), description, issueRecord::setDescription);
        changed |= updateStringField(issueRecord.getPriority(), priority, issueRecord::setPriority);
        changed |= updateStringField(issueRecord.getResponsibleRole(), responsibleRole, issueRecord::setResponsibleRole);
        changed |= updateStringField(issueRecord.getImageUrl(), resolvePreferredImageUrl(record), issueRecord::setImageUrl);
        changed |= updateIntegerField(issueRecord.getSlaHours(), slaHours, issueRecord::setSlaHours);
        changed |= updateDateTimeField(issueRecord.getDueAt(), dueAt, issueRecord::setDueAt);

        if (issueRecord.getCreatedAt() == null && record.getCreatedAt() != null) {
            issueRecord.setCreatedAt(record.getCreatedAt());
            changed = true;
        }

        if (!StringUtils.hasText(issueRecord.getStatus())) {
            issueRecord.setStatus(STATUS_PENDING);
            changed = true;
        }

        if (changed) {
            issueRecord.setUpdatedAt(LocalDateTime.now());
            qcIssueRecordMapper.updateById(issueRecord);
        }
    }

    /**
     * 将异步质控任务上卷为异常工单。
     */
    private void upsertQualityTaskIssue(QcTaskRecord taskRecord) {
        String issueType = StringUtils.hasText(taskRecord.getPrimaryIssue())
                ? taskRecord.getPrimaryIssue()
                : "图像质量异常";
        var appliedRule = resolveAppliedRule(taskRecord.getTaskType(), issueType);
        if (shouldSkipIssueCreation(appliedRule)) {
            return;
        }

        String description = buildQualityTaskIssueDescription(taskRecord, issueType);
        String priority = resolveQualityTaskPriority(taskRecord, appliedRule);
        String responsibleRole = resolveResponsibleRole(appliedRule);
        Integer slaHours = resolveSlaHours(appliedRule);
        LocalDateTime detectedAt = firstNonNull(
                taskRecord.getCompletedAt(),
                taskRecord.getUpdatedAt(),
                taskRecord.getSubmittedAt(),
                LocalDateTime.now());
        LocalDateTime dueAt = calculateDueAt(detectedAt, slaHours);

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectOne(new QueryWrapper<QcIssueRecord>()
                .eq("user_id", taskRecord.getUserId())
                .eq("source_type", taskRecord.getTaskType())
                .eq("source_record_id", taskRecord.getId()));

        if (issueRecord == null) {
            QcIssueRecord newIssueRecord = new QcIssueRecord();
            newIssueRecord.setUserId(taskRecord.getUserId());
            newIssueRecord.setSourceType(taskRecord.getTaskType());
            newIssueRecord.setSourceRecordId(taskRecord.getId());
            newIssueRecord.setPatientName(taskRecord.getPatientName());
            newIssueRecord.setExamId(taskRecord.getExamId());
            newIssueRecord.setIssueType(issueType);
            newIssueRecord.setDescription(description);
            newIssueRecord.setPriority(priority);
            newIssueRecord.setResponsibleRole(responsibleRole);
            newIssueRecord.setSlaHours(slaHours);
            newIssueRecord.setDueAt(dueAt);
            newIssueRecord.setStatus(STATUS_PENDING);
            newIssueRecord.setCreatedAt(detectedAt);
            newIssueRecord.setUpdatedAt(detectedAt);
            qcIssueRecordMapper.insert(newIssueRecord);

            insertHandleLog(
                    newIssueRecord.getId(),
                    taskRecord.getUserId(),
                    "create",
                    null,
                    STATUS_PENDING,
                    "由异步质控任务结果自动生成异常工单",
                    detectedAt);
            return;
        }

        boolean changed = false;
        changed |= updateStringField(issueRecord.getPatientName(), taskRecord.getPatientName(), issueRecord::setPatientName);
        changed |= updateStringField(issueRecord.getExamId(), taskRecord.getExamId(), issueRecord::setExamId);
        changed |= updateStringField(issueRecord.getIssueType(), issueType, issueRecord::setIssueType);
        changed |= updateStringField(issueRecord.getDescription(), description, issueRecord::setDescription);
        changed |= updateStringField(issueRecord.getPriority(), priority, issueRecord::setPriority);
        changed |= updateStringField(issueRecord.getResponsibleRole(), responsibleRole, issueRecord::setResponsibleRole);
        changed |= updateIntegerField(issueRecord.getSlaHours(), slaHours, issueRecord::setSlaHours);
        changed |= updateDateTimeField(issueRecord.getDueAt(), dueAt, issueRecord::setDueAt);

        if (issueRecord.getCreatedAt() == null) {
            issueRecord.setCreatedAt(firstNonNull(taskRecord.getCompletedAt(), taskRecord.getSubmittedAt(), LocalDateTime.now()));
            changed = true;
        }

        if (!StringUtils.hasText(issueRecord.getStatus())) {
            issueRecord.setStatus(STATUS_PENDING);
            changed = true;
        }

        if (changed) {
            issueRecord.setUpdatedAt(LocalDateTime.now());
            qcIssueRecordMapper.updateById(issueRecord);
        }
    }

    private void insertHandleLog(Long issueId,
                                 Long operatorId,
                                 String actionType,
                                 String beforeStatus,
                                 String afterStatus,
                                 String remark,
                                 LocalDateTime createdAt) {
        QcIssueHandleLog handleLog = new QcIssueHandleLog();
        handleLog.setIssueId(issueId);
        handleLog.setOperatorId(operatorId);
        handleLog.setActionType(actionType);
        handleLog.setBeforeStatus(beforeStatus);
        handleLog.setAfterStatus(afterStatus);
        handleLog.setRemark(StringUtils.hasText(remark) ? remark.trim() : null);
        handleLog.setCreatedAt(createdAt == null ? LocalDateTime.now() : createdAt);
        qcIssueHandleLogMapper.insert(handleLog);
    }

    private List<QcIssueRecord> listIssuesByUserId(Long userId) {
        if (userId == null) {
            return qcIssueRecordMapper.selectList(new QueryWrapper<QcIssueRecord>()
                    .orderByDesc("created_at"));
        }

        return qcIssueRecordMapper.selectList(new QueryWrapper<QcIssueRecord>()
                .eq("user_id", userId)
                .orderByDesc("created_at"));
    }

    private Map<String, Object> toRiskAlertItem(QcIssueRecord issueRecord) {
        Map<String, Object> item = new HashMap<>();
        item.put("content", buildRiskContent(issueRecord));
        item.put("time", formatDateTime(issueRecord.getCreatedAt(), RISK_TIME_FORMATTER));
        item.put("targetRoute", "/issues");
        return item;
    }

    private Map<String, Object> toSummaryItem(QcIssueRecord issueRecord) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", issueRecord.getId());
        item.put("patientName", issueRecord.getPatientName());
        item.put("examId", issueRecord.getExamId());
        item.put("type", resolveModuleName(issueRecord.getSourceType()));
        item.put("sourceType", issueRecord.getSourceType());
        item.put("sourceRecordId", issueRecord.getSourceRecordId());
        item.put("issueType", issueRecord.getIssueType());
        item.put("description", issueRecord.getDescription());
        item.put("date", formatDateTime(issueRecord.getCreatedAt(), SUMMARY_TIME_FORMATTER));
        item.put("status", issueRecord.getStatus());
        item.put("priority", issueRecord.getPriority());
        item.put("assigneeUserId", issueRecord.getAssigneeUserId());
        item.put("assigneeName", resolveUserDisplayName(issueRecord.getAssigneeUserId()));
        item.put("responsibleRole", issueRecord.getResponsibleRole());
        item.put("responsibleRoleLabel", resolveResponsibleRoleLabel(issueRecord.getResponsibleRole()));
        item.put("slaHours", issueRecord.getSlaHours());
        item.put("dueAt", formatDateTime(issueRecord.getDueAt(), SUMMARY_TIME_FORMATTER));
        item.put("overdue", isOverdueIssue(issueRecord));
        item.put("imageUrl", normalizeImageUrl(issueRecord.getImageUrl()));
        item.put("remark", issueRecord.getLastRemark());
        item.put("resolvedAt", formatDateTime(issueRecord.getResolvedAt(), SUMMARY_TIME_FORMATTER));
        item.put("timestamp", issueRecord.getCreatedAt() == null ? null : Date.from(issueRecord.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
        return item;
    }

    private Map<String, Object> buildCapaDetail(Long issueId) {
        if (issueId == null) {
            return null;
        }

        QcIssueCapaRecord capaRecord = qcIssueCapaRecordMapper.selectOne(new QueryWrapper<QcIssueCapaRecord>()
                .eq("issue_id", issueId)
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
        item.put("createdAt", formatDateTime(capaRecord.getCreatedAt(), SUMMARY_TIME_FORMATTER));
        item.put("updatedAt", formatDateTime(capaRecord.getUpdatedAt(), SUMMARY_TIME_FORMATTER));
        return item;
    }

    private List<Map<String, Object>> buildHandleLogs(Long issueId) {
        if (issueId == null) {
            return List.of();
        }

        return qcIssueHandleLogMapper.selectList(new QueryWrapper<QcIssueHandleLog>()
                        .eq("issue_id", issueId)
                        .orderByDesc("created_at"))
                .stream()
                .map(this::toHandleLogItem)
                .toList();
    }

    private Map<String, Object> toHandleLogItem(QcIssueHandleLog handleLog) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", handleLog.getId());
        item.put("actionType", handleLog.getActionType());
        item.put("actionTypeLabel", resolveActionTypeLabel(handleLog.getActionType()));
        item.put("beforeStatus", handleLog.getBeforeStatus());
        item.put("afterStatus", handleLog.getAfterStatus());
        item.put("remark", handleLog.getRemark());
        item.put("operatorId", handleLog.getOperatorId());
        item.put("operatorName", resolveUserDisplayName(handleLog.getOperatorId()));
        item.put("createdAt", formatDateTime(handleLog.getCreatedAt(), SUMMARY_TIME_FORMATTER));
        return item;
    }

    private Map<String, Object> toAssignableUserItem(User user) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("username", user.getUsername());
        item.put("fullName", user.getFullName());
        item.put("displayName", resolveUserDisplayName(user));
        item.put("role", AuthRole.fromRoleId(user.getRoleId()).getCode());
        item.put("roleLabel", AuthRole.fromRoleId(user.getRoleId()).getDisplayName());
        item.put("department", user.getDepartment());
        item.put("hospital", user.getHospital());
        return item;
    }

    /**
     * 根据异常工单来源类型加载原始记录详情。
     *
     * @param issueRecord 异常工单
     * @return 来源明细；若暂未接入则返回 null
     */
    private Map<String, Object> buildSourceDetail(QcIssueRecord issueRecord) {
        if (issueRecord.getSourceRecordId() == null) {
            return null;
        }

        if (SOURCE_TYPE_HEMORRHAGE.equals(issueRecord.getSourceType())) {
            HemorrhageRecord record = hemorrhageRecordMapper.selectOne(new QueryWrapper<HemorrhageRecord>()
                    .eq("id", issueRecord.getSourceRecordId())
                    .eq("user_id", issueRecord.getUserId())
                    .last("LIMIT 1"));

            if (record == null) {
                return null;
            }
            return toHemorrhageDetailItem(record);
        }

        if (MockQualityAnalysisSupport.isSupportedTaskType(issueRecord.getSourceType())) {
            QcTaskRecord taskRecord = qcTaskRecordMapper.selectOne(new QueryWrapper<QcTaskRecord>()
                    .eq("id", issueRecord.getSourceRecordId())
                    .eq("user_id", issueRecord.getUserId())
                    .last("LIMIT 1"));

            if (taskRecord == null) {
                return null;
            }
            return toQualityTaskDetailItem(taskRecord);
        }

        return null;
    }

    /**
     * 将出血检测记录转换为异常详情弹窗所需的明细结构。
     *
     * @param record 出血检测记录
     * @return 详情数据
     */
    private Map<String, Object> toHemorrhageDetailItem(HemorrhageRecord record) {
        record.setPrimaryIssue(HemorrhageIssueSupport.resolvePrimaryIssue(record));
        record.setQcStatus(HemorrhageIssueSupport.resolveQcStatus(record));

        Map<String, Object> item = new HashMap<>();
        item.put("detailType", "hemorrhage");
        item.put("recordId", record.getId());
        item.put("patientName", record.getPatientName());
        item.put("examId", record.getExamId());
        item.put("prediction", record.getPrediction());
        item.put("qcStatus", record.getQcStatus());
        item.put("primaryIssue", record.getPrimaryIssue());
        item.put("confidenceLevel", record.getConfidenceLevel());
        item.put("hemorrhageProbability", record.getHemorrhageProbability());
        item.put("noHemorrhageProbability", record.getNoHemorrhageProbability());
        item.put("analysisDuration", record.getAnalysisDuration());
        item.put("midlineShift", record.getMidlineShift());
        item.put("shiftScore", record.getShiftScore());
        item.put("midlineDetail", record.getMidlineDetail());
        item.put("ventricleIssue", record.getVentricleIssue());
        item.put("ventricleDetail", record.getVentricleDetail());
        item.put("device", record.getDevice());
        item.put("imageUrl", normalizeImageUrl(resolvePreferredImageUrl(record)));
        item.put("createdAt", formatDateTime(record.getCreatedAt(), SUMMARY_TIME_FORMATTER));
        item.put("updatedAt", formatDateTime(record.getUpdatedAt(), SUMMARY_TIME_FORMATTER));

        Map<String, Object> rawResult = parseRawResult(record.getRawResultJson());
        item.put("modelName", rawResult.get("model_name"));
        item.put("bboxes", rawResult.containsKey("bboxes") ? rawResult.get("bboxes") : rawResult.get("bbox"));
        item.put("imageWidth", rawResult.get("image_width"));
        item.put("imageHeight", rawResult.get("image_height"));
        return item;
    }

    /**
     * 将异步质控任务转换为异常详情弹窗的通用明细结构。
     *
     * @param taskRecord 异步质控任务记录
     * @return 详情数据
     */
    private Map<String, Object> toQualityTaskDetailItem(QcTaskRecord taskRecord) {
        Map<String, Object> result = parseRawResult(taskRecord.getResultJson());
        Map<String, Object> patientInfo = MockQualityAnalysisSupport.extractPatientInfo(result);
        List<Map<String, Object>> qcItems = MockQualityAnalysisSupport.extractQcItems(result);

        Map<String, Object> item = new HashMap<>();
        item.put("detailType", "qualityTask");
        item.put("recordId", taskRecord.getId());
        item.put("taskId", taskRecord.getTaskId());
        item.put("taskType", taskRecord.getTaskType());
        item.put("taskTypeName", firstNonBlank(
                taskRecord.getTaskTypeName(),
                MockQualityAnalysisSupport.resolveTaskTypeName(taskRecord.getTaskType())));
        item.put("sourceMode", taskRecord.getSourceMode());
        item.put("sourceModeLabel", MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(taskRecord.getSourceMode())
                ? "PACS 调取" : "本地上传");
        item.put("qcStatus", taskRecord.getQcStatus());
        item.put("qualityScore", taskRecord.getQualityScore());
        item.put("abnormalCount", taskRecord.getAbnormalCount());
        item.put("primaryIssue", taskRecord.getPrimaryIssue());
        item.put("patientInfo", patientInfo);
        item.put("qcItems", qcItems);
        item.put("summary", MockQualityAnalysisSupport.extractSummary(result));
        item.put("device", patientInfo.get("device"));
        item.put("createdAt", formatDateTime(firstNonNull(
                taskRecord.getCompletedAt(),
                taskRecord.getSubmittedAt(),
                taskRecord.getCreatedAt()), SUMMARY_TIME_FORMATTER));
        item.put("completedAt", formatDateTime(taskRecord.getCompletedAt(), SUMMARY_TIME_FORMATTER));
        item.put("errorMessage", taskRecord.getErrorMessage());
        return item;
    }

    /**
     * 解析原始检测结果中的 JSON 扩展字段。
     *
     * @param rawResultJson 原始结果 JSON
     * @return 解析后的结果映射
     */
    private Map<String, Object> parseRawResult(String rawResultJson) {
        if (!StringUtils.hasText(rawResultJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(rawResultJson, Map.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String buildRiskContent(QcIssueRecord issueRecord) {
        String patientName = StringUtils.hasText(issueRecord.getPatientName()) ? issueRecord.getPatientName() : "匿名患者";
        String examId = StringUtils.hasText(issueRecord.getExamId()) ? issueRecord.getExamId() : "未知检查号";
        String issueType = StringUtils.hasText(issueRecord.getIssueType()) ? issueRecord.getIssueType() : "异常质控项";
        String overdueSuffix = isOverdueIssue(issueRecord) ? "，已超过SLA时限" : "";

        if ("脑出血".equals(issueType)) {
            return patientName + "（" + examId + "）检出脑出血，需立即复核" + overdueSuffix;
        }

        if (issueType.contains("中线")) {
            return patientName + "（" + examId + "）存在中线偏移，建议优先复核" + overdueSuffix;
        }

        if (issueType.contains("脑室")) {
            return patientName + "（" + examId + "）提示脑室结构异常，请尽快确认" + overdueSuffix;
        }

        if (MockQualityAnalysisSupport.isSupportedTaskType(issueRecord.getSourceType())) {
            return patientName + "（" + examId + "）" + resolveModuleName(issueRecord.getSourceType()) + "存在“" + issueType + "”，请及时复核" + overdueSuffix;
        }

        return patientName + "（" + examId + "）存在异常质控项，请及时处理" + overdueSuffix;
    }

    private String buildQualityTaskIssueDescription(QcTaskRecord taskRecord, String issueType) {
        String taskLabel = resolveModuleName(taskRecord.getTaskType());
        int abnormalCount = taskRecord.getAbnormalCount() == null ? 0 : taskRecord.getAbnormalCount();
        String qualityScoreText = taskRecord.getQualityScore() == null
                ? "--"
                : String.valueOf(taskRecord.getQualityScore().doubleValue());

        if (abnormalCount > 1) {
            return taskLabel + "发现 " + abnormalCount + " 项异常，主异常为“" + issueType + "”，当前质控分为 " + qualityScoreText;
        }

        return taskLabel + "发现异常：“" + issueType + "”，当前质控分为 " + qualityScoreText;
    }

    private String resolveQualityTaskPriority(QcTaskRecord taskRecord, QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getPriority())) {
            return appliedRule.getPriority();
        }

        double qualityScore = taskRecord.getQualityScore() == null ? 0D : taskRecord.getQualityScore().doubleValue();
        int abnormalCount = taskRecord.getAbnormalCount() == null ? 0 : taskRecord.getAbnormalCount();

        if (MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(taskRecord.getTaskType()) && abnormalCount > 0) {
            return "高";
        }

        if (qualityScore > 0D && qualityScore < 70D) {
            return "高";
        }

        if (abnormalCount >= 2) {
            return "高";
        }

        if (qualityScore > 0D && qualityScore < 85D) {
            return "中";
        }

        if (abnormalCount >= 1) {
            return "中";
        }

        return "低";
    }

    private QcRuleConfig resolveAppliedRule(String taskType, String issueType) {
        return qcRuleConfigService.resolveRule(taskType, issueType);
    }

    private boolean shouldSkipIssueCreation(QcRuleConfig appliedRule) {
        return appliedRule != null
                && (!Boolean.TRUE.equals(appliedRule.getEnabled())
                || Boolean.FALSE.equals(appliedRule.getAutoCreateIssue()));
    }

    private String resolveIssuePriority(String issueType, QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getPriority())) {
            return appliedRule.getPriority();
        }

        return HemorrhageIssueSupport.resolvePriority(issueType);
    }

    private String resolveResponsibleRole(QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getResponsibleRole())) {
            return appliedRule.getResponsibleRole();
        }
        return "doctor";
    }

    private Integer resolveSlaHours(QcRuleConfig appliedRule) {
        if (appliedRule != null && appliedRule.getSlaHours() != null && appliedRule.getSlaHours() > 0) {
            return appliedRule.getSlaHours();
        }
        return 24;
    }

    private LocalDateTime calculateDueAt(LocalDateTime createdAt, Integer slaHours) {
        if (createdAt == null || slaHours == null || slaHours <= 0) {
            return null;
        }
        return createdAt.plusHours(slaHours);
    }

    private boolean isOverdueIssue(QcIssueRecord issueRecord) {
        return issueRecord != null
                && !STATUS_RESOLVED.equals(issueRecord.getStatus())
                && issueRecord.getDueAt() != null
                && issueRecord.getDueAt().isBefore(LocalDateTime.now());
    }

    private String resolveResponsibleRoleLabel(String role) {
        if ("admin".equals(role)) {
            return "管理员";
        }
        if ("doctor".equals(role)) {
            return "医生";
        }
        return "--";
    }

    private Long resolveAssigneeUserId(Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }

        User assignee = userMapper.selectById(assigneeUserId);
        if (assignee == null || Boolean.FALSE.equals(assignee.getIsActive())) {
            throw new IllegalArgumentException("指派人员不存在或已停用");
        }
        return assignee.getId();
    }

    private void upsertCapaRecord(Long issueId, Long operatorId, IssueWorkflowUpdateReq request) {
        if (issueId == null || request == null || !hasCapaContent(request)) {
            return;
        }

        QcIssueCapaRecord capaRecord = qcIssueCapaRecordMapper.selectOne(new QueryWrapper<QcIssueCapaRecord>()
                .eq("issue_id", issueId)
                .last("LIMIT 1"));
        if (capaRecord == null) {
            capaRecord = new QcIssueCapaRecord();
            capaRecord.setIssueId(issueId);
        }

        capaRecord.setRootCauseCategory(trimToNull(request.getRootCauseCategory()));
        capaRecord.setRootCauseDetail(trimToNull(request.getRootCauseDetail()));
        capaRecord.setCorrectiveAction(trimToNull(request.getCorrectiveAction()));
        capaRecord.setPreventiveAction(trimToNull(request.getPreventiveAction()));
        capaRecord.setVerificationNote(trimToNull(request.getVerificationNote()));
        capaRecord.setUpdatedBy(operatorId);

        if (capaRecord.getId() == null) {
            qcIssueCapaRecordMapper.insert(capaRecord);
            return;
        }

        qcIssueCapaRecordMapper.updateById(capaRecord);
    }

    private boolean hasCapaContent(IssueWorkflowUpdateReq request) {
        return request != null
                && (StringUtils.hasText(request.getRootCauseCategory())
                || StringUtils.hasText(request.getRootCauseDetail())
                || StringUtils.hasText(request.getCorrectiveAction())
                || StringUtils.hasText(request.getPreventiveAction())
                || StringUtils.hasText(request.getVerificationNote()));
    }

    private String buildAssignmentRemark(Long beforeAssigneeUserId, Long afterAssigneeUserId) {
        String beforeName = resolveUserDisplayName(beforeAssigneeUserId);
        String afterName = resolveUserDisplayName(afterAssigneeUserId);
        if (beforeAssigneeUserId == null && afterAssigneeUserId != null) {
            return "指派给 " + afterName;
        }
        if (beforeAssigneeUserId != null && afterAssigneeUserId == null) {
            return "取消指派（原处理人：" + beforeName + "）";
        }
        return "处理人由 " + beforeName + " 调整为 " + afterName;
    }

    private String resolveUserDisplayName(Long userId) {
        if (userId == null) {
            return "--";
        }

        User user = userMapper.selectById(userId);
        return resolveUserDisplayName(user);
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return "--";
        }

        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName().trim();
        }

        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : "--";
    }

    private String resolveActionTypeLabel(String actionType) {
        if ("create".equals(actionType)) {
            return "创建工单";
        }
        if ("update_status".equals(actionType)) {
            return "状态流转";
        }
        if ("assign".equals(actionType)) {
            return "处理人指派";
        }
        if ("update_capa".equals(actionType)) {
            return "更新 CAPA";
        }
        return actionType;
    }

    private String normalizeImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        return imageUrl.startsWith("/") ? imageUrl.replace('\\', '/') : "/" + imageUrl.replace('\\', '/');
    }

    /**
     * 优先使用患者信息管理页维护的患者影像图片，缺失时回退到检测记录图片。
     *
     * @param record 脑出血检测记录
     * @return 图片路径
     */
    private String resolvePreferredImageUrl(HemorrhageRecord record) {
        if (record == null) {
            return null;
        }

        if (StringUtils.hasText(record.getExamId())) {
            var patientInfo = qualityPatientInfoService.getByAccessionNumber(
                    QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE,
                    record.getExamId());
            if (patientInfo != null && StringUtils.hasText(patientInfo.getImagePath())) {
                return patientInfo.getImagePath();
            }
        }

        return record.getImagePath();
    }

    private String resolveModuleName(String sourceType) {
        if (SOURCE_TYPE_HEMORRHAGE.equals(sourceType)) {
            return "头部出血检测";
        }

        if (MockQualityAnalysisSupport.isSupportedTaskType(sourceType)) {
            return MockQualityAnalysisSupport.resolveTaskTypeName(sourceType);
        }

        return sourceType;
    }

    private boolean isSupportedStatus(String status) {
        return STATUS_PENDING.equals(status) || STATUS_PROCESSING.equals(status) || STATUS_RESOLVED.equals(status);
    }

    private boolean matchesQuery(QcIssueRecord issueRecord, String query) {
        if (!StringUtils.hasText(query)) {
            return true;
        }

        String normalizedQuery = query.trim();
        return contains(issueRecord.getPatientName(), normalizedQuery)
                || contains(issueRecord.getExamId(), normalizedQuery)
                || contains(issueRecord.getIssueType(), normalizedQuery)
                || contains(issueRecord.getDescription(), normalizedQuery);
    }

    private boolean matchesStatus(QcIssueRecord issueRecord, String status) {
        if (!StringUtils.hasText(status)) {
            return true;
        }

        return Objects.equals(issueRecord.getStatus(), status.trim());
    }

    private boolean contains(String value, String query) {
        return value != null && value.contains(query);
    }

    private boolean isAbnormalRecord(HemorrhageRecord record) {
        return HemorrhageIssueSupport.isAbnormalRecord(record);
    }

    private boolean isAbnormalQualityTask(QcTaskRecord taskRecord) {
        return taskRecord != null
                && "SUCCESS".equals(taskRecord.getTaskStatus())
                && ("不合格".equals(taskRecord.getQcStatus())
                || (taskRecord.getAbnormalCount() != null && taskRecord.getAbnormalCount() > 0));
    }

    private boolean isUnresolvedIssue(QcIssueRecord issueRecord) {
        return issueRecord != null && !STATUS_RESOLVED.equals(issueRecord.getStatus());
    }

    private long countIssuesBetween(List<QcIssueRecord> records, LocalDate startDate, LocalDate endDate) {
        return records.stream()
                .filter(issue -> issue.getCreatedAt() != null)
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isBefore(startDate))
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isAfter(endDate))
                .count();
    }

    private long countUnresolvedIssuesBetween(List<QcIssueRecord> records, LocalDate startDate, LocalDate endDate) {
        return records.stream()
                .filter(this::isUnresolvedIssue)
                .filter(issue -> issue.getCreatedAt() != null)
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isBefore(startDate))
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isAfter(endDate))
                .count();
    }

    private double calculateResolutionRate(List<QcIssueRecord> records, LocalDate startDate, LocalDate endDate) {
        List<QcIssueRecord> dateRangeRecords = records.stream()
                .filter(issue -> issue.getCreatedAt() != null)
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isBefore(startDate))
                .filter(issue -> !issue.getCreatedAt().toLocalDate().isAfter(endDate))
                .toList();

        if (dateRangeRecords.isEmpty()) {
            return 0D;
        }

        long resolvedCount = dateRangeRecords.stream()
                .filter(issue -> STATUS_RESOLVED.equals(issue.getStatus()))
                .count();
        return roundOneDecimal(resolvedCount * 100.0D / dateRangeRecords.size());
    }

    private double calculateTrend(double currentValue, double previousValue) {
        if (previousValue == 0D) {
            return currentValue == 0D ? 0D : 100D;
        }

        return roundOneDecimal((currentValue - previousValue) * 100.0D / previousValue);
    }

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

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private boolean updateStringField(String currentValue,
                                      String targetValue,
                                      java.util.function.Consumer<String> setter) {
        if (Objects.equals(currentValue, targetValue)) {
            return false;
        }

        setter.accept(targetValue);
        return true;
    }

    private boolean updateIntegerField(Integer currentValue,
                                       Integer targetValue,
                                       java.util.function.Consumer<Integer> setter) {
        if (Objects.equals(currentValue, targetValue)) {
            return false;
        }

        setter.accept(targetValue);
        return true;
    }

    private boolean updateDateTimeField(LocalDateTime currentValue,
                                        LocalDateTime targetValue,
                                        java.util.function.Consumer<LocalDateTime> setter) {
        if (Objects.equals(currentValue, targetValue)) {
            return false;
        }

        setter.accept(targetValue);
        return true;
    }

    private String formatDateTime(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return "--";
        }

        return dateTime.format(formatter);
    }

    @SafeVarargs
    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }

        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}



