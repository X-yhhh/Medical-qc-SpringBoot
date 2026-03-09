package com.medical.qc.service.impl;

import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.entity.User;
import com.medical.qc.messaging.MockQualityTaskMessage;
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
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 其余四个质控模块的异步任务服务实现。
 *
 * <p>当前结果仍然是 mock，但任务提交、MQ 投递、消费者处理、轮询查询已按异步模式打通。</p>
 * <p>后续接真实算法时，优先替换 {@link #doProcessTask(MockQualityTaskSnapshot, MockQualityTaskMessage)} 内部逻辑。</p>
 */
@Service
public class MockQualityTaskServiceImpl implements MockQualityTaskService, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(MockQualityTaskServiceImpl.class);

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final ActiveMqProperties activeMqProperties;
    private final ObjectProvider<JmsTemplate> jmsTemplateProvider;
    private final ExecutorService fallbackExecutor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, MockQualityTaskSnapshot> taskStore = new ConcurrentHashMap<>();
    private final Path taskUploadRoot = Paths.get("uploads", "mock-quality-tasks");

    public MockQualityTaskServiceImpl(ActiveMqProperties activeMqProperties,
                                      ObjectProvider<JmsTemplate> jmsTemplateProvider) {
        this.activeMqProperties = activeMqProperties;
        this.jmsTemplateProvider = jmsTemplateProvider;
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
    public Map<String, Object> getTaskDetail(String taskId, Long currentUserId) {
        MockQualityTaskSnapshot snapshot = taskStore.get(taskId);
        if (snapshot == null) {
            throw new IllegalArgumentException("质控任务不存在或已过期");
        }

        if (currentUserId != null && snapshot.getUserId() != null && !currentUserId.equals(snapshot.getUserId())) {
            throw new IllegalArgumentException("无权访问该质控任务");
        }

        return toTaskDetailResponse(snapshot);
    }

    @Override
    public void processTask(MockQualityTaskMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())) {
            logger.warn("收到无效的 mock 质控任务消息");
            return;
        }

        MockQualityTaskSnapshot snapshot = taskStore.get(message.getTaskId());
        if (snapshot == null) {
            logger.warn("质控任务不存在，忽略消息: {}", message.getTaskId());
            return;
        }

        if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAILED.equals(snapshot.getStatus())) {
            return;
        }

        snapshot.setStatus(STATUS_PROCESSING);
        snapshot.setStartedAt(LocalDateTime.now());

        try {
            doProcessTask(snapshot, message);
            snapshot.setStatus(STATUS_SUCCESS);
            snapshot.setCompletedAt(LocalDateTime.now());
        } catch (Exception ex) {
            snapshot.setStatus(STATUS_FAILED);
            snapshot.setCompletedAt(LocalDateTime.now());
            snapshot.setErrorMessage(ex.getMessage());
            logger.error("处理 mock 质控任务失败: {}", message.getTaskId(), ex);
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

    private Map<String, Object> toSubmitResponse(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", snapshot.getTaskId());
        response.put("taskType", snapshot.getTaskType());
        response.put("taskTypeName", snapshot.getTaskTypeName());
        response.put("status", snapshot.getStatus());
        response.put("mock", true);
        response.put("submittedAt", snapshot.getSubmittedAt());
        response.put("pollingUrl", "/api/v1/quality/tasks/" + snapshot.getTaskId());
        response.put("message", "质控任务已提交，请轮询任务结果接口");
        return response;
    }

    private Map<String, Object> toTaskDetailResponse(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> response = new HashMap<>();
        response.put("taskId", snapshot.getTaskId());
        response.put("taskType", snapshot.getTaskType());
        response.put("taskTypeName", snapshot.getTaskTypeName());
        response.put("status", snapshot.getStatus());
        response.put("mock", snapshot.isMock());
        response.put("submittedAt", snapshot.getSubmittedAt());
        response.put("startedAt", snapshot.getStartedAt());
        response.put("completedAt", snapshot.getCompletedAt());
        response.put("result", snapshot.getResult());
        response.put("errorMessage", snapshot.getErrorMessage());
        return response;
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

    /**
     * 内存态任务快照。
     *
     * <p>当前先满足本地开发与演示需求；后续如需支持重启恢复、分布式部署，可替换为数据库或 Redis 持久化。</p>
     */
    private static class MockQualityTaskSnapshot {
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
