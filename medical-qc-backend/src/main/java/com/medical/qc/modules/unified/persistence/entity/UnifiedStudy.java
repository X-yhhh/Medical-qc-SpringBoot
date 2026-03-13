package com.medical.qc.modules.unified.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * 统一检查实例实体。
 */
@TableName("studies")
public class UnifiedStudy {
    // 检查主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 统一检查编号。
    private String studyNo;
    // 所属患者主数据 ID。
    private Long patientId;
    // Accession Number / Study UID 等检查标识。
    private String accessionNumber;
    private String studyInstanceUid;
    // 模态、部位与检查日期时间。
    private String modality;
    private String bodyPart;
    private LocalDate studyDate;
    private LocalTime studyTime;
    // 检查描述、来源和设备信息。
    private String studyDescription;
    private String sourceType;
    private String sourceRef;
    private String manufacturer;
    private String deviceModel;
    // 状态与时间戳。
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供主数据读写和查询服务复用。
    public Long getId() {
        return id;
    }

    /**
     * 设置检查主键。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回统一检查编号。
     */
    public String getStudyNo() {
        return studyNo;
    }

    /**
     * 设置统一检查编号。
     */
    public void setStudyNo(String studyNo) {
        this.studyNo = studyNo;
    }

    /**
     * 返回患者主数据 ID。
     */
    public Long getPatientId() {
        return patientId;
    }

    /**
     * 设置患者主数据 ID。
     */
    public void setPatientId(Long patientId) {
        this.patientId = patientId;
    }

    /**
     * 返回检查号。
     */
    public String getAccessionNumber() {
        return accessionNumber;
    }

    /**
     * 设置检查号。
     */
    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getStudyInstanceUid() {
        return studyInstanceUid;
    }

    public void setStudyInstanceUid(String studyInstanceUid) {
        this.studyInstanceUid = studyInstanceUid;
    }

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(LocalDate studyDate) {
        this.studyDate = studyDate;
    }

    public LocalTime getStudyTime() {
        return studyTime;
    }

    public void setStudyTime(LocalTime studyTime) {
        this.studyTime = studyTime;
    }

    public String getStudyDescription() {
        return studyDescription;
    }

    public void setStudyDescription(String studyDescription) {
        this.studyDescription = studyDescription;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public String getSourceRef() {
        return sourceRef;
    }

    public void setSourceRef(String sourceRef) {
        this.sourceRef = sourceRef;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getDeviceModel() {
        return deviceModel;
    }

    public void setDeviceModel(String deviceModel) {
        this.deviceModel = deviceModel;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

