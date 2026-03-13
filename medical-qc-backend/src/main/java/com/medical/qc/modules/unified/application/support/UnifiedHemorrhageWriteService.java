package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultItemMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 脑出血检测统一模型写服务。
 *
 * <p>直接落统一任务、结果和结果项表，不再经过旧历史表。</p>
 */
@Service
public class UnifiedHemorrhageWriteService {
    private final UnifiedStudyContextService unifiedStudyContextService;
    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedQcResultItemMapper unifiedQcResultItemMapper;
    private final ObjectMapper objectMapper;

    public UnifiedHemorrhageWriteService(UnifiedStudyContextService unifiedStudyContextService,
                                         UnifiedQcTaskMapper unifiedQcTaskMapper,
                                         UnifiedQcResultMapper unifiedQcResultMapper,
                                         UnifiedQcResultItemMapper unifiedQcResultItemMapper,
                                         ObjectMapper objectMapper) {
        this.unifiedStudyContextService = unifiedStudyContextService;
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedQcResultItemMapper = unifiedQcResultItemMapper;
        this.objectMapper = objectMapper;
    }

    public Long persist(User user,
                        HemorrhageRecord record,
                        String sourceMode,
                        String scannerModel,
                        String analysisImagePath,
                        String previewPath) {
        if (user == null || record == null) {
            throw new IllegalArgumentException("脑出血统一写入缺少必要上下文");
        }

        LocalDateTime detectedAt = firstNonNull(record.getCreatedAt(), LocalDateTime.now());
        record.setCreatedAt(detectedAt);
        record.setUpdatedAt(firstNonNull(record.getUpdatedAt(), detectedAt));

        UnifiedStudy study = unifiedStudyContextService.ensureStudy(
                "hemorrhage",
                record.getPatientCode(),
                record.getPatientName(),
                record.getExamId(),
                record.getGender(),
                record.getAge(),
                record.getStudyDate(),
                previewPath,
                resolveSourceType(sourceMode),
                "hemorrhage:" + record.getExamId(),
                scannerModel);

        unifiedStudyContextService.upsertStudyFile(
                study.getId(),
                "SOURCE",
                analysisImagePath,
                previewPath,
                null,
                null,
                null,
                true);

        UnifiedQcTask task = new UnifiedQcTask();
        task.setTaskNo("tmp-hemorrhage-" + UUID.randomUUID());
        task.setTaskTypeCode("hemorrhage");
        task.setStudyId(study.getId());
        task.setSubmittedBy(user.getId());
        task.setSourceMode(resolveSourceMode(sourceMode));
        task.setTaskStatus("SUCCESS");
        task.setSchedulerType("SYNC");
        task.setMock(false);
        task.setRequestedAt(detectedAt);
        task.setStartedAt(detectedAt);
        task.setCompletedAt(record.getUpdatedAt());
        task.setCreatedAt(detectedAt);
        task.setUpdatedAt(record.getUpdatedAt());
        unifiedQcTaskMapper.insert(task);

        task.setTaskNo("rt-hemorrhage-" + task.getId());
        unifiedQcTaskMapper.updateById(task);

        UnifiedQcResult result = new UnifiedQcResult();
        result.setTaskId(task.getId());
        result.setResultVersion(1);
        result.setModelCode("hemorrhage-model");
        result.setModelVersion(extractModelName(record.getRawResultJson(), "AdvancedHemorrhageModel"));
        result.setQcStatus(record.getQcStatus());
        result.setQualityScore(BigDecimal.valueOf(calculateQualityScore(record)));
        result.setAbnormalCount(isAbnormal(record) ? 1 : 0);
        result.setPrimaryIssueCode(record.getPrimaryIssue());
        result.setPrimaryIssueName(record.getPrimaryIssue());
        result.setSummaryJson(buildSummaryJson(record));
        result.setRawResultJson(record.getRawResultJson());
        result.setCreatedAt(detectedAt);
        result.setUpdatedAt(record.getUpdatedAt());
        unifiedQcResultMapper.insert(result);

        replaceResultItems(result.getId(), buildResultItems(record, detectedAt));
        return task.getId();
    }

