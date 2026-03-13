package com.medical.qc.modules.unified.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyFileMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.shared.JsonObjectMapReader;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 统一模型脑出血历史查询服务。
 *
 * <p>兼容旧接口语义，入参仍使用 legacy hemorrhage recordId。</p>
 */
@Service
public class UnifiedHemorrhageQueryService {
    // 统一任务、结果、检查和患者五张表共同组成脑出血历史查询视图。
    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final UnifiedStudyFileMapper unifiedStudyFileMapper;
    private final ObjectMapper objectMapper;

    public UnifiedHemorrhageQueryService(UnifiedQcTaskMapper unifiedQcTaskMapper,
                                         UnifiedQcResultMapper unifiedQcResultMapper,
                                         UnifiedStudyMapper unifiedStudyMapper,
                                         UnifiedPatientMapper unifiedPatientMapper,
                                         UnifiedStudyFileMapper unifiedStudyFileMapper,
                                         ObjectMapper objectMapper) {
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.unifiedStudyFileMapper = unifiedStudyFileMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 查询脑出血历史列表。
     */
    public List<HemorrhageRecord> getHistory(Long userId, Integer limit) {
        QueryWrapper<UnifiedQcTask> queryWrapper = new QueryWrapper<UnifiedQcTask>()
                .eq("task_type_code", "hemorrhage")
                .orderByDesc("requested_at")
                .orderByDesc("created_at");
        if (userId != null) {
            // 医生视角仅返回本人提交的脑出血检测记录。
            queryWrapper.eq("submitted_by", userId);
        }

        List<UnifiedQcTask> tasks = unifiedQcTaskMapper.selectList(queryWrapper);
        List<HemorrhageRecord> records = tasks.stream()
                .map(this::toHemorrhageRecord)
                .filter(Objects::nonNull)
                .toList();

        if (limit == null || limit <= 0 || records.size() <= limit) {
            return records;
        }
        return new ArrayList<>(records.subList(0, limit));
    }

    /**
     * 按 legacy recordId 查询单条历史记录。
     */
    public HemorrhageRecord getHistoryRecord(Long userId, Long legacyRecordId) {
        if (legacyRecordId == null) {
            return null;
        }

        UnifiedQcTask task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                .eq("task_no", "rt-hemorrhage-" + legacyRecordId)
                .last("LIMIT 1"));
        if (task == null) {
            task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                    .eq("task_no", "legacy-hemorrhage-" + legacyRecordId)
                    .last("LIMIT 1"));
        }
        if (task == null) {
            return null;
        }
        if (userId != null && task.getSubmittedBy() != null && !Objects.equals(userId, task.getSubmittedBy())) {
            return null;
        }
        return toHemorrhageRecord(task);
    }

    /**
     * 把统一模型任务实体转换为兼容旧前端结构的 HemorrhageRecord。
     */
    private HemorrhageRecord toHemorrhageRecord(UnifiedQcTask task) {
        if (task == null) {
            return null;
        }

        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));
        UnifiedStudy study = task.getStudyId() == null ? null : unifiedStudyMapper.selectById(task.getStudyId());
        UnifiedPatient patient = study == null || study.getPatientId() == null ? null : unifiedPatientMapper.selectById(study.getPatientId());
        Map<String, Object> rawResult = parseJson(result == null ? null : result.getRawResultJson());
        Map<String, UnifiedStudyFile> fileMap = loadStudyFiles(study == null ? null : study.getId());

        HemorrhageRecord record = new HemorrhageRecord();
        record.setId(extractLegacyId(task.getTaskNo()));
        record.setUserId(task.getSubmittedBy());
        record.setPatientName(patient == null ? null : patient.getPatientName());
        record.setPatientCode(patient == null ? null : patient.getPatientNo());
        record.setExamId(study == null ? null : study.getAccessionNumber());
        record.setGender(patient == null ? null : patient.getGender());
        record.setAge(parseAge(patient == null ? null : patient.getAgeText()));
        record.setStudyDate(study == null ? null : study.getStudyDate());
        record.setImagePath(resolveFilePath(fileMap.get("SOURCE"), fileMap.get("PREVIEW")));
        record.setPrediction(resolvePrediction(rawResult, result));
        record.setQcStatus(result == null ? null : result.getQcStatus());
        record.setConfidenceLevel(asString(rawResult.get("confidence_level")));
        record.setHemorrhageProbability(asFloat(rawResult.get("hemorrhage_probability")));
        record.setNoHemorrhageProbability(asFloat(rawResult.get("no_hemorrhage_probability")));
        record.setAnalysisDuration(asFloat(rawResult.get("analysis_duration")));
        record.setCreatedAt(firstNonNull(result == null ? null : result.getCreatedAt(), task.getRequestedAt(), task.getCreatedAt()));
        record.setUpdatedAt(firstNonNull(result == null ? null : result.getUpdatedAt(), task.getCompletedAt(), task.getUpdatedAt()));
        record.setMidlineShift(asBoolean(rawResult.get("midline_shift")));
        record.setShiftScore(asFloat(rawResult.get("shift_score")));
        record.setMidlineDetail(asString(rawResult.get("midline_detail")));
        record.setVentricleIssue(asBoolean(rawResult.get("ventricle_issue")));
        record.setVentricleDetail(asString(rawResult.get("ventricle_detail")));
        record.setDevice(firstNonBlank(asString(rawResult.get("device")), study == null ? null : study.getDeviceModel()));
        record.setRawResultJson(result == null ? null : result.getRawResultJson());
        record.setPrimaryIssue(firstNonBlank(result == null ? null : result.getPrimaryIssueName(), "未见明显异常"));
        record.setPatientImagePath(resolveFilePath(fileMap.get("PREVIEW"), fileMap.get("SOURCE")));
        return record;
    }

    /**
     * 加载检查关联文件并按 file_role 建索引。
     */
    private Map<String, UnifiedStudyFile> loadStudyFiles(Long studyId) {
        if (studyId == null) {
            return Map.of();
        }
        return unifiedStudyFileMapper.selectList(new QueryWrapper<UnifiedStudyFile>()
                        .eq("study_id", studyId))
                .stream()
                .collect(Collectors.toMap(UnifiedStudyFile::getFileRole, item -> item, (left, right) -> left));
    }

    /**
     * 安全解析 rawResultJson。
     */
    private Map<String, Object> parseJson(String rawJson) {
        return JsonObjectMapReader.read(objectMapper, rawJson);
    }

    /**
     * 从统一任务编号中解析旧的历史 recordId。
     */
    private Long extractLegacyId(String taskNo) {
        if (!StringUtils.hasText(taskNo)) {
            return null;
        }
        String[] parts = taskNo.trim().split("-");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 优先从 rawResult 中读取 prediction，缺失时再根据主异常项推断。
     */
    private String resolvePrediction(Map<String, Object> rawResult, UnifiedQcResult result) {
        String prediction = asString(rawResult.get("prediction"));
        if (StringUtils.hasText(prediction)) {
            return prediction;
        }
        String primaryIssue = result == null ? null : result.getPrimaryIssueName();
        return StringUtils.hasText(primaryIssue) && primaryIssue.contains("脑出血") ? "出血" : "未出血";
    }

    /**
     * 优先返回文件 publicPath，缺失时回退到 filePath。
     */
    private String resolveFilePath(UnifiedStudyFile primary, UnifiedStudyFile fallback) {
        String primaryPath = primary == null ? null : firstNonBlank(primary.getPublicPath(), primary.getFilePath());
        if (primaryPath != null) {
            return primaryPath;
        }
        return fallback == null ? null : firstNonBlank(fallback.getPublicPath(), fallback.getFilePath());
    }

    /**
     * 将年龄文本转为整数。
     */
    private Integer parseAge(String ageText) {
        if (!StringUtils.hasText(ageText)) {
            return null;
        }
        try {
            return Integer.parseInt(ageText.trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 类型安全地读取布尔值。
     */
    private Boolean asBoolean(Object value) {
        return value instanceof Boolean booleanValue ? booleanValue : null;
    }

    /**
     * 类型安全地读取浮点值。
     */
    private Float asFloat(Object value) {
        return value instanceof Number number ? number.floatValue() : null;
    }

    /**
     * 将任意对象转为字符串。
     */
    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /**
     * 从多个候选值中返回第一个非 null 值。
     */
    @SafeVarargs
    private final <T> T firstNonNull(T... values) {
        if (values == null) {
            return null;
        }
        for (T value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    /**
     * 从多个候选文本中返回第一个非空值。
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
}
