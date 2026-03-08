package com.medical.qc.service.impl;

import com.medical.qc.common.AuthRole;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import com.medical.qc.service.DashboardService;
import com.medical.qc.service.IssueService;
import com.medical.qc.service.QualityService;
import com.medical.qc.support.HemorrhageIssueSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 首页仪表盘服务实现。
 * 当前基于脑出血检测历史记录生成首页所需的真实数据，
 * 后续其他质控项接入后可在此处继续扩展聚合逻辑。
 */
@Service
public class DashboardServiceImpl implements DashboardService {
    private static final DateTimeFormatter ACTIVITY_TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter RISK_TIME_FORMATTER = DateTimeFormatter.ofPattern("MM-dd HH:mm");

    @Autowired
    private QualityService qualityService;

    @Autowired
    private IssueService issueService;

    @Override
    public Map<String, Object> getOverview(User user) {
        Long scopedUserId = resolveScopedUserId(user);
        List<HemorrhageRecord> history = qualityService.getHistory(scopedUserId);
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);

        long todayTotal = countByDate(history, today, record -> true);
        long yesterdayTotal = countByDate(history, yesterday, record -> true);
        long todayQualified = countByDate(history, today, this::isQualifiedRecord);
        long yesterdayQualified = countByDate(history, yesterday, this::isQualifiedRecord);
        long todayAbnormal = countByDate(history, today, this::isAbnormalRecord);
        long yesterdayAbnormal = countByDate(history, yesterday, this::isAbnormalRecord);
        double todayAverageScore = calculateAverageScore(history, today);
        double yesterdayAverageScore = calculateAverageScore(history, yesterday);

        Map<String, Object> response = new HashMap<>();
        response.put("welcomeName", resolveDisplayName(user));
        response.put("viewMode", resolveViewMode(user));
        response.put("pendingTaskCount", issueService.countPendingIssues(scopedUserId));
        response.put("stats", buildStats(todayTotal, yesterdayTotal, todayQualified, yesterdayQualified,
                todayAbnormal, yesterdayAbnormal, todayAverageScore, yesterdayAverageScore));

