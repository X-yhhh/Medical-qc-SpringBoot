package com.medical.qc.modules.qcresult.application.support;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.pacs.application.PacsServiceImpl;
import com.medical.qc.shared.storage.FileStorageGateway;
import com.medical.qc.shared.storage.StoredFile;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * 脑出血检测输入准备服务。
 *
 * <p>负责统一处理本地上传与 PACS 取片模式下的影像准备与基础信息回填。</p>
 */
@Component
public class HemorrhagePreparationService {
    private static final String DEFAULT_SCANNER_MODEL = "头颅 CT 标准采集设备";

    private final PacsServiceImpl pacsService;
    private final FileStorageGateway fileStorageGateway;

    public HemorrhagePreparationService(PacsServiceImpl pacsService,
                                        FileStorageGateway fileStorageGateway) {
        this.pacsService = pacsService;
        this.fileStorageGateway = fileStorageGateway;
    }

    public HemorrhagePreparedContext prepare(MultipartFile file,
                                             String patientName,
                                             String patientCode,
                                             String examId,
                                             String gender,
                                             Integer age,
                                             LocalDate studyDate,
                                             String sourceMode) throws IOException {
        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(sourceMode);
        String resolvedPatientName = normalizeText(patientName);
        String resolvedPatientCode = normalizeText(patientCode);
        String resolvedGender = normalizeText(gender);
        Integer resolvedAge = normalizeAge(age);
        LocalDate resolvedStudyDate = studyDate;
        String resolvedScannerModel = DEFAULT_SCANNER_MODEL;
        String imagePathForAnalysis;
        String savedImagePath;

        if ("pacs".equalsIgnoreCase(normalizedSourceMode)) {
            if (examId == null || examId.trim().isEmpty()) {
                throw new IllegalArgumentException("PACS模式下必须提供检查号(examId)");
            }

            List<PacsStudyCache> studies = pacsService.searchStudies(
                    QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE,
                    null,
                    null,
                    examId,
                    null,
                    null);
            if (studies.isEmpty()) {
                throw new IllegalArgumentException("未找到检查号为 " + examId + " 的PACS记录");
            }

            PacsStudyCache pacsStudy = studies.get(0);
            String pacsImageFilePath = normalizeText(pacsStudy.getImageFilePath());
            if (pacsImageFilePath == null) {
                throw new IllegalArgumentException("PACS记录中未配置影像文件路径");
            }

            Path pacsImagePath = Paths.get(pacsImageFilePath);
            if (!Files.exists(pacsImagePath)) {
                throw new IllegalArgumentException("PACS影像文件不存在: " + pacsImageFilePath);
            }

            imagePathForAnalysis = pacsImageFilePath;
            savedImagePath = copyPacsImageToUploads(pacsImagePath, examId);

            resolvedPatientName = firstNonBlank(resolvedPatientName, normalizeText(pacsStudy.getPatientName()));
            resolvedPatientCode = firstNonBlank(resolvedPatientCode,
                    normalizeText(pacsStudy.getPatientId()),
                    normalizeText(examId));
            resolvedGender = firstNonBlank(resolvedGender, normalizeText(pacsStudy.getGender()));
            resolvedAge = resolvedAge != null ? resolvedAge : normalizeAge(pacsStudy.getAge());
            resolvedStudyDate = resolvedStudyDate != null ? resolvedStudyDate : pacsStudy.getStudyDate();
            resolvedScannerModel = resolvePacsScannerModel(pacsStudy);
        } else {
            validateHemorrhageFile(file);
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            StoredFile storedFile = fileStorageGateway.store(file, filename);
            imagePathForAnalysis = storedFile.getAbsolutePath().toString();
            savedImagePath = storedFile.getPublicPath();
        }

        return new HemorrhagePreparedContext(
                normalizedSourceMode,
                imagePathForAnalysis,
                savedImagePath,
                resolvedPatientName,
                resolvedPatientCode,
                resolvedGender,
                resolvedAge,
                resolvedStudyDate,
                resolvedScannerModel);
    }

    private String copyPacsImageToUploads(Path pacsImagePath, String examId) throws IOException {
        String safeExamId = normalizeText(examId);
        if (safeExamId == null) {
            safeExamId = UUID.randomUUID().toString();
        }
        safeExamId = safeExamId.replaceAll("[^a-zA-Z0-9_-]", "_");

        String extension = resolveFileExtension(pacsImagePath.getFileName() == null
                ? ""
                : pacsImagePath.getFileName().toString());
        String targetFilename = safeExamId + "_" + UUID.randomUUID() + extension;
        StoredFile storedFile = fileStorageGateway.copy(pacsImagePath, "pacs/" + targetFilename);
        return storedFile.getPublicPath();
    }

    private String resolveFileExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return ".png";
        }

        String extension = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (extension.matches("\\.(png|jpg|jpeg|bmp)")) {
            return extension;
        }
        return ".png";
    }

    private String resolvePacsScannerModel(PacsStudyCache pacsStudy) {
        if (pacsStudy == null) {
            return DEFAULT_SCANNER_MODEL;
        }

        String manufacturer = normalizeText(pacsStudy.getManufacturer());
        String modelName = normalizeText(pacsStudy.getModelName());
        String mergedName = Stream.of(manufacturer, modelName)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return mergedName == null ? DEFAULT_SCANNER_MODEL : mergedName;
    }

    private void validateHemorrhageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("本地上传模式下必须提供影像文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("影像文件名不能为空");
        }

        String normalizedName = originalFilename.toLowerCase(Locale.ROOT);
        if (!(normalizedName.endsWith(".png")
                || normalizedName.endsWith(".jpg")
                || normalizedName.endsWith(".jpeg")
                || normalizedName.endsWith(".bmp"))) {
            throw new IllegalArgumentException("当前脑出血模型仅支持 PNG/JPG/JPEG/BMP 图片，暂不支持 DICOM 文件");
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            String normalizedValue = normalizeText(value);
            if (normalizedValue != null) {
                return normalizedValue;
            }
        }
        return null;
    }

    private Integer normalizeAge(Integer age) {
        if (age == null) {
            return null;
        }

        return age < 0 ? null : age;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

