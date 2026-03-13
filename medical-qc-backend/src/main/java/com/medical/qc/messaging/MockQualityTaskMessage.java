package com.medical.qc.messaging;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * mock 质控异步任务消息。
 *
 * <p>当前消息统一承载任务类型、来源模式、患者信息、文件信息与提交时间，后续对接真实算法时，消费者可以直接基于该消息继续做：</p>
 * <ul>
 *     <li>本地文件预处理</li>
 *     <li>PACS 模拟或真实调取</li>
 *     <li>算法推理与结果持久化</li>
 * </ul>
 */
public class MockQualityTaskMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String taskId;
    private String taskType;
    private String sourceMode;
    private Long userId;
    private String patientName;
    private String examId;
    private String originalFilename;
    private String storedFilePath;
    private LocalDateTime submittedAt;

    public MockQualityTaskMessage() {
    }

    public MockQualityTaskMessage(String taskId,
                                  String taskType,
                                  String sourceMode,
                                  Long userId,
                                  String patientName,
                                  String examId,
                                  String originalFilename,
                                  String storedFilePath,
                                  LocalDateTime submittedAt) {
        this.taskId = taskId;
        this.taskType = taskType;
        this.sourceMode = sourceMode;
        this.userId = userId;
        this.patientName = patientName;
        this.examId = examId;
        this.originalFilename = originalFilename;
        this.storedFilePath = storedFilePath;
        this.submittedAt = submittedAt;
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

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }
}

