package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.modules.patient.application.QualityPatientInfoServiceImpl;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyFileMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统一模型检查上下文写服务。
 *
 * <p>负责把患者信息模块已经统一后的主数据，补齐为可直接承接任务与结果的检查上下文。</p>
 */
@Service
public class UnifiedStudyContextService {
    private final QualityPatientInfoServiceImpl qualityPatientInfoService;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedStudyFileMapper unifiedStudyFileMapper;

    public UnifiedStudyContextService(QualityPatientInfoServiceImpl qualityPatientInfoService,
                                      UnifiedStudyMapper unifiedStudyMapper,
                                      UnifiedStudyFileMapper unifiedStudyFileMapper) {
        this.qualityPatientInfoService = qualityPatientInfoService;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedStudyFileMapper = unifiedStudyFileMapper;
    }

    public UnifiedStudy ensureStudy(String taskType,
                                    String patientCode,
                                    String patientName,
                                    String accessionNumber,
                                    String gender,
                                    Integer age,
                                    LocalDate studyDate,
                                    String previewPath,
                                    String sourceType,
                                    String sourceRef,
                                    String deviceModel) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        String normalizedPatientName = normalizeText(patientName);
        if (normalizedTaskType == null || normalizedAccessionNumber == null || normalizedPatientName == null) {
            throw new IllegalArgumentException("统一模型检查上下文缺少必要字段");
        }

        qualityPatientInfoService.upsertPatientByAccessionNumber(
                normalizedTaskType,
                patientCode,
                normalizedPatientName,
                normalizedAccessionNumber,
                gender,
                age,
                studyDate,
                previewPath);

        UnifiedStudy study = findStudyByAccessionNumber(normalizedAccessionNumber);
        if (study == null) {
            throw new IllegalStateException("统一模型检查实例创建失败");
        }

        study.setStudyNo(buildStudyNo(normalizedTaskType, normalizedAccessionNumber));
        study.setModality(resolveModality(normalizedTaskType));
        study.setBodyPart(resolveBodyPart(normalizedTaskType));
        study.setStudyDate(studyDate);
        study.setSourceType(firstNonBlank(normalizeText(sourceType), study.getSourceType(), "MANUAL"));
        study.setSourceRef(firstNonBlank(normalizeText(sourceRef), study.getSourceRef()));
        study.setDeviceModel(firstNonBlank(normalizeText(deviceModel), study.getDeviceModel()));
        study.setStatus("ACTIVE");
        study.setUpdatedAt(LocalDateTime.now());
        unifiedStudyMapper.updateById(study);

        if (StringUtils.hasText(previewPath)) {
            upsertStudyFile(
                    study.getId(),
                    "PREVIEW",
                    previewPath,
                    normalizePublicPath(previewPath),
                    extractFileName(previewPath),
                    null,
                    null,
                    true);
        }
        return unifiedStudyMapper.selectById(study.getId());
    }

    public UnifiedStudy findStudyByAccessionNumber(String accessionNumber) {
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        if (normalizedAccessionNumber == null) {
            return null;
        }
        return unifiedStudyMapper.selectOne(new QueryWrapper<UnifiedStudy>()
                .eq("accession_number", normalizedAccessionNumber)
                .last("LIMIT 1"));
    }

    public void upsertStudyFile(Long studyId,
                                String fileRole,
                                String filePath,
                                String publicPath,
                                String fileName,
                                String contentType,
                                Long fileSize,
                                boolean isPrimary) {
        String normalizedFileRole = normalizeText(fileRole);
        String normalizedFilePath = normalizeText(filePath);
        if (studyId == null || normalizedFileRole == null || normalizedFilePath == null) {
            return;
        }

        UnifiedStudyFile studyFile = unifiedStudyFileMapper.selectOne(new QueryWrapper<UnifiedStudyFile>()
                .eq("study_id", studyId)
                .eq("file_role", normalizedFileRole)
                .last("LIMIT 1"));
        if (studyFile == null) {
            studyFile = new UnifiedStudyFile();
            studyFile.setStudyId(studyId);
            studyFile.setFileRole(normalizedFileRole);
            studyFile.setCreatedAt(LocalDateTime.now());
        }

        studyFile.setStorageType("LOCAL");
        studyFile.setFilePath(normalizedFilePath);
        studyFile.setPublicPath(firstNonBlank(normalizeText(publicPath), normalizePublicPath(normalizedFilePath)));
        studyFile.setFileName(firstNonBlank(normalizeText(fileName), extractFileName(normalizedFilePath)));
        studyFile.setContentType(normalizeText(contentType));
        studyFile.setFileSize(fileSize);
        studyFile.setIsPrimary(isPrimary);
        studyFile.setUpdatedAt(LocalDateTime.now());

        if (studyFile.getId() == null) {
            unifiedStudyFileMapper.insert(studyFile);
            return;
        }
        unifiedStudyFileMapper.updateById(studyFile);
    }

    public UnifiedStudyFile findPreferredFile(Long studyId, String... fileRoles) {
        if (studyId == null || fileRoles == null) {
            return null;
        }

        for (String fileRole : fileRoles) {
            String normalizedFileRole = normalizeText(fileRole);
            if (normalizedFileRole == null) {
                continue;
            }

            UnifiedStudyFile studyFile = unifiedStudyFileMapper.selectOne(new QueryWrapper<UnifiedStudyFile>()
                    .eq("study_id", studyId)
                    .eq("file_role", normalizedFileRole)
                    .orderByDesc("is_primary")
                    .orderByDesc("updated_at")
                    .last("LIMIT 1"));
            if (studyFile != null) {
                return studyFile;
            }
        }
        return null;
    }

    private String buildStudyNo(String taskType, String accessionNumber) {
        return "rt-" + taskType + "-study-" + accessionNumber;
    }

    private String resolveModality(String taskType) {
        return QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(taskType) ? "CTA" : "CT";
    }

    private String resolveBodyPart(String taskType) {
        return switch (QualityPatientTaskSupport.normalizeTaskType(taskType)) {
            case QualityPatientTaskSupport.TASK_TYPE_HEAD, QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE -> "HEAD";
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST, QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST -> "CHEST";
            case QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA -> "CORONARY";
            default -> "UNKNOWN";
        };
    }

    private String normalizePublicPath(String rawPath) {
        String normalizedPath = normalizeText(rawPath);
        if (normalizedPath == null) {
            return null;
        }

        String slashPath = normalizedPath.replace('\\', '/');
        if (slashPath.startsWith("http://") || slashPath.startsWith("https://") || slashPath.startsWith("data:")) {
            return slashPath;
        }
        if (slashPath.startsWith("/")) {
            return slashPath;
        }
        if (slashPath.startsWith("uploads/")) {
            return "/" + slashPath;
        }

        int uploadsIndex = slashPath.indexOf("/uploads/");
        return uploadsIndex >= 0 ? slashPath.substring(uploadsIndex) : null;
    }

    private String extractFileName(String rawPath) {
        String normalizedPath = normalizeText(rawPath);
        if (normalizedPath == null) {
            return null;
        }

        String slashPath = normalizedPath.replace('\\', '/');
        int lastSlash = slashPath.lastIndexOf('/');
        return lastSlash < 0 ? slashPath : slashPath.substring(lastSlash + 1);
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
