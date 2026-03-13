package com.medical.qc.modules.qctask.application.support;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 异步质控任务执行期快照。
 *
 * <p>任务运行期保留在内存中，最终状态和结果以数据库记录为准。</p>
 */
public class MockQualityTaskSnapshot {
    // 数据库主记录 ID，用于任务完成后回写状态与工单同步。
    private Long recordId;
    // 前后端统一使用的任务 ID。
    private String taskId;
    // 任务类型编码。
    private String taskType;
    // 任务类型展示名。
    private String taskTypeName;
    // 来源模式：local 或 pacs。
    private String sourceMode;
    // 提交用户 ID。
    private Long userId;
    // 患者姓名。
    private String patientName;
    // 检查号。
    private String examId;
    // 原始文件名，主要用于详情展示。
    private String originalFilename;
    // 已存储文件绝对路径，供任务执行读取。
    private String storedFilePath;
    // 当前执行状态。
    private String status;
    // 标记当前任务是否为 mock 异步任务。
    private boolean mock;
    // 提交、开始、完成时间用于页面轮询和历史回显。
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    // 结构化分析结果，由 MockQualityAnalysisSupport 生成。
    private Map<String, Object> result;
    // 失败时保留错误信息。
    private String errorMessage;

    // 以下访问器同时服务于内存快照、数据库同步和控制器响应。
    public Long getRecordId() {
        return recordId;
    }

    public void setRecordId(Long recordId) {
        this.recordId = recordId;
    }

    public String getTaskId() {
        return taskId;
    }

    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    public String getTaskType() {
        return taskType;
    }

    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    public String getTaskTypeName() {
        return taskTypeName;
    }

    public void setTaskTypeName(String taskTypeName) {
        this.taskTypeName = taskTypeName;
    }

    public String getSourceMode() {
        return sourceMode;
    }

    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getExamId() {
        return examId;
    }

    public void setExamId(String examId) {
        this.examId = examId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    public String getStoredFilePath() {
        return storedFilePath;
    }

    public void setStoredFilePath(String storedFilePath) {
        this.storedFilePath = storedFilePath;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public boolean isMock() {
        return mock;
    }

    public void setMock(boolean mock) {
        this.mock = mock;
    }

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    public Map<String, Object> getResult() {
        return result;
    }

    public void setResult(Map<String, Object> result) {
        this.result = result;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}

