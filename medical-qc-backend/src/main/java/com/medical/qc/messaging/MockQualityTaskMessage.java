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
    // 消息体序列化版本号。
    private static final long serialVersionUID = 1L;

    // 统一任务 ID。
    private String taskId;
    // 任务类型编码。
    private String taskType;
    // 来源模式：local 或 pacs。
    private String sourceMode;
    // 提交用户 ID。
    private Long userId;
    // 患者姓名与检查号。
    private String patientName;
    private String examId;
    // 原始文件名和已存储文件路径。
    private String originalFilename;
    private String storedFilePath;
    // 提交时间。
    private LocalDateTime submittedAt;

    // 无参构造供 JMS / Jackson 反序列化使用。
    public MockQualityTaskMessage() {
    }

    // 全参构造供任务提交时快速创建消息体。
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

    // 以下访问器供 JMS 反序列化与消费者读取。
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

