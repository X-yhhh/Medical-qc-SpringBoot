package com.medical.qc.modules.patient.application.support;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.support.QualityPatientTaskSupport;
import com.medical.qc.support.TaskScopedSourceTableSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.sql.Date;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 任务专属患者缓存表读写服务。
 */
@Service
public class TaskScopedPatientInfoStorageService {
    private final JdbcTemplate jdbcTemplate;
    private final TaskScopedSourceTableSupport tableSupport;
    private final PatientInfoImageSupport patientInfoImageSupport;

    public TaskScopedPatientInfoStorageService(JdbcTemplate jdbcTemplate,
                                               TaskScopedSourceTableSupport tableSupport,
                                               PatientInfoImageSupport patientInfoImageSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableSupport = tableSupport;
        this.patientInfoImageSupport = patientInfoImageSupport;
    }

    /**
     * 分页查询当前任务类型对应的患者缓存列表。
     */
    public Map<String, Object> getPatientPage(String taskType,
                                              String keyword,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber,
                                              Integer page,
                                              Integer limit) {
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        int normalizedPage = page == null || page <= 0 ? 1 : page;
        int normalizedLimit = limit == null || limit <= 0 ? 10 : Math.min(limit, 50);

        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();
        appendLike(sql, params, keyword, "(patient_name LIKE ? OR patient_id LIKE ? OR accession_number LIKE ?)");
        appendEqual(sql, params, patientId, "patient_id");
        appendLike(sql, params, patientName, "patient_name LIKE ?");
        appendEqual(sql, params, accessionNumber, "accession_number");
        sql.append(" ORDER BY study_date DESC, id DESC");

        List<QualityPatientInfo> all = jdbcTemplate.query(sql.toString(), patientInfoRowMapper(), params.toArray());
        int total = all.size();
        int fromIndex = Math.min((normalizedPage - 1) * normalizedLimit, total);
        int toIndex = Math.min(fromIndex + normalizedLimit, total);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("page", normalizedPage);
        pagination.put("limit", normalizedLimit);
        pagination.put("total", total);

        Map<String, Object> summary = new HashMap<>();
        summary.put("taskType", QualityPatientTaskSupport.normalizeTaskType(taskType));
        summary.put("taskLabel", QualityPatientTaskSupport.resolveTaskLabel(taskType));
        summary.put("totalPatients", total);

        Map<String, Object> response = new HashMap<>();
        response.put("data", all.subList(fromIndex, toIndex));
        response.put("pagination", pagination);
        response.put("summary", summary);
        return response;
    }

