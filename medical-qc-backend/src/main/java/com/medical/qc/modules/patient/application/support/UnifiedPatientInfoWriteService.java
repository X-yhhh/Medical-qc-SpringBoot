package com.medical.qc.modules.patient.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.pacs.persistence.mapper.PacsStudyMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyFileMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.modules.unified.application.UnifiedPatientInfoQueryService;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一模型患者信息写服务。
 *
 * <p>负责患者信息模块在新统一模型下的新增、更新、删除与 PACS 同步。</p>
 */
@Service
public class UnifiedPatientInfoWriteService {
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedStudyFileMapper unifiedStudyFileMapper;
    private final PacsStudyMapper pacsStudyMapper;
    private final PatientInfoImageSupport patientInfoImageSupport;
    private final UnifiedPatientInfoQueryService unifiedPatientInfoQueryService;

    public UnifiedPatientInfoWriteService(UnifiedPatientMapper unifiedPatientMapper,
                                          UnifiedStudyMapper unifiedStudyMapper,
                                          UnifiedStudyFileMapper unifiedStudyFileMapper,
                                          PacsStudyMapper pacsStudyMapper,
                                          PatientInfoImageSupport patientInfoImageSupport,
                                          UnifiedPatientInfoQueryService unifiedPatientInfoQueryService) {
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedStudyFileMapper = unifiedStudyFileMapper;
        this.pacsStudyMapper = pacsStudyMapper;
        this.patientInfoImageSupport = patientInfoImageSupport;
        this.unifiedPatientInfoQueryService = unifiedPatientInfoQueryService;
    }