    private void replaceResultItems(Long resultId, List<UnifiedQcResultItem> items) {
        unifiedQcResultItemMapper.delete(new QueryWrapper<UnifiedQcResultItem>().eq("result_id", resultId));
        for (UnifiedQcResultItem item : items) {
            item.setResultId(resultId);
            unifiedQcResultItemMapper.insert(item);
        }
    }

    private List<UnifiedQcResultItem> buildResultItems(HemorrhageRecord record, LocalDateTime detectedAt) {
        List<UnifiedQcResultItem> items = new ArrayList<>();
        items.add(buildItem(
                "HEMORRHAGE_PREDICTION",
                "脑出血判定",
                "出血".equals(record.getPrediction()) ? "异常" : "正常",
                null,
                record.getPrediction(),
                10,
                detectedAt));

        if (Boolean.TRUE.equals(record.getMidlineShift())) {
            items.add(buildItem(
                    "MIDLINE_SHIFT",
                    "中线偏移",
                    "异常",
                    record.getShiftScore() == null ? null : BigDecimal.valueOf(record.getShiftScore()),
                    record.getMidlineDetail(),
                    20,
                    detectedAt));
        }

        if (Boolean.TRUE.equals(record.getVentricleIssue())) {
            items.add(buildItem(
                    "VENTRICLE_ISSUE",
                    "脑室结构异常",
                    "异常",
                    null,
                    record.getVentricleDetail(),
                    30,
                    detectedAt));
        }
        return items;
    }

    private UnifiedQcResultItem buildItem(String code,
                                          String name,
                                          String status,
                                          BigDecimal score,
                                          String detail,
                                          int sortOrder,
                                          LocalDateTime detectedAt) {
        UnifiedQcResultItem item = new UnifiedQcResultItem();
        item.setItemCode(code);
        item.setItemName(name);
        item.setItemStatus(status);
        item.setScore(score);
        item.setDetailText(normalizeText(detail));
        item.setSortOrder(sortOrder);
        item.setCreatedAt(detectedAt);
        item.setUpdatedAt(detectedAt);
        return item;
    }

    private String buildSummaryJson(HemorrhageRecord record) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "patientName", firstNonBlank(record.getPatientName(), ""),
                    "examId", firstNonBlank(record.getExamId(), ""),
                    "prediction", firstNonBlank(record.getPrediction(), ""),
                    "qcStatus", firstNonBlank(record.getQcStatus(), ""),
                    "primaryIssue", firstNonBlank(record.getPrimaryIssue(), "未见明显异常")));
        } catch (Exception exception) {
            return null;
        }
    }

    private String extractModelName(String rawResultJson, String fallbackValue) {
        if (!StringUtils.hasText(rawResultJson)) {
            return fallbackValue;
        }

        try {
            Map<String, Object> rawResult = objectMapper.readValue(rawResultJson, new TypeReference<>() {
            });
            Object modelName = rawResult.get("model_name");
            return modelName == null ? fallbackValue : String.valueOf(modelName);
        } catch (Exception exception) {
            return fallbackValue;
        }
    }

    private boolean isAbnormal(HemorrhageRecord record) {
        return record != null
                && ("不合格".equals(record.getQcStatus())
                || "出血".equals(record.getPrediction())
                || Boolean.TRUE.equals(record.getMidlineShift())
                || Boolean.TRUE.equals(record.getVentricleIssue()));
    }

    private double calculateQualityScore(HemorrhageRecord record) {
        if (!isAbnormal(record)) {
            return 98D;
        }
        if ("出血".equals(record.getPrediction())) {
            return 65D;
        }
        if (Boolean.TRUE.equals(record.getMidlineShift())) {
            return 78D;
        }
        if (Boolean.TRUE.equals(record.getVentricleIssue())) {
            return 85D;
        }
        return 90D;
    }

    private String resolveSourceType(String sourceMode) {
        return "pacs".equalsIgnoreCase(sourceMode) ? "PACS" : "MANUAL";
    }

    private String resolveSourceMode(String sourceMode) {
        return "pacs".equalsIgnoreCase(sourceMode) ? "pacs" : "local";
    }

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
