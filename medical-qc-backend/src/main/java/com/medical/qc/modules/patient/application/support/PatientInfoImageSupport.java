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
    // 文件真正落盘和复制都委托给统一文件存储网关。
    private final FileStorageGateway fileStorageGateway;

    public PatientInfoImageSupport(FileStorageGateway fileStorageGateway) {
        this.fileStorageGateway = fileStorageGateway;
    }

    /**
     * 校验上传图片格式。
     */
    public void validateImageFile(MultipartFile imageFile) {
        if (imageFile == null || imageFile.isEmpty()) {
            return;
        }

        // 与前端上传组件保持一致，只允许常见位图格式。
        String filename = imageFile.getOriginalFilename();
        String extension = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (!(extension.endsWith(".png")
                || extension.endsWith(".jpg")
                || extension.endsWith(".jpeg")
                || extension.endsWith(".bmp"))) {
            throw new IllegalArgumentException("患者影像图片仅支持 PNG/JPG/JPEG/BMP 格式");
        }
    }

    /**
     * 保存本地上传的患者影像图片。
     * 数据链路：MultipartFile -> FileStorageGateway.store -> /uploads/patient-info/... 公共路径。
     */
    public String storeUploadedImage(String taskType, MultipartFile imageFile, String accessionNumber) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            // 先把检查号清洗成安全文件名前缀，缺失时退化为随机 UUID。
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

    /**
     * 把 PACS 缓存图片复制到患者图片目录。
     */
    public String copyPacsImageToPatientDirectory(String taskType, String imageFilePath, String accessionNumber) {
        String normalizedImageFilePath = normalizeText(imageFilePath);
        if (normalizedImageFilePath == null) {
            return null;
        }

        // PACS 缓存文件不存在时直接跳过当前记录。
        Path sourcePath = Paths.get(normalizedImageFilePath);
        if (Files.notExists(sourcePath)) {
            return null;
        }

        try {
            // 同样以检查号作为文件名前缀，便于后续追踪。
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

    /**
     * 根据 PACS 记录的部位和描述判断其是否属于当前 taskType。
     */
    public boolean matchesTaskType(String taskType, PacsStudyCache pacsStudy) {
        if (pacsStudy == null) {
            return false;
        }

        // bodyPart + studyDescription 合并后做宽松文本匹配。
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

    /**
     * 解析图片扩展名，未知格式统一回退为 png。
     */
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

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