    public QualityPatientInfo createPatient(String taskType,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        validateRequest(request, null, imageFile, null);
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String accessionNumber = normalizeText(request.getAccessionNumber());
        if (unifiedPatientInfoQueryService.getByAccessionNumber(normalizedTaskType, accessionNumber) != null) {
            throw new IllegalArgumentException("当前检查号已存在患者信息，请直接编辑");
        }

        String imagePath = patientInfoImageSupport.storeUploadedImage(normalizedTaskType, imageFile, accessionNumber);
        Long studyId = upsertPatientAndStudy(normalizedTaskType, request, imagePath, null);
        return unifiedPatientInfoQueryService.getByStudyId(studyId);
    }

    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }

        UnifiedStudy existingStudy = unifiedStudyMapper.selectById(id);
        if (existingStudy == null) {
            throw new IllegalArgumentException("患者信息不存在");
        }

        QualityPatientInfo existingPatient = unifiedPatientInfoQueryService.getByStudyId(id);
        validateRequest(request, id, imageFile, existingPatient);

        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String accessionNumber = normalizeText(request.getAccessionNumber());
        QualityPatientInfo duplicatedPatient = unifiedPatientInfoQueryService.getByAccessionNumber(normalizedTaskType, accessionNumber);
        if (duplicatedPatient != null && !Objects.equals(duplicatedPatient.getId(), id)) {
            throw new IllegalArgumentException("当前检查号已被其他患者信息占用");
        }

        String imagePath = imageFile != null && !imageFile.isEmpty()
                ? patientInfoImageSupport.storeUploadedImage(normalizedTaskType, imageFile, accessionNumber)
                : existingPatient == null ? null : existingPatient.getImagePath();
        Long studyId = upsertPatientAndStudy(normalizedTaskType, request, imagePath, existingStudy);
        return unifiedPatientInfoQueryService.getByStudyId(studyId);
    }

    public void deletePatient(Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }

        UnifiedStudy study = unifiedStudyMapper.selectById(id);
        if (study == null) {
            throw new IllegalArgumentException("患者信息不存在");
        }

        Long patientId = study.getPatientId();
        unifiedStudyFileMapper.delete(new QueryWrapper<UnifiedStudyFile>().eq("study_id", id));
        unifiedStudyMapper.deleteById(id);

        if (patientId != null) {
            long remainingStudyCount = unifiedStudyMapper.selectCount(new QueryWrapper<UnifiedStudy>().eq("patient_id", patientId));
            if (remainingStudyCount == 0) {
                unifiedPatientMapper.deleteById(patientId);
            }
        }
    }

    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        String normalizedPatientName = normalizeText(patientName);
        if (normalizedAccessionNumber == null || normalizedPatientName == null) {
            return null;
        }

        QualityPatientInfoSaveReq request = new QualityPatientInfoSaveReq();
        request.setPatientId(patientId);
        request.setPatientName(patientName);
        request.setAccessionNumber(accessionNumber);
        request.setGender(gender);
        request.setAge(age);
        request.setStudyDate(studyDate);
        request.setRemark("统一模型自动回填");

        QualityPatientInfo existing = unifiedPatientInfoQueryService.getByAccessionNumber(normalizedTaskType, normalizedAccessionNumber);
        Long studyId = upsertPatientAndStudy(
                normalizedTaskType,
                request,
                firstNonBlank(normalizeText(imagePath), existing == null ? null : existing.getImagePath()),
                existing == null ? null : unifiedStudyMapper.selectById(existing.getId()));
        return unifiedPatientInfoQueryService.getByStudyId(studyId);
    }

    public Map<String, Object> syncPatientsFromPacs(String taskType) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        List<PacsStudyCache> pacsStudies = pacsStudyMapper.selectStudiesForSync();
        int totalCount = pacsStudies.size();
        int matchedCount = 0;
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        for (PacsStudyCache pacsStudy : pacsStudies) {
            if (!patientInfoImageSupport.matchesTaskType(normalizedTaskType, pacsStudy)) {
                continue;
            }
            matchedCount += 1;

            String accessionNumber = normalizeText(pacsStudy.getAccessionNumber());
            String patientName = normalizeText(pacsStudy.getPatientName());
            if (accessionNumber == null || patientName == null) {
                skippedCount += 1;
                continue;
            }

            String copiedImagePath = patientInfoImageSupport.copyPacsImageToPatientDirectory(
                    normalizedTaskType,
                    pacsStudy.getImageFilePath(),
                    accessionNumber);
            if (copiedImagePath == null) {
                skippedCount += 1;
                continue;
            }

            QualityPatientInfo existing = unifiedPatientInfoQueryService.getByAccessionNumber(normalizedTaskType, accessionNumber);
            QualityPatientInfo result = upsertPatientByAccessionNumber(
                    normalizedTaskType,
                    pacsStudy.getPatientId(),
                    patientName,
                    accessionNumber,
                    pacsStudy.getGender(),
                    pacsStudy.getAge(),
                    pacsStudy.getStudyDate(),
                    copiedImagePath);

            if (result == null) {
                skippedCount += 1;
                continue;
            }

            if (existing == null) {
                createdCount += 1;
            } else {
                updatedCount += 1;
            }
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

    private Long upsertPatientAndStudy(String taskType,
                                       QualityPatientInfoSaveReq request,
                                       String imagePath,
                                       UnifiedStudy existingStudy) {
        String accessionNumber = normalizeText(request.getAccessionNumber());

        UnifiedPatient patient;
        if (existingStudy != null && existingStudy.getPatientId() != null) {
            patient = unifiedPatientMapper.selectById(existingStudy.getPatientId());
        } else {
            patient = resolveExistingPatient(taskType, request.getPatientId(), accessionNumber);
        }

        if (patient == null) {
            patient = new UnifiedPatient();
            patient.setPatientNo(buildPatientNo(taskType, request.getPatientId(), accessionNumber));
            patient.setCreatedAt(LocalDateTime.now());
        }
        patient.setPatientName(normalizeText(request.getPatientName()));
        patient.setGender(normalizeText(request.getGender()));
        patient.setAgeText(request.getAge() == null ? null : String.valueOf(request.getAge()));
        patient.setStatus("ACTIVE");
        patient.setRemark(normalizeText(request.getRemark()));
        patient.setUpdatedAt(LocalDateTime.now());
        if (patient.getId() == null) {
            unifiedPatientMapper.insert(patient);
        } else {
            unifiedPatientMapper.updateById(patient);
        }

        UnifiedStudy study = existingStudy == null ? new UnifiedStudy() : existingStudy;
        if (study.getId() == null) {
            study.setStudyNo(buildStudyNo(taskType, accessionNumber));
            study.setCreatedAt(LocalDateTime.now());
        }
        study.setPatientId(patient.getId());
        study.setAccessionNumber(accessionNumber);
        study.setModality(resolveModality(taskType));
        study.setBodyPart(resolveBodyPart(taskType));
        study.setStudyDate(request.getStudyDate());
        study.setSourceType("MANUAL");
        study.setSourceRef("unified_patient_info");
        study.setStatus("ACTIVE");
        study.setUpdatedAt(LocalDateTime.now());
        if (study.getId() == null) {
            unifiedStudyMapper.insert(study);
        } else {
            unifiedStudyMapper.updateById(study);
        }

        if (StringUtils.hasText(imagePath)) {
            upsertPreviewFile(study.getId(), imagePath);
        }

        return study.getId();
    }

    private UnifiedPatient resolveExistingPatient(String taskType, String patientId, String accessionNumber) {
        String patientNo = buildPatientNo(taskType, patientId, accessionNumber);
        return unifiedPatientMapper.selectOne(new QueryWrapper<UnifiedPatient>()
                .eq("patient_no", patientNo)
                .last("LIMIT 1"));
    }

    private void upsertPreviewFile(Long studyId, String imagePath) {
        UnifiedStudyFile previewFile = unifiedStudyFileMapper.selectOne(new QueryWrapper<UnifiedStudyFile>()
                .eq("study_id", studyId)
                .eq("file_role", "PREVIEW")
                .last("LIMIT 1"));
        if (previewFile == null) {
            previewFile = new UnifiedStudyFile();
            previewFile.setStudyId(studyId);
            previewFile.setFileRole("PREVIEW");
            previewFile.setCreatedAt(LocalDateTime.now());
        }
        previewFile.setStorageType("LOCAL");
        previewFile.setFilePath(imagePath);
        previewFile.setPublicPath(normalizePublicPath(imagePath));
        previewFile.setFileName(extractFileName(imagePath));
        previewFile.setIsPrimary(true);
        previewFile.setUpdatedAt(LocalDateTime.now());
        if (previewFile.getId() == null) {
            unifiedStudyFileMapper.insert(previewFile);
        } else {
            unifiedStudyFileMapper.updateById(previewFile);
        }
    }

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
        Integer normalizedAge = request.getAge() == null ? null : normalizeAge(request.getAge());
        if (request.getAge() != null && normalizedAge == null) {
            throw new IllegalArgumentException("年龄必须为非负数");
        }
        if (currentId == null && (imageFile == null || imageFile.isEmpty())) {
            throw new IllegalArgumentException("新增患者信息时必须上传影像图片");
        }
        if (currentId != null
                && (imageFile == null || imageFile.isEmpty())
                && (existingPatient == null || !StringUtils.hasText(existingPatient.getImagePath()))) {
            throw new IllegalArgumentException("当前患者尚未维护影像图片，请上传图片后再保存");
        }
        patientInfoImageSupport.validateImageFile(imageFile);
    }

    private Integer normalizeAge(Integer age) {
        return age == null ? null : (age >= 0 ? age : null);
    }

    private String buildPatientNo(String taskType, String patientId, String accessionNumber) {
        String normalizedPatientId = normalizeText(patientId);
        if (normalizedPatientId != null) {
            return normalizedPatientId;
        }
        return "rt-" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "-patient-" + accessionNumber;
    }

    private String buildStudyNo(String taskType, String accessionNumber) {
        return "rt-" + QualityPatientTaskSupport.normalizeTaskType(taskType) + "-study-" + accessionNumber;
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

    private String extractFileName(String imagePath) {
        String normalizedPath = normalizeText(imagePath);
        if (normalizedPath == null) {
            return null;
        }
        String slashPath = normalizedPath.replace('\\', '/');
        int lastSlash = slashPath.lastIndexOf('/');
        return lastSlash < 0 ? slashPath : slashPath.substring(lastSlash + 1);
    }

    private String normalizePublicPath(String rawPath) {
        String normalizedPath = normalizeText(rawPath);
        if (normalizedPath == null) {
            return null;
        }
        String slashPath = normalizedPath.replace('\\', '/');
        if (slashPath.startsWith("/")) {
            return slashPath;
        }
        if (slashPath.startsWith("uploads/")) {
            return "/" + slashPath;
        }
        return slashPath;
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

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}

