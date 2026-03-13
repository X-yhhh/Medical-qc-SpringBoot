package com.medical.qc.modules.patient.application.query;

/**
 * 患者信息分页查询条件。
 *
 * @param taskType 当前质控任务类型
 * @param keyword 综合搜索词
 * @param patientId 精确患者 ID
 * @param patientName 精确/半模糊患者姓名
 * @param accessionNumber 精确检查号
 * @param page 页码
 * @param limit 每页大小
 */
public record PatientInfoPageQuery(String taskType,
                                   String keyword,
                                   String patientId,
                                   String patientName,
                                   String accessionNumber,
                                   Integer page,
                                   Integer limit) {
}

