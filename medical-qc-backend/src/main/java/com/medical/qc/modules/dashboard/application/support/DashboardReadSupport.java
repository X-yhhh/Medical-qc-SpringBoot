package com.medical.qc.modules.dashboard.application.support;

import com.medical.qc.common.AuthRole;
import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService;
import com.medical.qc.support.HemorrhageIssueSupport;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * 仪表盘读侧支持组件。
 *
 * <p>负责首页总览、趋势、活动流和最近访问区的聚合与投影。</p>
 */
@Component
public class DashboardReadSupport {
    private static final DateTimeFormatter ACTIVITY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter RECENT_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");
    private static final DateTimeFormatter TREND_DATE_FORMATTER = DateTimeFormatter.ofPattern("MM-dd");

    private final UnifiedQcTaskQueryService unifiedQcTaskQueryService;

    public DashboardReadSupport(UnifiedQcTaskQueryService unifiedQcTaskQueryService) {
        this.unifiedQcTaskQueryService = unifiedQcTaskQueryService;
    }

    public Long resolveScopedUserId(User user) {
        return AuthRole.ADMIN.matchesRoleId(user.getRoleId()) ? null : user.getId();
    }

    public Map<String, Object> buildOverview(User user,
                                             List<HemorrhageRecord> hemorrhageHistory,
                                             List<QcTaskRecord> qualityTasks,
                                             long pendingTaskCount,
                                             List<Map<String, Object>> riskList,
                                             long highRiskCount) {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        long todayTotal = countHemorrhageByDate(hemorrhageHistory, today, record -> true)
                + countTaskByDate(qualityTasks, today, this::isSuccessfulQualityTask);
        long yesterdayTotal = countHemorrhageByDate(hemorrhageHistory, yesterday, record -> true)
                + countTaskByDate(qualityTasks, yesterday, this::isSuccessfulQualityTask);
        long todayQualified = countHemorrhageByDate(hemorrhageHistory, today, this::isQualifiedHemorrhageRecord)
                + countTaskByDate(qualityTasks, today, this::isQualifiedQualityTask);
        long yesterdayQualified = countHemorrhageByDate(hemorrhageHistory, yesterday, this::isQualifiedHemorrhageRecord)
                + countTaskByDate(qualityTasks, yesterday, this::isQualifiedQualityTask);
        long todayAbnormal = countHemorrhageByDate(hemorrhageHistory, today, this::isAbnormalHemorrhageRecord)
                + countTaskByDate(qualityTasks, today, this::isAbnormalQualityTask);
        long yesterdayAbnormal = countHemorrhageByDate(hemorrhageHistory, yesterday, this::isAbnormalHemorrhageRecord)
                + countTaskByDate(qualityTasks, yesterday, this::isAbnormalQualityTask);
        double todayAverageScore = calculateAverageScore(hemorrhageHistory, qualityTasks, today);
        double yesterdayAverageScore = calculateAverageScore(hemorrhageHistory, qualityTasks, yesterday);

        Map<String, Object> response = new HashMap<>();
        response.put("welcomeName", resolveDisplayName(user));
        response.put("viewMode", resolveViewMode(user));
        response.put("pendingTaskCount", pendingTaskCount);
        response.put("stats", buildStats(
                todayTotal,
                yesterdayTotal,
                todayQualified,
                yesterdayQualified,
                todayAbnormal,
                yesterdayAbnormal,
                todayAverageScore,
                yesterdayAverageScore));
        response.put("riskList", riskList);
        response.put("highRiskCount", highRiskCount);
        response.put("activities", buildActivities(hemorrhageHistory, qualityTasks));
        response.put("recentVisits", buildRecentVisits(hemorrhageHistory, qualityTasks));
        return response;
    }

