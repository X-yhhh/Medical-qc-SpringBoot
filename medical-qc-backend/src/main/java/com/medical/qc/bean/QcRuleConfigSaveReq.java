package com.medical.qc.bean;

/**
 * 质控规则保存请求。
 */
public class QcRuleConfigSaveReq {
    // 模块编码，如 hemorrhage/head/chest-contrast。
    private String taskType;
    // 模块展示名，可由前端显式传入，也可后端自动推导。
    private String taskTypeName;
    // 异常项名称；DEFAULT 表示兜底规则。
    private String issueType;
    // 工单优先级：高/中/低。
    private String priority;
    // 责任角色：doctor/admin。
    private String responsibleRole;
    // SLA 处理时限，单位小时。
    private Integer slaHours;
    // 是否在命中规则时自动建单。
    private Boolean autoCreateIssue;
    // 规则启停状态。
    private Boolean enabled;
    // 规则说明。
    private String description;

    // 以下访问器供规则中心表单与后端服务校验复用。
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

    public String getIssueType() {
        return issueType;
    }

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
}

