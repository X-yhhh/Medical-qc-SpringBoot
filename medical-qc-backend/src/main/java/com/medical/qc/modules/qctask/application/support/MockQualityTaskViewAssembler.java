package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 异步质控任务视图装配组件。
 */
@Component
public class MockQualityTaskViewAssembler {
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public Map<String, Object> buildTaskSummary(List<QcTaskRecord> taskRecords) {
        long totalTasks = taskRecords.size();
        long pendingTasks = taskRecords.stream().filter(record -> STATUS_PENDING.equals(record.getTaskStatus())).count();
        long processingTasks = taskRecords.stream().filter(record -> STATUS_PROCESSING.equals(record.getTaskStatus())).count();
        long successTasks = taskRecords.stream().filter(record -> STATUS_SUCCESS.equals(record.getTaskStatus())).count();
        long failedTasks = taskRecords.stream().filter(record -> STATUS_FAILED.equals(record.getTaskStatus())).count();
        long abnormalTasks = taskRecords.stream().filter(this::isAbnormalTaskRecord).count();
        long todayTasks = taskRecords.stream()
                .filter(record -> record.getSubmittedAt() != null)
                .filter(record -> LocalDate.now().equals(record.getSubmittedAt().toLocalDate()))
                .count();
        double averageQualityScore = roundOneDecimal(taskRecords.stream()
                .filter(record -> record.getQualityScore() != null)
                .map(QcTaskRecord::getQualityScore)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTasks", totalTasks);
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

    public Map<String, Object> toSubmitResponse(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", snapshot.getTaskId());
        response.put("taskType", snapshot.getTaskType());
        response.put("taskTypeName", snapshot.getTaskTypeName());
        response.put("status", snapshot.getStatus());
        response.put("mock", true);
        response.put("submittedAt", formatDateTime(snapshot.getSubmittedAt()));
        response.put("pollingUrl", "/api/v1/quality/tasks/" + snapshot.getTaskId());
        response.put("message", "质控任务已提交，请轮询任务结果接口");
        return response;
    }

    public Map<String, Object> toTaskListItem(QcTaskRecord taskRecord) {
        Map<String, Object> item = new HashMap<>();
        item.put("recordId", taskRecord.getId());
        item.put("taskId", taskRecord.getTaskId());
        item.put("taskType", taskRecord.getTaskType());
        item.put("taskTypeName", taskRecord.getTaskTypeName());
        item.put("patientName", taskRecord.getPatientName());
        item.put("examId", taskRecord.getExamId());
        item.put("sourceMode", taskRecord.getSourceMode());
        item.put("sourceModeLabel", resolveSourceModeLabel(taskRecord.getSourceMode()));
        item.put("status", taskRecord.getTaskStatus());
        item.put("mock", Boolean.TRUE.equals(taskRecord.getMock()));
        item.put("qcStatus", taskRecord.getQcStatus());
        item.put("qualityScore", taskRecord.getQualityScore() == null ? null : taskRecord.getQualityScore().doubleValue());
        item.put("abnormalCount", taskRecord.getAbnormalCount());
        item.put("primaryIssue", taskRecord.getPrimaryIssue());
        item.put("submittedAt", formatDateTime(taskRecord.getSubmittedAt()));
        item.put("startedAt", formatDateTime(taskRecord.getStartedAt()));
        item.put("completedAt", formatDateTime(taskRecord.getCompletedAt()));
        item.put("errorMessage", taskRecord.getErrorMessage());
        return item;
    }

    public Map<String, Object> toTaskDetailResponse(QcTaskRecord taskRecord, Map<String, Object> result) {
        Map<String, Object> response = new HashMap<>(toTaskListItem(taskRecord));
        response.put("originalFilename", taskRecord.getOriginalFilename());
        response.put("storedFilePath", taskRecord.getStoredFilePath());
        response.put("result", result);
        return response;
    }

    private boolean isAbnormalTaskRecord(QcTaskRecord taskRecord) {
        return taskRecord != null
                && STATUS_SUCCESS.equals(taskRecord.getTaskStatus())
                && ("不合格".equals(taskRecord.getQcStatus())
                || (taskRecord.getAbnormalCount() != null && taskRecord.getAbnormalCount() > 0));
    }

    private String resolveSourceModeLabel(String sourceMode) {
        return MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode) ? "PACS 调取" : "本地上传";
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "--";
        }

        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}