    public Map<String, Object> buildTrend(String period,
                                          List<HemorrhageRecord> hemorrhageHistory,
                                          List<QcTaskRecord> qualityTasks) {
        int days = "month".equalsIgnoreCase(period) ? 30 : 7;
        LocalDate startDate = LocalDate.now().minusDays(days - 1L);

        List<String> dates = new ArrayList<>();
        List<Double> passRates = new ArrayList<>();
        List<Long> totalCounts = new ArrayList<>();
        List<Long> abnormalCounts = new ArrayList<>();
        List<Double> averageScores = new ArrayList<>();

        long totalChecks = 0L;
        long totalQualifiedChecks = 0L;
        double weightedScoreSum = 0D;
        long scoreSampleCount = 0L;
        long abnormalPeak = 0L;
        String abnormalPeakDate = "--";
        double bestPassRate = -1D;
        String bestPassRateDate = "--";

        for (int i = 0; i < days; i++) {
            LocalDate currentDate = startDate.plusDays(i);
            long totalCount = countHemorrhageByDate(hemorrhageHistory, currentDate, record -> true)
                    + countTaskByDate(qualityTasks, currentDate, this::isSuccessfulQualityTask);
            long qualifiedCount = countHemorrhageByDate(hemorrhageHistory, currentDate, this::isQualifiedHemorrhageRecord)
                    + countTaskByDate(qualityTasks, currentDate, this::isQualifiedQualityTask);
            long abnormalCount = countHemorrhageByDate(hemorrhageHistory, currentDate, this::isAbnormalHemorrhageRecord)
                    + countTaskByDate(qualityTasks, currentDate, this::isAbnormalQualityTask);
            double averageScore = calculateAverageScore(hemorrhageHistory, qualityTasks, currentDate);
            double passRate = totalCount == 0 ? 0D : roundOneDecimal(qualifiedCount * 100.0D / totalCount);

            dates.add(currentDate.format(TREND_DATE_FORMATTER));
            passRates.add(passRate);
            totalCounts.add(totalCount);
            abnormalCounts.add(abnormalCount);
            averageScores.add(totalCount == 0 ? 0D : averageScore);

            totalChecks += totalCount;
            totalQualifiedChecks += qualifiedCount;

            if (totalCount > 0) {
                weightedScoreSum += averageScore * totalCount;
                scoreSampleCount += totalCount;
            }

            if (abnormalCount > abnormalPeak) {
                abnormalPeak = abnormalCount;
                abnormalPeakDate = currentDate.format(TREND_DATE_FORMATTER);
            }

            if (totalCount > 0 && passRate > bestPassRate) {
                bestPassRate = passRate;
                bestPassRateDate = currentDate.format(TREND_DATE_FORMATTER);
            }
        }

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("totalChecks", totalChecks);
        summary.put("averagePassRate", totalChecks == 0 ? 0D : roundOneDecimal(totalQualifiedChecks * 100.0D / totalChecks));
        summary.put("averageScore", scoreSampleCount == 0 ? 0D : roundOneDecimal(weightedScoreSum / scoreSampleCount));
        summary.put("abnormalPeak", abnormalPeak);
        summary.put("abnormalPeakDate", abnormalPeak > 0 ? abnormalPeakDate : "--");
        summary.put("bestPassRate", bestPassRate < 0 ? 0D : bestPassRate);
        summary.put("bestPassRateDate", bestPassRate >= 0 ? bestPassRateDate : "--");
        summary.put("hasData", totalChecks > 0);

        Map<String, Object> response = new HashMap<>();
        response.put("dates", dates);
        response.put("passRates", passRates);
        response.put("totalCounts", totalCounts);
        response.put("abnormalCounts", abnormalCounts);
        response.put("averageScores", averageScores);
        response.put("summary", summary);
        return response;
    }

    public List<QcTaskRecord> listQualityTasks(Long scopedUserId) {
        return unifiedQcTaskQueryService.getTaskRecords(scopedUserId);
    }

