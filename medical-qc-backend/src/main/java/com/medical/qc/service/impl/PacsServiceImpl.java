package com.medical.qc.service.impl;

import com.medical.qc.entity.PacsStudyCache;
import com.medical.qc.mapper.PacsStudyMapper;
import com.medical.qc.service.PacsService;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * PACS服务实现类
 * 当前为模拟实现，从pacs_study_cache表查询数据
 * 后续对接真实PACS时，可替换为DICOM C-FIND或DICOMweb查询
 */
@Service
public class PacsServiceImpl implements PacsService {

    @Autowired
    private PacsStudyMapper pacsStudyMapper;

    /**
     * 查询PACS检查记录
     * 支持多条件组合查询，所有条件为AND关系
     *
     * @param taskType 质控任务类型；若已配置患者信息表，则优先返回该表中的患者主数据
     * @param patientId 患者ID（精确匹配）
     * @param patientName 患者姓名（模糊匹配）
     * @param accessionNumber 检查号（精确匹配）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 检查记录列表，按检查日期降序排列，最多返回100条
     */
    @Override
    public List<PacsStudyCache> searchStudies(String taskType,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber, LocalDate startDate,
                                              LocalDate endDate) {
        String patientTableName = QualityPatientTaskSupport.resolveTableName(taskType);
        if (patientTableName != null) {
            return pacsStudyMapper.searchStudiesWithPatientTable(
                    patientTableName,
                    normalizeText(patientId),
                    normalizeText(patientName),
                    normalizeText(accessionNumber),
                    startDate,
                    endDate);
        }

        return pacsStudyMapper.searchStudiesFromCache(
                normalizeText(patientId),
                normalizeText(patientName),
                normalizeText(accessionNumber),
                startDate,
                endDate);
    }

    /**
     * 规范化查询文本。
     *
     * @param value 原始文本
     * @return 去首尾空白后的文本；为空时返回 null
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
