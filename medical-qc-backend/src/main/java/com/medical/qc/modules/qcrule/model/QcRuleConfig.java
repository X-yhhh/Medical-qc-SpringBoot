package com.medical.qc.modules.qcrule.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 质控规则配置实体。
 */
@TableName("qc_rules")
public class QcRuleConfig implements Serializable {
    // 规则实体序列化版本号。
    private static final long serialVersionUID = 1L;

    // 规则主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 模块编码与展示名称。
    private String taskType;
    private String taskTypeName;
    // 异常项、优先级和责任角色。
    private String issueType;
    private String priority;
    private String responsibleRole;
    // SLA 时限、自动建单和启用状态。
    private Integer slaHours;
    private Boolean autoCreateIssue;
    private Boolean enabled;
    // 规则说明、最后更新人和时间戳。
    private String description;
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供规则中心、工单写服务和 MyBatis 映射复用。
    public Long getId() {
        return id;
    }

    /**
     * 设置规则主键。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回模块类型编码。
     */
    public String getTaskType() {
        return taskType;
    }

    /**
     * 设置模块类型编码。
     */
    public void setTaskType(String taskType) {
        this.taskType = taskType;
    }

    /**
     * 返回模块展示名。
     */
    public String getTaskTypeName() {
        return taskTypeName;
    }

    /**
     * 设置模块展示名。
     */
    public void setTaskTypeName(String taskTypeName) {
        this.taskTypeName = taskTypeName;
    }

    /**
     * 返回异常项名称。
     */
    public String getIssueType() {
        return issueType;
    }

    /**
     * 设置异常项名称。
     */
    public void setIssueType(String issueType) {
        this.issueType = issueType;
    }

    public String getPriority() {
        return priority;
    }

    public void setPriority(String priority) {
        this.priority = priority;
    }

    public String getResponsibleRole() {
        return responsibleRole;
    }

    public void setResponsibleRole(String responsibleRole) {
        this.responsibleRole = responsibleRole;
    }

    public Integer getSlaHours() {
        return slaHours;
    }

    public void setSlaHours(Integer slaHours) {
        this.slaHours = slaHours;
    }

    public Boolean getAutoCreateIssue() {
        return autoCreateIssue;
    }

    public void setAutoCreateIssue(Boolean autoCreateIssue) {
        this.autoCreateIssue = autoCreateIssue;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    public void setEnabled(Boolean enabled) {
        this.enabled = enabled;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
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

