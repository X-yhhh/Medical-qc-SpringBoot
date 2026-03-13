package com.medical.qc.bean;

import java.time.LocalDate;

/**
 * 患者信息新增/编辑请求体。
 *
 * <p>供患者信息管理页面与脑出血检测自动同步逻辑复用。</p>
 */
public class QualityPatientInfoSaveReq {
    // 患者 ID，可来自人工录入或 PACS 回填。
    private String patientId;
    // 患者姓名，是患者档案的核心展示字段。
    private String patientName;
    // 检查号，作为检查级主键和去重依据。
    private String accessionNumber;
    // 性别信息。
    private String gender;
    // 年龄，前端按整数输入，后端最终转成年龄文本保存。
    private Integer age;
    // 检查日期。
    private LocalDate studyDate;
    // 备注信息，可承载业务说明或补充上下文。
    private String remark;

    // 以下访问器供患者管理页面和自动回填逻辑共用。
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

