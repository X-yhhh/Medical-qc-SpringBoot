package com.medical.qc.modules.unified.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 统一质控任务实体。
 */
@TableName("qc_tasks")
public class UnifiedQcTask {
    // 任务主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 统一任务编号，对外轮询和兼容旧接口时使用。
    private String taskNo;
    // 任务类型编码，如 hemorrhage/head/chest-contrast。
    private String taskTypeCode;
    // 关联检查实例 ID。
    private Long studyId;
    // 提交任务的用户 ID。
    private Long submittedBy;
    // 来源模式：local 或 pacs。
    private String sourceMode;
    // 执行状态、优先级和调度方式。
    private String taskStatus;
    private String priority;
    private String schedulerType;
    // 是否为 mock 任务，数据库字段名为 is_mock。
    @TableField("is_mock")
    private Boolean mock;
    // 提交、开始、完成时间。
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime completedAt;
    // 执行失败时的错误码与错误信息。
    private String errorCode;
    private String errorMessage;
    // 创建与更新时间。
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供写服务、查询服务和控制器序列化复用。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTaskNo() {
        return taskNo;
    }

    public void setTaskNo(String taskNo) {
        this.taskNo = taskNo;
    }

    public String getTaskTypeCode() {
        return taskTypeCode;
    }

    public void setTaskTypeCode(String taskTypeCode) {
        this.taskTypeCode = taskTypeCode;
    }

    public Long getStudyId() {
        return studyId;
    }

    /**
     * 设置关联检查实例 ID。
     */
    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    /**
     * 返回提交任务的用户 ID。
     */
    public Long getSubmittedBy() {
        return submittedBy;
    }

    /**
     * 设置提交任务的用户 ID。
     */
    public void setSubmittedBy(Long submittedBy) {
        this.submittedBy = submittedBy;
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
     * 返回任务状态。
     */
    public String getTaskStatus() {
        return taskStatus;
    }

    /**
     * 设置任务状态。
     */
    public void setTaskStatus(String taskStatus) {
        this.taskStatus = taskStatus;
    }

    /**
     * 返回优先级。
     */
    public String getPriority() {
        return priority;
    }

    /**
     * 设置优先级。
     */
    public void setPriority(String priority) {
        this.priority = priority;
    }

    /**
     * 返回调度方式。
     */
    public String getSchedulerType() {
        return schedulerType;
    }

    /**
     * 设置调度方式。
     */
    public void setSchedulerType(String schedulerType) {
        this.schedulerType = schedulerType;
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
     * 返回请求时间。
     */
    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    /**
     * 设置请求时间。
     */
    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    /**
     * 返回开始执行时间。
     */
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    /**
     * 设置开始执行时间。
     */
    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    /**
     * 返回完成时间。
     */
    public LocalDateTime getCompletedAt() {
        return completedAt;
    }

    /**
     * 设置完成时间。
     */
    public void setCompletedAt(LocalDateTime completedAt) {
        this.completedAt = completedAt;
    }

    /**
     * 返回错误码。
     */
    public String getErrorCode() {
        return errorCode;
    }

    /**
     * 设置错误码。
     */
    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    /**
     * 返回错误信息。
     */
    public String getErrorMessage() {
        return errorMessage;
    }

    /**
     * 设置错误信息。
     */
    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    /**
     * 返回创建时间。
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回更新时间。
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

