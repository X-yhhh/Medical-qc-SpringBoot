package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.QcIssueHandleLog;
import com.medical.qc.entity.QcIssueRecord;
import com.medical.qc.mapper.HemorrhageRecordMapper;
import com.medical.qc.mapper.QcIssueHandleLogMapper;
import com.medical.qc.mapper.QcIssueRecordMapper;
import com.medical.qc.service.IssueService;
import com.medical.qc.support.HemorrhageIssueSupport;
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
    private HemorrhageRecordMapper hemorrhageRecordMapper;

    @Override
    public void syncHemorrhageIssue(HemorrhageRecord record) {
        if (!isAbnormalRecord(record) || record.getUserId() == null || record.getId() == null) {
            return;
        }

        upsertHemorrhageIssue(record);
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
    public long countPendingIssues(Long userId) {
        syncHemorrhageIssues(userId);
        return listIssuesByUserId(userId).stream()
                .filter(this::isUnresolvedIssue)
                .count();
    }

    @Override
    public long countHighRiskIssues(Long userId) {
        syncHemorrhageIssues(userId);
        return listIssuesByUserId(userId).stream()
                .filter(this::isUnresolvedIssue)
                .filter(issue -> Objects.equals("高", issue.getPriority()))
                .count();
    }

    @Override
    public List<Map<String, Object>> getRiskAlerts(Long userId, int limit) {
        syncHemorrhageIssues(userId);
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
        syncHemorrhageIssues(userId);
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
        syncHemorrhageIssues(userId);
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
        syncHemorrhageIssues(userId);
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
        syncHemorrhageIssues(userId);
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

        syncHemorrhageIssues(userId);

        QcIssueRecord issueRecord = qcIssueRecordMapper.selectById(issueId);
        if (issueRecord == null || (userId != null && !Objects.equals(issueRecord.getUserId(), userId))) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        Map<String, Object> detail = new HashMap<>(toSummaryItem(issueRecord));
        detail.put("sourceDetail", buildSourceDetail(issueRecord));
        return detail;
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

    /**
     * 将脑出血历史记录上卷为异常工单。
     * 为避免覆盖人工处理结果，仅同步来源字段与异常展示字段，保留状态与处理备注。
     */
    private void upsertHemorrhageIssue(HemorrhageRecord record) {
        String issueType = HemorrhageIssueSupport.resolvePrimaryIssue(record);
        String priority = HemorrhageIssueSupport.resolvePriority(issueType);
        String description = HemorrhageIssueSupport.buildIssueDescription(issueType);

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
            newIssueRecord.setStatus(STATUS_PENDING);
            newIssueRecord.setImageUrl(record.getImagePath());
            newIssueRecord.setCreatedAt(record.getCreatedAt() == null ? LocalDateTime.now() : record.getCreatedAt());
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
        changed |= updateStringField(issueRecord.getImageUrl(), record.getImagePath(), issueRecord::setImageUrl);

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
        item.put("imageUrl", normalizeImageUrl(issueRecord.getImageUrl()));
        item.put("remark", issueRecord.getLastRemark());
        item.put("resolvedAt", formatDateTime(issueRecord.getResolvedAt(), SUMMARY_TIME_FORMATTER));
        item.put("timestamp", issueRecord.getCreatedAt() == null ? null : Date.from(issueRecord.getCreatedAt().atZone(ZoneId.systemDefault()).toInstant()));
        return item;
    }

    /**
     * 根据异常工单来源类型加载原始记录详情。
     *
     * @param issueRecord 异常工单
     * @return 来源明细；若暂未接入则返回 null
     */
    private Map<String, Object> buildSourceDetail(QcIssueRecord issueRecord) {
        if (!SOURCE_TYPE_HEMORRHAGE.equals(issueRecord.getSourceType()) || issueRecord.getSourceRecordId() == null) {
            return null;
        }

        HemorrhageRecord record = hemorrhageRecordMapper.selectOne(new QueryWrapper<HemorrhageRecord>()
                .eq("id", issueRecord.getSourceRecordId())
                .eq("user_id", issueRecord.getUserId())
                .last("LIMIT 1"));

        if (record == null) {
            return null;
        }

        return toHemorrhageDetailItem(record);
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
        item.put("imageUrl", normalizeImageUrl(record.getImagePath()));
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
            return new ObjectMapper().readValue(rawResultJson, Map.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String buildRiskContent(QcIssueRecord issueRecord) {
        String patientName = StringUtils.hasText(issueRecord.getPatientName()) ? issueRecord.getPatientName() : "匿名患者";
        String examId = StringUtils.hasText(issueRecord.getExamId()) ? issueRecord.getExamId() : "未知检查号";
        String issueType = StringUtils.hasText(issueRecord.getIssueType()) ? issueRecord.getIssueType() : "异常质控项";

        if ("脑出血".equals(issueType)) {
            return patientName + "（" + examId + "）检出脑出血，需立即复核";
        }

        if (issueType.contains("中线")) {
            return patientName + "（" + examId + "）存在中线偏移，建议优先复核";
        }

        if (issueType.contains("脑室")) {
            return patientName + "（" + examId + "）提示脑室结构异常，请尽快确认";
        }

        return patientName + "（" + examId + "）存在异常质控项，请及时处理";
    }

    private String normalizeImageUrl(String imageUrl) {
        if (!StringUtils.hasText(imageUrl)) {
            return null;
        }

        return imageUrl.startsWith("/") ? imageUrl.replace('\\', '/') : "/" + imageUrl.replace('\\', '/');
    }

    private String resolveModuleName(String sourceType) {
        if (SOURCE_TYPE_HEMORRHAGE.equals(sourceType)) {
            return "头部出血检测";
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

    private String formatDateTime(LocalDateTime dateTime, DateTimeFormatter formatter) {
        if (dateTime == null) {
            return "--";
        }

        return dateTime.format(formatter);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}