    private List<Map<String, Object>> buildStats(long todayTotal,
                                                 long yesterdayTotal,
                                                 long todayQualified,
                                                 long yesterdayQualified,
                                                 long todayAbnormal,
                                                 long yesterdayAbnormal,
                                                 double todayAverageScore,
                                                 double yesterdayAverageScore) {
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(buildStatItem("今日完成质控", todayTotal, "例", "DataLine",
                calculateTrend(todayTotal, yesterdayTotal), "primary"));
        stats.add(buildStatItem("自动通过数", todayQualified, "例", "Cpu",
                calculateTrend(todayQualified, yesterdayQualified), "success"));
        stats.add(buildStatItem("异常待复核", todayAbnormal, "例", "Warning",
                calculateTrend(todayAbnormal, yesterdayAbnormal), "warning"));
        stats.add(buildStatItem("平均质控分", roundOneDecimal(todayAverageScore), "分", "Trophy",
                calculateTrend(todayAverageScore, yesterdayAverageScore), "info"));
        return stats;
    }

    private Map<String, Object> buildStatItem(String title,
                                              Object value,
                                              String unit,
                                              String icon,
                                              double trend,
                                              String type) {
        Map<String, Object> item = new HashMap<>();
        item.put("title", title);
        item.put("value", value);
        item.put("unit", unit);
        item.put("icon", icon);
        item.put("trend", trend);
        item.put("type", type);
        return item;
    }

    private List<Map<String, Object>> buildActivities(List<HemorrhageRecord> hemorrhageHistory,
                                                      List<QcTaskRecord> qualityTasks) {
        List<DashboardEvent> dashboardEvents = new ArrayList<>();
        hemorrhageHistory.forEach(record -> dashboardEvents.add(toHemorrhageEvent(record)));
        qualityTasks.forEach(taskRecord -> {
            DashboardEvent taskEvent = toTaskEvent(taskRecord);
            if (taskEvent != null) {
                dashboardEvents.add(taskEvent);
            }
        });

        return dashboardEvents.stream()
                .filter(event -> event.occurredAt() != null)
                .sorted(Comparator.comparing(DashboardEvent::occurredAt).reversed())
                .limit(5)
                .map(this::toActivityItem)
                .toList();
    }

    private List<Map<String, Object>> buildRecentVisits(List<HemorrhageRecord> hemorrhageHistory,
                                                        List<QcTaskRecord> qualityTasks) {
        List<DashboardVisit> dashboardVisits = new ArrayList<>();
        hemorrhageHistory.forEach(record -> dashboardVisits.add(toHemorrhageVisit(record)));
        qualityTasks.forEach(taskRecord -> {
            DashboardVisit visit = toTaskVisit(taskRecord);
            if (visit != null) {
                dashboardVisits.add(visit);
            }
        });

        return dashboardVisits.stream()
                .filter(visit -> visit.occurredAt() != null)
                .sorted(Comparator.comparing(DashboardVisit::occurredAt).reversed())
                .limit(3)
                .map(this::toVisitItem)
                .toList();
    }

    private DashboardEvent toHemorrhageEvent(HemorrhageRecord record) {
        LocalDateTime occurredAt = record == null ? null : record.getCreatedAt();
        String patientName = normalizePatientName(record == null ? null : record.getPatientName());
        String primaryIssue = record == null ? "未见明显异常" : normalizePrimaryIssue(record.getPrimaryIssue());
        return new DashboardEvent(
                occurredAt,
                patientName + " 完成头部出血检测：" + primaryIssue,
                resolveHemorrhageEventType(record),
                resolveHemorrhageEventColor(record));
    }

    private DashboardEvent toTaskEvent(QcTaskRecord taskRecord) {
        if (taskRecord == null) {
            return null;
        }

        LocalDateTime occurredAt = resolveTaskOccurredAt(taskRecord);
        if (occurredAt == null) {
            return null;
        }

        String patientName = normalizePatientName(taskRecord.getPatientName());
        String taskLabel = resolveTaskLabel(taskRecord);

        if (isFailedQualityTask(taskRecord)) {
            return new DashboardEvent(
                    occurredAt,
                    patientName + " 的" + taskLabel + "执行失败：" + normalizeFailureReason(taskRecord.getErrorMessage()),
                    "danger",
                    "#F56C6C");
        }

        if (!isSuccessfulQualityTask(taskRecord)) {
            return null;
        }

        String primaryIssue = normalizePrimaryIssue(taskRecord.getPrimaryIssue());
        return new DashboardEvent(
                occurredAt,
                patientName + " 完成" + taskLabel + "：" + primaryIssue,
                resolveTaskEventType(taskRecord),
                resolveTaskEventColor(taskRecord));
    }

