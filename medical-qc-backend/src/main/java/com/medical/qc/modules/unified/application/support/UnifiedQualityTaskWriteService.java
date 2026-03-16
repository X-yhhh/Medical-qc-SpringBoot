package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskSnapshot;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultAuditLog;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedPatientMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultAuditLogMapper;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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
    private final UnifiedQcResultAuditLogMapper unifiedQcResultAuditLogMapper;
    private final UnifiedQcResultItemMapper unifiedQcResultItemMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final UnifiedPatientMapper unifiedPatientMapper;
    private final ObjectMapper objectMapper;

    public UnifiedQualityTaskWriteService(UnifiedStudyContextService unifiedStudyContextService,
                                          UnifiedQcTaskMapper unifiedQcTaskMapper,
                                          UnifiedQcResultMapper unifiedQcResultMapper,
                                          UnifiedQcResultAuditLogMapper unifiedQcResultAuditLogMapper,
                                          UnifiedQcResultItemMapper unifiedQcResultItemMapper,
                                          UnifiedStudyMapper unifiedStudyMapper,
                                          UnifiedPatientMapper unifiedPatientMapper,
                                          ObjectMapper objectMapper) {
        this.unifiedStudyContextService = unifiedStudyContextService;
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedQcResultAuditLogMapper = unifiedQcResultAuditLogMapper;
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
                snapshot.getPatientId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                snapshot.getGender(),
                snapshot.getAge(),
                snapshot.getStudyDate(),
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

        // 任务执行阶段用最终患者信息和 PACS/本地文件路径反向补齐统一检查主数据。
        syncStudyContext(task, snapshot);

        if (snapshot.getResult() == null || snapshot.getResult().isEmpty()) {
            return;
        }

        // 所有任务结果在入库前统一做一次归一化，避免 summary 与 qcItems 口径不一致。
        Map<String, Object> normalizedResult = MockQualityAnalysisSupport.normalizeResultPayload(snapshot.getResult());
        snapshot.setResult(normalizedResult);

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
        result.setModelCode(resolveModelCode(snapshot));
        result.setModelVersion(resolveModelVersion(snapshot));
        result.setQcStatus(MockQualityAnalysisSupport.resolveQcStatus(normalizedResult));
        result.setQualityScore(BigDecimal.valueOf(roundOneDecimal(
                MockQualityAnalysisSupport.resolveQualityScore(normalizedResult))));
        result.setAbnormalCount(MockQualityAnalysisSupport.resolveAbnormalCount(normalizedResult));

        String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(normalizedResult);
        String normalizedPrimaryIssue = "未见明显异常".equals(primaryIssue) ? null : primaryIssue;
        result.setPrimaryIssueCode(normalizedPrimaryIssue);
        result.setPrimaryIssueName(normalizedPrimaryIssue);
        result.setSummaryJson(writeJson(Map.of(
                "summary", MockQualityAnalysisSupport.extractSummary(normalizedResult),
                "patientInfo", MockQualityAnalysisSupport.extractPatientInfo(normalizedResult))));
        result.setRawResultJson(writeJson(normalizedResult));
        result.setReviewStatus(firstNonBlank(result.getReviewStatus(), "PENDING"));
        result.setUpdatedAt(LocalDateTime.now());

        if (result.getId() == null) {
            unifiedQcResultMapper.insert(result);
        } else {
            unifiedQcResultMapper.updateById(result);
        }

        // 明细项采取“先删后插”的简化策略，保持和最新结果完全一致。
        replaceResultItems(result.getId(), buildResultItems(normalizedResult));
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
        snapshot.setPatientId(patient == null ? null : patient.getPatientNo());
        snapshot.setGender(patient == null ? null : patient.getGender());
        snapshot.setAge(parseAge(patient == null ? null : patient.getAgeText()));
        snapshot.setStudyDate(study == null ? null : study.getStudyDate());
        snapshot.setOriginalFilename(sourceFile == null ? null : sourceFile.getFileName());
        snapshot.setStoredFilePath(sourceFile == null
                ? null
                : firstNonBlank(sourceFile.getFilePath(), sourceFile.getPublicPath()));
        snapshot.setStatus(task.getTaskStatus());
        snapshot.setMock(Boolean.TRUE.equals(task.getMock()));
        snapshot.setSubmittedAt(firstNonNull(task.getRequestedAt(), task.getCreatedAt()));
        snapshot.setStartedAt(task.getStartedAt());
        snapshot.setCompletedAt(task.getCompletedAt());
        snapshot.setResult(MockQualityAnalysisSupport.normalizeResultPayload(
                parseJson(result == null ? null : result.getRawResultJson())));
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
        Map<String, Object> normalizedResult = MockQualityAnalysisSupport.normalizeResultPayload(
                parseJson(result == null ? null : result.getRawResultJson()));

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
        if (!normalizedResult.isEmpty()) {
            taskRecord.setQcStatus(MockQualityAnalysisSupport.resolveQcStatus(normalizedResult));
            taskRecord.setQualityScore(BigDecimal.valueOf(roundOneDecimal(
                    MockQualityAnalysisSupport.resolveQualityScore(normalizedResult))));
            taskRecord.setAbnormalCount(MockQualityAnalysisSupport.resolveAbnormalCount(normalizedResult));
            String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(normalizedResult);
            taskRecord.setPrimaryIssue("未见明显异常".equals(primaryIssue) ? null : primaryIssue);
            taskRecord.setResultJson(writeJson(normalizedResult));
        } else {
            taskRecord.setQcStatus(result == null ? null : result.getQcStatus());
            taskRecord.setQualityScore(result == null ? null : result.getQualityScore());
            taskRecord.setAbnormalCount(result == null ? null : result.getAbnormalCount());
            taskRecord.setPrimaryIssue(result == null ? null : result.getPrimaryIssueName());
            taskRecord.setResultJson(result == null ? null : result.getRawResultJson());
        }
        taskRecord.setReviewStatus(result == null ? "PENDING" : firstNonBlank(result.getReviewStatus(), "PENDING"));
        taskRecord.setReviewComment(result == null ? null : result.getReviewComment());
        taskRecord.setReviewedBy(result == null ? null : result.getReviewedBy());
        taskRecord.setReviewedAt(result == null ? null : result.getReviewedAt());
        taskRecord.setLockedAt(result == null ? null : result.getLockedAt());
        taskRecord.setExternalRef(result == null ? null : result.getExternalRef());
        taskRecord.setDevice(study == null ? null : study.getDeviceModel());
        taskRecord.setErrorMessage(task.getErrorMessage());
        taskRecord.setSubmittedAt(task.getRequestedAt());
        taskRecord.setStartedAt(task.getStartedAt());
        taskRecord.setCompletedAt(task.getCompletedAt());
        taskRecord.setCreatedAt(task.getCreatedAt());
        taskRecord.setUpdatedAt(task.getUpdatedAt());
        return taskRecord;
    }

    /**
     * 修复历史任务结果中的 summary、主异常项和 mock 标记。
     */
    public Map<String, Object> repairHistoricalTaskResults(List<String> taskIds, Long operatorId) {
        List<UnifiedQcTask> tasks = loadRepairTasks(taskIds);
        int scannedCount = 0;
        int updatedTaskCount = 0;
        int updatedResultCount = 0;
        int skippedCount = 0;
        List<Map<String, Object>> repairedItems = new ArrayList<>();

        for (UnifiedQcTask task : tasks) {
            scannedCount += 1;
            UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                    .eq("task_id", task.getId())
                    .eq("result_version", 1)
                    .last("LIMIT 1"));
            if (result == null) {
                skippedCount += 1;
                continue;
            }

            Map<String, Object> repairedPayload = buildRepairResultPayload(task, result);
            if (repairedPayload.isEmpty()) {
                skippedCount += 1;
                continue;
            }

            String repairedRawJson = writeJson(repairedPayload);
            Map<String, Object> repairedSummaryPayload = Map.of(
                    "summary", MockQualityAnalysisSupport.extractSummary(repairedPayload),
                    "patientInfo", MockQualityAnalysisSupport.extractPatientInfo(repairedPayload));
            String repairedSummaryJson = writeJson(repairedSummaryPayload);
            String repairedQcStatus = MockQualityAnalysisSupport.resolveQcStatus(repairedPayload);
            BigDecimal repairedScore = BigDecimal.valueOf(roundOneDecimal(
                    MockQualityAnalysisSupport.resolveQualityScore(repairedPayload)));
            int repairedAbnormalCount = MockQualityAnalysisSupport.resolveAbnormalCount(repairedPayload);
            String repairedPrimaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(repairedPayload);
            String normalizedPrimaryIssue = "未见明显异常".equals(repairedPrimaryIssue) ? null : repairedPrimaryIssue;
            Map<String, Object> existingRawPayload = MockQualityAnalysisSupport.normalizeResultPayload(
                    parseJson(result.getRawResultJson()));
            Map<String, Object> existingSummaryPayload = parseJson(result.getSummaryJson());

            boolean resultChanged = false;
            if (!Objects.equals(result.getQcStatus(), repairedQcStatus)) {
                result.setQcStatus(repairedQcStatus);
                resultChanged = true;
            }
            if (!isSameDecimal(result.getQualityScore(), repairedScore)) {
                result.setQualityScore(repairedScore);
                resultChanged = true;
            }
            if (!Objects.equals(result.getAbnormalCount(), repairedAbnormalCount)) {
                result.setAbnormalCount(repairedAbnormalCount);
                resultChanged = true;
            }
            if (!Objects.equals(result.getPrimaryIssueCode(), normalizedPrimaryIssue)) {
                result.setPrimaryIssueCode(normalizedPrimaryIssue);
                resultChanged = true;
            }
            if (!Objects.equals(result.getPrimaryIssueName(), normalizedPrimaryIssue)) {
                result.setPrimaryIssueName(normalizedPrimaryIssue);
                resultChanged = true;
            }
            if (resultChanged || !StringUtils.hasText(result.getRawResultJson())) {
                if (!Objects.equals(existingRawPayload, repairedPayload) || !StringUtils.hasText(result.getRawResultJson())) {
                    result.setRawResultJson(repairedRawJson);
                }
            }
            if (resultChanged || !StringUtils.hasText(result.getSummaryJson())) {
                if (!Objects.equals(existingSummaryPayload, repairedSummaryPayload) || !StringUtils.hasText(result.getSummaryJson())) {
                    result.setSummaryJson(repairedSummaryJson);
                }
            }
            if (resultChanged) {
                LocalDateTime updatedAt = LocalDateTime.now();
                result.setUpdatedAt(updatedAt);
                unifiedQcResultMapper.update(
                        null,
                        new UpdateWrapper<UnifiedQcResult>()
                                .eq("id", result.getId())
                                .set("raw_result_json", result.getRawResultJson())
                                .set("summary_json", result.getSummaryJson())
                                .set("qc_status", result.getQcStatus())
                                .set("quality_score", result.getQualityScore())
                                .set("abnormal_count", result.getAbnormalCount())
                                .set("primary_issue_code", result.getPrimaryIssueCode())
                                .set("primary_issue_name", result.getPrimaryIssueName())
                                .set("updated_at", updatedAt));
                updatedResultCount += 1;
            }

            boolean taskChanged = !Boolean.FALSE.equals(task.getMock());
            if (taskChanged) {
                task.setMock(false);
                task.setUpdatedAt(LocalDateTime.now());
                unifiedQcTaskMapper.updateById(task);
                updatedTaskCount += 1;
            }

            if (resultChanged || taskChanged) {
                if (operatorId != null) {
                    Map<String, Object> repairPayload = new HashMap<>();
                    repairPayload.put("qcStatus", repairedQcStatus);
                    repairPayload.put("abnormalCount", repairedAbnormalCount);
                    repairPayload.put("primaryIssue", normalizedPrimaryIssue);
                    repairPayload.put("taskMock", false);
                    insertAuditLog(
                            task.getId(),
                            result.getId(),
                            operatorId,
                            "repair",
                            "回填历史任务结果口径",
                            repairPayload);
                }
                Map<String, Object> repairedItem = new HashMap<>();
                repairedItem.put("recordId", task.getId());
                repairedItem.put("taskId", task.getTaskNo());
                repairedItem.put("taskType", task.getTaskTypeCode());
                repairedItem.put("qcStatus", repairedQcStatus);
                repairedItem.put("abnormalCount", repairedAbnormalCount);
                repairedItem.put("primaryIssue", normalizedPrimaryIssue);
                repairedItem.put("mock", false);
                repairedItems.add(repairedItem);
            }
        }

        Map<String, Object> response = new HashMap<>();
        response.put("scannedCount", scannedCount);
        response.put("updatedTaskCount", updatedTaskCount);
        response.put("updatedResultCount", updatedResultCount);
        response.put("skippedCount", skippedCount);
        response.put("items", repairedItems);
        return response;
    }

    /**
     * 更新人工复核状态并记录审计日志。
     */
    public void updateReview(String taskNo,
                             Long scopedUserId,
                             Long operatorId,
                             String reviewStatus,
                             String reviewComment,
                             boolean lockResult,
                             String externalRef) {
        UnifiedQcTask task = unifiedQcTaskMapper.selectOne(new QueryWrapper<UnifiedQcTask>()
                .eq("task_no", normalizeText(taskNo))
                .last("LIMIT 1"));
        if (task == null) {
            throw new IllegalArgumentException("质控任务不存在");
        }
        if (scopedUserId != null && task.getSubmittedBy() != null && !scopedUserId.equals(task.getSubmittedBy())) {
            throw new IllegalArgumentException("无权修改该质控任务");
        }

        UnifiedQcResult result = unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));
        if (result == null) {
            throw new IllegalArgumentException("质控结果不存在，暂无法复核");
        }

        String normalizedReviewStatus = normalizeReviewStatus(reviewStatus);
        LocalDateTime now = LocalDateTime.now();
        result.setReviewStatus(normalizedReviewStatus);
        result.setReviewComment(normalizeText(reviewComment));
        result.setReviewedBy(operatorId);
        result.setReviewedAt(now);
        if (lockResult) {
            result.setLockedAt(now);
        }
        if (StringUtils.hasText(externalRef)) {
            result.setExternalRef(externalRef.trim());
        }
        result.setUpdatedAt(now);
        unifiedQcResultMapper.updateById(result);

        insertAuditLog(
                task.getId(),
                result.getId(),
                operatorId,
                "review",
                firstNonBlank(normalizeText(reviewComment), "更新人工复核状态"),
                Map.of(
                        "reviewStatus", normalizedReviewStatus,
                        "lockResult", lockResult,
                        "externalRef", normalizeText(externalRef)));
    }

    /**
     * 记录任务级审计日志。
     */
    public void insertAuditLog(Long taskId,
                               Long resultId,
                               Long operatorId,
                               String actionType,
                               String actionComment,
                               Object payload) {
        UnifiedQcResultAuditLog auditLog = new UnifiedQcResultAuditLog();
        auditLog.setTaskId(taskId);
        auditLog.setResultId(resultId);
        auditLog.setOperatorId(operatorId);
        auditLog.setActionType(actionType);
        auditLog.setActionComment(normalizeText(actionComment));
        auditLog.setPayloadJson(writeJson(payload));
        auditLog.setCreatedAt(LocalDateTime.now());
        unifiedQcResultAuditLogMapper.insert(auditLog);
    }

    /**
     * 用快照和最终结果中的患者信息回写统一检查上下文。
     */
    private void syncStudyContext(UnifiedQcTask task, MockQualityTaskSnapshot snapshot) {
        if (task == null || snapshot == null) {
            return;
        }

        Map<String, Object> patientInfo = MockQualityAnalysisSupport.extractPatientInfo(snapshot.getResult());
        String patientId = firstNonBlank(normalizeObjectText(patientInfo.get("patientId")), normalizeText(snapshot.getPatientId()));
        String patientName = firstNonBlank(normalizeObjectText(patientInfo.get("name")), normalizeText(snapshot.getPatientName()));
        String accessionNumber = firstNonBlank(
                normalizeObjectText(patientInfo.get("accessionNumber")),
                normalizeObjectText(patientInfo.get("studyId")),
                normalizeText(snapshot.getExamId()));
        String gender = firstNonBlank(normalizeObjectText(patientInfo.get("gender")), normalizeText(snapshot.getGender()));
        Integer age = firstNonNull(parseInteger(patientInfo.get("age")), snapshot.getAge());
        LocalDate studyDate = firstNonNull(parseLocalDate(patientInfo.get("studyDate")), snapshot.getStudyDate());
        String deviceModel = normalizeObjectText(patientInfo.get("device"));

        if (!StringUtils.hasText(patientName) || !StringUtils.hasText(accessionNumber)) {
            return;
        }

        UnifiedStudy study = unifiedStudyContextService.ensureStudy(
                task.getTaskTypeCode(),
                patientId,
                patientName,
                accessionNumber,
                gender,
                age,
                studyDate,
                null,
                resolveSourceType(snapshot.getSourceMode()),
                "quality-task:" + snapshot.getTaskId(),
                deviceModel);
        task.setStudyId(study.getId());
        task.setUpdatedAt(LocalDateTime.now());
        unifiedQcTaskMapper.updateById(task);
        snapshot.setRecordId(task.getId());

        if (StringUtils.hasText(snapshot.getStoredFilePath())) {
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
            item.setItemCode(firstNonBlank(
                    normalizeObjectText(qcItem.get("key")),
                    normalizeObjectText(qcItem.get("itemCode")),
                    "QC_ITEM_" + sortOrder));
            item.setItemName(String.valueOf(qcItem.getOrDefault("name", "质控项")));
            item.setItemStatus(String.valueOf(qcItem.getOrDefault("status", "")));
            item.setThresholdValue(normalizeThreshold(qcItem.get("threshold")));
            item.setScore(resolveItemScore(qcItem));
            item.setDetailText(String.valueOf(qcItem.getOrDefault("detail", qcItem.getOrDefault("description", ""))));
            item.setDetailJson(writeJson(qcItem));
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
     * 加载需要修复的任务集合；taskIds 为空时返回全部历史任务。
     */
    private List<UnifiedQcTask> loadRepairTasks(List<String> taskIds) {
        QueryWrapper<UnifiedQcTask> queryWrapper = new QueryWrapper<>();
        if (taskIds != null && !taskIds.isEmpty()) {
            List<String> normalizedTaskIds = taskIds.stream()
                    .map(this::normalizeText)
                    .filter(StringUtils::hasText)
                    .toList();
            if (normalizedTaskIds.isEmpty()) {
                return List.of();
            }
            queryWrapper.in("task_no", normalizedTaskIds);
        }
        queryWrapper.orderByAsc("id");
        return unifiedQcTaskMapper.selectList(queryWrapper);
    }

    /**
     * 基于原始 JSON 或结果项表构造可回写的标准化结果载荷。
     */
    private Map<String, Object> buildRepairResultPayload(UnifiedQcTask task, UnifiedQcResult result) {
        Map<String, Object> normalizedRawResult = MockQualityAnalysisSupport.normalizeResultPayload(
                parseJson(result.getRawResultJson()));
        if (!normalizedRawResult.isEmpty()) {
            return normalizedRawResult;
        }

        List<UnifiedQcResultItem> resultItems = unifiedQcResultItemMapper.selectList(new QueryWrapper<UnifiedQcResultItem>()
                .eq("result_id", result.getId())
                .orderByAsc("sort_order"));
        if (resultItems.isEmpty()) {
            return Map.of();
        }

        UnifiedStudy study = task.getStudyId() == null ? null : unifiedStudyMapper.selectById(task.getStudyId());
        UnifiedPatient patient = study == null || study.getPatientId() == null
                ? null
                : unifiedPatientMapper.selectById(study.getPatientId());
        UnifiedStudyFile sourceFile = study == null
                ? null
                : unifiedStudyContextService.findPreferredFile(study.getId(), "SOURCE", "PREVIEW");

        Map<String, Object> payload = new HashMap<>();
        payload.put("taskType", task.getTaskTypeCode());
        payload.put("taskTypeName", MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode()));
        payload.put("mock", false);
        payload.put("modelCode", result.getModelCode());
        payload.put("modelVersion", result.getModelVersion());
        payload.put("analysisMode", isRuleBasedTask(task.getTaskTypeCode()) ? "rule-based" : "python-model");
        payload.put("analysisLabel", isRuleBasedTask(task.getTaskTypeCode()) ? "规则辅助分析" : "模型推理");
        payload.put("patientInfo", buildRepairPatientInfo(task, study, patient, sourceFile));
        payload.put("qcItems", buildRepairQcItems(resultItems));
        return MockQualityAnalysisSupport.normalizeResultPayload(payload);
    }

    /**
     * 构造历史任务修复时的患者信息块。
     */
    private Map<String, Object> buildRepairPatientInfo(UnifiedQcTask task,
                                                       UnifiedStudy study,
                                                       UnifiedPatient patient,
                                                       UnifiedStudyFile sourceFile) {
        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("name", patient == null ? null : patient.getPatientName());
        patientInfo.put("patientId", patient == null ? null : patient.getPatientNo());
        patientInfo.put("gender", patient == null ? null : patient.getGender());
        patientInfo.put("age", patient == null ? null : patient.getAgeText());
        patientInfo.put("studyId", study == null ? null : study.getAccessionNumber());
        patientInfo.put("accessionNumber", study == null ? null : study.getAccessionNumber());
        patientInfo.put("studyDate", study == null ? null : study.getStudyDate());
        patientInfo.put("device", study == null ? null : study.getDeviceModel());
        patientInfo.put("sourceMode", task.getSourceMode());
        patientInfo.put("sourceLabel", MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(task.getSourceMode()) ? "PACS 调取" : "本地上传");
        patientInfo.put("originalFilename", sourceFile == null ? null : sourceFile.getFileName());
        return patientInfo;
    }

    /**
     * 构造历史任务修复时的质控项列表，优先保留明细 JSON 中的扩展字段。
     */
    private List<Map<String, Object>> buildRepairQcItems(List<UnifiedQcResultItem> resultItems) {
        List<Map<String, Object>> qcItems = new ArrayList<>();
        for (UnifiedQcResultItem resultItem : resultItems) {
            Map<String, Object> detailJson = parseJson(resultItem.getDetailJson());
            Map<String, Object> qcItem = detailJson.isEmpty() ? new HashMap<>() : new HashMap<>(detailJson);
            qcItem.putIfAbsent("key", resultItem.getItemCode());
            qcItem.putIfAbsent("itemCode", resultItem.getItemCode());
            qcItem.putIfAbsent("name", resultItem.getItemName());
            qcItem.putIfAbsent("status", resultItem.getItemStatus());
            qcItem.putIfAbsent("detail", resultItem.getDetailText());
            qcItem.putIfAbsent("description", firstNonBlank(resultItem.getDetailText(), resultItem.getItemName()));
            qcItems.add(qcItem);
        }
        return qcItems;
    }

    /**
     * 判断任务是否属于规则辅助分析链路。
     */
    private boolean isRuleBasedTask(String taskType) {
        return MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST.equals(taskType)
                || MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(taskType);
    }

    /**
     * 比较两个分值是否数值相同，忽略 BigDecimal scale 差异。
     */
    private boolean isSameDecimal(BigDecimal left, BigDecimal right) {
        if (left == null || right == null) {
            return left == null && right == null;
        }
        return left.compareTo(right) == 0;
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
     * 归一化人工复核状态。
     */
    private String normalizeReviewStatus(String reviewStatus) {
        String normalized = firstNonBlank(reviewStatus, "PENDING");
        return switch (normalized) {
            case "CONFIRMED", "REJECTED", "PENDING" -> normalized;
            default -> throw new IllegalArgumentException("不支持的人工复核状态");
        };
    }

    /**
     * 规范化任意对象的文本值。
     */
    private String normalizeObjectText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        if (!StringUtils.hasText(text) || "null".equalsIgnoreCase(text)) {
            return null;
        }
        return text;
    }

    /**
     * 安全把任意对象解析为整数。
     */
    private Integer parseInteger(Object value) {
        String text = normalizeObjectText(value);
        if (text == null) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 安全把任意对象解析为日期。
     */
    private LocalDate parseLocalDate(Object value) {
        String text = normalizeObjectText(value);
        if (text == null) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception exception) {
            return null;
        }
    }

    /**
     * 安全把年龄文本解析回整数。
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
     * 解析结果中的模型编码。
     */
    private String resolveModelCode(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> result = snapshot.getResult();
        String modelCode = result == null ? null : normalizeObjectText(result.get("modelCode"));
        if (modelCode != null) {
            return modelCode;
        }
        return snapshot.getTaskType();
    }

    /**
     * 解析结果中的模型版本。
     */
    private String resolveModelVersion(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> result = snapshot.getResult();
        String modelVersion = result == null ? null : normalizeObjectText(result.get("modelVersion"));
        if (modelVersion != null) {
            return modelVersion;
        }
        return snapshot.isMock() ? "mock" : "unknown";
    }

    /**
     * 归一化阈值字段。
     */
    private String normalizeThreshold(Object threshold) {
        if (threshold == null) {
            return null;
        }
        String text = String.valueOf(threshold).trim();
        return text.isEmpty() || "null".equalsIgnoreCase(text) ? null : text;
    }

    /**
     * 从明细项中解析可展示分值。
     */
    private BigDecimal resolveItemScore(Map<String, Object> qcItem) {
        Object passProbability = qcItem.get("passProbability");
        if (passProbability instanceof Number number) {
            return BigDecimal.valueOf(roundOneDecimal(number.doubleValue() * 100.0D));
        }
        Object failProbability = qcItem.get("failProbability");
        if (failProbability instanceof Number number) {
            return BigDecimal.valueOf(roundOneDecimal((1.0D - number.doubleValue()) * 100.0D));
        }
        return null;
    }

    /**
     * 保留一位小数，供评分字段展示。
     */
    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }
}
