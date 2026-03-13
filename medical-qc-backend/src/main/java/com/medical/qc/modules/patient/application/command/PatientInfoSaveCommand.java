package com.medical.qc.modules.patient.application.command;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import org.springframework.web.multipart.MultipartFile;

/**
 * 患者信息保存命令。
 */
public record PatientInfoSaveCommand(String taskType,
                                     Long id,
                                     QualityPatientInfoSaveReq request,
                                     MultipartFile imageFile) {
}

