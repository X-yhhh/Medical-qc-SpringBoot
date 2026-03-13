package com.medical.qc.bean;

/**
 * 异常工单工作流更新请求。
 */
public class IssueWorkflowUpdateReq {
    // 目标工单状态，如待处理/处理中/已解决。
    private String status;
    // 本次流转备注，写入工单最后备注和动作日志。
    private String remark;
    // 新的处理人用户 ID，可为空表示取消指派。
    private Long assigneeUserId;
    // CAPA 根因分类。
    private String rootCauseCategory;
    // CAPA 根因详情说明。
    private String rootCauseDetail;
    // 针对当前问题的纠正措施。
    private String correctiveAction;
    // 防止同类问题再次发生的预防措施。
    private String preventiveAction;
    // CAPA 验证结论或复盘说明。
    private String verificationNote;

    // 以下访问器供 Jackson 绑定前端表单数据，并由写服务读取各字段。
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Long getAssigneeUserId() {
        return assigneeUserId;
    }

    public void setAssigneeUserId(Long assigneeUserId) {
        this.assigneeUserId = assigneeUserId;
    }

    public String getRootCauseCategory() {
        return rootCauseCategory;
    }

    public void setRootCauseCategory(String rootCauseCategory) {
        this.rootCauseCategory = rootCauseCategory;
    }

    public String getRootCauseDetail() {
        return rootCauseDetail;
    }

    public void setRootCauseDetail(String rootCauseDetail) {
        this.rootCauseDetail = rootCauseDetail;
    }

    public String getCorrectiveAction() {
        return correctiveAction;
    }

    public void setCorrectiveAction(String correctiveAction) {
        this.correctiveAction = correctiveAction;
    }

    public String getPreventiveAction() {
        return preventiveAction;
    }

    public void setPreventiveAction(String preventiveAction) {
        this.preventiveAction = preventiveAction;
    }

    public String getVerificationNote() {
        return verificationNote;
    }

    public void setVerificationNote(String verificationNote) {
        this.verificationNote = verificationNote;
    }
}

