package com.medical.qc.modules.unified.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 统一质控结果实体。
 */
@TableName("qc_results")
public class UnifiedQcResult {
    // 结果主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 关联任务 ID 与结果版本。
    private Long taskId;
    private Integer resultVersion;
    // 模型编码和模型版本。
    private String modelCode;
    private String modelVersion;
    // 质控结论、质控分和异常数量。
    private String qcStatus;
    private BigDecimal qualityScore;
    private Integer abnormalCount;
    // 主异常项编码/名称与结果 JSON。
    private String primaryIssueCode;
    private String primaryIssueName;
    private String summaryJson;
    private String rawResultJson;
    // 创建与更新时间。
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供结果写入、工单查询和前端序列化复用。
    public Long getId() {
        return id;
    }

    /**
     * 设置结果主键。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回任务 ID。
     */
    public Long getTaskId() {
        return taskId;
    }

    /**
     * 设置任务 ID。
     */
    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    /**
     * 返回结果版本号。
     */
    public Integer getResultVersion() {
        return resultVersion;
    }

    /**
     * 设置结果版本号。
     */
    public void setResultVersion(Integer resultVersion) {
        this.resultVersion = resultVersion;
    }

    /**
     * 返回模型编码。
     */
    public String getModelCode() {
        return modelCode;
    }

    /**
     * 设置模型编码。
     */
    public void setModelCode(String modelCode) {
        this.modelCode = modelCode;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public void setModelVersion(String modelVersion) {
        this.modelVersion = modelVersion;
    }

    public String getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    public BigDecimal getQualityScore() {
        return qualityScore;
    }

    public void setQualityScore(BigDecimal qualityScore) {
        this.qualityScore = qualityScore;
    }

    public Integer getAbnormalCount() {
        return abnormalCount;
    }

    public void setAbnormalCount(Integer abnormalCount) {
        this.abnormalCount = abnormalCount;
    }

    public String getPrimaryIssueCode() {
        return primaryIssueCode;
    }

    public void setPrimaryIssueCode(String primaryIssueCode) {
        this.primaryIssueCode = primaryIssueCode;
    }

    public String getPrimaryIssueName() {
        return primaryIssueName;
    }

    public void setPrimaryIssueName(String primaryIssueName) {
        this.primaryIssueName = primaryIssueName;
    }

    public String getSummaryJson() {
        return summaryJson;
    }

    public void setSummaryJson(String summaryJson) {
        this.summaryJson = summaryJson;
    }

    public String getRawResultJson() {
        return rawResultJson;
    }

    public void setRawResultJson(String rawResultJson) {
        this.rawResultJson = rawResultJson;
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

