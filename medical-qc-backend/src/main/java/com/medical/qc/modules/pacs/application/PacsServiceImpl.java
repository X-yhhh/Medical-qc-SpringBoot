package com.medical.qc.modules.pacs.application;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.pacs.persistence.mapper.PacsStudyMapper;
import com.medical.qc.modules.unified.application.UnifiedPatientInfoQueryService;
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
public class PacsServiceImpl {
    private final PacsStudyMapper pacsStudyMapper;
    private final UnifiedPatientInfoQueryService unifiedPatientInfoQueryService;

    public PacsServiceImpl(PacsStudyMapper pacsStudyMapper,
                           UnifiedPatientInfoQueryService unifiedPatientInfoQueryService) {
        this.pacsStudyMapper = pacsStudyMapper;
        this.unifiedPatientInfoQueryService = unifiedPatientInfoQueryService;
    }

    /**
     * 查询PACS检查记录
     * 支持多条件组合查询，所有条件为AND关系
     *
     * @param taskType 质控任务类型；用于补齐统一患者主数据
     * @param patientId 患者ID（精确匹配）
     * @param patientName 患者姓名（模糊匹配）
     * @param accessionNumber 检查号（精确匹配）
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return 检查记录列表，按检查日期降序排列，最多返回100条
     */
    public List<PacsStudyCache> searchStudies(String taskType,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber, LocalDate startDate,
                                              LocalDate endDate) {
        List<PacsStudyCache> studies = pacsStudyMapper.searchStudiesFromCache(
                normalizeText(patientId),
                normalizeText(patientName),
                normalizeText(accessionNumber),
                startDate,
                endDate);
        studies.forEach(study -> enrichFromUnifiedPatientInfo(taskType, study));
        return studies;
    }

    private void enrichFromUnifiedPatientInfo(String taskType, PacsStudyCache study) {
        if (study == null || !StringUtils.hasText(study.getAccessionNumber())) {
            return;
        }

        var patientInfo = unifiedPatientInfoQueryService.getByAccessionNumber(taskType, study.getAccessionNumber());
        if (patientInfo == null) {
            return;
        }

        if (StringUtils.hasText(patientInfo.getPatientId())) {
            study.setPatientId(patientInfo.getPatientId());
        }
        if (StringUtils.hasText(patientInfo.getPatientName())) {
            study.setPatientName(patientInfo.getPatientName());
        }
        if (StringUtils.hasText(patientInfo.getGender())) {
            study.setGender(patientInfo.getGender());
        }
        if (patientInfo.getAge() != null) {
            study.setAge(patientInfo.getAge());
        }
        if (patientInfo.getStudyDate() != null) {
            study.setStudyDate(patientInfo.getStudyDate());
        }
        if (StringUtils.hasText(patientInfo.getImagePath())) {
            study.setPatientImagePath(patientInfo.getImagePath());
        }
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

