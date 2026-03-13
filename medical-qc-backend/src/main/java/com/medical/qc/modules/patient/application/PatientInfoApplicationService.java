package com.medical.qc.modules.patient.application;

import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.patient.application.command.PatientInfoSaveCommand;
import com.medical.qc.modules.patient.application.query.PatientInfoPageQuery;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 患者信息应用服务。
 *
 * <p>当前用于承接五类质控患者信息的统一管理入口，
 * 后续可逐步迁移为基于统一患者主数据模型的应用层。</p>
 */
@Service
public class PatientInfoApplicationService {
    // 具体读写逻辑下沉到患者服务实现，这里只保留应用层入口。
    private final QualityPatientInfoServiceImpl qualityPatientInfoService;

    public PatientInfoApplicationService(QualityPatientInfoServiceImpl qualityPatientInfoService) {
        this.qualityPatientInfoService = qualityPatientInfoService;
    }

    /**
     * 查询患者分页列表。
     */
    public Map<String, Object> getPatientPage(PatientInfoPageQuery query) {
        return qualityPatientInfoService.getPatientPage(
                query.taskType(),
                query.keyword(),
                query.patientId(),
                query.patientName(),
                query.accessionNumber(),
                query.page(),
                query.limit());
    }

    /**
     * 新增患者信息。
     */
    public QualityPatientInfo createPatient(PatientInfoSaveCommand command) {
        return qualityPatientInfoService.createPatient(command.taskType(), command.request(), command.imageFile());
    }

    /**
     * 更新患者信息。
     */
    public QualityPatientInfo updatePatient(PatientInfoSaveCommand command) {
        return qualityPatientInfoService.updatePatient(
                command.taskType(),
                command.id(),
                command.request(),
                command.imageFile());
    }

    /**
     * 删除患者信息。
     */
    public void deletePatient(String taskType, Long id) {
        qualityPatientInfoService.deletePatient(taskType, id);
    }

    /**
     * 从 PACS 缓存同步患者数据。
     */
    public Map<String, Object> syncPatientsFromPacs(String taskType) {
        return qualityPatientInfoService.syncPatientsFromPacs(taskType);
    }
}

