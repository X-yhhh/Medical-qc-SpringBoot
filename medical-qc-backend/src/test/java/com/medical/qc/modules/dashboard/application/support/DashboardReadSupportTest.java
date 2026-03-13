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

/**
 * DashboardReadSupport 单元测试。
 * 重点校验看板统计、活动流和趋势数据的组装逻辑。
 */
class DashboardReadSupportTest {

    @Test
    void buildOverviewShouldIncludeStatsActivitiesAndVisits() {
        // 构造仅依赖查询服务 Mock 的支持类实例。
        DashboardReadSupport support = new DashboardReadSupport(mock(com.medical.qc.modules.unified.application.UnifiedQcTaskQueryService.class));

        // 构造医生用户，验证欢迎语和视图模式。
        User user = new User();
        user.setId(1L);
        user.setUsername("doctor01");
        user.setRoleId(2);

        // 构造一条脑出血检测记录，模拟合格检测历史。
        HemorrhageRecord hemorrhageRecord = new HemorrhageRecord();
        hemorrhageRecord.setId(11L);
        hemorrhageRecord.setPatientName("张三");
        hemorrhageRecord.setExamId("EX-1001");
        hemorrhageRecord.setPrimaryIssue("未见明显异常");
        hemorrhageRecord.setQcStatus("合格");
        hemorrhageRecord.setCreatedAt(LocalDateTime.now());
        hemorrhageRecord.setPatientImagePath("uploads/patient.png");

        // 构造一条异步质控任务记录，模拟不合格任务。
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

        // 调用看板总览组装逻辑。
        Map<String, Object> overview = support.buildOverview(
                user,
                List.of(hemorrhageRecord),
                List.of(taskRecord),
                3,
                List.of(Map.of("content", "风险提示")),
                1);

        // 断言总览结果中包含欢迎语、角色视图、统计卡片和最近活动。
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

        // 构造当日合格记录，用于验证周趋势默认长度和摘要标记。
        HemorrhageRecord hemorrhageRecord = new HemorrhageRecord();
        hemorrhageRecord.setCreatedAt(LocalDate.now().atStartOfDay());
        hemorrhageRecord.setQcStatus("合格");
        hemorrhageRecord.setPrimaryIssue("未见明显异常");

        Map<String, Object> trend = support.buildTrend("week", List.of(hemorrhageRecord), List.of());

        // 周趋势固定返回 7 天，且存在数据时摘要标记应为 true。
        assertThat((List<?>) trend.get("dates")).hasSize(7);
        assertThat((List<?>) trend.get("passRates")).hasSize(7);
        assertThat(((Map<?, ?>) trend.get("summary")).get("hasData")).isEqualTo(true);
    }
}
