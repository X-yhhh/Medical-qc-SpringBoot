package com.medical.qc.modules.pacs.application.support;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.support.TaskScopedSourceTableSupport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.sql.Date;
import java.sql.SQLException;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 任务专属 PACS 缓存表读服务。
 */
@Service
public class TaskScopedPacsStudyStorageService {
    private final JdbcTemplate jdbcTemplate;
    private final TaskScopedSourceTableSupport tableSupport;

    public TaskScopedPacsStudyStorageService(JdbcTemplate jdbcTemplate,
                                             TaskScopedSourceTableSupport tableSupport) {
        this.jdbcTemplate = jdbcTemplate;
        this.tableSupport = tableSupport;
    }

    /**
     * 查询指定质控项对应的 PACS 缓存记录。
     */
    public List<PacsStudyCache> searchStudies(String taskType,
                                              String patientId,
                                              String patientName,
                                              String accessionNumber,
                                              LocalDate startDate,
                                              LocalDate endDate) {
        String tableName = tableSupport.resolvePacsTable(taskType);
        StringBuilder sql = new StringBuilder("SELECT * FROM ").append(tableName).append(" WHERE 1=1");
        List<Object> params = new ArrayList<>();

        appendEqual(sql, params, patientId, "patient_id");
        appendLike(sql, params, patientName, "patient_name");
        appendEqual(sql, params, accessionNumber, "accession_number");
        if (startDate != null) {
            sql.append(" AND study_date >= ?");
            params.add(startDate);
        }
        if (endDate != null) {
            sql.append(" AND study_date <= ?");
            params.add(endDate);
        }
        if ("head".equals(taskType)) {
            // 头部平扫真实模型当前仅支持 NIfTI 体数据，过滤掉旧 PNG 演示记录。
            sql.append(" AND (LOWER(image_file_path) LIKE '%.nii' OR LOWER(image_file_path) LIKE '%.nii.gz')");
        }
        sql.append(" ORDER BY study_date DESC, study_time DESC, id DESC LIMIT 100");
        return jdbcTemplate.query(sql.toString(), pacsStudyRowMapper(), params.toArray());
    }

    /**
     * 读取指定质控项对应 PACS 表中的全部记录，用于初始化本地患者缓存。
     */
    public List<PacsStudyCache> listStudiesForSync(String taskType) {
        String tableName = tableSupport.resolvePacsTable(taskType);
        String sql = "head".equals(taskType)
                ? "SELECT * FROM " + tableName + " WHERE (LOWER(image_file_path) LIKE '%.nii' OR LOWER(image_file_path) LIKE '%.nii.gz') ORDER BY study_date DESC, study_time DESC, id DESC"
                : "SELECT * FROM " + tableName + " ORDER BY study_date DESC, study_time DESC, id DESC";
        return jdbcTemplate.query(sql, pacsStudyRowMapper());
    }

    /**
     * 构建 PACS 行映射器。
     */
    private RowMapper<PacsStudyCache> pacsStudyRowMapper() {
        return (resultSet, rowNum) -> {
            PacsStudyCache study = new PacsStudyCache();
            study.setId(resultSet.getLong("id"));
            study.setStudyInstanceUid(resultSet.getString("study_instance_uid"));
            study.setPatientId(resultSet.getString("patient_id"));
            study.setPatientName(resultSet.getString("patient_name"));
            study.setGender(resultSet.getString("gender"));
            study.setAge((Integer) resultSet.getObject("age"));
            study.setAccessionNumber(resultSet.getString("accession_number"));

            Date studyDate = resultSet.getDate("study_date");
            study.setStudyDate(studyDate == null ? null : studyDate.toLocalDate());
            Time studyTime = resultSet.getTime("study_time");
            study.setStudyTime(studyTime == null ? null : studyTime.toLocalTime());

            study.setStudyDescription(resultSet.getString("study_description"));
            study.setModality(resultSet.getString("modality"));
            study.setSeriesCount((Integer) resultSet.getObject("series_count"));
            study.setImageCount((Integer) resultSet.getObject("image_count"));
            study.setBodyPart(resultSet.getString("body_part"));
            study.setManufacturer(resultSet.getString("manufacturer"));
            study.setModelName(resultSet.getString("model_name"));
            study.setImageFilePath(resultSet.getString("image_file_path"));
            study.setPatientImagePath(resultSet.getString("patient_image_path"));
            try {
                study.setFlowRate(toDouble(resultSet.getObject("flow_rate")));
                study.setContrastVolume((Integer) resultSet.getObject("contrast_volume"));
                study.setInjectionSite(resultSet.getString("injection_site"));
                study.setSliceThickness(toDouble(resultSet.getObject("slice_thickness")));
                study.setBolusTrackingHu((Integer) resultSet.getObject("bolus_tracking_hu"));
                study.setScanDelaySec((Integer) resultSet.getObject("scan_delay_sec"));
                study.setHeartRate((Integer) resultSet.getObject("heart_rate"));
                study.setHrVariability((Integer) resultSet.getObject("hr_variability"));
                study.setReconPhase(resultSet.getString("recon_phase"));
                study.setKvp(resultSet.getString("kvp"));
            } catch (SQLException ignore) {
                // 非CTA表不存在这些列时直接忽略。
            }

            Timestamp createdAt = resultSet.getTimestamp("created_at");
            study.setCreatedAt(createdAt == null ? null : createdAt.toLocalDateTime());
            Timestamp updatedAt = resultSet.getTimestamp("updated_at");
            study.setUpdatedAt(updatedAt == null ? null : updatedAt.toLocalDateTime());
            return study;
        };
    }

    /**
     * 拼接模糊查询条件。
     */
    private void appendLike(StringBuilder sql, List<Object> params, String value, String column) {
        String normalizedValue = normalizeText(value);
        if (normalizedValue == null) {
            return;
        }
        sql.append(" AND ").append(column).append(" LIKE ?");
        params.add("%" + normalizedValue + "%");
    }

    /**
     * 拼接等值查询条件。
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
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private Double toDouble(Object value) {
        return value instanceof Number number ? number.doubleValue() : null;
    }
}
