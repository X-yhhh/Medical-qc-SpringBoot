package com.medical.qc.modules.qctask.application.support;

import com.medical.qc.messaging.MockQualityTaskMessage;
import com.medical.qc.modules.pacs.application.PacsServiceImpl;
import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * CT胸部增强任务上下文准备服务。
 *
 * <p>当前任务仍走 mock，但 PACS 模式下需要从任务专属 PACS 表带回患者与设备信息。</p>
 */
@Component
public class ChestContrastPreparationService {
    private static final String DEFAULT_SCANNER_MODEL = "胸部增强 CT 采集设备";

    private final PacsServiceImpl pacsService;

    public ChestContrastPreparationService(PacsServiceImpl pacsService) {
        this.pacsService = pacsService;
    }

    public ChestContrastPreparedContext prepare(MockQualityTaskMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("质控任务消息不能为空");
        }

        String sourceMode = MockQualityAnalysisSupport.normalizeSourceMode(message.getSourceMode());
        if (MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode)) {
            return prepareFromPacs(message, sourceMode);
        }
        return prepareFromLocalUpload(message, sourceMode);
    }

    private ChestContrastPreparedContext prepareFromLocalUpload(MockQualityTaskMessage message, String sourceMode) {
        Map<String, Object> metadata = message.getMetadata();
        return new ChestContrastPreparedContext(
                sourceMode,
                normalizeText(message.getStoredFilePath()),
                firstNonBlank(message.getPatientName(), "匿名患者"),
                normalizeText(message.getExamId()),
                null,
                null,
                null,
                DEFAULT_SCANNER_MODEL,
                normalizeText(message.getOriginalFilename()),
                parseDouble(metadata == null ? null : metadata.get("flow_rate")),
                parseInteger(metadata == null ? null : metadata.get("contrast_volume")),
                normalizeObjectText(metadata == null ? null : metadata.get("injection_site")),
                parseDouble(metadata == null ? null : metadata.get("slice_thickness")),
                parseInteger(metadata == null ? null : metadata.get("bolus_tracking_hu")),
                parseInteger(metadata == null ? null : metadata.get("scan_delay_sec")));
    }

    private ChestContrastPreparedContext prepareFromPacs(MockQualityTaskMessage message, String sourceMode) {
        String examId = normalizeText(message.getExamId());
        if (examId == null) {
            throw new IllegalArgumentException("PACS 模式下必须提供检查号");
        }

        List<PacsStudyCache> studies = pacsService.searchStudies(
                QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST,
                null,
                null,
                examId,
                null,
                null);
        if (studies.isEmpty()) {
            Map<String, Object> metadata = message.getMetadata();
            return new ChestContrastPreparedContext(
                    sourceMode,
                    normalizeText(message.getStoredFilePath()),
                    firstNonBlank(message.getPatientName(), "匿名患者"),
                    examId,
                    null,
                    null,
                    null,
                    DEFAULT_SCANNER_MODEL,
                    normalizeText(message.getOriginalFilename()),
                    parseDouble(metadata == null ? null : metadata.get("flow_rate")),
                    parseInteger(metadata == null ? null : metadata.get("contrast_volume")),
                    normalizeObjectText(metadata == null ? null : metadata.get("injection_site")),
                    parseDouble(metadata == null ? null : metadata.get("slice_thickness")),
                    parseInteger(metadata == null ? null : metadata.get("bolus_tracking_hu")),
                    parseInteger(metadata == null ? null : metadata.get("scan_delay_sec")));
        }

        PacsStudyCache study = studies.get(0);
        String imageFilePath = normalizeText(study.getImageFilePath());
        String originalFilename = null;
        if (imageFilePath != null) {
            Path path = Paths.get(imageFilePath);
            originalFilename = path.getFileName() == null ? null : path.getFileName().toString();
        }

        Map<String, Object> metadata = message.getMetadata();
        return new ChestContrastPreparedContext(
                sourceMode,
                imageFilePath,
                firstNonBlank(message.getPatientName(), normalizeText(study.getPatientName()), "匿名患者"),
                examId,
                normalizeText(study.getGender()),
                normalizeAge(study.getAge()),
                study.getStudyDate(),
                resolvePacsScannerModel(study),
                firstNonBlank(originalFilename, normalizeText(message.getOriginalFilename())),
                firstNonNull(parseDouble(metadata == null ? null : metadata.get("flow_rate")), study.getFlowRate()),
                firstNonNull(parseInteger(metadata == null ? null : metadata.get("contrast_volume")), study.getContrastVolume()),
                firstNonBlank(normalizeObjectText(metadata == null ? null : metadata.get("injection_site")), normalizeText(study.getInjectionSite())),
                firstNonNull(parseDouble(metadata == null ? null : metadata.get("slice_thickness")), study.getSliceThickness()),
                firstNonNull(parseInteger(metadata == null ? null : metadata.get("bolus_tracking_hu")), study.getBolusTrackingHu()),
                firstNonNull(parseInteger(metadata == null ? null : metadata.get("scan_delay_sec")), study.getScanDelaySec()));
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

    private <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
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
