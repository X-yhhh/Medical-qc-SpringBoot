package com.medical.qc.modules.qcresult.model;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 脑出血检测历史兼容模型。
 *
 * <p>当前仅用于接口返回和应用层编排，不再直接映射旧表。</p>
 */
public class HemorrhageRecord implements Serializable {
    // 兼容模型的序列化版本号。
    private static final long serialVersionUID = 1L;

    // legacy recordId；当前实际来源于统一任务主键。
    private Long id;
    // 提交检测的用户 ID。
    private Long userId;
    // 患者基本信息。
    private String patientName;
    private String patientCode;
    private String examId;
    private String gender;
    private Integer age;
    private LocalDate studyDate;
    // 分析图与预测结论。
    private String imagePath;
    private String prediction;
    private String qcStatus;
    private String confidenceLevel;
    private Float hemorrhageProbability;
    private Float noHemorrhageProbability;
    private Float analysisDuration;
    // 检测时间轴。
    private LocalDateTime createdAt;
    // 中线偏移与脑室异常的附加指标。
    private Boolean midlineShift;
    private Float shiftScore;
    private String midlineDetail;
    private Boolean ventricleIssue;
    private String ventricleDetail;
    // 推理设备、原始结果 JSON 与主异常项。
    private String device;
    private String rawResultJson;
    private LocalDateTime updatedAt;
    private String primaryIssue;
    // 患者预览图路径，优先用于列表和详情页回显。
    private String patientImagePath;

    // 以下访问器供查询服务、控制器和前端序列化复用。
    public Long getId() {
        return id;
    }

    /**
     * 设置兼容历史记录 ID。
     */
    public void setId(Long id) {
        this.id = id;
    }

    /**
     * 返回提交检测的用户 ID。
     */
    public Long getUserId() {
        return userId;
    }

    /**
     * 设置提交检测的用户 ID。
     */
    public void setUserId(Long userId) {
        this.userId = userId;
    }

    /**
     * 返回患者姓名。
     */
    public String getPatientName() {
        return patientName;
    }

    /**
     * 设置患者姓名。
     */
    public void setPatientName(String patientName) {
        this.patientName = patientName;
    }

    /**
     * 返回患者编号。
     */
    public String getPatientCode() {
        return patientCode;
    }

    /**
     * 设置患者编号。
     */
    public void setPatientCode(String patientCode) {
        this.patientCode = patientCode;
    }

    /**
     * 返回检查号。
     */
    public String getExamId() {
        return examId;
    }

    /**
     * 设置检查号。
     */
    public void setExamId(String examId) {
        this.examId = examId;
    }

    /**
     * 返回性别。
     */
    public String getGender() {
        return gender;
    }

    /**
     * 设置性别。
     */
    public void setGender(String gender) {
        this.gender = gender;
    }

    /**
     * 返回年龄。
     */
    public Integer getAge() {
        return age;
    }

    /**
     * 设置年龄。
     */
    public void setAge(Integer age) {
        this.age = age;
    }

    /**
     * 返回检查日期。
     */
    public LocalDate getStudyDate() {
        return studyDate;
    }

    /**
     * 设置检查日期。
     */
    public void setStudyDate(LocalDate studyDate) {
        this.studyDate = studyDate;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getPrediction() {
        return prediction;
    }

    public void setPrediction(String prediction) {
        this.prediction = prediction;
    }

    public String getQcStatus() {
        return qcStatus;
    }

    public void setQcStatus(String qcStatus) {
        this.qcStatus = qcStatus;
    }

    public String getConfidenceLevel() {
        return confidenceLevel;
    }

    public void setConfidenceLevel(String confidenceLevel) {
        this.confidenceLevel = confidenceLevel;
    }

    public Float getHemorrhageProbability() {
        return hemorrhageProbability;
    }

    public void setHemorrhageProbability(Float hemorrhageProbability) {
        this.hemorrhageProbability = hemorrhageProbability;
    }

    public Float getNoHemorrhageProbability() {
        return noHemorrhageProbability;
    }

    public void setNoHemorrhageProbability(Float noHemorrhageProbability) {
        this.noHemorrhageProbability = noHemorrhageProbability;
    }

    public Float getAnalysisDuration() {
        return analysisDuration;
    }

    public void setAnalysisDuration(Float analysisDuration) {
        this.analysisDuration = analysisDuration;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Boolean getMidlineShift() {
        return midlineShift;
    }

    public void setMidlineShift(Boolean midlineShift) {
        this.midlineShift = midlineShift;
    }

    public Float getShiftScore() {
        return shiftScore;
    }

    public void setShiftScore(Float shiftScore) {
        this.shiftScore = shiftScore;
    }

    public String getMidlineDetail() {
        return midlineDetail;
    }

    public void setMidlineDetail(String midlineDetail) {
        this.midlineDetail = midlineDetail;
    }

    public Boolean getVentricleIssue() {
        return ventricleIssue;
    }

    public void setVentricleIssue(Boolean ventricleIssue) {
        this.ventricleIssue = ventricleIssue;
    }

    public String getVentricleDetail() {
        return ventricleDetail;
    }

    public void setVentricleDetail(String ventricleDetail) {
        this.ventricleDetail = ventricleDetail;
    }

    public String getDevice() {
        return device;
    }

    public void setDevice(String device) {
        this.device = device;
    }

    public String getRawResultJson() {
        return rawResultJson;
    }

    public void setRawResultJson(String rawResultJson) {
        this.rawResultJson = rawResultJson;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getPrimaryIssue() {
        return primaryIssue;
    }

    public void setPrimaryIssue(String primaryIssue) {
        this.primaryIssue = primaryIssue;
    }

    public String getPatientImagePath() {
        return patientImagePath;
    }

    public void setPatientImagePath(String patientImagePath) {
        this.patientImagePath = patientImagePath;
    }
}
