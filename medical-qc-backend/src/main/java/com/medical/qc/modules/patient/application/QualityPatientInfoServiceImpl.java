package com.medical.qc.modules.patient.application;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.modules.pacs.application.support.TaskScopedPacsStudyStorageService;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.patient.application.support.TaskScopedPatientInfoStorageService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质控项患者信息服务实现。
 *
 * <p>当前基于任务专属患者缓存表实现。</p>
 */
@Service
public class QualityPatientInfoServiceImpl {
    // 各质控项的本地上传患者缓存均通过任务专属表服务读写。
    private final TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService;
    // PACS 同步从任务专属 PACS 缓存表读取。
    private final TaskScopedPacsStudyStorageService taskScopedPacsStudyStorageService;

    public QualityPatientInfoServiceImpl(TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService,
                                         TaskScopedPacsStudyStorageService taskScopedPacsStudyStorageService) {
        this.taskScopedPatientInfoStorageService = taskScopedPatientInfoStorageService;
        this.taskScopedPacsStudyStorageService = taskScopedPacsStudyStorageService;
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
        return taskScopedPatientInfoStorageService.getPatientPage(
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
        return taskScopedPatientInfoStorageService.createPatient(taskType, request, imageFile);
    }

    /**
     * 更新患者信息。
     */
    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        return taskScopedPatientInfoStorageService.updatePatient(taskType, id, request, imageFile);
    }

    /**
     * 删除患者信息。
     */
    public void deletePatient(String taskType, Long id) {
        taskScopedPatientInfoStorageService.deletePatient(taskType, id);
    }

    /**
     * 根据检查号查询患者信息。
     */
    public QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber) {
        return taskScopedPatientInfoStorageService.getByAccessionNumber(taskType, accessionNumber);
    }

    /**
     * 根据检查号幂等新增或更新患者缓存记录。
     */
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath) {
        return taskScopedPatientInfoStorageService.upsertPatientByAccessionNumber(
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
        return taskScopedPatientInfoStorageService.syncPatientsFromPacs(
                taskType,
                taskScopedPacsStudyStorageService.listStudiesForSync(taskType));
    }
}

