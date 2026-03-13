package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskSnapshot;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultItemMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 统一模型异步质控任务写服务。
 *
 * <p>负责四类异步任务在统一模型下的提交、状态更新和结果落库。</p>
 */
@Service
public class UnifiedQualityTaskWriteService {
    // 统一记录 JSON 读写失败等非阻断型问题。
    private static final Logger logger = LoggerFactory.getLogger(UnifiedQualityTaskWriteService.class);

    // Study 上下文服务负责患者、检查和文件的主数据联动。
    private final UnifiedStudyContextService unifiedStudyContextService;
    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedQcResultItemMapper unifiedQcResultItemMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final ObjectMapper objectMapper;

    public UnifiedQualityTaskWriteService(UnifiedStudyContextService unifiedStudyContextService,
                                          UnifiedQcTaskMapper unifiedQcTaskMapper,
                                          UnifiedQcResultMapper unifiedQcResultMapper,
                                          UnifiedQcResultItemMapper unifiedQcResultItemMapper,
                                          UnifiedStudyMapper unifiedStudyMapper,
                                          UnifiedPatientMapper unifiedPatientMapper,
                                          ObjectMapper objectMapper) {
        this.unifiedStudyContextService = unifiedStudyContextService;
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedQcResultItemMapper = unifiedQcResultItemMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.unifiedPatientMapper = unifiedPatientMapper;
        this.objectMapper = objectMapper;
    }

    /**
     * 持久化刚提交的异步任务。
     * 数据链路：MockQualityTaskSnapshot -> studies / study_files / qc_tasks。
     */
    public void persistSubmittedTask(MockQualityTaskSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }

        // requestedAt 用于任务中心的提交时间展示，也是后续状态推进的时间基线。
        LocalDateTime submittedAt = firstNonNull(snapshot.getSubmittedAt(), LocalDateTime.now());
        UnifiedStudy study = unifiedStudyContextService.ensureStudy(
                snapshot.getTaskType(),
                snapshot.getExamId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                null,
                null,
                null,
                null,
                resolveSourceType(snapshot.getSourceMode()),
                "quality-task:" + snapshot.getTaskId(),
                null);

        if (StringUtils.hasText(snapshot.getStoredFilePath())) {
            // 本地上传的原始文件挂在 SOURCE 角色下，供详情页和后续处理复用。
            unifiedStudyContextService.upsertStudyFile(
                    study.getId(),
                    "SOURCE",
                    snapshot.getStoredFilePath(),
                    null,
                    snapshot.getOriginalFilename(),
                    null,
                    null,
                    true);
        }