    /**
     * 按检查号查询单条患者缓存记录。
     */
    public QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber) {
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        if (normalizedAccessionNumber == null) {
            return null;
        }
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        List<QualityPatientInfo> rows = jdbcTemplate.query(
                "SELECT * FROM " + tableName + " WHERE accession_number = ? LIMIT 1",
                patientInfoRowMapper(),
                normalizedAccessionNumber);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 新增患者缓存记录。
     */
    public QualityPatientInfo createPatient(String taskType,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        validateRequest(request, null, imageFile, null);
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        String normalizedAccessionNumber = normalizeText(request.getAccessionNumber());
        if (getByAccessionNumber(taskType, normalizedAccessionNumber) != null) {
            throw new IllegalArgumentException("当前检查号已存在患者信息，请直接编辑");
        }

        String imagePath = patientInfoImageSupport.storeUploadedImage(taskType, imageFile, normalizedAccessionNumber);
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
            jdbcTemplate.update(
                    "INSERT INTO " + tableName
                            + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, heart_rate, hr_variability, recon_phase, kvp, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()),
                    request.getHeartRate(),
                    request.getHrVariability(),
                    normalizeText(request.getReconPhase()),
                    normalizeText(request.getKvp()));
        } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
            jdbcTemplate.update(
                    "INSERT INTO " + tableName
                            + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, flow_rate, contrast_volume, injection_site, slice_thickness, bolus_tracking_hu, scan_delay_sec, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()),
                    request.getFlowRate(),
                    request.getContrastVolume(),
                    normalizeText(request.getInjectionSite()),
                    request.getSliceThickness(),
                    request.getBolusTrackingHu(),
                    request.getScanDelaySec());
        } else {
            jdbcTemplate.update(
                    "INSERT INTO " + tableName
                            + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, created_at, updated_at)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()));
        }
        return getByAccessionNumber(taskType, normalizedAccessionNumber);
    }

    /**
     * 更新患者缓存记录。
     */
    public QualityPatientInfo updatePatient(String taskType,
                                            Long id,
                                            QualityPatientInfoSaveReq request,
                                            MultipartFile imageFile) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        QualityPatientInfo existing = getById(taskType, id);
        if (existing == null) {
            throw new IllegalArgumentException("患者信息不存在");
        }

        validateRequest(request, id, imageFile, existing);
        String normalizedAccessionNumber = normalizeText(request.getAccessionNumber());
        QualityPatientInfo duplicated = getByAccessionNumber(taskType, normalizedAccessionNumber);
        if (duplicated != null && !Objects.equals(duplicated.getId(), id)) {
            throw new IllegalArgumentException("当前检查号已被其他患者信息占用");
        }

        String imagePath = imageFile != null && !imageFile.isEmpty()
                ? patientInfoImageSupport.storeUploadedImage(taskType, imageFile, normalizedAccessionNumber)
                : existing.getImagePath();
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
            jdbcTemplate.update(
                    "UPDATE " + tableName
                            + " SET patient_id = ?, patient_name = ?, accession_number = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                            + " heart_rate = ?, hr_variability = ?, recon_phase = ?, kvp = ?, updated_at = NOW() WHERE id = ?",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()),
                    request.getHeartRate(),
                    request.getHrVariability(),
                    normalizeText(request.getReconPhase()),
                    normalizeText(request.getKvp()),
                    id);
        } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
            jdbcTemplate.update(
                    "UPDATE " + tableName
                            + " SET patient_id = ?, patient_name = ?, accession_number = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                            + " flow_rate = ?, contrast_volume = ?, injection_site = ?, slice_thickness = ?, bolus_tracking_hu = ?, scan_delay_sec = ?, updated_at = NOW() WHERE id = ?",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()),
                    request.getFlowRate(),
                    request.getContrastVolume(),
                    normalizeText(request.getInjectionSite()),
                    request.getSliceThickness(),
                    request.getBolusTrackingHu(),
                    request.getScanDelaySec(),
                    id);
        } else {
            jdbcTemplate.update(
                    "UPDATE " + tableName
                            + " SET patient_id = ?, patient_name = ?, accession_number = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                    normalizeText(request.getPatientId()),
                    normalizeText(request.getPatientName()),
                    normalizedAccessionNumber,
                    normalizeText(request.getGender()),
                    request.getAge(),
                    request.getStudyDate(),
                    imagePath,
                    normalizeText(request.getRemark()),
                    id);
        }
        return getById(taskType, id);
    }

    /**
     * 删除单条患者缓存记录。
     */
    public void deletePatient(String taskType, Long id) {
        if (id == null || id <= 0) {
            throw new IllegalArgumentException("患者信息 ID 非法");
        }
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        jdbcTemplate.update("DELETE FROM " + tableName + " WHERE id = ?", id);
    }

    /**
     * 从任务专属 PACS 缓存表同步患者缓存记录。
     */
    public Map<String, Object> syncPatientsFromPacs(String taskType, List<PacsStudyCache> pacsStudies) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        int totalCount = pacsStudies == null ? 0 : pacsStudies.size();
        int createdCount = 0;
        int updatedCount = 0;
        int skippedCount = 0;

        if (pacsStudies != null) {
            for (PacsStudyCache pacsStudy : pacsStudies) {
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
                QualityPatientInfoSaveReq request = new QualityPatientInfoSaveReq();
                request.setPatientId(pacsStudy.getPatientId());
                request.setPatientName(patientName);
                request.setAccessionNumber(accessionNumber);
                request.setGender(pacsStudy.getGender());
                request.setAge(pacsStudy.getAge());
                request.setStudyDate(pacsStudy.getStudyDate());
                request.setRemark("按任务专属 PACS 表同步");
                request.setHeartRate(pacsStudy.getHeartRate());
                request.setHrVariability(pacsStudy.getHrVariability());
                request.setReconPhase(pacsStudy.getReconPhase());
                request.setKvp(pacsStudy.getKvp());
                request.setFlowRate(pacsStudy.getFlowRate());
                request.setContrastVolume(pacsStudy.getContrastVolume());
                request.setInjectionSite(pacsStudy.getInjectionSite());
                request.setSliceThickness(pacsStudy.getSliceThickness());
                request.setBolusTrackingHu(pacsStudy.getBolusTrackingHu());
                request.setScanDelaySec(pacsStudy.getScanDelaySec());

                QualityPatientInfo existing = getByAccessionNumber(normalizedTaskType, accessionNumber);
                if (existing == null) {
                    if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
                        jdbcTemplate.update(
                                "INSERT INTO " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, heart_rate, hr_variability, recon_phase, kvp, created_at, updated_at)"
                                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                accessionNumber,
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                copiedImagePath,
                                normalizeText(request.getRemark()),
                                request.getHeartRate(),
                                request.getHrVariability(),
                                normalizeText(request.getReconPhase()),
                                normalizeText(request.getKvp()));
                    } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
                        jdbcTemplate.update(
                                "INSERT INTO " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, flow_rate, contrast_volume, injection_site, slice_thickness, bolus_tracking_hu, scan_delay_sec, created_at, updated_at)"
                                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                accessionNumber,
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                copiedImagePath,
                                normalizeText(request.getRemark()),
                                request.getFlowRate(),
                                request.getContrastVolume(),
                                normalizeText(request.getInjectionSite()),
                                request.getSliceThickness(),
                                request.getBolusTrackingHu(),
                                request.getScanDelaySec());
                    } else {
                        jdbcTemplate.update(
                                "INSERT INTO " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, created_at, updated_at)"
                                        + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                accessionNumber,
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                copiedImagePath,
                                normalizeText(request.getRemark()));
                    }
                    createdCount += 1;
                } else {
                    if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
                        jdbcTemplate.update(
                                "UPDATE " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                                        + " heart_rate = ?, hr_variability = ?, recon_phase = ?, kvp = ?, updated_at = NOW() WHERE id = ?",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                firstNonBlank(copiedImagePath, existing.getImagePath()),
                                normalizeText(request.getRemark()),
                                request.getHeartRate(),
                                request.getHrVariability(),
                                normalizeText(request.getReconPhase()),
                                normalizeText(request.getKvp()),
                                existing.getId());
                    } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
                        jdbcTemplate.update(
                                "UPDATE " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                                        + " flow_rate = ?, contrast_volume = ?, injection_site = ?, slice_thickness = ?, bolus_tracking_hu = ?, scan_delay_sec = ?, updated_at = NOW() WHERE id = ?",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                firstNonBlank(copiedImagePath, existing.getImagePath()),
                                normalizeText(request.getRemark()),
                                request.getFlowRate(),
                                request.getContrastVolume(),
                                normalizeText(request.getInjectionSite()),
                                request.getSliceThickness(),
                                request.getBolusTrackingHu(),
                                request.getScanDelaySec(),
                                existing.getId());
                    } else {
                        jdbcTemplate.update(
                                "UPDATE " + tableSupport.resolvePatientInfoTable(normalizedTaskType)
                                        + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                                normalizeText(request.getPatientId()),
                                normalizeText(request.getPatientName()),
                                normalizeText(request.getGender()),
                                request.getAge(),
                                request.getStudyDate(),
                                firstNonBlank(copiedImagePath, existing.getImagePath()),
                                normalizeText(request.getRemark()),
                                existing.getId());
                    }
                    updatedCount += 1;
                }
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("taskType", normalizedTaskType);
        response.put("taskLabel", QualityPatientTaskSupport.resolveTaskLabel(normalizedTaskType));
        response.put("totalPacsStudies", totalCount);
        response.put("matchedStudies", totalCount);
        response.put("createdCount", createdCount);
        response.put("updatedCount", updatedCount);
        response.put("skippedCount", skippedCount);
        return response;
    }

    /**
     * 按检查号幂等新增或更新患者缓存记录。
     */
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath) {
        return upsertPatientByAccessionNumber(
                taskType,
                patientId,
                patientName,
                accessionNumber,
                gender,
                age,
                studyDate,
                imagePath,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    /**
     * 按检查号幂等新增或更新患者缓存记录，并支持冠脉 CTA 扩展元数据。
     */
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath,
                                                             Integer heartRate,
                                                             Integer hrVariability,
                                                             String reconPhase,
                                                             String kvp) {
        return upsertPatientByAccessionNumber(
                taskType,
                patientId,
                patientName,
                accessionNumber,
                gender,
                age,
                studyDate,
                imagePath,
                null,
                null,
                null,
                null,
                null,
                null,
                heartRate,
                hrVariability,
                reconPhase,
                kvp);
    }

    /**
     * 按检查号幂等新增或更新患者缓存记录，并支持 CTA 扩展元数据。
     */
    public QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                             String patientId,
                                                             String patientName,
                                                             String accessionNumber,
                                                             String gender,
                                                             Integer age,
                                                             LocalDate studyDate,
                                                             String imagePath,
                                                             Double flowRate,
                                                             Integer contrastVolume,
                                                             String injectionSite,
                                                             Double sliceThickness,
                                                             Integer bolusTrackingHu,
                                                             Integer scanDelaySec,
                                                             Integer heartRate,
                                                             Integer hrVariability,
                                                             String reconPhase,
                                                             String kvp) {
        String normalizedTaskType = QualityPatientTaskSupport.normalizeTaskType(taskType);
        String normalizedAccessionNumber = normalizeText(accessionNumber);
        String normalizedPatientName = normalizeText(patientName);
        if (normalizedAccessionNumber == null || normalizedPatientName == null) {
            return null;
        }

        String tableName = tableSupport.resolvePatientInfoTable(normalizedTaskType);
        QualityPatientInfo existing = getByAccessionNumber(normalizedTaskType, normalizedAccessionNumber);
        if (existing == null) {
            if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
                jdbcTemplate.update(
                        "INSERT INTO " + tableName
                                + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, heart_rate, hr_variability, recon_phase, kvp, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        normalizeText(patientId),
                        normalizedPatientName,
                        normalizedAccessionNumber,
                        normalizeText(gender),
                        age,
                        studyDate,
                        normalizeText(imagePath),
                        "统一任务链路自动回填",
                        heartRate,
                        hrVariability,
                        normalizeText(reconPhase),
                        normalizeText(kvp));
            } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
                jdbcTemplate.update(
                        "INSERT INTO " + tableName
                                + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, flow_rate, contrast_volume, injection_site, slice_thickness, bolus_tracking_hu, scan_delay_sec, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        normalizeText(patientId),
                        normalizedPatientName,
                        normalizedAccessionNumber,
                        normalizeText(gender),
                        age,
                        studyDate,
                        normalizeText(imagePath),
                        "统一任务链路自动回填",
                        flowRate,
                        contrastVolume,
                        normalizeText(injectionSite),
                        sliceThickness,
                        bolusTrackingHu,
                        scanDelaySec);
            } else {
                jdbcTemplate.update(
                        "INSERT INTO " + tableName
                                + " (patient_id, patient_name, accession_number, gender, age, study_date, image_path, remark, created_at, updated_at)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
                        normalizeText(patientId),
                        normalizedPatientName,
                        normalizedAccessionNumber,
                        normalizeText(gender),
                        age,
                        studyDate,
                        normalizeText(imagePath),
                        "统一任务链路自动回填");
            }
        } else {
            if (QualityPatientTaskSupport.TASK_TYPE_CORONARY_CTA.equals(normalizedTaskType)) {
                jdbcTemplate.update(
                        "UPDATE " + tableName
                                + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                                + " heart_rate = ?, hr_variability = ?, recon_phase = ?, kvp = ?, updated_at = NOW() WHERE id = ?",
                        firstNonBlank(normalizeText(patientId), existing.getPatientId()),
                        normalizedPatientName,
                        firstNonBlank(normalizeText(gender), existing.getGender()),
                        age == null ? existing.getAge() : age,
                        studyDate == null ? existing.getStudyDate() : studyDate,
                        firstNonBlank(normalizeText(imagePath), existing.getImagePath()),
                        "统一任务链路自动回填",
                        heartRate == null ? existing.getHeartRate() : heartRate,
                        hrVariability == null ? existing.getHrVariability() : hrVariability,
                        firstNonBlank(normalizeText(reconPhase), existing.getReconPhase()),
                        firstNonBlank(normalizeText(kvp), existing.getKVp()),
                        existing.getId());
            } else if (QualityPatientTaskSupport.TASK_TYPE_CHEST_CONTRAST.equals(normalizedTaskType)) {
                jdbcTemplate.update(
                        "UPDATE " + tableName
                                + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?,"
                                + " flow_rate = ?, contrast_volume = ?, injection_site = ?, slice_thickness = ?, bolus_tracking_hu = ?, scan_delay_sec = ?, updated_at = NOW() WHERE id = ?",
                        firstNonBlank(normalizeText(patientId), existing.getPatientId()),
                        normalizedPatientName,
                        firstNonBlank(normalizeText(gender), existing.getGender()),
                        age == null ? existing.getAge() : age,
                        studyDate == null ? existing.getStudyDate() : studyDate,
                        firstNonBlank(normalizeText(imagePath), existing.getImagePath()),
                        "统一任务链路自动回填",
                        flowRate == null ? existing.getFlowRate() : flowRate,
                        contrastVolume == null ? existing.getContrastVolume() : contrastVolume,
                        firstNonBlank(normalizeText(injectionSite), existing.getInjectionSite()),
                        sliceThickness == null ? existing.getSliceThickness() : sliceThickness,
                        bolusTrackingHu == null ? existing.getBolusTrackingHu() : bolusTrackingHu,
                        scanDelaySec == null ? existing.getScanDelaySec() : scanDelaySec,
                        existing.getId());
            } else {
                jdbcTemplate.update(
                        "UPDATE " + tableName
                                + " SET patient_id = ?, patient_name = ?, gender = ?, age = ?, study_date = ?, image_path = ?, remark = ?, updated_at = NOW() WHERE id = ?",
                        firstNonBlank(normalizeText(patientId), existing.getPatientId()),
                        normalizedPatientName,
                        firstNonBlank(normalizeText(gender), existing.getGender()),
                        age == null ? existing.getAge() : age,
                        studyDate == null ? existing.getStudyDate() : studyDate,
                        firstNonBlank(normalizeText(imagePath), existing.getImagePath()),
                        "统一任务链路自动回填",
                        existing.getId());
            }
        }
        return getByAccessionNumber(normalizedTaskType, normalizedAccessionNumber);
    }

    /**
     * 按主键查询患者缓存记录。
     */
    private QualityPatientInfo getById(String taskType, Long id) {
        String tableName = tableSupport.resolvePatientInfoTable(taskType);
        List<QualityPatientInfo> rows = jdbcTemplate.query(
                "SELECT * FROM " + tableName + " WHERE id = ? LIMIT 1",
                patientInfoRowMapper(),
                id);
        return rows.isEmpty() ? null : rows.get(0);
    }

    /**
     * 构建患者缓存行映射器。
     */
    private RowMapper<QualityPatientInfo> patientInfoRowMapper() {
        return (resultSet, rowNum) -> {
            QualityPatientInfo item = new QualityPatientInfo();
            item.setId(resultSet.getLong("id"));
            item.setPatientId(resultSet.getString("patient_id"));
            item.setPatientName(resultSet.getString("patient_name"));
            item.setAccessionNumber(resultSet.getString("accession_number"));
            item.setGender(resultSet.getString("gender"));
            item.setAge((Integer) resultSet.getObject("age"));
            Date studyDate = resultSet.getDate("study_date");
            item.setStudyDate(studyDate == null ? null : studyDate.toLocalDate());
            item.setImagePath(resultSet.getString("image_path"));
            item.setRemark(resultSet.getString("remark"));
            try {
                item.setFlowRate(toDouble(resultSet.getObject("flow_rate")));
                item.setContrastVolume((Integer) resultSet.getObject("contrast_volume"));
                item.setInjectionSite(resultSet.getString("injection_site"));
                item.setSliceThickness(toDouble(resultSet.getObject("slice_thickness")));
                item.setBolusTrackingHu((Integer) resultSet.getObject("bolus_tracking_hu"));
                item.setScanDelaySec((Integer) resultSet.getObject("scan_delay_sec"));
                item.setHeartRate((Integer) resultSet.getObject("heart_rate"));
                item.setHrVariability((Integer) resultSet.getObject("hr_variability"));
                item.setReconPhase(resultSet.getString("recon_phase"));
                item.setKVp(resultSet.getString("kvp"));
            } catch (SQLException ignore) {
                // 非CTA表没有扩展列时直接忽略。
            }
            Timestamp createdAt = resultSet.getTimestamp("created_at");
            item.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            item.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
            return item;
        };
    }

    /**
     * 拼接 LIKE 条件。
     */
    private void appendLike(StringBuilder sql, List<Object> params, String value, String clause) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return;
        }
        sql.append(" AND ").append(clause);
        if (clause.contains("OR")) {
            params.add("%" + normalizedValue + "%");
            params.add("%" + normalizedValue + "%");
            params.add("%" + normalizedValue + "%");
        } else {
            params.add("%" + normalizedValue + "%");
        }
    }

    /**
     * 拼接等值条件。
     */
    private void appendEqual(StringBuilder sql, List<Object> params, String value, String column) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" = ?");
        params.add(normalizedValue);
    }

    /**
     * 校验请求体。
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
        if (request.getAge() != null && request.getAge() < 0) {
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

    /**
     * 从多个候选文本中返回第一个非空值。
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
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
