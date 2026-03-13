package com.medical.qc.modules.unified.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 统一异常工单 CAPA 实体。
 */
@TableName("issue_capa_records")
public class UnifiedIssueCapaRecord {
    // CAPA 记录主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 所属工单 ID。
    private Long ticketId;
    // 根因分析、纠正预防措施和验证结论。
    private String rootCauseCategory;
    private String rootCauseDetail;
    private String correctiveAction;
    private String preventiveAction;
    private String verificationNote;
    // 最后更新人和时间戳。
    private Long updatedBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供工单详情 CAPA 面板和 MyBatis 映射复用。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
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

