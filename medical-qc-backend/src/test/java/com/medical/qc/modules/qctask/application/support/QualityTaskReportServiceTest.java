package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 质控任务报告生成服务测试。
 */
class QualityTaskReportServiceTest {

    private final QualityTaskReportService qualityTaskReportService = new QualityTaskReportService();

    @Test
    void shouldBuildDocxReportForTaskDetail() throws IOException {
        Map<String, Object> taskDetail = Map.of(
                "taskId", "task-001",
                "taskTypeName", "CT头部平扫质控",
                "status", "SUCCESS",
                "qcStatus", "合格",
                "reviewStatus", "CONFIRMED",
                "result", Map.of(
                        "patientInfo", Map.of("name", "张三", "studyId", "EXAM-001"),
                        "qcItems", List.of(
                                Map.of("name", "扫描覆盖范围", "status", "合格", "description", "范围完整", "detail", ""),
                                Map.of("name", "运动伪影", "status", "待人工确认", "description", "需复核", "detail", "边界案例")),
                        "summary", Map.of("qualityScore", 83)),
                "auditLogs", List.of(
                        Map.of("createdAt", "2026-03-15 20:00:00", "actionType", "review", "operatorName", "医生A", "comment", "人工确认")));
        byte[] payload = qualityTaskReportService.buildTaskReportDocx(taskDetail);

        assertTrue(payload.length > 0);
        assertTrue(payload[0] == 'P' && payload[1] == 'K');
    }

    @Test
    void shouldBuildCsvForSelectedTasks() {
        byte[] payload = qualityTaskReportService.buildTaskCsv(List.of(
                Map.ofEntries(
                        Map.entry("taskId", "task-001"),
                        Map.entry("taskTypeName", "CT头部平扫质控"),
                        Map.entry("patientName", "张三"),
                        Map.entry("examId", "EXAM-001"),
                        Map.entry("status", "SUCCESS"),
                        Map.entry("qcStatus", "合格"),
                        Map.entry("qualityScore", 95),
                        Map.entry("abnormalCount", 0),
                        Map.entry("reviewStatus", "CONFIRMED"),
                        Map.entry("device", "CT-01"),
                        Map.entry("submittedAt", "2026-03-15 10:00:00"),
                        Map.entry("completedAt", "2026-03-15 10:02:00"))));

        String csvText = new String(payload, StandardCharsets.UTF_8);
        assertFalse(csvText.isBlank());
        assertTrue(csvText.contains("任务ID"));
        assertTrue(csvText.contains("task-001"));
    }

    @Test
    void shouldBuildDocxReportForHemorrhageRecord() throws IOException {
        HemorrhageRecord record = new HemorrhageRecord();
        record.setId(101L);
        record.setPatientName("李四");
        record.setPatientCode("P-101");
        record.setExamId("EXAM-H-001");
        record.setQcStatus("异常");
        record.setPrediction("出血");
        record.setHemorrhageProbability(0.91F);
        record.setNoHemorrhageProbability(0.09F);
        record.setConfidenceLevel("91.0%");
        record.setPrimaryIssue("脑出血");
        record.setMidlineDetail("未见中线偏移");
        record.setVentricleDetail("脑室系统形态正常");
        record.setGender("男");
        record.setAge(58);
        record.setStudyDate(LocalDate.of(2026, 3, 15));
        record.setDevice("CT-Head-01");
        record.setAnalysisDuration(235.0F);
        record.setCreatedAt(LocalDateTime.of(2026, 3, 15, 20, 0, 0));

        byte[] payload = qualityTaskReportService.buildHemorrhageReportDocx(record);

        assertTrue(payload.length > 0);
        assertTrue(payload[0] == 'P' && payload[1] == 'K');
    }

    @Test
    void shouldBuildCsvForIssueSummary() {
        byte[] payload = qualityTaskReportService.buildIssueCsv(List.of(
                Map.ofEntries(
                        Map.entry("id", 1),
                        Map.entry("date", "2026-03-15 12:00:00"),
                        Map.entry("patientName", "王五"),
                        Map.entry("examId", "ISSUE-001"),
                        Map.entry("type", "CT头部平扫质控"),
                        Map.entry("issueType", "运动伪影"),
                        Map.entry("description", "存在明显运动伪影"),
                        Map.entry("priority", "高"),
                        Map.entry("responsibleRoleLabel", "医生"),
                        Map.entry("dueAt", "2026-03-16 12:00:00"),
                        Map.entry("status", "待处理"))));

        String csvText = new String(payload, StandardCharsets.UTF_8);
        assertTrue(csvText.contains("异常ID"));
        assertTrue(csvText.contains("运动伪影"));
    }
}
