package com.medical.qc.modules.dashboard.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class DashboardReadSupportTest {

    @Test
    void buildOverviewShouldIncludeStatsActivitiesAndVisits() {
        DashboardReadSupport support = new DashboardReadSupport(mock(com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService.class));

        User user = new User();
        user.setId(1L);
        user.setUsername("doctor01");
        user.setRoleId(2);

        HemorrhageRecord hemorrhageRecord = new HemorrhageRecord();
        hemorrhageRecord.setId(11L);
        hemorrhageRecord.setPatientName("张三");
        hemorrhageRecord.setExamId("EX-1001");
        hemorrhageRecord.setPrimaryIssue("未见明显异常");
        hemorrhageRecord.setQcStatus("合格");
        hemorrhageRecord.setCreatedAt(LocalDateTime.now());
        hemorrhageRecord.setPatientImagePath("uploads/patient.png");

        QcTaskRecord taskRecord = new QcTaskRecord();
        taskRecord.setTaskId("task-001");
        taskRecord.setPatientName("李四");
        taskRecord.setExamId("EX-1002");
        taskRecord.setTaskType("head");
        taskRecord.setTaskTypeName("CT头部平扫质控");
        taskRecord.setTaskStatus("SUCCESS");
        taskRecord.setQcStatus("不合格");
        taskRecord.setPrimaryIssue("运动伪影");
        taskRecord.setAbnormalCount(1);
        taskRecord.setQualityScore(BigDecimal.valueOf(80));
        taskRecord.setCompletedAt(LocalDateTime.now());
        taskRecord.setStoredFilePath("C:/workspace/uploads/mock-quality-tasks/task-001.png");

        Map<String, Object> overview = support.buildOverview(
                user,
                List.of(hemorrhageRecord),
                List.of(taskRecord),
                3,
                List.of(Map.of("content", "风险提示")),
                1);

        assertThat(overview.get("welcomeName")).isEqualTo("doctor01");
        assertThat(overview.get("viewMode")).isEqualTo("doctor");
        assertThat(overview.get("pendingTaskCount")).isEqualTo(3L);
        assertThat((List<?>) overview.get("stats")).hasSize(4);
        assertThat((List<?>) overview.get("activities")).isNotEmpty();
        assertThat((List<?>) overview.get("recentVisits")).isNotEmpty();
    }

    @Test
    void buildTrendShouldReturnSevenDaysForWeekPeriod() {
        DashboardReadSupport support = new DashboardReadSupport(mock(com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService.class));

        HemorrhageRecord hemorrhageRecord = new HemorrhageRecord();
        hemorrhageRecord.setCreatedAt(LocalDate.now().atStartOfDay());
        hemorrhageRecord.setQcStatus("合格");
        hemorrhageRecord.setPrimaryIssue("未见明显异常");

        Map<String, Object> trend = support.buildTrend("week", List.of(hemorrhageRecord), List.of());

        assertThat((List<?>) trend.get("dates")).hasSize(7);
        assertThat((List<?>) trend.get("passRates")).hasSize(7);
        assertThat(((Map<?, ?>) trend.get("summary")).get("hasData")).isEqualTo(true);
    }
}
