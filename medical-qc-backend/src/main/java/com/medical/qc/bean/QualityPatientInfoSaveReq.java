package com.medical.qc.bean;

import java.time.LocalDate;

/**
 * 患者信息新增/编辑请求体。
 *
 * <p>供患者信息管理页面与脑出血检测自动同步逻辑复用。</p>
 */
public class QualityPatientInfoSaveReq {
    private String patientId;
    private String patientName;
    private String accessionNumber;
    private String gender;
    private Integer age;
    private LocalDate studyDate;
    private String remark;

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

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }
}

