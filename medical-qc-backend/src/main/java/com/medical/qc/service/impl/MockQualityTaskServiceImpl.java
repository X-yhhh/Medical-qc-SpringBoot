package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.entity.QcTaskRecord;
import com.medical.qc.entity.User;
import com.medical.qc.mapper.QcTaskRecordMapper;
import com.medical.qc.messaging.MockQualityTaskMessage;
import com.medical.qc.service.IssueService;
import com.medical.qc.service.MockQualityTaskService;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jms.JmsException;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 其余四个质控模块的异步任务服务实现。
 *
 * <p>当前结果仍然是 mock，但任务状态、结果摘要和完整结果都会持久化到数据库，
 * 从而支撑任务中心页面、异常工单闭环和重启后的历史追踪。</p>
 */
@Service
public class MockQualityTaskServiceImpl implements MockQualityTaskService, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(MockQualityTaskServiceImpl.class);
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final ActiveMqProperties activeMqProperties;
    private final ObjectProvider<JmsTemplate> jmsTemplateProvider;
    private final QcTaskRecordMapper qcTaskRecordMapper;
    private final IssueService issueService;
    private final ObjectMapper objectMapper;
    private final ExecutorService fallbackExecutor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, MockQualityTaskSnapshot> taskStore = new ConcurrentHashMap<>();
    private final Path taskUploadRoot = Paths.get("uploads", "mock-quality-tasks");

    public MockQualityTaskServiceImpl(ActiveMqProperties activeMqProperties,
                                      ObjectProvider<JmsTemplate> jmsTemplateProvider,
                                      QcTaskRecordMapper qcTaskRecordMapper,
                                      IssueService issueService,
                                      ObjectMapper objectMapper) {
        this.activeMqProperties = activeMqProperties;
        this.jmsTemplateProvider = jmsTemplateProvider;
        this.qcTaskRecordMapper = qcTaskRecordMapper;
        this.issueService = issueService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> submitTask(String taskType,
                                          MultipartFile file,
                                          String patientName,
                                          String examId,
                                          String sourceMode,
                                          User user) throws IOException {
        validateTaskType(taskType);
        validatePatientName(patientName);
        validateExamId(examId);

        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(sourceMode);
        if (!MockQualityAnalysisSupport.isSupportedSourceMode(normalizedSourceMode)) {
            throw new IllegalArgumentException("不支持的任务来源类型: " + sourceMode);
        }

        MultipartFile normalizedFile = normalizeUploadFile(file);
        if (MockQualityAnalysisSupport.SOURCE_MODE_LOCAL.equals(normalizedSourceMode) && normalizedFile == null) {
            throw new IllegalArgumentException("本地上传模式下，影像文件不能为空");
        }

        String taskId = UUID.randomUUID().toString();
        LocalDateTime submittedAt = LocalDateTime.now();
        String originalFilename = resolveOriginalFilename(normalizedFile, normalizedSourceMode, examId);
        String storedFilePath = storeUploadFile(taskId, normalizedFile, originalFilename);

        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setTaskType(taskType);
        snapshot.setTaskTypeName(MockQualityAnalysisSupport.resolveTaskTypeName(taskType));
        snapshot.setSourceMode(normalizedSourceMode);
        snapshot.setUserId(user == null ? null : user.getId());
        snapshot.setPatientName(patientName.trim());
        snapshot.setExamId(examId.trim());
        snapshot.setOriginalFilename(originalFilename);
        snapshot.setStoredFilePath(storedFilePath);
        snapshot.setStatus(STATUS_PENDING);
        snapshot.setSubmittedAt(submittedAt);
        snapshot.setMock(true);
        taskStore.put(taskId, snapshot);
        persistSubmittedTask(snapshot);

        MockQualityTaskMessage message = new MockQualityTaskMessage(
                taskId,
                taskType,
                normalizedSourceMode,
                snapshot.getUserId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                originalFilename,
                storedFilePath,
                submittedAt);

        dispatchTask(snapshot, message);
        return toSubmitResponse(snapshot);
    }

    @Override
    public Map<String, Object> getTaskPage(Long scopedUserId,
                                           int page,
                                           int limit,
                                           String query,
                                           String taskType,
                                           String status,
                                           String sourceMode) {
        Page<QcTaskRecord> taskPage = new Page<>(normalizePage(page), normalizeLimit(limit));
        QueryWrapper<QcTaskRecord> pageQueryWrapper = buildTaskQueryWrapper(
                scopedUserId, query, taskType, status, sourceMode);
        pageQueryWrapper.orderByDesc("submitted_at");

        Page<QcTaskRecord> pageResult = qcTaskRecordMapper.selectPage(taskPage, pageQueryWrapper);
        List<QcTaskRecord> scopedTaskRecords = qcTaskRecordMapper.selectList(buildTaskQueryWrapper(
                scopedUserId, null, null, null, null).orderByDesc("submitted_at"));

        Map<String, Object> response = new HashMap<>();
        response.put("items", pageResult.getRecords().stream().map(this::toTaskListItem).toList());
        response.put("total", pageResult.getTotal());
        response.put("page", (int) pageResult.getCurrent());
        response.put("limit", (int) pageResult.getSize());
        response.put("pages", pageResult.getPages());
        response.put("summary", buildTaskSummary(scopedTaskRecords));
        return response;
    }

    @Override
    public Map<String, Object> getTaskDetail(String taskId, Long currentUserId) {
        if (!StringUtils.hasText(taskId)) {
            throw new IllegalArgumentException("任务 ID 不能为空");
        }

        QcTaskRecord taskRecord = findTaskRecordByTaskId(taskId.trim());
        if (taskRecord == null) {
            throw new IllegalArgumentException("质控任务不存在或已过期");
        }

        validateTaskAccess(taskRecord, currentUserId);
        return toTaskDetailResponse(taskRecord);
    }

    @Override
    public void processTask(MockQualityTaskMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())) {
            logger.warn("收到无效的 mock 质控任务消息");
            return;
        }

        QcTaskRecord taskRecord = findTaskRecordByTaskId(message.getTaskId());
        if (taskRecord == null) {
            logger.warn("质控任务不存在，忽略消息: {}", message.getTaskId());
            return;
        }

        MockQualityTaskSnapshot snapshot = taskStore.computeIfAbsent(
                message.getTaskId(),
                taskId -> buildSnapshotFromRecord(taskRecord));
        if (snapshot == null) {
            logger.warn("质控任务快照构建失败，忽略消息: {}", message.getTaskId());
            return;
        }

        if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAILED.equals(snapshot.getStatus())) {
            return;
        }

        snapshot.setStatus(STATUS_PROCESSING);
        snapshot.setStartedAt(LocalDateTime.now());
        syncTaskRecordFromSnapshot(snapshot);

        try {
            doProcessTask(snapshot, message);
            snapshot.setStatus(STATUS_SUCCESS);
            snapshot.setCompletedAt(LocalDateTime.now());
            syncTaskRecordFromSnapshot(snapshot);
            issueService.syncQualityTaskIssue(qcTaskRecordMapper.selectById(snapshot.getRecordId()));
        } catch (Exception ex) {
            snapshot.setStatus(STATUS_FAILED);
            snapshot.setCompletedAt(LocalDateTime.now());
            snapshot.setErrorMessage(ex.getMessage());
            syncTaskRecordFromSnapshot(snapshot);
            logger.error("处理 mock 质控任务失败: {}", message.getTaskId(), ex);
        } finally {
            if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAILED.equals(snapshot.getStatus())) {
                taskStore.remove(message.getTaskId());
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        fallbackExecutor.shutdown();
        fallbackExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 当前真正执行 mock 质控分析的位置。
     *
     * <p>后续接真实算法时建议直接替换此方法：</p>
     * <ul>
     *     <li>本地上传模式可根据 message.getStoredFilePath() 读取文件</li>
     *     <li>PACS 模式可根据 message.getPatientName()/getExamId() 发起真实调取</li>
     *     <li>算法完成后将结果写回 snapshot.setResult(...)</li>
     * </ul>
     */
    private void doProcessTask(MockQualityTaskSnapshot snapshot, MockQualityTaskMessage message) throws Exception {
        Map<String, Object> result = MockQualityAnalysisSupport.createMockResult(
                message.getTaskType(),
                message.getPatientName(),
                message.getExamId(),
                message.getSourceMode(),
                message.getOriginalFilename());

        long sleepMs = resolveDuration(result);
        Thread.sleep(Math.max(300L, sleepMs));
        snapshot.setResult(result);
    }

    /**
     * 首次提交任务时插入持久化记录，保证前端轮询和任务中心都能查询到任务状态。
     */
    private void persistSubmittedTask(MockQualityTaskSnapshot snapshot) {
        QcTaskRecord taskRecord = new QcTaskRecord();
        taskRecord.setTaskId(snapshot.getTaskId());
        taskRecord.setUserId(snapshot.getUserId());
        taskRecord.setTaskType(snapshot.getTaskType());
        taskRecord.setTaskTypeName(snapshot.getTaskTypeName());
        taskRecord.setPatientName(snapshot.getPatientName());
        taskRecord.setExamId(snapshot.getExamId());
        taskRecord.setSourceMode(snapshot.getSourceMode());
        taskRecord.setOriginalFilename(snapshot.getOriginalFilename());
        taskRecord.setStoredFilePath(snapshot.getStoredFilePath());
        taskRecord.setTaskStatus(snapshot.getStatus());
        taskRecord.setMock(snapshot.isMock());
        taskRecord.setSubmittedAt(snapshot.getSubmittedAt());
        taskRecord.setCreatedAt(snapshot.getSubmittedAt());
        taskRecord.setUpdatedAt(snapshot.getSubmittedAt());
        qcTaskRecordMapper.insert(taskRecord);
        snapshot.setRecordId(taskRecord.getId());
    }

    /**
     * 将内存任务快照回写到持久化记录。
     */
    private void syncTaskRecordFromSnapshot(MockQualityTaskSnapshot snapshot) {
        QcTaskRecord taskRecord = resolveTaskRecord(snapshot);
        if (taskRecord == null) {
            return;
        }

        taskRecord.setTaskType(snapshot.getTaskType());
        taskRecord.setTaskTypeName(snapshot.getTaskTypeName());
        taskRecord.setPatientName(snapshot.getPatientName());
        taskRecord.setExamId(snapshot.getExamId());
        taskRecord.setSourceMode(snapshot.getSourceMode());
        taskRecord.setOriginalFilename(snapshot.getOriginalFilename());
        taskRecord.setStoredFilePath(snapshot.getStoredFilePath());
        taskRecord.setTaskStatus(snapshot.getStatus());
        taskRecord.setMock(snapshot.isMock());
        taskRecord.setSubmittedAt(snapshot.getSubmittedAt());
        taskRecord.setStartedAt(snapshot.getStartedAt());
        taskRecord.setCompletedAt(snapshot.getCompletedAt());
        taskRecord.setErrorMessage(normalizeText(snapshot.getErrorMessage()));

        if (snapshot.getResult() != null && !snapshot.getResult().isEmpty()) {
            applyResultSummary(taskRecord, snapshot.getResult());
            taskRecord.setResultJson(writeResultJson(snapshot.getResult()));
        }

        taskRecord.setUpdatedAt(LocalDateTime.now());
        qcTaskRecordMapper.updateById(taskRecord);
    }

    /**
     * 将质控结果摘要同步到任务记录，便于列表页直接展示评分和异常数。
     */
    private void applyResultSummary(QcTaskRecord taskRecord, Map<String, Object> result) {
        taskRecord.setQcStatus(MockQualityAnalysisSupport.resolveQcStatus(result));
        taskRecord.setQualityScore(BigDecimal.valueOf(roundOneDecimal(
                MockQualityAnalysisSupport.resolveQualityScore(result))));
        taskRecord.setAbnormalCount(MockQualityAnalysisSupport.resolveAbnormalCount(result));

        String primaryIssue = MockQualityAnalysisSupport.resolvePrimaryIssue(result);
        taskRecord.setPrimaryIssue("未见明显异常".equals(primaryIssue) ? null : primaryIssue);
    }

    private void dispatchTask(MockQualityTaskSnapshot snapshot, MockQualityTaskMessage message) {
        if (!activeMqProperties.isEnabled()) {
            dispatchByLocalExecutor(message);
            return;
        }

        JmsTemplate jmsTemplate = jmsTemplateProvider.getIfAvailable();
        if (jmsTemplate == null) {
            logger.warn("ActiveMQ 已启用，但未找到 JmsTemplate，任务改为本地异步执行: {}", snapshot.getTaskId());
            dispatchByLocalExecutor(message);
            return;
        }

        try {
            jmsTemplate.convertAndSend(activeMqProperties.getQueue().getMockQualityTask(), message);
        } catch (JmsException ex) {
            logger.warn("发送 mock 质控任务消息失败，改为本地异步执行: {}", snapshot.getTaskId(), ex);
            dispatchByLocalExecutor(message);
        }
    }

    private void dispatchByLocalExecutor(MockQualityTaskMessage message) {
        fallbackExecutor.submit(() -> processTask(message));
    }

    private QueryWrapper<QcTaskRecord> buildTaskQueryWrapper(Long scopedUserId,
                                                             String query,
                                                             String taskType,
                                                             String status,
                                                             String sourceMode) {
        QueryWrapper<QcTaskRecord> queryWrapper = new QueryWrapper<>();
        if (scopedUserId != null) {
            queryWrapper.eq("user_id", scopedUserId);
        }

        String normalizedQuery = normalizeText(query);
        if (normalizedQuery != null) {
            queryWrapper.and(wrapper -> wrapper.like("patient_name", normalizedQuery)
                    .or().like("exam_id", normalizedQuery)
                    .or().like("task_id", normalizedQuery)
                    .or().like("primary_issue", normalizedQuery));
        }

        String normalizedTaskType = normalizeText(taskType);
        if (normalizedTaskType != null) {
            queryWrapper.eq("task_type", normalizedTaskType);
        }

        String normalizedStatus = normalizeText(status);
        if (normalizedStatus != null) {
            queryWrapper.eq("task_status", normalizedStatus);
        }

        String normalizedSourceMode = normalizeText(sourceMode);
        if (normalizedSourceMode != null) {
            queryWrapper.eq("source_mode", normalizedSourceMode);
        }
        return queryWrapper;
    }

    private Map<String, Object> buildTaskSummary(List<QcTaskRecord> taskRecords) {
        long totalTasks = taskRecords.size();
        long pendingTasks = taskRecords.stream().filter(record -> STATUS_PENDING.equals(record.getTaskStatus())).count();
        long processingTasks = taskRecords.stream().filter(record -> STATUS_PROCESSING.equals(record.getTaskStatus())).count();
        long successTasks = taskRecords.stream().filter(record -> STATUS_SUCCESS.equals(record.getTaskStatus())).count();
        long failedTasks = taskRecords.stream().filter(record -> STATUS_FAILED.equals(record.getTaskStatus())).count();
        long abnormalTasks = taskRecords.stream().filter(this::isAbnormalTaskRecord).count();
        long todayTasks = taskRecords.stream()
                .filter(record -> record.getSubmittedAt() != null)
                .filter(record -> LocalDate.now().equals(record.getSubmittedAt().toLocalDate()))
                .count();
        double averageQualityScore = roundOneDecimal(taskRecords.stream()
                .filter(record -> record.getQualityScore() != null)
                .map(QcTaskRecord::getQualityScore)
                .mapToDouble(BigDecimal::doubleValue)
                .average()
                .orElse(0D));

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalTasks", totalTasks);
        summary.put("pendingTasks", pendingTasks);
        summary.put("processingTasks", processingTasks);
        summary.put("runningTasks", pendingTasks + processingTasks);
        summary.put("successTasks", successTasks);
        summary.put("failedTasks", failedTasks);
        summary.put("abnormalTasks", abnormalTasks);
        summary.put("todayTasks", todayTasks);
        summary.put("averageQualityScore", averageQualityScore);
        return summary;
    }

    private Map<String, Object> toSubmitResponse(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", snapshot.getTaskId());
        response.put("taskType", snapshot.getTaskType());
        response.put("taskTypeName", snapshot.getTaskTypeName());
        response.put("status", snapshot.getStatus());
        response.put("mock", true);
        response.put("submittedAt", formatDateTime(snapshot.getSubmittedAt()));
        response.put("pollingUrl", "/api/v1/quality/tasks/" + snapshot.getTaskId());
        response.put("message", "质控任务已提交，请轮询任务结果接口");
        return response;
    }

    private Map<String, Object> toTaskListItem(QcTaskRecord taskRecord) {
        Map<String, Object> item = new HashMap<>();
        item.put("recordId", taskRecord.getId());
        item.put("taskId", taskRecord.getTaskId());
        item.put("taskType", taskRecord.getTaskType());
        item.put("taskTypeName", taskRecord.getTaskTypeName());
        item.put("patientName", taskRecord.getPatientName());
        item.put("examId", taskRecord.getExamId());
        item.put("sourceMode", taskRecord.getSourceMode());
        item.put("sourceModeLabel", resolveSourceModeLabel(taskRecord.getSourceMode()));
        item.put("status", taskRecord.getTaskStatus());
        item.put("mock", Boolean.TRUE.equals(taskRecord.getMock()));
        item.put("qcStatus", taskRecord.getQcStatus());
        item.put("qualityScore", taskRecord.getQualityScore() == null ? null : taskRecord.getQualityScore().doubleValue());
        item.put("abnormalCount", taskRecord.getAbnormalCount());
        item.put("primaryIssue", taskRecord.getPrimaryIssue());
        item.put("submittedAt", formatDateTime(taskRecord.getSubmittedAt()));
        item.put("startedAt", formatDateTime(taskRecord.getStartedAt()));
        item.put("completedAt", formatDateTime(taskRecord.getCompletedAt()));
        item.put("errorMessage", taskRecord.getErrorMessage());
        return item;
    }

    private Map<String, Object> toTaskDetailResponse(QcTaskRecord taskRecord) {
        Map<String, Object> response = new HashMap<>(toTaskListItem(taskRecord));
        response.put("originalFilename", taskRecord.getOriginalFilename());
        response.put("storedFilePath", taskRecord.getStoredFilePath());
        response.put("result", parseResultJson(taskRecord.getResultJson()));
        return response;
    }

    private MockQualityTaskSnapshot buildSnapshotFromRecord(QcTaskRecord taskRecord) {
        if (taskRecord == null) {
            return null;
        }

        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setRecordId(taskRecord.getId());
        snapshot.setTaskId(taskRecord.getTaskId());
        snapshot.setTaskType(taskRecord.getTaskType());
        snapshot.setTaskTypeName(taskRecord.getTaskTypeName());
        snapshot.setSourceMode(taskRecord.getSourceMode());
        snapshot.setUserId(taskRecord.getUserId());
        snapshot.setPatientName(taskRecord.getPatientName());
        snapshot.setExamId(taskRecord.getExamId());
        snapshot.setOriginalFilename(taskRecord.getOriginalFilename());
        snapshot.setStoredFilePath(taskRecord.getStoredFilePath());
        snapshot.setStatus(taskRecord.getTaskStatus());
        snapshot.setMock(Boolean.TRUE.equals(taskRecord.getMock()));
        snapshot.setSubmittedAt(taskRecord.getSubmittedAt() == null ? taskRecord.getCreatedAt() : taskRecord.getSubmittedAt());
        snapshot.setStartedAt(taskRecord.getStartedAt());
        snapshot.setCompletedAt(taskRecord.getCompletedAt());
        snapshot.setResult(parseResultJson(taskRecord.getResultJson()));
        snapshot.setErrorMessage(taskRecord.getErrorMessage());
        return snapshot;
    }

    private QcTaskRecord resolveTaskRecord(MockQualityTaskSnapshot snapshot) {
        if (snapshot == null) {
            return null;
        }

        if (snapshot.getRecordId() != null) {
            QcTaskRecord taskRecord = qcTaskRecordMapper.selectById(snapshot.getRecordId());
            if (taskRecord != null) {
                return taskRecord;
            }
        }

        QcTaskRecord taskRecord = findTaskRecordByTaskId(snapshot.getTaskId());
        if (taskRecord != null) {
            snapshot.setRecordId(taskRecord.getId());
        }
        return taskRecord;
    }

    private QcTaskRecord findTaskRecordByTaskId(String taskId) {
        if (!StringUtils.hasText(taskId)) {
            return null;
        }

        return qcTaskRecordMapper.selectOne(new QueryWrapper<QcTaskRecord>()
                .eq("task_id", taskId.trim())
                .last("LIMIT 1"));
    }

    private void validateTaskAccess(QcTaskRecord taskRecord, Long currentUserId) {
        if (taskRecord == null) {
            throw new IllegalArgumentException("质控任务不存在或已过期");
        }

        if (currentUserId != null && taskRecord.getUserId() != null && !Objects.equals(currentUserId, taskRecord.getUserId())) {
            throw new IllegalArgumentException("无权访问该质控任务");
        }
    }

    private Map<String, Object> parseResultJson(String resultJson) {
        if (!StringUtils.hasText(resultJson)) {
            return Map.of();
        }

        try {
            return objectMapper.readValue(resultJson, Map.class);
        } catch (Exception exception) {
            return Map.of();
        }
    }

    private String writeResultJson(Map<String, Object> result) {
        if (result == null || result.isEmpty()) {
            return null;
        }

        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception exception) {
            logger.warn("写入质控结果 JSON 失败: {}", exception.getMessage());
            return null;
        }
    }

    private MultipartFile normalizeUploadFile(MultipartFile file) {
        return file == null || file.isEmpty() ? null : file;
    }

    private String resolveOriginalFilename(MultipartFile file, String sourceMode, String examId) {
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }

        if (MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode)) {
            return examId + "_pacs_mock.dcm";
        }

        return examId + "_upload.bin";
    }

    private String storeUploadFile(String taskId, MultipartFile file, String originalFilename) throws IOException {
        if (file == null) {
            return null;
        }

        if (Files.notExists(taskUploadRoot)) {
            Files.createDirectories(taskUploadRoot);
        }

        String storedFilename = taskId + "_" + originalFilename;
        Path storedFile = taskUploadRoot.resolve(storedFilename).normalize().toAbsolutePath();
        file.transferTo(storedFile.toFile());
        return storedFile.toString();
    }

    private long resolveDuration(Map<String, Object> result) {
        Object durationValue = result == null ? null : result.get("duration");
        return durationValue instanceof Number ? ((Number) durationValue).longValue() : 1000L;
    }

    private boolean isAbnormalTaskRecord(QcTaskRecord taskRecord) {
        return taskRecord != null
                && STATUS_SUCCESS.equals(taskRecord.getTaskStatus())
                && ("不合格".equals(taskRecord.getQcStatus())
                || (taskRecord.getAbnormalCount() != null && taskRecord.getAbnormalCount() > 0));
    }

    private String resolveSourceModeLabel(String sourceMode) {
        return MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode) ? "PACS 调取" : "本地上传";
    }

    private void validateTaskType(String taskType) {
        if (!MockQualityAnalysisSupport.isSupportedTaskType(taskType)) {
            throw new IllegalArgumentException("不支持的质控任务类型: " + taskType);
        }
    }

    private void validatePatientName(String patientName) {
        if (!StringUtils.hasText(patientName)) {
            throw new IllegalArgumentException("患者姓名不能为空");
        }
    }

    private void validateExamId(String examId) {
        if (!StringUtils.hasText(examId)) {
            throw new IllegalArgumentException("检查 ID 不能为空");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "--";
        }

        return dateTime.format(DATE_TIME_FORMATTER);
    }

    private double roundOneDecimal(double value) {
        return Math.round(value * 10.0D) / 10.0D;
    }

    /**
     * 内存态任务快照。
     *
     * <p>任务执行期保留在内存中；最终状态和结果以数据库记录为准。</p>
     */
    private static class MockQualityTaskSnapshot {
        private Long recordId;
        private String taskId;
        private String taskType;
        private String taskTypeName;
        private String sourceMode;
        private Long userId;
        private String patientName;
        private String examId;
        private String originalFilename;
        private String storedFilePath;
        private String status;
        private boolean mock;
        private LocalDateTime submittedAt;
        private LocalDateTime startedAt;
        private LocalDateTime completedAt;
        private Map<String, Object> result;
        private String errorMessage;

        public Long getRecordId() {
            return recordId;
        }

        public void setRecordId(Long recordId) {
            this.recordId = recordId;
        }

        public String getTaskId() {
            return taskId;
        }

        public void setTaskId(String taskId) {
            this.taskId = taskId;
        }

        public String getTaskType() {
            return taskType;
        }

        public void setTaskType(String taskType) {
            this.taskType = taskType;
        }

        public String getTaskTypeName() {
            return taskTypeName;
        }

        public void setTaskTypeName(String taskTypeName) {
            this.taskTypeName = taskTypeName;
        }

        public String getSourceMode() {
            return sourceMode;
        }

        public void setSourceMode(String sourceMode) {
            this.sourceMode = sourceMode;
        }

        public Long getUserId() {
            return userId;
        }

        public void setUserId(Long userId) {
            this.userId = userId;
        }

        public String getPatientName() {
            return patientName;
        }

        public void setPatientName(String patientName) {
            this.patientName = patientName;
        }

        public String getExamId() {
            return examId;
        }

        public void setExamId(String examId) {
            this.examId = examId;
        }

        public String getOriginalFilename() {
            return originalFilename;
        }

        public void setOriginalFilename(String originalFilename) {
            this.originalFilename = originalFilename;
        }

        public String getStoredFilePath() {
            return storedFilePath;
        }

        public void setStoredFilePath(String storedFilePath) {
            this.storedFilePath = storedFilePath;
        }

        public String getStatus() {
            return status;
        }

        public void setStatus(String status) {
            this.status = status;
        }

        public boolean isMock() {
            return mock;
        }

        public void setMock(boolean mock) {
            this.mock = mock;
        }

        public LocalDateTime getSubmittedAt() {
            return submittedAt;
        }

        public void setSubmittedAt(LocalDateTime submittedAt) {
            this.submittedAt = submittedAt;
        }

        public LocalDateTime getStartedAt() {
            return startedAt;
        }

        public void setStartedAt(LocalDateTime startedAt) {
            this.startedAt = startedAt;
        }

        public LocalDateTime getCompletedAt() {
            return completedAt;
        }

        public void setCompletedAt(LocalDateTime completedAt) {
            this.completedAt = completedAt;
        }

        public Map<String, Object> getResult() {
            return result;
        }

        public void setResult(Map<String, Object> result) {
            this.result = result;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}
