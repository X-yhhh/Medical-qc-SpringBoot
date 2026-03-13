package com.medical.qc.modules.patient.application.command;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import org.springframework.web.multipart.MultipartFile;

/**
 * 患者信息保存命令。
 *
 * @param taskType 当前页面或流程对应的质控任务类型
 * @param id 编辑场景下的患者主记录 ID；新增时为 null
 * @param request 患者表单请求体
 * @param imageFile 患者影像图片文件
 */
public record PatientInfoSaveCommand(String taskType,
                                     Long id,
                                     QualityPatientInfoSaveReq request,
                                     MultipartFile imageFile) {
}

