package com.medical.qc.modules.patient.application.support;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.shared.storage.FileStorageGateway;
import com.medical.qc.shared.storage.StoredFile;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.UUID;

/**
 * 患者信息影像处理支持组件。
 */
@Component
public class PatientInfoImageSupport {
    private final FileStorageGateway fileStorageGateway;

    public PatientInfoImageSupport(FileStorageGateway fileStorageGateway) {
        this.fileStorageGateway = fileStorageGateway;
    }

    public void validateImageFile(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return;
        }

        String filename = imageFile.getOriginalFilename();
        String extension = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(extension.endsWith(".png")
                || extension.endsWith(".jpg")
                || extension.endsWith(".jpeg")
                || extension.endsWith(".bmp"))) {
            throw new IllegalArgumentException("患者影像图片仅支持 PNG/JPG/JPEG/BMP 格式");
        }
    }

    public String storeUploadedImage(String taskType, MultipartFile imageFile, String accessionNumber) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            String safeAccessionNumber = normalizeText(accessionNumber);
            if (safeAccessionNumber == null) {
                safeAccessionNumber = UUID.randomUUID().toString();
            }
            safeAccessionNumber = safeAccessionNumber.replaceAll("[^a-zA-Z0-9_-]", "_");

            String extension = resolveImageExtension(imageFile.getOriginalFilename());
            String filename = safeAccessionNumber + "_" + UUID.randomUUID() + extension;
            StoredFile storedFile = fileStorageGateway.store(
                    imageFile,
                    "patient-info/" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "/" + filename);
            return storedFile.getPublicPath();
        } catch (IOException exception) {
            throw new IllegalStateException("保存患者影像图片失败：" + exception.getMessage(), exception);
        }
    }

    public String copyPacsImageToPatientDirectory(String taskType, String imageFilePath, String accessionNumber) {
        String normalizedImageFilePath = normalizeText(imageFilePath);
        if (normalizedImageFilePath == null) {
            return null;
        }

        Path sourcePath = Paths.get(normalizedImageFilePath);
        if (Files.notExists(sourcePath)) {
            return null;
        }

        try {
            String safeAccessionNumber = normalizeText(accessionNumber);
            if (safeAccessionNumber == null) {
                safeAccessionNumber = UUID.randomUUID().toString();
            }
            safeAccessionNumber = safeAccessionNumber.replaceAll("[^a-zA-Z0-9_-]", "_");

            String extension = resolveImageExtension(sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString());
            String filename = safeAccessionNumber + "_" + UUID.randomUUID() + extension;
            StoredFile storedFile = fileStorageGateway.copy(
                    sourcePath,
                    "patient-info/" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "/pacs-sync/" + filename);
            return storedFile.getPublicPath();
        } catch (IOException exception) {
            return null;
        }
    }

    public boolean matchesTaskType(String taskType, PacsStudyCache pacsStudy) {
        if (pacsStudy == null) {
            return false;
        }

        String bodyPart = normalizeText(pacsStudy.getBodyPart());
        String studyDescription = normalizeText(pacsStudy.getStudyDescription());
        String mergedText = ((bodyPart == null ? "" : bodyPart) + " " + (studyDescription == null ? "" : studyDescription))
                .toLowerCase(Locale.ROOT);

        return switch (taskType) {
            case QualityPatientTaskSupport.TASK_TYPE_HEAD,
                 QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE -> mergedText.contains("头");
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST ->
                    (mergedText.contains("胸") || mergedText.contains("肺")) && !mergedText.contains("增强") && !mergedText.contains("cta");
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST ->
                    (mergedText.contains("胸") || mergedText.contains("肺")) && mergedText.contains("增强");
            case QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA ->
                    mergedText.contains("cta") || mergedText.contains("冠脉") || mergedText.contains("冠状动脉");
            default -> false;
        };
    }

    private String resolveImageExtension(String filename) {
        String normalizedFilename = filename == null ? "" : filename.trim().toLowerCase(Locale.ROOT);
        if (normalizedFilename.endsWith(".jpg")) {
            return ".jpg";
        }
        if (normalizedFilename.endsWith(".jpeg")) {
            return ".jpeg";
        }
        if (normalizedFilename.endsWith(".bmp")) {
            return ".bmp";
        }
        return ".png";
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

