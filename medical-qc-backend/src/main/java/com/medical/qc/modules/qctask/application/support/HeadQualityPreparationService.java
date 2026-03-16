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
 * 头部 CT 平扫质控输入准备服务。
 *
 * <p>负责统一处理本地上传与 PACS 调取两种来源下的体数据路径和患者信息。</p>
 * <p>当前允许 NIfTI、单个 DICOM 文件或打包 DICOM ZIP，由 Python 推理服务负责进一步识别。</p>
 */
@Component
public class HeadQualityPreparationService {
    private static final String DEFAULT_SCANNER_MODEL = "头部 CT 平扫采集设备";

    private final PacsServiceImpl pacsService;

    public HeadQualityPreparationService(PacsServiceImpl pacsService) {
        this.pacsService = pacsService;
    }

    /**
     * 为头部平扫真实推理准备输入上下文。
     */
    public HeadQualityPreparedContext prepare(MockQualityTaskMessage message) {
        if (message == null) {
            throw new IllegalArgumentException("质控任务消息不能为空");
        }

        String sourceMode = MockQualityAnalysisSupport.normalizeSourceMode(message.getSourceMode());
        if ("pacs".equalsIgnoreCase(sourceMode)) {
            return prepareFromPacs(message, sourceMode);
        }
        return prepareFromLocalUpload(message, sourceMode);
    }

    /**
     * 本地上传模式直接使用受管存储中的 NIfTI 文件。
     */
    private HeadQualityPreparedContext prepareFromLocalUpload(MockQualityTaskMessage message, String sourceMode) {
        String storedFilePath = normalizeText(message.getStoredFilePath());
        if (storedFilePath == null) {
            throw new IllegalArgumentException("头部平扫本地上传任务缺少已存储的影像文件");
        }

        Path volumePath = Paths.get(storedFilePath);
        if (!Files.exists(volumePath)) {
            throw new IllegalArgumentException("头部平扫影像文件不存在: " + storedFilePath);
        }
        validateSupportedMedicalInput(volumePath.toString());

        return new HeadQualityPreparedContext(
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

    /**
     * PACS 模式下根据检查号检索缓存记录，并直接复用其服务器侧影像路径。
     */
    private HeadQualityPreparedContext prepareFromPacs(MockQualityTaskMessage message, String sourceMode) {
        String examId = normalizeText(message.getExamId());
        if (examId == null) {
            throw new IllegalArgumentException("PACS 模式下必须提供检查号");
        }

        List<PacsStudyCache> studies = pacsService.searchStudies(
                QualityPatientTaskSupport.TASK_TYPE_HEAD,
                null,
                null,
                examId,
                null,
                null);
        if (studies.isEmpty()) {
            throw new IllegalArgumentException("未找到检查号为 " + examId + " 的 PACS 记录");
        }

        PacsStudyCache study = studies.get(0);
        String imageFilePath = normalizeText(study.getImageFilePath());
        if (imageFilePath == null) {
            throw new IllegalArgumentException("PACS 记录中未配置头部平扫影像路径");
        }

        Path volumePath = Paths.get(imageFilePath);
        if (!Files.exists(volumePath)) {
            throw new IllegalArgumentException("PACS 影像文件不存在: " + imageFilePath);
        }
        validateSupportedMedicalInput(imageFilePath);

        return new HeadQualityPreparedContext(
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

    /**
     * 当前真实头部平扫模型只支持 NIfTI 体数据。
     */
    private void validateSupportedMedicalInput(String filePath) {
        String lowerCasePath = filePath == null ? "" : filePath.toLowerCase(Locale.ROOT);
        if (!(lowerCasePath.endsWith(".nii")
                || lowerCasePath.endsWith(".nii.gz")
                || lowerCasePath.endsWith(".dcm")
                || lowerCasePath.endsWith(".dicom")
                || lowerCasePath.endsWith(".zip"))) {
            throw new IllegalArgumentException("当前头部平扫链路仅支持 .nii / .nii.gz / .dcm / .dicom / .zip 影像输入");
        }
    }

    /**
     * 解析 PACS 厂商与型号为统一展示文案。
     */
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

    /**
     * 归一化年龄字段。
     */
    private Integer normalizeAge(Integer age) {
        if (age == null || age < 0) {
            return null;
        }
        return age;
    }

    /**
     * 返回第一个非空文本。
     */
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

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