    private Map<String, Object> toActivityItem(DashboardEvent event) {
        Map<String, Object> item = new HashMap<>();
        item.put("content", event.content());
        item.put("timestamp", formatDateTime(event.occurredAt(), ACTIVITY_TIME_FORMATTER));
        item.put("type", event.type());
        item.put("color", event.color());
        return item;
    }

    private DashboardVisit toHemorrhageVisit(HemorrhageRecord record) {
        String tag = isQualifiedHemorrhageRecord(record) ? "合格" : "不合格";
        return new DashboardVisit(
                record == null ? null : record.getCreatedAt(),
                "hemorrhage-" + (record == null ? "unknown" : record.getId()),
                buildVisitName(record == null ? null : record.getPatientName(), record == null ? null : record.getExamId()),
                normalizePrimaryIssue(record == null ? null : record.getPrimaryIssue()),
                resolveVisitType(tag),
                tag,
                normalizeImageUrl(record == null ? null : record.getPatientImagePath()),
                "/hemorrhage",
                record == null || record.getId() == null ? null : Map.of("recordId", record.getId()));
    }

    private DashboardVisit toTaskVisit(QcTaskRecord taskRecord) {
        if (taskRecord == null) {
            return null;
        }

        LocalDateTime occurredAt = resolveTaskOccurredAt(taskRecord);
        if (occurredAt == null) {
            return null;
        }

        String tag;
        String issue;
        if (isFailedQualityTask(taskRecord)) {
            tag = "失败";
            issue = normalizeFailureReason(taskRecord.getErrorMessage());
        } else if (isSuccessfulQualityTask(taskRecord)) {
            tag = StringUtils.hasText(taskRecord.getQcStatus()) ? taskRecord.getQcStatus() : "已完成";
            issue = normalizePrimaryIssue(taskRecord.getPrimaryIssue());
        } else {
            return null;
        }

        return new DashboardVisit(
                occurredAt,
                "task-" + taskRecord.getTaskId(),
                buildVisitName(taskRecord.getPatientName(), taskRecord.getExamId()),
                issue,
                resolveVisitType(tag),
                tag,
                resolveTaskImageUrl(taskRecord.getStoredFilePath()),
                "/quality-tasks",
                StringUtils.hasText(taskRecord.getTaskId()) ? Map.of("taskId", taskRecord.getTaskId()) : null);
    }

