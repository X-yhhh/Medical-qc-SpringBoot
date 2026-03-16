package com.medical.qc.modules.qctask.application.support;

import java.time.LocalDate;

/**
 * CT胸部平扫真实推理输入准备上下文。
 */
public record ChestNonContrastPreparedContext(
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
