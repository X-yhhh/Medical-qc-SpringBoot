package com.medical.qc.modules.patient.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 质控项患者信息实体。
 *
 * <p>该实体对应五张患者信息管理表的公共字段结构，
 * 通过动态表名映射复用于不同质控项的患者信息 CRUD。</p>
 */
public class QualityPatientInfo implements Serializable {
    // 兼容模型序列化版本号。
    private static final long serialVersionUID = 1L;

    // 记录主键。
    private Long id;
    // 患者基础信息。
    private String patientId;
    private String patientName;
    private String accessionNumber;
    private String gender;
    private Integer age;
    private LocalDate studyDate;
    // 预览图和备注。
    private String imagePath;
    private String remark;
    // 胸部增强扩展采集参数。
    private Double flowRate;
    private Integer contrastVolume;
    private String injectionSite;
    private Double sliceThickness;
    private Integer bolusTrackingHu;
    private Integer scanDelaySec;
    // 冠脉CTA扩展采集参数。
    private Integer heartRate;
    private Integer hrVariability;
    private String reconPhase;
    private String kVp;
    // 创建与更新时间。
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供患者列表、PACS 补齐和表单回显复用。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPatientId() {
        return patientId;
    }

    public void setPatientId(String patientId) {
        this.patientId = patientId;
    }

    public String getPatientName() {
        return patientName;
    }

    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
    }

    public String getGender() {
        return gender;
    }

    public void setGender(String gender) {
        this.gender = gender;
    }

    public Integer getAge() {
        return age;
    }

    public void setAge(Integer age) {
        this.age = age;
    }

    public LocalDate getStudyDate() {
        return studyDate;
    }

    public void setStudyDate(LocalDate studyDate) {
        this.studyDate = studyDate;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Double getFlowRate() {
        return flowRate;
    }

    public void setFlowRate(Double flowRate) {
        this.flowRate = flowRate;
    }

    public Integer getContrastVolume() {
        return contrastVolume;
    }

    public void setContrastVolume(Integer contrastVolume) {
        this.contrastVolume = contrastVolume;
    }

    public String getInjectionSite() {
        return injectionSite;
    }

    public void setInjectionSite(String injectionSite) {
        this.injectionSite = injectionSite;
    }

    public Double getSliceThickness() {
        return sliceThickness;
    }

    public void setSliceThickness(Double sliceThickness) {
        this.sliceThickness = sliceThickness;
    }

    public Integer getBolusTrackingHu() {
        return bolusTrackingHu;
    }

    public void setBolusTrackingHu(Integer bolusTrackingHu) {
        this.bolusTrackingHu = bolusTrackingHu;
    }

    public Integer getScanDelaySec() {
        return scanDelaySec;
    }

    public void setScanDelaySec(Integer scanDelaySec) {
        this.scanDelaySec = scanDelaySec;
    }

    public Integer getHeartRate() {
        return heartRate;
    }

    public void setHeartRate(Integer heartRate) {
        this.heartRate = heartRate;
    }

    public Integer getHrVariability() {
        return hrVariability;
    }

    public void setHrVariability(Integer hrVariability) {
        this.hrVariability = hrVariability;
    }

    public String getReconPhase() {
        return reconPhase;
    }

    public void setReconPhase(String reconPhase) {
        this.reconPhase = reconPhase;
    }

    public String getKVp() {
        return kVp;
    }

    public void setKVp(String kVp) {
        this.kVp = kVp;
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

