package com.medical.qc.modules.qctask.application.support;

import java.time.LocalDate;

/**
 * 头部 CT 平扫真实推理前的输入准备上下文。
 */
public record HeadQualityPreparedContext(
        String sourceMode,
        String analysisVolumePath,
        String patientName,
        String examId,
        String gender,
        Integer age,
        LocalDate studyDate,
        String scannerModel,
        String originalFilename) {
}
