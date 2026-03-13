package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.modules.qctask.model.QcTaskRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * MockQualityTaskViewAssembler 单元测试。
 * 校验任务汇总统计和提交响应体的组装结果。
 */
class MockQualityTaskViewAssemblerTest {

    @Test
    void buildTaskSummaryShouldAggregateStatusAndScore() {
        MockQualityTaskViewAssembler assembler = new MockQualityTaskViewAssembler();

        // 构造一条成功但不合格的任务，验证异常任务和平均分统计。
        QcTaskRecord successRecord = new QcTaskRecord();
        successRecord.setTaskStatus("SUCCESS");
        successRecord.setQcStatus("不合格");
        successRecord.setAbnormalCount(2);
        successRecord.setQualityScore(BigDecimal.valueOf(78.5));
        successRecord.setSubmittedAt(LocalDateTime.now());

        // 构造一条待处理任务，验证 pending 计数。
        QcTaskRecord pendingRecord = new QcTaskRecord();
        pendingRecord.setTaskStatus("PENDING");
        pendingRecord.setSubmittedAt(LocalDateTime.now());

        Map<String, Object> summary = assembler.buildTaskSummary(List.of(successRecord, pendingRecord));

        // 汇总结果应同时覆盖任务总数、待处理数、异常数和平均质控分。
        assertThat(summary.get("totalTasks")).isEqualTo(2L);
        assertThat(summary.get("pendingTasks")).isEqualTo(1L);
        assertThat(summary.get("successTasks")).isEqualTo(1L);
        assertThat(summary.get("abnormalTasks")).isEqualTo(1L);
        assertThat(summary.get("averageQualityScore")).isEqualTo(78.5D);
    }

    @Test
    void toSubmitResponseShouldExposePollingInformation() {
        MockQualityTaskViewAssembler assembler = new MockQualityTaskViewAssembler();
        // 提交响应需要携带任务 ID、轮询地址和前端提示信息。
        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setTaskId("task-001");
        snapshot.setTaskType("head");
        snapshot.setTaskTypeName("CT头部平扫质控");
        snapshot.setStatus("PENDING");
        snapshot.setSubmittedAt(LocalDateTime.of(2026, 3, 12, 10, 30, 0));
        snapshot.setMock(true);

        Map<String, Object> response = assembler.toSubmitResponse(snapshot);

        // 轮询地址必须与前端质量任务中心使用的接口保持一致。
        assertThat(response.get("taskId")).isEqualTo("task-001");
        assertThat(response.get("pollingUrl")).isEqualTo("/api/v1/quality/tasks/task-001");
        assertThat(response.get("message")).isEqualTo("质控任务已提交，请轮询任务结果接口");
    }
}
