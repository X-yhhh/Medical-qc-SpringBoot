package com.medical.qc.modules.qctask.application.support;

import java.time.LocalDate;

/**
 * 冠脉CTA真实推理输入准备上下文。
 */
public record CoronaryCtaPreparedContext(
        String sourceMode,
        String analysisVolumePath,
        String patientName,
        String examId,
        String gender,
        Integer age,
        LocalDate studyDate,
        String scannerModel,
        String originalFilename,
        Integer heartRate,
        Integer hrVariability,
        String reconPhase,
        String kVp,
        Double sliceThickness) {
}
