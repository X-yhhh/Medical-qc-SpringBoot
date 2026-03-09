package com.medical.qc.service;

import com.medical.qc.entity.PacsStudyCache;

import java.time.LocalDate;
import java.util.List;

/**
 * PACS服务接口
 * 提供PACS检查记录查询功能
 */
public interface PacsService {

    /**
     * 查询PACS检查记录
     *
     * @param taskType 质控任务类型，用于关联对应患者信息表；为空时仅查询缓存表
     * @param patientId 患者ID（精确匹配）
     * @param patientName 患者姓名（模糊匹配）
     * @param accessionNumber 检查号（精确匹配）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 检查记录列表
     */
    List<PacsStudyCache> searchStudies(String taskType,
                                       String patientId,
                                       String patientName,
                                       String accessionNumber, LocalDate startDate,
                                       LocalDate endDate);
}
