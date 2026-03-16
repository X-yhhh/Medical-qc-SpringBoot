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
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * 冠脉CTA任务上下文准备服务。
 */
@Component
public class CoronaryCtaPreparationService {
    private static final String DEFAULT_SCANNER_MODEL = "冠脉CTA采集设备";

    private final PacsServiceImpl pacsService;

    public CoronaryCtaPreparationService(PacsServiceImpl pacsService) {
        this.pacsService = pacsService;
    }

    /**
     * 规则链路只准备上下文，不强制要求影像文件真实存在。
     */
    public CoronaryCtaPreparedContext prepareForRule(MockQualityTaskMessage message) {
        return prepareInternal(message, false);
    }

    private CoronaryCtaPreparedContext prepareInternal(MockQualityTaskMessage message, boolean requireExistingFile) {
        if (message == null) {
            throw new IllegalArgumentException("质控任务消息不能为空");
        }
        String sourceMode = MockQualityAnalysisSupport.normalizeSourceMode(message.getSourceMode());
        if ("pacs".equalsIgnoreCase(sourceMode)) {
            return prepareFromPacs(message, sourceMode, requireExistingFile);
        }
        return prepareFromLocalUpload(message, sourceMode, requireExistingFile);
    }

    private CoronaryCtaPreparedContext prepareFromLocalUpload(MockQualityTaskMessage message,
                                                              String sourceMode,
                                                              boolean requireExistingFile) {
        String storedFilePath = normalizeText(message.getStoredFilePath());
        if (storedFilePath == null) {
            throw new IllegalArgumentException("冠脉CTA本地上传任务缺少已存储影像文件");
        }
        Path volumePath = Paths.get(storedFilePath);
        if (requireExistingFile && !Files.exists(volumePath)) {
            throw new IllegalArgumentException("冠脉CTA影像文件不存在: " + storedFilePath);
        }
        validateSupportedMedicalInput(volumePath.toString());
        Map<String, Object> metadata = message.getMetadata();
        return new CoronaryCtaPreparedContext(
                sourceMode,
                volumePath.toString(),
                firstNonBlank(message.getPatientName(), "匿名患者"),
                normalizeText(message.getExamId()),
                null,
                null,
                null,
                DEFAULT_SCANNER_MODEL,
                normalizeText(message.getOriginalFilename()),
                parseInteger(metadata == null ? null : metadata.get("heart_rate")),
                parseInteger(metadata == null ? null : metadata.get("hr_variability")),
                normalizeObjectText(metadata == null ? null : metadata.get("recon_phase")),
                normalizeObjectText(metadata == null ? null : metadata.get("kvp")),
                parseDouble(metadata == null ? null : metadata.get("slice_thickness")));
    }

    private CoronaryCtaPreparedContext prepareFromPacs(MockQualityTaskMessage message,
                                                       String sourceMode,
                                                       boolean requireExistingFile) {
        String examId = normalizeText(message.getExamId());
        if (examId == null) {
            throw new IllegalArgumentException("PACS 模式下必须提供检查号");
        }
        List<PacsStudyCache> studies = pacsService.searchStudies(
                QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA,
                null,
                null,
                examId,
                null,
                null);
        if (studies.isEmpty()) {
            throw new IllegalArgumentException("未找到检查号为 " + examId + " 的冠脉CTA PACS 记录");
        }
        PacsStudyCache study = studies.get(0);
        String imageFilePath = normalizeText(study.getImageFilePath());
        if (imageFilePath == null) {
            throw new IllegalArgumentException("PACS 记录中未配置冠脉CTA影像路径");
        }
        Path volumePath = Paths.get(imageFilePath);
        if (requireExistingFile && !Files.exists(volumePath)) {
            throw new IllegalArgumentException("PACS 影像文件不存在: " + imageFilePath);
        }
        validateSupportedMedicalInput(imageFilePath);

        Map<String, Object> metadata = message.getMetadata();
        return new CoronaryCtaPreparedContext(
                sourceMode,
                imageFilePath,
                firstNonBlank(message.getPatientName(), normalizeText(study.getPatientName()), "匿名患者"),
                examId,
                normalizeText(study.getGender()),
                normalizeAge(study.getAge()),
                study.getStudyDate(),
                resolvePacsScannerModel(study),
                normalizeText(volumePath.getFileName() == null ? null : volumePath.getFileName().toString()),
                firstNonNull(parseInteger(metadata == null ? null : metadata.get("heart_rate")), study.getHeartRate()),
                firstNonNull(parseInteger(metadata == null ? null : metadata.get("hr_variability")), study.getHrVariability()),
                firstNonBlank(normalizeObjectText(metadata == null ? null : metadata.get("recon_phase")), normalizeText(study.getReconPhase())),
                firstNonBlank(normalizeObjectText(metadata == null ? null : metadata.get("kvp")), normalizeText(study.getKvp())),
                parseDouble(metadata == null ? null : metadata.get("slice_thickness")));
    }

    private void validateSupportedMedicalInput(String filePath) {
        String lowerCasePath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (!(lowerCasePath.endsWith(".nii")
                || lowerCasePath.endsWith(".nii.gz")
                || lowerCasePath.endsWith(".dcm")
                || lowerCasePath.endsWith(".dicom")
                || lowerCasePath.endsWith(".zip"))) {
            throw new IllegalArgumentException("当前冠脉 CTA 链路仅支持 .nii / .nii.gz / .dcm / .dicom / .zip 影像输入");
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
        return age == null || age < 0 ? null : age;
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeObjectText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
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

    private Integer firstNonNull(Integer... values) {
        if (values == null) {
            return null;
        }
        for (Integer value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
