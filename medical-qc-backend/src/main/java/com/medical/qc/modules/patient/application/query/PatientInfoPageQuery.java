package com.medical.qc.modules.patient.application.query;

/**
 * 患者信息分页查询条件。
 */
public record PatientInfoPageQuery(String taskType,
                                   String keyword,
                                   String patientId,
                                   String patientName,
                                   String accessionNumber,
                                   Integer page,
                                   Integer limit) {
}

