package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
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
    // 统一任务链路直接维护 patients / studies / study_files，不再依赖任务专属缓存表。
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedStudyFileMapper unifiedStudyFileMapper;

    public UnifiedStudyContextService(UnifiedPatientMapper unifiedPatientMapper,
                                      UnifiedStudyMapper unifiedStudyMapper,
                                      UnifiedStudyFileMapper unifiedStudyFileMapper) {
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedStudyFileMapper = unifiedStudyFileMapper;
    }

    /**
     * 确保指定检查号对应的统一检查上下文存在。
     * 数据链路：患者模块 upsert -> studies 更新 -> preview/source 文件挂载。
     */
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
        // taskType、检查号和患者姓名是构造统一检查上下文的最小必需字段。
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        String normalizedPatientName = normalizeText(patientName);
        if (normalizedTaskType == null || normalizedAccessionNumber == null || normalizedPatientName == null) {
            throw new IllegalArgumentException("统一模型检查上下文缺少必要字段");
        }

        UnifiedStudy study = findStudyByAccessionNumber(normalizedAccessionNumber);
        UnifiedPatient patient = study == null || study.getPatientId() == null
                ? null
                : unifiedPatientMapper.selectById(study.getPatientId());
        if (patient == null) {
            patient = ensurePatient(
                    normalizedTaskType,
                    normalizeText(patientCode),
                    normalizedPatientName,
                    normalizeText(gender),
                    age);
        } else {
            // 已有关联患者时优先复用同一主记录，避免同一检查号在补齐患者编号后生成重复患者。
            String normalizedPatientCode = normalizeText(patientCode);
            if (normalizedPatientCode != null) {
                patient.setPatientNo(normalizedPatientCode);
            }
            patient.setPatientName(normalizedPatientName);
            patient.setGender(normalizeText(gender));
            patient.setAgeText(age == null ? null : String.valueOf(age));
            patient.setStatus("ACTIVE");
            patient.setUpdatedAt(LocalDateTime.now());
            unifiedPatientMapper.updateById(patient);
        }
        if (study == null) {
            study = new UnifiedStudy();
            study.setAccessionNumber(normalizedAccessionNumber);
            study.setCreatedAt(LocalDateTime.now());
        }

        study.setPatientId(patient.getId());
        study.setStudyNo(buildStudyNo(normalizedTaskType, normalizedAccessionNumber));
        study.setModality(resolveModality(normalizedTaskType));
        study.setBodyPart(resolveBodyPart(normalizedTaskType));
        study.setStudyDate(studyDate);
        study.setSourceType(firstNonBlank(normalizeText(sourceType), study.getSourceType(), "MANUAL"));
        study.setSourceRef(firstNonBlank(normalizeText(sourceRef), study.getSourceRef()));
        study.setDeviceModel(firstNonBlank(normalizeText(deviceModel), study.getDeviceModel()));
        study.setStatus("ACTIVE");
        study.setUpdatedAt(LocalDateTime.now());
        if (study.getId() == null) {
            unifiedStudyMapper.insert(study);
        } else {
            unifiedStudyMapper.updateById(study);
        }

        if (StringUtils.hasText(previewPath)) {
            // 预览图统一挂在 PREVIEW 角色下，供列表和详情页优先读取。
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

    /**
     * 确保统一患者主数据存在。
     */
    private UnifiedPatient ensurePatient(String taskType,
                                        String patientCode,
                                        String patientName,
                                        String gender,
                                        Integer age) {
        String patientNo = buildPatientNo(taskType, patientCode, patientName);
        UnifiedPatient patient = unifiedPatientMapper.selectOne(new QueryWrapper<UnifiedPatient>()
                .eq("patient_no", patientNo)
                .last("LIMIT 1"));
        if (patient == null) {
            patient = new UnifiedPatient();
            patient.setPatientNo(patientNo);
            patient.setCreatedAt(LocalDateTime.now());
        }
        patient.setPatientName(patientName);
        patient.setGender(gender);
        patient.setAgeText(age == null ? null : String.valueOf(age));
        patient.setStatus("ACTIVE");
        patient.setUpdatedAt(LocalDateTime.now());
        if (patient.getId() == null) {
            unifiedPatientMapper.insert(patient);
        } else {
            unifiedPatientMapper.updateById(patient);
        }
        return patient;
    }

    /**
     * 按检查号查找统一检查实例。
     */
    public UnifiedStudy findStudyByAccessionNumber(String accessionNumber) {
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        if (normalizedAccessionNumber == null) {
            return null;
        }
        return unifiedStudyMapper.selectOne(new QueryWrapper<UnifiedStudy>()
                .eq("accession_number", normalizedAccessionNumber)
                .last("LIMIT 1"));
    }

    /**
     * 新增或更新检查文件记录。
     */
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

        // publicPath/fileName 可由调用方显式传入，缺失时从 filePath 自动推导。
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

    /**
     * 按优先级查找检查文件。
     * 调用方可依次传入 SOURCE、PREVIEW 等角色，返回第一条命中的文件。
     */
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

    /**
     * 构造统一检查编号。
     */
    private String buildStudyNo(String taskType, String accessionNumber) {
        return "rt-" + taskType + "-study-" + accessionNumber;
    }

    /**
     * 构造统一患者编号。
     */
    private String buildPatientNo(String taskType, String patientCode, String patientName) {
        if (StringUtils.hasText(patientCode)) {
            return patientCode.trim();
        }
        return "rt-" + taskType + "-patient-" + patientName.hashCode();
    }

    /**
     * 根据任务类型推导检查模态。
     */
    private String resolveModality(String taskType) {
        return QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(taskType) ? "CTA" : "CT";
    }

    /**
     * 根据任务类型推导检查部位。
     */
    private String resolveBodyPart(String taskType) {
        return switch (QualityPatientTaskSupport.normalizeTaskType(taskType)) {
            case QualityPatientTaskSupport.TASK_TYPE_HEAD, QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE -> "HEAD";
            case QualityPatientTaskSupport.TASK_TYPE_CHEST_NON_CONTRAST, QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST -> "CHEST";
            case QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA -> "CORONARY";
            default -> "UNKNOWN";
        };
    }

    /**
     * 把本地文件路径转换为前端可访问的 publicPath。
     */
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

    /**
     * 从路径中提取文件名。
     */
    private String extractFileName(String rawPath) {
        String normalizedPath = normalizeText(rawPath);
        if (normalizedPath == null) {
            return null;
        }

        String slashPath = normalizedPath.replace('\\', '/');
        int lastSlash = slashPath.lastIndexOf('/');
        return lastSlash < 0 ? slashPath : slashPath.substring(lastSlash + 1);
    }

    /**
     * 从多个候选文本中取第一个非空值。
     */
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

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
