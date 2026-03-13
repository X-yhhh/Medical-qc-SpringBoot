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
    // 任务执行状态统一使用固定字符串，前后端和数据库保持一致。
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
    // 当消息总线不可用时，回退到本地线程池异步执行，保证演示链路仍可跑通。
    private final ExecutorService fallbackExecutor = Executors.newFixedThreadPool(2);
    // 运行中的任务快照暂存于内存，最终一致性以数据库记录为准。
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

    /**
     * 提交异步质控任务。
     * 数据链路：Controller -> submitTask -> 文件存储 -> 创建快照 -> persistSubmittedTask -> 发送消息 / 本地回退执行。
     */
    public Map<String, Object> submitTask(String taskType,
                                          MultipartFile file,
                                          String patientName,
                                          String examId,
                                          String sourceMode,
                                          User user) throws IOException {
        // 提交前先校验任务类型和最小业务参数。
        validateTaskType(taskType);
        validatePatientName(patientName);
        validateExamId(examId);

        // 统一来源模式，确保后续写库和前端回显使用同一套编码。
        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(sourceMode);
        if (!MockQualityAnalysisSupport.isSupportedSourceMode(normalizedSourceMode)) {
            throw new IllegalArgumentException("不支持的任务来源类型: " + sourceMode);
        }

        MultipartFile normalizedFile = normalizeUploadFile(file);
        if (MockQualityAnalysisSupport.SOURCE_MODE_LOCAL.equals(normalizedSourceMode) && normalizedFile == null) {
            throw new IllegalArgumentException("本地上传模式下，影像文件不能为空");
        }

        // 生成任务主键、时间戳和原始文件名，为任务快照和数据库记录提供统一来源。
        String taskId = UUID.randomUUID().toString();
        LocalDateTime submittedAt = LocalDateTime.now();
        String originalFilename = resolveOriginalFilename(normalizedFile, normalizedSourceMode, examId);
        String storedFilePath = storeUploadFile(taskId, normalizedFile, originalFilename);

        // 先构建运行期快照，再把其核心信息持久化为统一任务记录。
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

        // 发送到消息总线的消息只保留执行所需字段，避免序列化整个快照对象。
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

    /**
     * 消费并执行异步质控任务。
     * 数据链路：JMS Consumer / fallbackExecutor -> processTask -> doProcessTask -> syncTask -> syncQualityTaskIssue。
     */
    public void processTask(MockQualityTaskMessage message) {
        if (message == null || !StringUtils.hasText(message.getTaskId())) {
            logger.warn("收到无效的 mock 质控任务消息");
            return;
        }

        // 优先复用内存快照，若服务重启则从数据库恢复任务运行上下文。
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

        // 进入处理中状态后立刻同步数据库，便于前端轮询看到实时状态。
        snapshot.setStatus(STATUS_PROCESSING);
        snapshot.setStartedAt(LocalDateTime.now());
        unifiedQualityTaskWriteService.syncTask(snapshot);

        try {
            // 执行模拟分析并将结果写回快照。
            doProcessTask(snapshot, message);
            snapshot.setStatus(STATUS_SUCCESS);
            snapshot.setCompletedAt(LocalDateTime.now());
            unifiedQualityTaskWriteService.syncTask(snapshot);
            // 成功后触发异常工单同步，保证不合格任务能进入异常汇总看板。
            issueService.syncQualityTaskIssue(snapshot.getRecordId());
        } catch (Exception exception) {
            snapshot.setStatus(STATUS_FAILED);
            snapshot.setCompletedAt(LocalDateTime.now());
            snapshot.setErrorMessage(exception.getMessage());
            unifiedQualityTaskWriteService.syncTask(snapshot);
            logger.error("处理 mock 质控任务失败: {}", message.getTaskId(), exception);
        } finally {
            // 已终态任务无需继续保留在内存快照表中。
            if (STATUS_SUCCESS.equals(snapshot.getStatus()) || STATUS_FAILED.equals(snapshot.getStatus())) {
                taskStore.remove(message.getTaskId());
            }
        }
    }

    @Override
    public void destroy() throws Exception {
        // 应用关闭时等待回退线程池结束，避免任务处理中断在未知状态。
        fallbackExecutor.shutdown();
        fallbackExecutor.awaitTermination(5, TimeUnit.SECONDS);
    }

    /**
     * 执行单个任务的模拟分析逻辑。
     */
    private void doProcessTask(MockQualityTaskSnapshot snapshot, MockQualityTaskMessage message) throws Exception {
        // 根据任务类型和来源模式构造模拟结果，格式与前端详情页消费结构一致。
        Map<String, Object> result = MockQualityAnalysisSupport.createMockResult(
                message.getTaskType(),
                message.getPatientName(),
                message.getExamId(),
                message.getSourceMode(),
                message.getOriginalFilename());

        // 使用结果中的 duration 模拟实际推理耗时，保证轮询和进度更接近真实体验。
        long sleepMs = resolveDuration(result);
        Thread.sleep(Math.max(300L, sleepMs));
        snapshot.setResult(result);
    }

    /**
     * 优先通过消息总线异步执行；若消息总线不可用则降级到本地线程池。
     */
    private void dispatchTask(MockQualityTaskSnapshot snapshot, MockQualityTaskMessage message) {
        if (!messageBus.send(activeMqProperties.getQueue().getMockQualityTask(), message)) {
            logger.info("消息总线未接管 mock 质控任务，改为本地异步执行: {}", snapshot.getTaskId());
            fallbackExecutor.submit(() -> processTask(message));
        }
    }

    /**
     * 规范化上传文件对象，把空文件视为未上传。
     */
    private MultipartFile normalizeUploadFile(MultipartFile file) {
        return file == null || file.isEmpty() ? null : file;
    }

    /**
     * 解析原始文件名。
     * 本地上传优先使用真实文件名；PACS 模式生成用于展示的虚拟文件名。
     */
    private String resolveOriginalFilename(MultipartFile file, String sourceMode, String examId) {
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }
        if (MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode)) {
            return examId + "_pacs_mock.dcm";
        }
        return examId + "_upload.bin";
    }

    /**
     * 保存上传文件到受管目录，并返回绝对路径供后续任务执行使用。
     */
    private String storeUploadFile(String taskId, MultipartFile file, String originalFilename) throws IOException {
        if (file == null) {
            return null;
        }

        // 统一用 taskId 前缀命名，避免不同任务上传同名文件时互相覆盖。
        String storedFilename = taskId + "_" + originalFilename;
        StoredFile storedFile = fileStorageGateway.store(file, "mock-quality-tasks/" + storedFilename);
        return storedFile.getAbsolutePath().toString();
    }

    /**
     * 从结果对象中提取模拟执行时长。
     */
    private long resolveDuration(Map<String, Object> result) {
        Object durationValue = result == null ? null : result.get("duration");
        return durationValue instanceof Number ? ((Number) durationValue).longValue() : 1000L;
    }

    /**
     * 校验任务类型是否合法。
     */
    private void validateTaskType(String taskType) {
        if (!MockQualityAnalysisSupport.isSupportedTaskType(taskType)) {
            throw new IllegalArgumentException("不支持的质控任务类型: " + taskType);
        }
    }

    /**
     * 校验患者姓名不能为空。
     */
    private void validatePatientName(String patientName) {
        if (!StringUtils.hasText(patientName)) {
            throw new IllegalArgumentException("患者姓名不能为空");
        }
    }

    /**
     * 校验检查号不能为空。
     */
    private void validateExamId(String examId) {
        if (!StringUtils.hasText(examId)) {
            throw new IllegalArgumentException("检查 ID 不能为空");
        }
    }
}
