package com.medical.qc.modules.qctask.application;

import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.messaging.MockQualityTaskMessage;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.issue.application.IssueServiceImpl;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskSnapshot;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskViewAssembler;
import com.medical.qc.modules.unified.application.support.UnifiedQualityTaskWriteService;
import com.medical.qc.shared.messaging.MessageBus;
import com.medical.qc.shared.storage.FileStorageGateway;
import com.medical.qc.shared.storage.StoredFile;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * 异步质控任务服务实现。
 *
 * <p>提交、状态机和结果落库全部切换到统一模型。</p>
 */
@Service
public class MockQualityTaskServiceImpl implements DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(MockQualityTaskServiceImpl.class);
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_FAILED = "FAILED";

    private final ActiveMqProperties activeMqProperties;
    private final IssueServiceImpl issueService;
    private final MessageBus messageBus;
    private final FileStorageGateway fileStorageGateway;
    private final MockQualityTaskViewAssembler taskViewAssembler;
    private final UnifiedQualityTaskWriteService unifiedQualityTaskWriteService;
    private final ExecutorService fallbackExecutor = Executors.newFixedThreadPool(2);
    private final ConcurrentMap<String, MockQualityTaskSnapshot> taskStore = new ConcurrentHashMap<>();

    public MockQualityTaskServiceImpl(ActiveMqProperties activeMqProperties,
                                      IssueServiceImpl issueService,
                                      MessageBus messageBus,
                                      FileStorageGateway fileStorageGateway,
                                      MockQualityTaskViewAssembler taskViewAssembler,
                                      UnifiedQualityTaskWriteService unifiedQualityTaskWriteService) {
        this.activeMqProperties = activeMqProperties;
        this.issueService = issueService;
        this.messageBus = messageBus;
        this.fileStorageGateway = fileStorageGateway;
        this.taskViewAssembler = taskViewAssembler;
        this.unifiedQualityTaskWriteService = unifiedQualityTaskWriteService;
    }

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
        unifiedQualityTaskWriteService.persistSubmittedTask(snapshot);

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
        return taskViewAssembler.toSubmitResponse(snapshot);
    }

    public void processTask(MockQualityTaskMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())) {
            logger.warn("收到无效的 mock 质控任务消息");
            return;
        }

        MockQualityTaskSnapshot snapshot = taskStore.computeIfAbsent(
                message.getTaskId(),
                unifiedQualityTaskWriteService::loadSnapshot);
        if (snapshot == null) {
            logger.warn("质控任务不存在，忽略消息: {}", message.getTaskId());
            return;
        }
        if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAILED.equals(snapshot.getStatus())) {
            return;
        }

        snapshot.setStatus(STATUS_PROCESSING);
        snapshot.setStartedAt(LocalDateTime.now());
        unifiedQualityTaskWriteService.syncTask(snapshot);

        try {
            doProcessTask(snapshot, message);
            snapshot.setStatus(STATUS_SUCCESS);
            snapshot.setCompletedAt(LocalDateTime.now());
            unifiedQualityTaskWriteService.syncTask(snapshot);
            issueService.syncQualityTaskIssue(snapshot.getRecordId());
        } catch (Exception exception) {
            snapshot.setStatus(STATUS_FAILED);
            snapshot.setCompletedAt(LocalDateTime.now());
            snapshot.setErrorMessage(exception.getMessage());
            unifiedQualityTaskWriteService.syncTask(snapshot);
            logger.error("处理 mock 质控任务失败: {}", message.getTaskId(), exception);
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
        if (!messageBus.send(activeMqProperties.getQueue().getMockQualityTask(), message)) {
            logger.info("消息总线未接管 mock 质控任务，改为本地异步执行: {}", snapshot.getTaskId());
            fallbackExecutor.submit(() -> processTask(message));
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

        String storedFilename = taskId + "_" + originalFilename;
        StoredFile storedFile = fileStorageGateway.store(file, "mock-quality-tasks/" + storedFilename);
        return storedFile.getAbsolutePath().toString();
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
}
