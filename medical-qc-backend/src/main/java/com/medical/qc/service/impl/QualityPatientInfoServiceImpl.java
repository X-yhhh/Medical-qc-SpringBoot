package com.medical.qc.service.impl;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.entity.PacsStudyCache;
import com.medical.qc.entity.QualityPatientInfo;
import com.medical.qc.mapper.PacsStudyMapper;
import com.medical.qc.mapper.QualityPatientInfoMapper;
import com.medical.qc.service.QualityPatientInfoService;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

/**
 * 质控项患者信息服务实现。
 *
 * <p>当前五个质控项共用一套管理逻辑，仅通过任务类型切换实际操作的数据表。</p>
 */
@Service
public class QualityPatientInfoServiceImpl implements QualityPatientInfoService {
    private final Path patientImageRoot = Paths.get("uploads", "patient-info");

    private final QualityPatientInfoMapper qualityPatientInfoMapper;
    private final PacsStudyMapper pacsStudyMapper;

    public QualityPatientInfoServiceImpl(QualityPatientInfoMapper qualityPatientInfoMapper,
                                         PacsStudyMapper pacsStudyMapper) {
        this.qualityPatientInfoMapper = qualityPatientInfoMapper;
        this.pacsStudyMapper = pacsStudyMapper;
    }

    /**
     * 分页查询患者信息。
     */
    @Override
    public Map<String, Object> getPatientPage(String taskType,
                                              String keyword,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber,
                                              Integer page,
                                              Integer limit) {
        String tableName = requireTableName(taskType);
        int normalizedPage = normalizePage(page);
        int normalizedLimit = normalizeLimit(limit);
        int offset = (normalizedPage - 1) * normalizedLimit;

        List<QualityPatientInfo> patientList = qualityPatientInfoMapper.selectPage(
                tableName,
                normalizeText(keyword),
                normalizeText(patientId),
                normalizeText(patientName),
                normalizeText(accessionNumber),
                offset,
                normalizedLimit);
        long total = qualityPatientInfoMapper.countPage(
                tableName,
                normalizeText(keyword),
                normalizeText(patientId),
                normalizeText(patientName),
                normalizeText(accessionNumber));

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", normalizedPage);
        pagination.put("limit", normalizedLimit);
        pagination.put("total", total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("taskType", QualityPatientTaskSupport.normalizeTaskType(taskType));
        summary.put("taskLabel", QualityPatientTaskSupport.resolveTaskLabel(taskType));
        summary.put("totalPatients", total);

        Map<String, Object> response = new HashMap<>();
        response.put("data", patientList);
        response.put("pagination", pagination);
        response.put("summary", summary);
        return response;
    }

    /**
     * 新增患者信息。
     */
    @Override
    public QualityPatientInfo createPatient(String taskType,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        String tableName = requireTableName(taskType);
        validateRequest(request, null, imageFile, null);

        QualityPatientInfo existingPatient = qualityPatientInfoMapper.selectByAccessionNumber(
                tableName,
                request.getAccessionNumber().trim());
        if (existingPatient != null) {
            throw new IllegalArgumentException("当前检查号已存在患者信息，请直接编辑");
        }

        QualityPatientInfo entity = buildEntity(request);
        entity.setCreatedAt(LocalDateTime.now());
        entity.setUpdatedAt(entity.getCreatedAt());
        entity.setImagePath(storeUploadedImage(taskType, imageFile, request.getAccessionNumber()));
        qualityPatientInfoMapper.insertPatient(tableName, entity);
        return entity;
    }

    /**
     * 更新患者信息。
     */
    @Override
    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        String tableName = requireTableName(taskType);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }

        QualityPatientInfo existingPatient = qualityPatientInfoMapper.selectById(tableName, id);
        if (existingPatient == null) {
            throw new IllegalArgumentException("患者信息不存在");
        }

        validateRequest(request, id, imageFile, existingPatient);

        QualityPatientInfo duplicatedPatient = qualityPatientInfoMapper.selectByAccessionNumber(
                tableName,
                request.getAccessionNumber().trim());
        if (duplicatedPatient != null && !duplicatedPatient.getId().equals(id)) {
            throw new IllegalArgumentException("当前检查号已被其他患者信息占用");
        }

