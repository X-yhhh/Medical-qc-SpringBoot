package com.medical.qc.modules.pacs.application;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.pacs.application.support.TaskScopedPacsStudyStorageService;
import com.medical.qc.modules.patient.application.support.TaskScopedPatientInfoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.List;

/**
 * PACS服务实现类
 * 当前为模拟实现，从任务专属 PACS 缓存表查询数据
 * 后续对接真实PACS时，可替换为DICOM C-FIND或DICOMweb查询
 */
@Service
public class PacsServiceImpl {
    // 各任务类型的 PACS 检索统一路由到任务专属缓存表。
    private final TaskScopedPacsStudyStorageService taskScopedPacsStudyStorageService;
    // 患者缓存补齐也按任务专属表读取。
    private final TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService;

    public PacsServiceImpl(TaskScopedPacsStudyStorageService taskScopedPacsStudyStorageService,
                           TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService) {
        this.taskScopedPacsStudyStorageService = taskScopedPacsStudyStorageService;
        this.taskScopedPatientInfoStorageService = taskScopedPatientInfoStorageService;
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
        // 先查任务专属 PACS 表，再把任务专属患者缓存表中的补录字段覆写到结果里。
        List<PacsStudyCache> studies = taskScopedPacsStudyStorageService.searchStudies(
                taskType,
                normalizeText(patientId),
                normalizeText(patientName),
                normalizeText(accessionNumber),
                startDate,
                endDate);
        studies.forEach(study -> enrichFromUnifiedPatientInfo(taskType, study));
        return studies;
    }

    /**
     * 用统一患者主数据补齐 PACS 记录中的患者信息与预览图。
     */
    private void enrichFromUnifiedPatientInfo(String taskType, PacsStudyCache study) {
        if (study == null || !StringUtils.hasText(study.getAccessionNumber())) {
            return;
        }

        // 检查号是 PACS 记录与任务专属患者缓存的主关联键。
        var patientInfo = taskScopedPatientInfoStorageService.getByAccessionNumber(taskType, study.getAccessionNumber());
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
        if (patientInfo.getHeartRate() != null) {
            study.setHeartRate(patientInfo.getHeartRate());
        }
        if (patientInfo.getHrVariability() != null) {
            study.setHrVariability(patientInfo.getHrVariability());
        }
        if (StringUtils.hasText(patientInfo.getReconPhase())) {
            study.setReconPhase(patientInfo.getReconPhase());
        }
        if (StringUtils.hasText(patientInfo.getKVp())) {
            study.setKvp(patientInfo.getKVp());
        }
        if (patientInfo.getFlowRate() != null) {
            study.setFlowRate(patientInfo.getFlowRate());
        }
        if (patientInfo.getContrastVolume() != null) {
            study.setContrastVolume(patientInfo.getContrastVolume());
        }
        if (StringUtils.hasText(patientInfo.getInjectionSite())) {
            study.setInjectionSite(patientInfo.getInjectionSite());
        }
        if (patientInfo.getSliceThickness() != null) {
            study.setSliceThickness(patientInfo.getSliceThickness());
        }
        if (patientInfo.getBolusTrackingHu() != null) {
            study.setBolusTrackingHu(patientInfo.getBolusTrackingHu());
        }
        if (patientInfo.getScanDelaySec() != null) {
            study.setScanDelaySec(patientInfo.getScanDelaySec());
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

