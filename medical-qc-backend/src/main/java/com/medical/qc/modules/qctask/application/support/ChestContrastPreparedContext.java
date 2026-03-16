package com.medical.qc.modules.qctask.application.support;

import java.time.LocalDate;

/**
 * CT胸部增强 mock/真实链路共用的输入准备上下文。
 */
public record ChestContrastPreparedContext(
        String sourceMode,
        String analysisFilePath,
        String patientName,
        String examId,
        String gender,
        Integer age,
        LocalDate studyDate,
        String scannerModel,
        String originalFilename,
        Double flowRate,
        Integer contrastVolume,
        String injectionSite,
        Double sliceThickness,
        Integer bolusTrackingHu,
        Integer scanDelaySec) {
}