    private Map<String, Object> toVisitItem(DashboardVisit visit) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", visit.id());
        item.put("name", visit.name());
        item.put("issue", visit.issue());
        item.put("type", visit.type());
        item.put("tag", visit.tag());
        item.put("imageUrl", visit.imageUrl());
        item.put("time", formatRelativeTime(visit.occurredAt()));
        item.put("path", visit.path());
        item.put("query", visit.query());
        return item;
    }

    private long countHemorrhageByDate(List<HemorrhageRecord> hemorrhageHistory,
                                       LocalDate targetDate,
                                       Predicate<HemorrhageRecord> predicate) {
        return hemorrhageHistory.stream()
                .filter(record -> record.getCreatedAt() != null)
                .filter(record -> targetDate.equals(record.getCreatedAt().toLocalDate()))
                .filter(predicate)
                .count();
    }

    private long countTaskByDate(List<QcTaskRecord> qualityTasks,
                                 LocalDate targetDate,
                                 Predicate<QcTaskRecord> predicate) {
        return qualityTasks.stream()
                .filter(predicate)
                .filter(taskRecord -> resolveTaskOccurredAt(taskRecord) != null)
                .filter(taskRecord -> targetDate.equals(resolveTaskOccurredAt(taskRecord).toLocalDate()))
                .count();
    }

    private double calculateAverageScore(List<HemorrhageRecord> hemorrhageHistory,
                                         List<QcTaskRecord> qualityTasks,
                                         LocalDate targetDate) {
        List<Double> scores = new ArrayList<>();

        hemorrhageHistory.stream()
                .filter(record -> record.getCreatedAt() != null)
                .filter(record -> targetDate.equals(record.getCreatedAt().toLocalDate()))
                .mapToDouble(this::calculateHemorrhageScore)
                .forEach(scores::add);

        qualityTasks.stream()
                .filter(this::isSuccessfulQualityTask)
                .filter(taskRecord -> resolveTaskOccurredAt(taskRecord) != null)
                .filter(taskRecord -> targetDate.equals(resolveTaskOccurredAt(taskRecord).toLocalDate()))
                .map(QcTaskRecord::getQualityScore)
                .filter(value -> value != null)
                .map(BigDecimal::doubleValue)
                .forEach(scores::add);

        return roundOneDecimal(scores.stream().mapToDouble(Double::doubleValue).average().orElse(0D));
    }

    private double calculateHemorrhageScore(HemorrhageRecord record) {
        String primaryIssue = normalizePrimaryIssue(record == null ? null : record.getPrimaryIssue());
        if (!isAbnormalHemorrhageRecord(record)) {
            return 98D;
        }
        if ("脑出血".equals(primaryIssue)) {
            return 65D;
        }
        if (primaryIssue.contains("中线")) {
            return 78D;
        }
        if (primaryIssue.contains("脑室")) {
            return 85D;
        }
        return 90D;
    }

    private double calculateTrend(double currentValue, double previousValue) {
        if (previousValue == 0D) {
            return currentValue == 0D ? 0D : 100D;
        }
        return roundOneDecimal((currentValue - previousValue) * 100.0D / previousValue);
    }

    private boolean isAbnormalHemorrhageRecord(HemorrhageRecord record) {
        return HemorrhageIssueSupport.isAbnormalRecord(record);
    }

    private boolean isQualifiedHemorrhageRecord(HemorrhageRecord record) {
        return HemorrhageIssueSupport.isQualifiedRecord(record);
    }

    private boolean isSuccessfulQualityTask(QcTaskRecord taskRecord) {
        return taskRecord != null && "SUCCESS".equals(taskRecord.getTaskStatus());
    }

    private boolean isFailedQualityTask(QcTaskRecord taskRecord) {
        return taskRecord != null && "FAILED".equals(taskRecord.getTaskStatus());
    }

    private boolean isQualifiedQualityTask(QcTaskRecord taskRecord) {
        return isSuccessfulQualityTask(taskRecord)
                && "合格".equals(taskRecord.getQcStatus())
                && (taskRecord.getAbnormalCount() == null || taskRecord.getAbnormalCount() == 0);
    }

    private boolean isAbnormalQualityTask(QcTaskRecord taskRecord) {
        return isSuccessfulQualityTask(taskRecord)
                && ("不合格".equals(taskRecord.getQcStatus())
                || (taskRecord.getAbnormalCount() != null && taskRecord.getAbnormalCount() > 0));
    }

    private String resolveDisplayName(User user) {
        if (user.getFullName() != null && !user.getFullName().isBlank()) {
            return user.getFullName();
        }
        return user.getUsername();
    }

    private String resolveViewMode(User user) {
        return AuthRole.ADMIN.matchesRoleId(user.getRoleId()) ? "admin" : "doctor";
    }

    private String resolveHemorrhageEventType(HemorrhageRecord record) {
        if (!isAbnormalHemorrhageRecord(record)) {
            return "success";
        }
        if ("脑出血".equals(normalizePrimaryIssue(record.getPrimaryIssue()))) {
            return "danger";
        }
        return "warning";
    }

    private String resolveHemorrhageEventColor(HemorrhageRecord record) {
        if (!isAbnormalHemorrhageRecord(record)) {
            return "#67C23A";
        }
        if ("脑出血".equals(normalizePrimaryIssue(record.getPrimaryIssue()))) {
            return "#F56C6C";
        }
        return "#E6A23C";
    }

    private String resolveTaskEventType(QcTaskRecord taskRecord) {
        return isAbnormalQualityTask(taskRecord) ? "danger" : "success";
    }

    private String resolveTaskEventColor(QcTaskRecord taskRecord) {
        return isAbnormalQualityTask(taskRecord) ? "#F56C6C" : "#67C23A";
    }

    private String resolveVisitType(String tag) {
        if ("不合格".equals(tag) || "失败".equals(tag)) {
            return "danger";
        }
        if ("合格".equals(tag)) {
            return "success";
        }
        return "info";
    }

    private String resolveTaskLabel(QcTaskRecord taskRecord) {
        if (StringUtils.hasText(taskRecord.getTaskTypeName())) {
            return taskRecord.getTaskTypeName().trim();
        }
        return MockQualityAnalysisSupport.resolveTaskTypeName(taskRecord.getTaskType());
    }

    private LocalDateTime resolveTaskOccurredAt(QcTaskRecord taskRecord) {
        if (taskRecord == null) {
            return null;
        }
        if (taskRecord.getCompletedAt() != null) {
            return taskRecord.getCompletedAt();
        }
        if (taskRecord.getSubmittedAt() != null) {
            return taskRecord.getSubmittedAt();
        }
        return taskRecord.getCreatedAt();
    }

    private String normalizePatientName(String patientName) {
        return StringUtils.hasText(patientName) ? patientName.trim() : "匿名患者";
    }

    private String normalizePrimaryIssue(String primaryIssue) {
        return StringUtils.hasText(primaryIssue) ? primaryIssue.trim() : "未见明显异常";
    }

    private String normalizeFailureReason(String errorMessage) {
        return StringUtils.hasText(errorMessage) ? errorMessage.trim() : "任务执行失败";
    }

    private String buildVisitName(String patientName, String examId) {
        String normalizedPatientName = normalizePatientName(patientName);
        if (!StringUtils.hasText(examId)) {
            return normalizedPatientName;
        }
        return normalizedPatientName + " (" + examId.trim() + ")";
    }

    private String normalizeImageUrl(String rawUrl) {
        if (!StringUtils.hasText(rawUrl)) {
            return null;
        }

        String normalizedUrl = rawUrl.trim().replace('\\', '/');
        if (normalizedUrl.startsWith("http://") || normalizedUrl.startsWith("https://") || normalizedUrl.startsWith("data:")) {
            return normalizedUrl;
        }
        return normalizedUrl.startsWith("/") ? normalizedUrl : "/" + normalizedUrl;
    }

    private String resolveTaskImageUrl(String storedFilePath) {
        if (!StringUtils.hasText(storedFilePath)) {
            return null;
        }

        String normalizedPath = storedFilePath.trim().replace('\\', '/');
        int uploadsIndex = normalizedPath.indexOf("/uploads/");
        if (uploadsIndex < 0) {
            return null;
        }
        return normalizedPath.substring(uploadsIndex);
    }

    private String formatRelativeTime(LocalDateTime occurredAt) {
        if (occurredAt == null) {
            return "--";
        }

        Duration duration = Duration.between(occurredAt, LocalDateTime.now());
        long minutes = Math.max(duration.toMinutes(), 0L);
        if (minutes < 1) {
            return "刚刚";
        }
        if (minutes < 60) {
            return minutes + "分钟前";
        }

        long hours = Math.max(duration.toHours(), 0L);
        if (hours < 24) {
            return hours + "小时前";
        }

        long days = Math.max(duration.toDays(), 0L);
        if (days < 30) {
            return days + "天前";
        }
        return occurredAt.format(RECENT_TIME_FORMATTER);
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

    private record DashboardEvent(LocalDateTime occurredAt, String content, String type, String color) {
    }

    private record DashboardVisit(LocalDateTime occurredAt,
                                  String id,
                                  String name,
                                  String issue,
                                  String type,
                                  String tag,
                                  String imageUrl,
                                  String path,
                                  Map<String, Object> query) {
    }
}