        response.put("riskList", issueService.getRiskAlerts(scopedUserId, 5));
        response.put("highRiskCount", issueService.countHighRiskIssues(scopedUserId));
        response.put("activities", buildActivities(history));
        return response;
    }

    @Override
    public Map<String, Object> getTrend(User user, String period) {
        int days = "month".equalsIgnoreCase(period) ? 30 : 7;
        List<HemorrhageRecord> history = qualityService.getHistory(resolveScopedUserId(user));
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
            long totalCount = countByDate(history, currentDate, record -> true);
            long qualifiedCount = countByDate(history, currentDate, this::isQualifiedRecord);
            long abnormalCount = countByDate(history, currentDate, this::isAbnormalRecord);
            double averageScore = calculateAverageScore(history, currentDate);
            double passRate = totalCount == 0 ? 0D : roundOneDecimal(qualifiedCount * 100.0D / totalCount);

            dates.add(currentDate.format(DateTimeFormatter.ofPattern("MM-dd")));
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
                abnormalPeakDate = currentDate.format(DateTimeFormatter.ofPattern("MM-dd"));
            }

            if (totalCount > 0 && passRate > bestPassRate) {
                bestPassRate = passRate;
                bestPassRateDate = currentDate.format(DateTimeFormatter.ofPattern("MM-dd"));
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

    private List<Map<String, Object>> buildStats(long todayTotal,
                                                 long yesterdayTotal,
                                                 long todayQualified,
                                                 long yesterdayQualified,
                                                 long todayAbnormal,
                                                 long yesterdayAbnormal,
                                                 double todayAverageScore,
                                                 double yesterdayAverageScore) {
        List<Map<String, Object>> stats = new ArrayList<>();
        stats.add(buildStatItem("今日检查总量", todayTotal, "例", "DataLine",
                calculateTrend(todayTotal, yesterdayTotal), "primary"));
        stats.add(buildStatItem("AI 自动审核", todayQualified, "例", "Cpu",
                calculateTrend(todayQualified, yesterdayQualified), "success"));
        stats.add(buildStatItem("人工复核数", todayAbnormal, "例", "Edit",
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

    private List<Map<String, Object>> buildRiskList(List<HemorrhageRecord> history) {
        return history.stream()
                .filter(this::isAbnormalRecord)
                .sorted(Comparator
                        .comparingInt(this::resolveSeverityLevel).reversed()
                        .thenComparing(HemorrhageRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(3)
                .map(record -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("content", buildRiskContent(record));
                    item.put("time", formatDateTime(record.getCreatedAt(), RISK_TIME_FORMATTER));
                    item.put("targetRoute", "/issues");
                    return item;
                })
                .toList();
    }

    private List<Map<String, Object>> buildActivities(List<HemorrhageRecord> history) {
        return history.stream()
                .sorted(Comparator.comparing(HemorrhageRecord::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .limit(5)
                .map(record -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("content", buildActivityContent(record));
                    item.put("timestamp", formatDateTime(record.getCreatedAt(), ACTIVITY_TIME_FORMATTER));
                    item.put("type", resolveActivityType(record));
                    item.put("color", resolveActivityColor(record));
                    return item;
                })
                .toList();
    }

    private long countByDate(List<HemorrhageRecord> history, LocalDate targetDate,
                             java.util.function.Predicate<HemorrhageRecord> predicate) {
        return history.stream()
                .filter(record -> record.getCreatedAt() != null)
                .filter(record -> targetDate.equals(record.getCreatedAt().toLocalDate()))
                .filter(predicate)
                .count();
    }

    private double calculateAverageScore(List<HemorrhageRecord> history, LocalDate targetDate) {
        return roundOneDecimal(history.stream()
                .filter(record -> record.getCreatedAt() != null)
                .filter(record -> targetDate.equals(record.getCreatedAt().toLocalDate()))
                .mapToDouble(this::calculateQualityScore)
                .average()
                .orElse(0D));
    }

    private double calculateQualityScore(HemorrhageRecord record) {
        String primaryIssue = record.getPrimaryIssue();
        if (!isAbnormalRecord(record)) {
            return 98D;
        }

        if ("脑出血".equals(primaryIssue)) {
            return 65D;
        }

        if (primaryIssue != null && primaryIssue.contains("中线")) {
            return 78D;
        }

        if (primaryIssue != null && primaryIssue.contains("脑室")) {
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

    private boolean isAbnormalRecord(HemorrhageRecord record) {
        return HemorrhageIssueSupport.isAbnormalRecord(record);
    }

    private boolean isQualifiedRecord(HemorrhageRecord record) {
        return HemorrhageIssueSupport.isQualifiedRecord(record);
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

    private Long resolveScopedUserId(User user) {
        return AuthRole.ADMIN.matchesRoleId(user.getRoleId()) ? null : user.getId();
    }

    private int resolveSeverityLevel(HemorrhageRecord record) {
        String primaryIssue = record.getPrimaryIssue();
        if ("脑出血".equals(primaryIssue)) {
            return 3;
        }

        if (primaryIssue != null && primaryIssue.contains("中线")) {
            return 2;
        }

        if (primaryIssue != null && primaryIssue.contains("脑室")) {
            return 1;
        }

        return 0;
    }

    private String buildRiskContent(HemorrhageRecord record) {
        String patientName = record.getPatientName() == null || record.getPatientName().isBlank()
                ? "匿名患者" : record.getPatientName();
        String examId = record.getExamId() == null || record.getExamId().isBlank() ? "未知检查号" : record.getExamId();
        String primaryIssue = record.getPrimaryIssue() == null ? "未见明显异常" : record.getPrimaryIssue();

        if ("脑出血".equals(primaryIssue)) {
            return patientName + "（" + examId + "）检出脑出血，需立即复核";
        }

        if (primaryIssue.contains("中线")) {
            return patientName + "（" + examId + "）存在中线偏移，建议优先复核";
        }

        if (primaryIssue.contains("脑室")) {
            return patientName + "（" + examId + "）提示脑室结构异常，请尽快确认";
        }

        return patientName + "（" + examId + "）存在异常质控项，请及时处理";
    }

    private String buildActivityContent(HemorrhageRecord record) {
        String patientName = record.getPatientName() == null || record.getPatientName().isBlank()
                ? "匿名患者" : record.getPatientName();
        String primaryIssue = record.getPrimaryIssue() == null ? "未见明显异常" : record.getPrimaryIssue();
        return patientName + " 完成头部出血检测：" + primaryIssue;
    }

    private String resolveActivityType(HemorrhageRecord record) {
        int severityLevel = resolveSeverityLevel(record);
        if (severityLevel >= 3) {
            return "danger";
        }

        if (severityLevel >= 1) {
            return "warning";
        }

        return "success";
    }

    private String resolveActivityColor(HemorrhageRecord record) {
        int severityLevel = resolveSeverityLevel(record);
        if (severityLevel >= 3) {
            return "#F56C6C";
        }

        if (severityLevel >= 1) {
            return "#E6A23C";
        }

        return "#67C23A";
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




