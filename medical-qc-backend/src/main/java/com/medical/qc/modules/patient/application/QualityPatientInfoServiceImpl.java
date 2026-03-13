package com.medical.qc.modules.patient.application;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.unified.application.UnifiedPatientInfoQueryService;
import com.medical.qc.modules.patient.application.support.UnifiedPatientInfoWriteService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 质控项患者信息服务实现。
 *
 * <p>当前已切换为基于统一模型的新实现。</p>
 */
@Service
public class QualityPatientInfoServiceImpl {
    private final UnifiedPatientInfoQueryService unifiedPatientInfoQueryService;
    private final UnifiedPatientInfoWriteService unifiedPatientInfoWriteService;

    public QualityPatientInfoServiceImpl(UnifiedPatientInfoQueryService unifiedPatientInfoQueryService,
                                         UnifiedPatientInfoWriteService unifiedPatientInfoWriteService) {
        this.unifiedPatientInfoQueryService = unifiedPatientInfoQueryService;
        this.unifiedPatientInfoWriteService = unifiedPatientInfoWriteService;
    }

    /**
     * 分页查询患者信息。
     */
    public Map<String, Object> getPatientPage(String taskType,
                                              String keyword,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber,
                                              Integer page,
                                              Integer limit) {
        return unifiedPatientInfoQueryService.getPatientPage(
                taskType,
                keyword,
                patientId,
                patientName,
                accessionNumber,
                page,
                limit);
    }

    /**
     * 新增患者信息。
     */
    public QualityPatientInfo createPatient(String taskType,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        return unifiedPatientInfoWriteService.createPatient(taskType, request, imageFile);
    }

    /**
     * 更新患者信息。
     */
    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        return unifiedPatientInfoWriteService.updatePatient(taskType, id, request, imageFile);
    }

    /**
     * 删除患者信息。
     */
    public void deletePatient(String taskType, Long id) {
        unifiedPatientInfoWriteService.deletePatient(id);
    }

    /**
     * 根据检查号查询患者信息。
     */
    public QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber) {
        return unifiedPatientInfoQueryService.getByAccessionNumber(taskType, accessionNumber);
    }

    /**
     * 根据检查号新增或更新患者信息。
     */
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath) {
        return unifiedPatientInfoWriteService.upsertPatientByAccessionNumber(
                taskType,
                patientId,
                patientName,
                accessionNumber,
                gender,
                age,
                studyDate,
                imagePath);
    }

    /**
     * 从 PACS 缓存批量初始化当前质控项的统一患者主数据。
     */
    public Map<String, Object> syncPatientsFromPacs(String taskType) {
        return unifiedPatientInfoWriteService.syncPatientsFromPacs(taskType);
    }
}

