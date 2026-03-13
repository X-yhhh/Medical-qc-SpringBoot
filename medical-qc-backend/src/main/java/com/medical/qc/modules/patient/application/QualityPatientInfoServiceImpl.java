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
    // 读操作统一走统一患者查询服务。
    private final UnifiedPatientInfoQueryService unifiedPatientInfoQueryService;
    // 写操作统一走统一患者写服务，负责主数据、检查和预览图联动落库。
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
        // 列表查询只负责透传筛选条件，具体分页和映射逻辑由统一查询服务处理。
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
        // 新增场景必须经过统一写服务校验检查号、图片和患者主数据关系。
        return unifiedPatientInfoWriteService.createPatient(taskType, request, imageFile);
    }

    /**
     * 更新患者信息。
     */
    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        // 更新时由统一写服务负责保留旧图片或替换为新图片。
        return unifiedPatientInfoWriteService.updatePatient(taskType, id, request, imageFile);
    }

    /**
     * 删除患者信息。
     */
    public void deletePatient(String taskType, Long id) {
        // 删除逻辑在统一模型中会连带检查预览图和空患者主记录清理。
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
        // 该入口给脑出血检测和 PACS 同步复用，用检查号做幂等更新。
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
        // 批量同步场景由统一写服务负责统计新增、更新和跳过数量。
        return unifiedPatientInfoWriteService.syncPatientsFromPacs(taskType);
    }
}

