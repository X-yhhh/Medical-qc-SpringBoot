package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.messaging.MockQualityTaskMessage;
import com.medical.qc.modules.pacs.application.PacsServiceImpl;
import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * CT胸部平扫真实推理输入准备服务。
 *
 * <p>当前允许 NIfTI、DICOM 文件和打包 DICOM ZIP，由 Python 侧统一识别。</p>
 */
@Component
public class ChestNonContrastPreparationService {
    private static final String DEFAULT_SCANNER_MODEL = "胸部 CT 平扫采集设备";

    private final PacsServiceImpl pacsService;

    public ChestNonContrastPreparationService(PacsServiceImpl pacsService) {
        this.pacsService = pacsService;
    }

    public ChestNonContrastPreparedContext prepare(MockQualityTaskMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("质控任务消息不能为空");
        }

        String sourceMode = MockQualityAnalysisSupport.normalizeSourceMode(message.getSourceMode());
        if ("pacs".equalsIgnoreCase(sourceMode)) {
            return prepareFromPacs(message, sourceMode);
        }
        return prepareFromLocalUpload(message, sourceMode);
    }

    private ChestNonContrastPreparedContext prepareFromLocalUpload(MockQualityTaskMessage message, String sourceMode) {
        String storedFilePath = normalizeText(message.getStoredFilePath());
        if (storedFilePath == null) {
            throw new IllegalArgumentException("胸部平扫本地上传任务缺少已存储影像文件");
        }

        Path volumePath = Paths.get(storedFilePath);
        if (!Files.exists(volumePath)) {
            throw new IllegalArgumentException("胸部平扫影像文件不存在: " + storedFilePath);
        }
        validateSupportedMedicalInput(volumePath.toString());

        return new ChestNonContrastPreparedContext(
                sourceMode,
                volumePath.toString(),
                firstNonBlank(message.getPatientName(), "匿名患者"),
                normalizeText(message.getExamId()),
                null,
                null,
                null,
                DEFAULT_SCANNER_MODEL,
                normalizeText(message.getOriginalFilename()));
    }

    private ChestNonContrastPreparedContext prepareFromPacs(MockQualityTaskMessage message, String sourceMode) {
        String examId = normalizeText(message.getExamId());
        if (examId == null) {
            throw new IllegalArgumentException("PACS 模式下必须提供检查号");
        }

        List<PacsStudyCache> studies = pacsService.searchStudies(
                QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST,
                null,
                null,
                examId,
                null,
                null);
        if (studies.isEmpty()) {
            throw new IllegalArgumentException("未找到检查号为 " + examId + " 的胸部平扫 PACS 记录");
        }

        PacsStudyCache study = studies.get(0);
        String imageFilePath = normalizeText(study.getImageFilePath());
        if (imageFilePath == null) {
            throw new IllegalArgumentException("PACS 记录中未配置胸部平扫影像路径");
        }
        Path volumePath = Paths.get(imageFilePath);
        if (!Files.exists(volumePath)) {
            throw new IllegalArgumentException("PACS 影像文件不存在: " + imageFilePath);
        }
        validateSupportedMedicalInput(imageFilePath);

        return new ChestNonContrastPreparedContext(
                sourceMode,
                imageFilePath,
                firstNonBlank(message.getPatientName(), normalizeText(study.getPatientName()), "匿名患者"),
                examId,
                normalizeText(study.getGender()),
                normalizeAge(study.getAge()),
                study.getStudyDate(),
                resolvePacsScannerModel(study),
                normalizeText(volumePath.getFileName() == null ? null : volumePath.getFileName().toString()));
    }

    private void validateSupportedMedicalInput(String filePath) {
        String lowerCasePath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (!(lowerCasePath.endsWith(".nii")
                || lowerCasePath.endsWith(".nii.gz")
                || lowerCasePath.endsWith(".dcm")
                || lowerCasePath.endsWith(".dicom")
                || lowerCasePath.endsWith(".zip"))) {
            throw new IllegalArgumentException("当前胸部平扫链路仅支持 .nii / .nii.gz / .dcm / .dicom / .zip 影像输入");
        }
    }

    private String resolvePacsScannerModel(PacsStudyCache study) {
        if (study == null) {
            return DEFAULT_SCANNER_MODEL;
        }
        String merged = Stream.of(normalizeText(study.getManufacturer()), normalizeText(study.getModelName()))
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return merged == null ? DEFAULT_SCANNER_MODEL : merged;
    }

    private Integer normalizeAge(Integer age) {
        if (age == null || age < 0) {
            return null;
        }
        return age;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = normalizeText(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
