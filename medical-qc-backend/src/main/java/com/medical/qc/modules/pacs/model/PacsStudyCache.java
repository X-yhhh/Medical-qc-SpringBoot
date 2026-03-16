package com.medical.qc.modules.pacs.model;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

/**
 * PACS检查记录缓存实体类
 * 用于承载任务专属 PACS 缓存表查询结果。
 */
public class PacsStudyCache {

    /** 主键ID */
    @TableId(type = IdType.AUTO)
    private Long id;

    /** 检查实例UID（DICOM标准） */
    private String studyInstanceUid;

    /** 患者ID */
    private String patientId;

    /** 患者姓名 */
    private String patientName;

    /** 性别 */
    private String gender;

    /** 年龄 */
    private Integer age;

    /** 检查号（Accession Number） */
    private String accessionNumber;

    /** 检查日期 */
    private LocalDate studyDate;

    /** 检查时间 */
    private LocalTime studyTime;

    /** 检查描述 */
    private String studyDescription;

    /** 影像模态（CT/MR/CR等） */
    private String modality;

    /** 序列数量 */
    private Integer seriesCount;

    /** 图像数量 */
    private Integer imageCount;

    /** 检查部位 */
    private String bodyPart;

    /** 设备厂商 */
    private String manufacturer;

    /** 设备型号 */
    private String modelName;

    /** 影像文件路径 */
    private String imageFilePath;

    /** 统一患者主数据补齐后的预览图片路径 */
    private String patientImagePath;

    /** 胸部增强扩展采集参数 */
    private Double flowRate;
    private Integer contrastVolume;
    private String injectionSite;
    private Double sliceThickness;
    private Integer bolusTrackingHu;
    private Integer scanDelaySec;

    /** 冠脉CTA扩展采集参数 */
    private Integer heartRate;
    private Integer hrVariability;
    private String reconPhase;
    private String kvp;

    /** 缓存创建时间 */
    private LocalDateTime createdAt;

    /** 缓存更新时间 */
    private LocalDateTime updatedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getStudyInstanceUid() {
        return studyInstanceUid;
    }

    public void setStudyInstanceUid(String studyInstanceUid) {
        this.studyInstanceUid = studyInstanceUid;
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

    public String getAccessionNumber() {
        return accessionNumber;
    }

    public void setAccessionNumber(String accessionNumber) {
        this.accessionNumber = accessionNumber;
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

    public String getModality() {
        return modality;
    }

    public void setModality(String modality) {
        this.modality = modality;
    }

    public Integer getSeriesCount() {
        return seriesCount;
    }

    public void setSeriesCount(Integer seriesCount) {
        this.seriesCount = seriesCount;
    }

    public Integer getImageCount() {
        return imageCount;
    }

    public void setImageCount(Integer imageCount) {
        this.imageCount = imageCount;
    }

    public String getBodyPart() {
        return bodyPart;
    }

    public void setBodyPart(String bodyPart) {
        this.bodyPart = bodyPart;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModelName() {
        return modelName;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getImageFilePath() {
        return imageFilePath;
    }

    public void setImageFilePath(String imageFilePath) {
        this.imageFilePath = imageFilePath;
    }

    public String getPatientImagePath() {
        return patientImagePath;
    }

    public void setPatientImagePath(String patientImagePath) {
        this.patientImagePath = patientImagePath;
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

    public String getKvp() {
        return kvp;
    }

    public void setKvp(String kvp) {
        this.kvp = kvp;
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

