package com.medical.qc.modules.qctask.model;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 质控任务兼容模型。
 *
 * <p>当前仅用于统一模型查询结果的接口适配，不再直接映射旧表。</p>
 */
public class QcTaskRecord implements Serializable {
    // 兼容模型序列化版本号。
    private static final long serialVersionUID = 1L;

    // legacy 兼容 ID 与统一任务编号。
    private Long id;
    private String taskId;
    // 提交用户与任务类型信息。
    private Long userId;
    private String taskType;
    private String taskTypeName;
    // 患者、检查与来源文件信息。
    private String patientName;
    private String examId;
    private String sourceMode;
    private String originalFilename;
    private String storedFilePath;
    // 执行状态与 mock 标识。
    private String taskStatus;
    private Boolean mock;
    // 质控结论、评分、异常项和原始结果 JSON。
    private String qcStatus;
    private BigDecimal qualityScore;
    private Integer abnormalCount;
    private String primaryIssue;
    private String resultJson;
    private String errorMessage;
    // 提交、开始、完成及创建更新时间。
    private LocalDateTime submittedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供任务中心、首页看板和异常汇总复用。
    public Long getId() {
        return id;
    }

    /**
     * 设置兼容记录主键。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回统一任务编号。
     */
    public String getTaskId() {
        return taskId;
    }

    /**
     * 设置统一任务编号。
     */
    public void setTaskId(String taskId) {
        this.taskId = taskId;
    }

    /**
     * 返回提交任务的用户 ID。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置提交任务的用户 ID。
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 返回任务类型编码。
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * 设置任务类型编码。
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * 返回任务类型展示名。
     */
    public String getTaskTypeName() {
        return taskTypeName;
    }

    /**
     * 设置任务类型展示名。
     */
    public void setTaskTypeName(String taskTypeName) {
        this.taskTypeName = taskTypeName;
    }

    /**
     * 返回患者姓名。
     */
    public String getPatientName() {
        return patientName;
    }

    /**
     * 设置患者姓名。
     */
    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    /**
     * 返回检查号。
     */
    public String getExamId() {
        return examId;
    }

    /**
     * 设置检查号。
     */
    public void setExamId(String examId) {
        this.examId = examId;
    }

    /**
     * 返回来源模式。
     */
    public String getSourceMode() {
        return sourceMode;
    }

    /**
     * 设置来源模式。
     */
    public void setSourceMode(String sourceMode) {
        this.sourceMode = sourceMode;
    }

    /**
     * 返回原始文件名。
     */
    public String getOriginalFilename() {
        return originalFilename;
    }

    /**
     * 设置原始文件名。
     */
    public void setOriginalFilename(String originalFilename) {
        this.originalFilename = originalFilename;
    }

    /**
     * 返回已存储文件路径。
     */
    public String getStoredFilePath() {
        return storedFilePath;
    }

    /**
     * 设置已存储文件路径。
     */
    public void setStoredFilePath(String storedFilePath) {
        this.storedFilePath = storedFilePath;
    }

    /**
     * 返回任务执行状态。
     */
    public String getTaskStatus() {
        return taskStatus;
    }

    /**
     * 设置任务执行状态。
     */
    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    /**
     * 返回是否为 mock 任务。
     */
    public Boolean getMock() {
        return mock;
    }

    /**
     * 设置是否为 mock 任务。
     */
    public void setMock(Boolean mock) {
        this.mock = mock;
    }

    /**
     * 返回质控结论。
     */
    public String getQcStatus() {
        return qcStatus;
    }

    /**
     * 设置质控结论。
     */
    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    /**
     * 返回质控分。
     */
    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    /**
     * 设置质控分。
     */
    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Integer getAbnormalCount() {
        return abnormalCount;
    }

    public void setAbnormalCount(Integer abnormalCount) {
        this.abnormalCount = abnormalCount;
    }

    public String getPrimaryIssue() {
        return primaryIssue;
    }

    public void setPrimaryIssue(String primaryIssue) {
        this.primaryIssue = primaryIssue;
    }

    public String getResultJson() {
        return resultJson;
    }

    public void setResultJson(String resultJson) {
        this.resultJson = resultJson;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
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

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
