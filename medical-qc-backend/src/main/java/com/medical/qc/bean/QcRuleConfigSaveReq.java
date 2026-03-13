package com.medical.qc.bean;

/**
 * 质控规则保存请求。
 */
public class QcRuleConfigSaveReq {
    private String taskType;
    private String taskTypeName;
    private String issueType;
    private String priority;
    private String responsibleRole;
    private Integer slaHours;
    private Boolean autoCreateIssue;
    private Boolean enabled;
    private String description;

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