        QualityPatientInfo entity = buildEntity(request);
        entity.setId(id);
        entity.setCreatedAt(existingPatient.getCreatedAt());
        entity.setUpdatedAt(LocalDateTime.now());
        if (imageFile != null && !imageFile.isEmpty()) {
            entity.setImagePath(storeUploadedImage(taskType, imageFile, request.getAccessionNumber()));
        } else {
            entity.setImagePath(existingPatient.getImagePath());
        }
        qualityPatientInfoMapper.updatePatient(tableName, entity);
        return qualityPatientInfoMapper.selectById(tableName, id);
    }

    /**
     * 删除患者信息。
     */
    @Override
    public void deletePatient(String taskType, Long id) {
        String tableName = requireTableName(taskType);
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }

        QualityPatientInfo existingPatient = qualityPatientInfoMapper.selectById(tableName, id);
        if (existingPatient == null) {
            throw new IllegalArgumentException("患者信息不存在");
        }

        qualityPatientInfoMapper.deletePatient(tableName, id);
    }

    /**
     * 根据检查号查询患者信息。
     */
    @Override
    public QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber) {
        String tableName = requireTableName(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        if (normalizedAccessionNumber == null) {
            return null;
        }

        return qualityPatientInfoMapper.selectByAccessionNumber(tableName, normalizedAccessionNumber);
    }

    /**
     * 根据检查号新增或更新患者信息。
     */
    @Override
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath) {
        String tableName = requireTableName(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        String normalizedPatientName = normalizeText(patientName);
        if (normalizedAccessionNumber == null || normalizedPatientName == null) {
            return null;
        }

        QualityPatientInfo existingPatient = qualityPatientInfoMapper.selectByAccessionNumber(tableName, normalizedAccessionNumber);
        LocalDateTime now = LocalDateTime.now();
        if (existingPatient == null) {
            QualityPatientInfo entity = new QualityPatientInfo();
            entity.setPatientId(normalizeText(patientId));
            entity.setPatientName(normalizedPatientName);
            entity.setAccessionNumber(normalizedAccessionNumber);
            entity.setGender(normalizeText(gender));
            entity.setAge(normalizeAge(age));
            entity.setStudyDate(studyDate);
            entity.setImagePath(normalizeText(imagePath));
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            qualityPatientInfoMapper.insertPatient(tableName, entity);
            return entity;
        }

        existingPatient.setPatientId(normalizeText(patientId));
        existingPatient.setPatientName(normalizedPatientName);
        existingPatient.setAccessionNumber(normalizedAccessionNumber);
        existingPatient.setGender(normalizeText(gender));
        existingPatient.setAge(normalizeAge(age));
        existingPatient.setStudyDate(studyDate);
        existingPatient.setImagePath(firstNonBlank(normalizeText(imagePath), existingPatient.getImagePath()));
        existingPatient.setUpdatedAt(now);
        qualityPatientInfoMapper.updatePatient(tableName, existingPatient);
        return qualityPatientInfoMapper.selectById(tableName, existingPatient.getId());
    }

    /**
     * 从 PACS 缓存批量初始化当前质控项的患者信息表。
     */
    @Override
    public Map<String, Object> syncPatientsFromPacs(String taskType) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        requireTableName(normalizedTaskType);

        List<PacsStudyCache> pacsStudies = pacsStudyMapper.selectStudiesForSync();
        int totalCount = pacsStudies.size();
        int matchedCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (PacsStudyCache pacsStudy : pacsStudies) {
            if (!matchesTaskType(normalizedTaskType, pacsStudy)) {
                continue;
            }
            matchedCount += 1;

            String accessionNumber = normalizeText(pacsStudy.getAccessionNumber());
            String patientName = normalizeText(pacsStudy.getPatientName());
            if (accessionNumber == null || patientName == null) {
                skippedCount += 1;
                continue;
            }

            QualityPatientInfo existingPatient = getByAccessionNumber(normalizedTaskType, accessionNumber);
            if (existingPatient == null) {
                String copiedImagePath = copyPacsImageToPatientDirectory(normalizedTaskType,
                        pacsStudy.getImageFilePath(), accessionNumber);
                if (copiedImagePath == null) {
                    skippedCount += 1;
                    continue;
                }

                QualityPatientInfo entity = new QualityPatientInfo();
                entity.setPatientId(normalizeText(pacsStudy.getPatientId()));
                entity.setPatientName(patientName);
                entity.setAccessionNumber(accessionNumber);
                entity.setGender(normalizeText(pacsStudy.getGender()));
                entity.setAge(normalizeAge(pacsStudy.getAge()));
                entity.setStudyDate(pacsStudy.getStudyDate());
                entity.setImagePath(copiedImagePath);
                entity.setRemark("由 PACS 缓存批量初始化生成");
                entity.setCreatedAt(LocalDateTime.now());
                entity.setUpdatedAt(entity.getCreatedAt());
                qualityPatientInfoMapper.insertPatient(requireTableName(normalizedTaskType), entity);
                createdCount += 1;
                continue;
            }

            String copiedImagePath = copyPacsImageToPatientDirectory(normalizedTaskType,
                    pacsStudy.getImageFilePath(), accessionNumber);
            existingPatient.setPatientId(firstNonBlank(normalizeText(pacsStudy.getPatientId()), existingPatient.getPatientId()));
            existingPatient.setPatientName(firstNonBlank(patientName, existingPatient.getPatientName()));
            existingPatient.setGender(firstNonBlank(normalizeText(pacsStudy.getGender()), existingPatient.getGender()));
            existingPatient.setAge(pacsStudy.getAge() != null ? normalizeAge(pacsStudy.getAge()) : existingPatient.getAge());
            existingPatient.setStudyDate(pacsStudy.getStudyDate() != null ? pacsStudy.getStudyDate() : existingPatient.getStudyDate());
            existingPatient.setImagePath(firstNonBlank(
                    copiedImagePath,
                    existingPatient.getImagePath()));
            existingPatient.setRemark(firstNonBlank(existingPatient.getRemark(), "由 PACS 缓存批量初始化生成"));
            existingPatient.setUpdatedAt(LocalDateTime.now());
            qualityPatientInfoMapper.updatePatient(requireTableName(normalizedTaskType), existingPatient);
            updatedCount += 1;
        }

        Map<String, Object> response = new HashMap<>();
        response.put("taskType", normalizedTaskType);
        response.put("taskLabel", QualityPatientTaskSupport.resolveTaskLabel(normalizedTaskType));
        response.put("totalPacsStudies", totalCount);
        response.put("matchedStudies", matchedCount);
        response.put("createdCount", createdCount);
        response.put("updatedCount", updatedCount);
        response.put("skippedCount", skippedCount);
        return response;
    }

    /**
     * 构建患者信息实体。
     */
    private QualityPatientInfo buildEntity(QualityPatientInfoSaveReq request) {
        QualityPatientInfo entity = new QualityPatientInfo();
        entity.setPatientId(normalizeText(request.getPatientId()));
        entity.setPatientName(normalizeText(request.getPatientName()));
        entity.setAccessionNumber(normalizeText(request.getAccessionNumber()));
        entity.setGender(normalizeText(request.getGender()));
        entity.setAge(normalizeAge(request.getAge()));
        entity.setStudyDate(request.getStudyDate());
        entity.setRemark(normalizeText(request.getRemark()));
        return entity;
    }

    /**
     * 校验新增或编辑请求。
     */
    private void validateRequest(QualityPatientInfoSaveReq request,
                                 Long currentId,
                                 MultipartFile imageFile,
                                 QualityPatientInfo existingPatient) {
        if (request == null) {
            throw new IllegalArgumentException("患者信息请求体不能为空");
        }

        if (!StringUtils.hasText(request.getPatientName())) {
            throw new IllegalArgumentException("患者姓名不能为空");
        }

        if (!StringUtils.hasText(request.getAccessionNumber())) {
            throw new IllegalArgumentException("检查号不能为空");
        }

        Integer normalizedAge = normalizeAge(request.getAge());
        if (request.getAge() != null && normalizedAge == null) {
            throw new IllegalArgumentException("年龄必须为非负数");
        }

        if (currentId != null && currentId <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }

        if (currentId == null && (imageFile == null || imageFile.isEmpty())) {
            throw new IllegalArgumentException("新增患者信息时必须上传影像图片");
        }

        if (currentId != null
                && (imageFile == null || imageFile.isEmpty())
                && (existingPatient == null || !StringUtils.hasText(existingPatient.getImagePath()))) {
            throw new IllegalArgumentException("当前患者尚未维护影像图片，请上传图片后再保存");
        }

        validateImageFile(imageFile);
    }

    /**
     * 解析任务对应的数据表。
     */
    private String requireTableName(String taskType) {
        String tableName = QualityPatientTaskSupport.resolveTableName(taskType);
        if (tableName == null) {
            throw new IllegalArgumentException("不支持的患者信息任务类型: " + taskType);
        }
        return tableName;
    }

    /**
     * 规范化文本字段。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 校验上传图片格式。
     */
    private void validateImageFile(MultipartFile imageFile) {
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

    /**
     * 将上传的患者影像图片保存到本地 uploads 目录。
     */
    private String storeUploadedImage(String taskType, MultipartFile imageFile, String accessionNumber) {
        if (imageFile == null || imageFile.isEmpty()) {
            return null;
        }

        try {
            Path taskDirectory = patientImageRoot.resolve(QualityPatientTaskSupport.normalizeTaskType(taskType));
            if (Files.notExists(taskDirectory)) {
                Files.createDirectories(taskDirectory);
            }

            String safeAccessionNumber = normalizeText(accessionNumber);
            if (safeAccessionNumber == null) {
                safeAccessionNumber = UUID.randomUUID().toString();
            }
            safeAccessionNumber = safeAccessionNumber.replaceAll("[^a-zA-Z0-9_-]", "_");

            String extension = resolveImageExtension(imageFile.getOriginalFilename());
            String filename = safeAccessionNumber + "_" + UUID.randomUUID() + extension;
            Path targetFile = taskDirectory.resolve(filename).normalize().toAbsolutePath();
            imageFile.transferTo(targetFile.toFile());
            return "uploads/patient-info/" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "/" + filename;
        } catch (IOException exception) {
            throw new IllegalStateException("保存患者影像图片失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 将 PACS 缓存中的影像图片复制到患者影像目录。
     */
    private String copyPacsImageToPatientDirectory(String taskType, String imageFilePath, String accessionNumber) {
        String normalizedImageFilePath = normalizeText(imageFilePath);
        if (normalizedImageFilePath == null) {
            return null;
        }

        Path sourcePath = Paths.get(normalizedImageFilePath);
        if (Files.notExists(sourcePath)) {
            return null;
        }

        try {
            Path taskDirectory = patientImageRoot.resolve(QualityPatientTaskSupport.normalizeTaskType(taskType)).resolve("pacs-sync");
            if (Files.notExists(taskDirectory)) {
                Files.createDirectories(taskDirectory);
            }

            String safeAccessionNumber = normalizeText(accessionNumber);
            if (safeAccessionNumber == null) {
                safeAccessionNumber = UUID.randomUUID().toString();
            }
            safeAccessionNumber = safeAccessionNumber.replaceAll("[^a-zA-Z0-9_-]", "_");

            String extension = resolveImageExtension(sourcePath.getFileName() == null ? "" : sourcePath.getFileName().toString());
            String filename = safeAccessionNumber + "_" + UUID.randomUUID() + extension;
            Path targetFile = taskDirectory.resolve(filename).normalize().toAbsolutePath();
            Files.copy(sourcePath, targetFile, StandardCopyOption.REPLACE_EXISTING);
            return "uploads/patient-info/" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "/pacs-sync/" + filename;
        } catch (IOException exception) {
            return null;
        }
    }

    /**
     * 解析并规范化图片扩展名。
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
     * 返回第一个非空白字符串。
     */
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

    /**
     * 判断 PACS 缓存记录是否匹配指定质控项。
     */
    private boolean matchesTaskType(String taskType, PacsStudyCache pacsStudy) {
        if (pacsStudy == null) {
            return false;
        }

        String bodyPart = normalizeText(pacsStudy.getBodyPart());
        String studyDescription = normalizeText(pacsStudy.getStudyDescription());
        String mergedText = ((bodyPart == null ? "" : bodyPart) + " " + (studyDescription == null ? "" : studyDescription)).toLowerCase();

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
     * 规范化年龄字段。
     */
    private Integer normalizeAge(Integer age) {
        if (age == null) {
            return null;
        }
        return age >= 0 ? age : null;
    }

    /**
     * 规范化分页页码。
     */
    private int normalizePage(Integer page) {
        return page == null || page <= 0 ? 1 : page;
    }

    /**
     * 规范化分页大小。
     */
    private int normalizeLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return 10;
        }
        return Math.min(limit, 50);
    }
}