        // 提交阶段先只落任务主记录，结果表会在任务执行完成后补写。
        UnifiedQcTask task = new UnifiedQcTask();
        task.setTaskNo(snapshot.getTaskId());
        task.setTaskTypeCode(snapshot.getTaskType());
        task.setStudyId(study.getId());
        task.setSubmittedBy(snapshot.getUserId());
        task.setSourceMode(firstNonBlank(normalizeText(snapshot.getSourceMode()), "local"));
        task.setTaskStatus(snapshot.getStatus());
        task.setSchedulerType("ASYNC");
        task.setMock(snapshot.isMock());
        task.setRequestedAt(submittedAt);
        task.setCreatedAt(submittedAt);
        task.setUpdatedAt(submittedAt);
        unifiedQcTaskMapper.insert(task);
        snapshot.setRecordId(task.getId());
    }

    /**
     * 根据任务执行快照同步任务状态和结果。
     * 数据链路：运行中快照 -> qc_tasks 更新 -> qc_results / qc_result_items 更新。
     */
    public void syncTask(MockQualityTaskSnapshot snapshot) {
        UnifiedQcTask task = resolveTask(snapshot);
        if (task == null || snapshot == null) {
            return;
        }

        // 先更新任务主记录状态，保证轮询任务详情时能及时看到状态变化。
        task.setTaskTypeCode(snapshot.getTaskType());
        task.setSourceMode(firstNonBlank(normalizeText(snapshot.getSourceMode()), task.getSourceMode(), "local"));
        task.setTaskStatus(snapshot.getStatus());
        task.setSchedulerType("ASYNC");
        task.setMock(snapshot.isMock());
        task.setRequestedAt(firstNonNull(snapshot.getSubmittedAt(), task.getRequestedAt(), task.getCreatedAt()));
        task.setStartedAt(snapshot.getStartedAt());
        task.setCompletedAt(snapshot.getCompletedAt());
        task.setErrorMessage(normalizeText(snapshot.getErrorMessage()));
        task.setUpdatedAt(LocalDateTime.now());
        unifiedQcTaskMapper.updateById(task);

        if (snapshot.getResult() == null || snapshot.getResult().isEmpty()) {
            return;
        }

        // result_version 当前固定为 1，后续若支持重复分析可扩展版本号。
        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));
        if (result == null) {
            result = new UnifiedQcResult();
        result.setTaskId(task.getId());
        result.setResultVersion(1);
        result.setCreatedAt(firstNonNull(snapshot.getCompletedAt(), snapshot.getSubmittedAt(), LocalDateTime.now()));
        }

        // 把 mock 结果中的质量结论、评分和主异常项抽取到结构化字段。
        result.setModelCode(snapshot.getTaskType());
        result.setModelVersion("mock");
        result.setQcStatus(MockQualityAnalysisSupport.resolveQcStatus(snapshot.getResult()));
        result.setQualityScore(BigDecimal.valueOf(roundOneDecimal(
                MockQualityAnalysisSupport.resolveQualityScore(snapshot.getResult()))));
        result.setAbnormalCount(MockQualityAnalysisSupport.resolveAbnormalCount(snapshot.getResult()));

        String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(snapshot.getResult());
        String normalizedPrimaryIssue = "未见明显异常".equals(primaryIssue) ? null : primaryIssue;
        result.setPrimaryIssueCode(normalizedPrimaryIssue);
        result.setPrimaryIssueName(normalizedPrimaryIssue);
        result.setSummaryJson(writeJson(Map.of(
                "summary", MockQualityAnalysisSupport.extractSummary(snapshot.getResult()),
                "patientInfo", MockQualityAnalysisSupport.extractPatientInfo(snapshot.getResult()))));
        result.setRawResultJson(writeJson(snapshot.getResult()));
        result.setUpdatedAt(LocalDateTime.now());

        if (result.getId() == null) {
            unifiedQcResultMapper.insert(result);
        } else {
            unifiedQcResultMapper.updateById(result);
        }

        // 明细项采取“先删后插”的简化策略，保持和最新结果完全一致。
        replaceResultItems(result.getId(), buildResultItems(snapshot.getResult()));
    }

    /**
     * 从数据库恢复运行期快照。
     * 主要用于消息消费端或服务重启后继续处理任务。
     */
    public MockQualityTaskSnapshot loadSnapshot(String taskNo) {
        String normalizedTaskNo = normalizeText(taskNo);
        if (normalizedTaskNo == null) {
            return null;
        }

        UnifiedQcTask task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                .eq("task_no", normalizedTaskNo)
                .last("LIMIT 1"));
        if (task == null) {
            return null;
        }

        UnifiedStudy study = task.getStudyId() == null ? null : unifiedStudyMapper.selectById(task.getStudyId());
        UnifiedPatient patient = study == null || study.getPatientId() == null
                ? null
                : unifiedPatientMapper.selectById(study.getPatientId());
        UnifiedStudyFile sourceFile = study == null
                ? null
                : unifiedStudyContextService.findPreferredFile(study.getId(), "SOURCE", "PREVIEW");
        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));

        // 用数据库中的统一模型记录重建前端和执行层都能识别的快照对象。
        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setRecordId(task.getId());
        snapshot.setTaskId(task.getTaskNo());
        snapshot.setTaskType(task.getTaskTypeCode());
        snapshot.setTaskTypeName(MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
        snapshot.setSourceMode(task.getSourceMode());
        snapshot.setUserId(task.getSubmittedBy());
        snapshot.setPatientName(patient == null ? null : patient.getPatientName());
        snapshot.setExamId(study == null ? null : study.getAccessionNumber());
        snapshot.setOriginalFilename(sourceFile == null ? null : sourceFile.getFileName());
        snapshot.setStoredFilePath(sourceFile == null
                ? null
                : firstNonBlank(sourceFile.getPublicPath(), sourceFile.getFilePath()));
        snapshot.setStatus(task.getTaskStatus());
        snapshot.setMock(Boolean.TRUE.equals(task.getMock()));
        snapshot.setSubmittedAt(firstNonNull(task.getRequestedAt(), task.getCreatedAt()));
        snapshot.setStartedAt(task.getStartedAt());
        snapshot.setCompletedAt(task.getCompletedAt());
        snapshot.setResult(parseJson(result == null ? null : result.getRawResultJson()));
        snapshot.setErrorMessage(task.getErrorMessage());
        return snapshot;
    }

    /**
     * 将统一模型任务实体转换为旧的 QcTaskRecord 视图。
     * 当前前端和部分服务仍依赖该结构，因此这里承担兼容层职责。
     */
    public QcTaskRecord toLegacyTaskRecord(UnifiedQcTask task) {
        if (task == null) {
            return null;
        }

        // 任务详情需要同时拼上 study、patient、source file 和 result 信息。
        UnifiedStudy study = task.getStudyId() == null ? null : unifiedStudyMapper.selectById(task.getStudyId());
        UnifiedPatient patient = study == null || study.getPatientId() == null
                ? null
                : unifiedPatientMapper.selectById(study.getPatientId());
        UnifiedStudyFile sourceFile = study == null
                ? null
                : unifiedStudyContextService.findPreferredFile(study.getId(), "SOURCE", "PREVIEW");
        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));

        QcTaskRecord taskRecord = new QcTaskRecord();
        taskRecord.setId(task.getId());
        taskRecord.setTaskId(task.getTaskNo());
        taskRecord.setUserId(task.getSubmittedBy());
        taskRecord.setTaskType(task.getTaskTypeCode());
        taskRecord.setTaskTypeName(MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
        taskRecord.setPatientName(patient == null ? null : patient.getPatientName());
        taskRecord.setExamId(study == null ? null : study.getAccessionNumber());
        taskRecord.setSourceMode(task.getSourceMode());
        taskRecord.setOriginalFilename(sourceFile == null ? null : sourceFile.getFileName());
        taskRecord.setStoredFilePath(sourceFile == null
                ? null
                : firstNonBlank(sourceFile.getPublicPath(), sourceFile.getFilePath()));
        taskRecord.setTaskStatus(task.getTaskStatus());
        taskRecord.setMock(task.getMock());
        taskRecord.setQcStatus(result == null ? null : result.getQcStatus());
        taskRecord.setQualityScore(result == null ? null : result.getQualityScore());
        taskRecord.setAbnormalCount(result == null ? null : result.getAbnormalCount());
        taskRecord.setPrimaryIssue(result == null ? null : result.getPrimaryIssueName());
        taskRecord.setResultJson(result == null ? null : result.getRawResultJson());
        taskRecord.setErrorMessage(task.getErrorMessage());
        taskRecord.setSubmittedAt(task.getRequestedAt());
        taskRecord.setStartedAt(task.getStartedAt());
        taskRecord.setCompletedAt(task.getCompletedAt());
        taskRecord.setCreatedAt(task.getCreatedAt());
        taskRecord.setUpdatedAt(task.getUpdatedAt());
        return taskRecord;
    }

    /**
     * 根据快照中的 recordId 或 taskId 定位统一任务实体。
     */
    private UnifiedQcTask resolveTask(MockQualityTaskSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        if (snapshot.getRecordId() != null) {
            UnifiedQcTask task = unifiedQcTaskMapper.selectById(snapshot.getRecordId());
            if (task != null) {
                return task;
            }
        }

        String normalizedTaskNo = normalizeText(snapshot.getTaskId());
        if (normalizedTaskNo == null) {
            return null;
        }

        UnifiedQcTask task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                .eq("task_no", normalizedTaskNo)
                .last("LIMIT 1"));
        if (task != null) {
            snapshot.setRecordId(task.getId());
        }
        return task;
    }

    /**
     * 用最新结果项替换旧的结果项明细。
     */
    private void replaceResultItems(Long resultId, List<UnifiedQcResultItem> items) {
        unifiedQcResultItemMapper.delete(new QueryWrapper<UnifiedQcResultItem>().eq("result_id", resultId));
        for (UnifiedQcResultItem item : items) {
            item.setResultId(resultId);
            unifiedQcResultItemMapper.insert(item);
        }
    }

    /**
     * 从结果 JSON 中构建结构化结果项列表。
     */
    private List<UnifiedQcResultItem> buildResultItems(Map<String, Object> result) {
        List<UnifiedQcResultItem> items = new ArrayList<>();
        int sortOrder = 10;
        for (Map<String, Object> qcItem : MockQualityAnalysisSupport.extractQcItems(result)) {
            // 每个质控项以 10 为步长排序，便于后续插入额外项。
            UnifiedQcResultItem item = new UnifiedQcResultItem();
            item.setItemCode("QC_ITEM_" + sortOrder);
            item.setItemName(String.valueOf(qcItem.getOrDefault("name", "质控项")));
            item.setItemStatus(String.valueOf(qcItem.getOrDefault("status", "")));
            item.setDetailText(String.valueOf(qcItem.getOrDefault("detail", qcItem.getOrDefault("description", ""))));
            item.setSortOrder(sortOrder);
            item.setCreatedAt(LocalDateTime.now());
            item.setUpdatedAt(LocalDateTime.now());
            items.add(item);
            sortOrder += 10;
        }

        if (!items.isEmpty()) {
            return items;
        }

        // 若结果中没有显式 qcItems，则退化为仅保留主异常项。
        UnifiedQcResultItem fallbackItem = new UnifiedQcResultItem();
        String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(result);
        fallbackItem.setItemCode("PRIMARY_ISSUE");
        fallbackItem.setItemName("未见明显异常".equals(primaryIssue) ? "质控结果" : primaryIssue);
        fallbackItem.setItemStatus(MockQualityAnalysisSupport.resolveQcStatus(result));
        fallbackItem.setScore(BigDecimal.valueOf(roundOneDecimal(MockQualityAnalysisSupport.resolveQualityScore(result))));
        fallbackItem.setDetailText("未见明显异常".equals(primaryIssue) ? "未见明显异常" : primaryIssue);
        fallbackItem.setSortOrder(10);
        fallbackItem.setCreatedAt(LocalDateTime.now());
        fallbackItem.setUpdatedAt(LocalDateTime.now());
        return List.of(fallbackItem);
    }

    /**
     * 安全解析 rawResultJson。
     */
    private Map<String, Object> parseJson(String rawJson) {
        if (!StringUtils.hasText(rawJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(rawJson, new TypeReference<>() {
            });
        } catch (Exception exception) {
            logger.warn("解析统一质控任务结果失败: {}", exception.getMessage());
            return Map.of();
        }
    }

    /**
     * 安全序列化结果对象。
     */
    private String writeJson(Object payload) {
        if (payload == null) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception exception) {
            logger.warn("写入统一质控任务结果失败: {}", exception.getMessage());
            return null;
        }
    }

    /**
     * 根据来源模式推导 Study.source_type。
     */
    private String resolveSourceType(String sourceMode) {
        return MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode) ? "PACS" : "MANUAL";
    }

    /**
     * 从多个候选值中取第一个非 null 值。
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
     * 从多个候选文本中取第一个非空字符串。
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

    /**
     * 保留一位小数，供评分字段展示。
     */
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
