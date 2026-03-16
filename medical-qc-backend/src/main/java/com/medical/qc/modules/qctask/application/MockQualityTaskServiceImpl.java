package com.medical.qc.modules.qctask.application;

import com.medical.qc.config.ActiveMqProperties;
import com.medical.qc.messaging.MockQualityTaskMessage;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.patient.application.support.TaskScopedPatientInfoStorageService;
import com.medical.qc.modules.issue.application.IssueServiceImpl;
import com.medical.qc.modules.qctask.application.support.ChestContrastPreparationService;
import com.medical.qc.modules.qctask.application.support.ChestContrastPreparedContext;
import com.medical.qc.modules.qctask.application.support.ChestContrastRuleAnalyzer;
import com.medical.qc.modules.qctask.application.support.ChestContrastResultAssembler;
import com.medical.qc.modules.qctask.application.support.ChestNonContrastPreparationService;
import com.medical.qc.modules.qctask.application.support.ChestNonContrastPreparedContext;
import com.medical.qc.modules.qctask.application.support.ChestNonContrastResultAssembler;
import com.medical.qc.modules.qctask.application.support.CoronaryCtaPreparationService;
import com.medical.qc.modules.qctask.application.support.CoronaryCtaPreparedContext;
import com.medical.qc.modules.qctask.application.support.CoronaryCtaRuleAnalyzer;
import com.medical.qc.modules.qctask.application.support.CoronaryCtaResultAssembler;
import com.medical.qc.modules.qctask.application.support.HeadQualityPreparationService;
import com.medical.qc.modules.qctask.application.support.HeadQualityPreparedContext;
import com.medical.qc.modules.qctask.application.support.HeadQualityResultAssembler;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskSnapshot;
import com.medical.qc.modules.qctask.application.support.MockQualityTaskViewAssembler;
import com.medical.qc.modules.unified.application.support.UnifiedQualityTaskWriteService;
import com.medical.qc.shared.ai.AiGateway;
import com.medical.qc.shared.messaging.MessageBus;
import com.medical.qc.shared.storage.FileStorageGateway;
import com.medical.qc.shared.storage.StoredFile;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.RealInferenceResultValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
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
    private final AiGateway aiGateway;
    private final HeadQualityPreparationService headQualityPreparationService;
    private final HeadQualityResultAssembler headQualityResultAssembler;
    private final ChestNonContrastPreparationService chestNonContrastPreparationService;
    private final ChestNonContrastResultAssembler chestNonContrastResultAssembler;
    private final ChestContrastPreparationService chestContrastPreparationService;
    private final ChestContrastRuleAnalyzer chestContrastRuleAnalyzer;
    private final ChestContrastResultAssembler chestContrastResultAssembler;
    private final CoronaryCtaPreparationService coronaryCtaPreparationService;
    private final CoronaryCtaRuleAnalyzer coronaryCtaRuleAnalyzer;
    private final CoronaryCtaResultAssembler coronaryCtaResultAssembler;
    private final TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService;
    private final RealInferenceResultValidator realInferenceResultValidator;
    // 当消息总线不可用时，回退到本地线程池异步执行，保证演示链路仍可跑通。
    private final ExecutorService fallbackExecutor = Executors.newFixedThreadPool(2);
    // 运行中的任务快照暂存于内存，最终一致性以数据库记录为准。
    private final ConcurrentMap<String, MockQualityTaskSnapshot> taskStore = new ConcurrentHashMap<>();

    public MockQualityTaskServiceImpl(ActiveMqProperties activeMqProperties,
                                      IssueServiceImpl issueService,
                                      MessageBus messageBus,
                                      FileStorageGateway fileStorageGateway,
                                      MockQualityTaskViewAssembler taskViewAssembler,
                                      UnifiedQualityTaskWriteService unifiedQualityTaskWriteService,
                                      AiGateway aiGateway,
                                      HeadQualityPreparationService headQualityPreparationService,
                                      HeadQualityResultAssembler headQualityResultAssembler,
                                       ChestNonContrastPreparationService chestNonContrastPreparationService,
                                       ChestNonContrastResultAssembler chestNonContrastResultAssembler,
                                       ChestContrastPreparationService chestContrastPreparationService,
                                       ChestContrastRuleAnalyzer chestContrastRuleAnalyzer,
                                       ChestContrastResultAssembler chestContrastResultAssembler,
                                       CoronaryCtaPreparationService coronaryCtaPreparationService,
                                       CoronaryCtaRuleAnalyzer coronaryCtaRuleAnalyzer,
                                       CoronaryCtaResultAssembler coronaryCtaResultAssembler,
                                       TaskScopedPatientInfoStorageService taskScopedPatientInfoStorageService,
                                       RealInferenceResultValidator realInferenceResultValidator) {
        this.activeMqProperties = activeMqProperties;
        this.issueService = issueService;
        this.messageBus = messageBus;
        this.fileStorageGateway = fileStorageGateway;
        this.taskViewAssembler = taskViewAssembler;
        this.unifiedQualityTaskWriteService = unifiedQualityTaskWriteService;
        this.aiGateway = aiGateway;
        this.headQualityPreparationService = headQualityPreparationService;
        this.headQualityResultAssembler = headQualityResultAssembler;
        this.chestNonContrastPreparationService = chestNonContrastPreparationService;
        this.chestNonContrastResultAssembler = chestNonContrastResultAssembler;
        this.chestContrastPreparationService = chestContrastPreparationService;
        this.chestContrastRuleAnalyzer = chestContrastRuleAnalyzer;
        this.chestContrastResultAssembler = chestContrastResultAssembler;
        this.coronaryCtaPreparationService = coronaryCtaPreparationService;
        this.coronaryCtaRuleAnalyzer = coronaryCtaRuleAnalyzer;
        this.coronaryCtaResultAssembler = coronaryCtaResultAssembler;
        this.taskScopedPatientInfoStorageService = taskScopedPatientInfoStorageService;
        this.realInferenceResultValidator = realInferenceResultValidator;
    }

    /**
     * 提交异步质控任务。
     * 数据链路：Controller -> submitTask -> 文件存储 -> 创建快照 -> persistSubmittedTask -> 发送消息 / 本地回退执行。
     */
    public Map<String, Object> submitTask(String taskType,
                                          MultipartFile file,
                                          String patientName,
                                          String examId,
                                          String patientId,
                                          String gender,
                                          Integer age,
                                          LocalDate studyDate,
                                          String sourceMode,
                                          Map<String, Object> metadata,
                                          User user) throws IOException {
        // 提交前先校验任务类型和最小业务参数。
        validateTaskType(taskType);
        validatePatientName(patientName);
        validateExamId(examId);
        if (requiresDetailedPatientInfo(taskType)) {
            validateGender(gender);
            validateAge(age);
            validateStudyDate(studyDate);
        }

        // 所有患者字段在提交入口归一化，避免后续消息、快照和数据库出现多套格式。
        String normalizedPatientName = patientName.trim();
        String normalizedExamId = examId.trim();
        String normalizedPatientId = normalizeText(patientId);
        String normalizedGender = normalizeText(gender);

        // 统一来源模式，确保后续写库和前端回显使用同一套编码。
        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(sourceMode);
        if (!MockQualityAnalysisSupport.isSupportedSourceMode(normalizedSourceMode)) {
            throw new IllegalArgumentException("不支持的任务来源类型: " + sourceMode);
        }

        MultipartFile normalizedFile = normalizeUploadFile(file);
        if (MockQualityAnalysisSupport.SOURCE_MODE_LOCAL.equals(normalizedSourceMode) && normalizedFile == null) {
            throw new IllegalArgumentException("本地上传模式下，影像文件不能为空");
        }
        validateTaskFile(taskType, normalizedFile, normalizedSourceMode);

        // 生成任务主键、时间戳和原始文件名，为任务快照和数据库记录提供统一来源。
        String taskId = UUID.randomUUID().toString();
        LocalDateTime submittedAt = LocalDateTime.now();
        String originalFilename = resolveOriginalFilename(taskType, normalizedFile, normalizedSourceMode, normalizedExamId);
        String storedFilePath = storeUploadFile(taskType, taskId, normalizedFile, originalFilename);

        // 先构建运行期快照，再把其核心信息持久化为统一任务记录。
        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setTaskId(taskId);
        snapshot.setTaskType(taskType);
        snapshot.setTaskTypeName(MockQualityAnalysisSupport.resolveTaskTypeName(taskType));
        snapshot.setSourceMode(normalizedSourceMode);
        snapshot.setUserId(user == null ? null : user.getId());
        snapshot.setPatientName(normalizedPatientName);
        snapshot.setExamId(normalizedExamId);
        snapshot.setPatientId(normalizedPatientId);
        snapshot.setGender(normalizedGender);
        snapshot.setAge(age);
        snapshot.setStudyDate(studyDate);
        snapshot.setOriginalFilename(originalFilename);
        snapshot.setStoredFilePath(storedFilePath);
        snapshot.setStatus(STATUS_PENDING);
        snapshot.setSubmittedAt(submittedAt);
        // 任务在提交阶段就标明 real/mock 类型，避免前端轮询过程中读到错误语义。
        snapshot.setMock(isRuleBasedMockTaskType(taskType));

        taskStore.put(taskId, snapshot);
        unifiedQualityTaskWriteService.persistSubmittedTask(snapshot);
        syncTaskScopedSourceCache(
                taskType,
                normalizedPatientId,
                normalizedPatientName,
                normalizedExamId,
                normalizedGender,
                age,
                studyDate,
                normalizedSourceMode,
                storedFilePath,
                metadata == null ? Map.of() : metadata);

        // 发送到消息总线的消息只保留执行所需字段，避免序列化整个快照对象。
        MockQualityTaskMessage message = new MockQualityTaskMessage(
                taskId,
                taskType,
                normalizedSourceMode,
                snapshot.getUserId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                snapshot.getPatientId(),
                snapshot.getGender(),
                snapshot.getAge(),
                snapshot.getStudyDate(),
                originalFilename,
                storedFilePath,
                metadata == null ? new HashMap<>() : metadata,
                submittedAt);

        dispatchTask(snapshot, message);
        return taskViewAssembler.toSubmitResponse(snapshot);
    }

    /**
     * 基于历史任务重新提交一次分析任务。
     */
    public Map<String, Object> retryTask(String taskNo, User user) {
        MockQualityTaskSnapshot previousSnapshot = unifiedQualityTaskWriteService.loadSnapshot(taskNo);
        if (previousSnapshot == null) {
            throw new IllegalArgumentException("原始质控任务不存在");
        }
        if (user == null) {
            throw new IllegalArgumentException("当前用户不能为空");
        }
        if (previousSnapshot.getUserId() != null
                && !Integer.valueOf(1).equals(user.getRoleId())
                && !previousSnapshot.getUserId().equals(user.getId())) {
            throw new IllegalArgumentException("无权重新分析该质控任务");
        }

        String newTaskId = UUID.randomUUID().toString();
        LocalDateTime submittedAt = LocalDateTime.now();
        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(previousSnapshot.getSourceMode());
        Map<String, Object> metadata = extractRetryMetadata(previousSnapshot);

        MockQualityTaskSnapshot snapshot = new MockQualityTaskSnapshot();
        snapshot.setTaskId(newTaskId);
        snapshot.setTaskType(previousSnapshot.getTaskType());
        snapshot.setTaskTypeName(MockQualityAnalysisSupport.resolveTaskTypeName(previousSnapshot.getTaskType()));
        snapshot.setSourceMode(normalizedSourceMode);
        snapshot.setUserId(user.getId());
        snapshot.setPatientName(previousSnapshot.getPatientName());
        snapshot.setExamId(previousSnapshot.getExamId());
        snapshot.setPatientId(previousSnapshot.getPatientId());
        snapshot.setGender(previousSnapshot.getGender());
        snapshot.setAge(previousSnapshot.getAge());
        snapshot.setStudyDate(previousSnapshot.getStudyDate());
        snapshot.setOriginalFilename(previousSnapshot.getOriginalFilename());
        snapshot.setStoredFilePath(previousSnapshot.getStoredFilePath());
        snapshot.setStatus(STATUS_PENDING);
        snapshot.setSubmittedAt(submittedAt);
        snapshot.setMock(isRuleBasedMockTaskType(previousSnapshot.getTaskType()));

        taskStore.put(newTaskId, snapshot);
        unifiedQualityTaskWriteService.persistSubmittedTask(snapshot);
        syncTaskScopedSourceCache(
                snapshot.getTaskType(),
                snapshot.getPatientId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                snapshot.getGender(),
                snapshot.getAge(),
                snapshot.getStudyDate(),
                normalizedSourceMode,
                snapshot.getStoredFilePath(),
                metadata);

        MockQualityTaskMessage message = new MockQualityTaskMessage(
                newTaskId,
                snapshot.getTaskType(),
                normalizedSourceMode,
                snapshot.getUserId(),
                snapshot.getPatientName(),
                snapshot.getExamId(),
                snapshot.getPatientId(),
                snapshot.getGender(),
                snapshot.getAge(),
                snapshot.getStudyDate(),
                snapshot.getOriginalFilename(),
                snapshot.getStoredFilePath(),
                metadata,
                submittedAt);
        dispatchTask(snapshot, message);
        unifiedQualityTaskWriteService.insertAuditLog(
                snapshot.getRecordId(),
                null,
                user.getId(),
                "retry",
                "基于历史任务重新分析",
                Map.of("sourceTaskNo", taskNo));
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
        if (MockQualityAnalysisSupport.TASK_TYPE_HEAD.equals(message.getTaskType())) {
            HeadQualityPreparedContext preparedContext = headQualityPreparationService.prepare(message);
            Map<String, Object> predictionResult = aiGateway.analyzeHeadQuality(preparedContext.analysisVolumePath());
            applyPreparedContextToSnapshot(
                    snapshot,
                    preparedContext.analysisVolumePath(),
                    preparedContext.patientName(),
                    preparedContext.examId(),
                    preparedContext.gender(),
                    preparedContext.age(),
                    preparedContext.studyDate(),
                    preparedContext.originalFilename());
            snapshot.setMock(false);
            Map<String, Object> enrichedResult = enrichResultPatientInfo(
                    headQualityResultAssembler.enrichResult(predictionResult, preparedContext),
                    snapshot);
            snapshot.setResult(realInferenceResultValidator.validateStructuredResult(
                    MockQualityAnalysisSupport.TASK_TYPE_HEAD,
                    enrichedResult));
            return;
        }

        if (MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST.equals(message.getTaskType())) {
            ChestNonContrastPreparedContext preparedContext = chestNonContrastPreparationService.prepare(message);
            Map<String, Object> predictionResult = aiGateway.analyzeChestNonContrast(preparedContext.analysisVolumePath());
            applyPreparedContextToSnapshot(
                    snapshot,
                    preparedContext.analysisVolumePath(),
                    preparedContext.patientName(),
                    preparedContext.examId(),
                    preparedContext.gender(),
                    preparedContext.age(),
                    preparedContext.studyDate(),
                    preparedContext.originalFilename());
            snapshot.setMock(false);
            Map<String, Object> enrichedResult = enrichResultPatientInfo(
                    chestNonContrastResultAssembler.enrichResult(predictionResult, preparedContext),
                    snapshot);
            snapshot.setResult(realInferenceResultValidator.validateStructuredResult(
                    MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST,
                    enrichedResult));
            return;
        }

        if (MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(message.getTaskType())) {
            CoronaryCtaPreparedContext preparedContext = coronaryCtaPreparationService.prepareForRule(message);
            // 冠脉 CTA 本轮固定走规则型 mock，不再尝试真实模型推理。
            Map<String, Object> result = coronaryCtaRuleAnalyzer.analyze(preparedContext);
            applyPreparedContextToSnapshot(
                    snapshot,
                    preparedContext.analysisVolumePath(),
                    preparedContext.patientName(),
                    preparedContext.examId(),
                    preparedContext.gender(),
                    preparedContext.age(),
                    preparedContext.studyDate(),
                    preparedContext.originalFilename());
            snapshot.setMock(true);
            snapshot.setResult(enrichResultPatientInfo(
                    coronaryCtaResultAssembler.enrichResult(result, preparedContext),
                    snapshot));
            return;
        }

        if (MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST.equals(message.getTaskType())) {
            ChestContrastPreparedContext preparedContext = chestContrastPreparationService.prepare(message);
            // 胸部增强本轮固定走规则型 mock，不再尝试真实模型推理。
            Map<String, Object> result = chestContrastRuleAnalyzer.analyze(preparedContext);
            applyPreparedContextToSnapshot(
                    snapshot,
                    preparedContext.analysisFilePath(),
                    preparedContext.patientName(),
                    preparedContext.examId(),
                    preparedContext.gender(),
                    preparedContext.age(),
                    preparedContext.studyDate(),
                    preparedContext.originalFilename());
            snapshot.setMock(true);
            snapshot.setResult(enrichResultPatientInfo(
                    chestContrastResultAssembler.enrichResult(result, preparedContext),
                    snapshot));
            return;
        }

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
        snapshot.setResult(enrichResultPatientInfo(
                enrichGenericMockResult(
                        result,
                        message.getTaskType(),
                        message.getPatientName(),
                        message.getExamId(),
                        message.getSourceMode(),
                        message.getOriginalFilename()),
                snapshot));
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
     * 针对不同任务类型校验上传文件格式。
     */
    private void validateTaskFile(String taskType, MultipartFile file, String sourceMode) {
        if (!MockQualityAnalysisSupport.SOURCE_MODE_LOCAL.equals(sourceMode) || file == null) {
            return;
        }

        String originalFilename = file.getOriginalFilename();
        if (!StringUtils.hasText(originalFilename)) {
            throw new IllegalArgumentException("影像文件名不能为空");
        }

        String normalizedFilename = originalFilename.toLowerCase();
        boolean supportedMedicalInput = normalizedFilename.endsWith(".nii")
                || normalizedFilename.endsWith(".nii.gz")
                || normalizedFilename.endsWith(".dcm")
                || normalizedFilename.endsWith(".dicom")
                || normalizedFilename.endsWith(".zip");
        if (!supportedMedicalInput) {
            throw new IllegalArgumentException("当前任务仅支持 .nii / .nii.gz / .dcm / .dicom / .zip 影像输入");
        }
    }

    /**
     * 解析原始文件名。
     * 本地上传优先使用真实文件名；PACS 模式生成用于展示的虚拟文件名。
     */
    private String resolveOriginalFilename(String taskType, MultipartFile file, String sourceMode, String examId) {
        if (file != null && StringUtils.hasText(file.getOriginalFilename())) {
            return file.getOriginalFilename();
        }
        if (MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode)) {
            if (MockQualityAnalysisSupport.TASK_TYPE_HEAD.equals(taskType)) {
                return examId + "_pacs_head.nii.gz";
            }
            if (MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST.equals(taskType)) {
                return examId + "_pacs_chest_non_contrast.nii.gz";
            }
            if (MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST.equals(taskType)) {
                return examId + "_pacs_chest_contrast.dcm";
            }
            if (MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(taskType)) {
                return examId + "_pacs_coronary_cta.nii.gz";
            }
            return examId + "_pacs_mock.dcm";
        }
        return examId + "_upload.bin";
    }

    /**
     * 保存上传文件到受管目录，并返回绝对路径供后续任务执行使用。
     */
    private String storeUploadFile(String taskType, String taskId, MultipartFile file, String originalFilename) throws IOException {
        if (file == null) {
            return null;
        }

        // 统一用 taskId 前缀命名，避免不同任务上传同名文件时互相覆盖。
        String storedFilename = taskId + "_" + originalFilename;
        String folder = switch (taskType) {
            case MockQualityAnalysisSupport.TASK_TYPE_HEAD -> "quality-tasks/head";
            case MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST -> "quality-tasks/chest-non-contrast";
            case MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST -> "quality-tasks/chest-contrast";
            case MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA -> "quality-tasks/coronary-cta";
            default -> "quality-tasks/mock";
        };
        StoredFile storedFile = fileStorageGateway.store(file, folder + "/" + storedFilename);
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

    /**
     * 真实推理链路要求患者必要字段在提交前完整。
     */
    private boolean requiresDetailedPatientInfo(String taskType) {
        return MockQualityAnalysisSupport.TASK_TYPE_HEAD.equals(taskType)
                || MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST.equals(taskType);
    }

    /**
     * 当前规则型 mock 任务集合。
     *
     * <p>胸部增强与冠脉 CTA 先保留前后端和数据库骨架，但明确不走真实模型。</p>
     */
    private boolean isRuleBasedMockTaskType(String taskType) {
        return MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST.equals(taskType)
                || MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(taskType);
    }

    /**
     * 校验性别不能为空。
     */
    private void validateGender(String gender) {
        if (!StringUtils.hasText(gender)) {
            throw new IllegalArgumentException("性别不能为空");
        }
    }

    /**
     * 校验年龄不能为空且不能为负数。
     */
    private void validateAge(Integer age) {
        if (age == null || age < 0) {
            throw new IllegalArgumentException("年龄不能为空");
        }
    }

    /**
     * 校验检查日期不能为空。
     */
    private void validateStudyDate(LocalDate studyDate) {
        if (studyDate == null) {
            throw new IllegalArgumentException("检查日期不能为空");
        }
    }

    /**
     * 本地上传场景在提交时同步写入任务专属患者缓存表。
     */
    private void syncTaskScopedSourceCache(String taskType,
                                           String patientId,
                                           String patientName,
                                           String examId,
                                           String gender,
                                           Integer age,
                                           LocalDate studyDate,
                                           String sourceMode,
                                           String storedFilePath,
                                           Map<String, Object> metadata) {
        if (!MockQualityAnalysisSupport.SOURCE_MODE_LOCAL.equals(sourceMode) || !StringUtils.hasText(storedFilePath)) {
            return;
        }

        if (MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(taskType)) {
            taskScopedPatientInfoStorageService.upsertPatientByAccessionNumber(
                    taskType,
                    patientId,
                    patientName,
                    examId,
                    gender,
                    age,
                    studyDate,
                    storedFilePath,
                    parseInteger(metadata == null ? null : metadata.get("heart_rate")),
                    parseInteger(metadata == null ? null : metadata.get("hr_variability")),
                    normalizeObjectText(metadata == null ? null : metadata.get("recon_phase")),
                    normalizeObjectText(metadata == null ? null : metadata.get("kvp")));
            return;
        }

        if (MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST.equals(taskType)) {
            taskScopedPatientInfoStorageService.upsertPatientByAccessionNumber(
                    taskType,
                    patientId,
                    patientName,
                    examId,
                    gender,
                    age,
                    studyDate,
                    storedFilePath,
                    parseDouble(metadata == null ? null : metadata.get("flow_rate")),
                    parseInteger(metadata == null ? null : metadata.get("contrast_volume")),
                    normalizeObjectText(metadata == null ? null : metadata.get("injection_site")),
                    parseDouble(metadata == null ? null : metadata.get("slice_thickness")),
                    parseInteger(metadata == null ? null : metadata.get("bolus_tracking_hu")),
                    parseInteger(metadata == null ? null : metadata.get("scan_delay_sec")),
                    null,
                    null,
                    null,
                    null);
            return;
        }

        taskScopedPatientInfoStorageService.upsertPatientByAccessionNumber(
                taskType,
                patientId,
                patientName,
                examId,
                gender,
                age,
                studyDate,
                storedFilePath);
    }

    private Integer parseInteger(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private Double parseDouble(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    private String normalizeObjectText(Object value) {
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return StringUtils.hasText(text) && !"null".equalsIgnoreCase(text) ? text : null;
    }

    /**
     * 从历史结果中提取重跑所需的任务元数据。
     */
    private Map<String, Object> extractRetryMetadata(MockQualityTaskSnapshot snapshot) {
        Map<String, Object> metadata = new HashMap<>();
        Map<String, Object> patientInfo = snapshot.getResult() == null
                ? Map.of()
                : MockQualityAnalysisSupport.extractPatientInfo(snapshot.getResult());

        copyIfPresent(metadata, "flow_rate", patientInfo.get("flowRate"));
        copyIfPresent(metadata, "contrast_volume", patientInfo.get("contrastVolume"));
        copyIfPresent(metadata, "injection_site", patientInfo.get("injectionSite"));
        copyIfPresent(metadata, "slice_thickness", patientInfo.get("sliceThickness"));
        copyIfPresent(metadata, "bolus_tracking_hu", patientInfo.get("bolusTrackingHu"));
        copyIfPresent(metadata, "scan_delay_sec", patientInfo.get("scanDelaySec"));
        copyIfPresent(metadata, "heart_rate", patientInfo.get("heartRate"));
        copyIfPresent(metadata, "hr_variability", patientInfo.get("hrVariability"));
        copyIfPresent(metadata, "recon_phase", patientInfo.get("reconPhase"));
        copyIfPresent(metadata, "kvp", patientInfo.get("kVp"));
        return metadata;
    }

    /**
     * 值存在时才写入重跑元数据。
     */
    private void copyIfPresent(Map<String, Object> metadata, String key, Object value) {
        if (metadata == null || key == null || value == null) {
            return;
        }
        if (value instanceof String textValue && !StringUtils.hasText(textValue)) {
            return;
        }
        metadata.put(key, value);
    }

    /**
     * 把准备阶段解析出的患者与文件信息回写到快照。
     */
    private void applyPreparedContextToSnapshot(MockQualityTaskSnapshot snapshot,
                                               String storedFilePath,
                                               String patientName,
                                               String examId,
                                               String gender,
                                               Integer age,
                                               LocalDate studyDate,
                                               String originalFilename) {
        if (snapshot == null) {
            return;
        }
        if (StringUtils.hasText(storedFilePath)) {
            snapshot.setStoredFilePath(storedFilePath.trim());
        }
        if (StringUtils.hasText(patientName)) {
            snapshot.setPatientName(patientName.trim());
        }
        if (StringUtils.hasText(examId)) {
            snapshot.setExamId(examId.trim());
        }
        if (StringUtils.hasText(gender)) {
            snapshot.setGender(gender.trim());
        }
        if (age != null) {
            snapshot.setAge(age);
        }
        if (studyDate != null) {
            snapshot.setStudyDate(studyDate);
        }
        if (StringUtils.hasText(originalFilename)) {
            snapshot.setOriginalFilename(originalFilename.trim());
        }
    }

    /**
     * 统一补齐结果中的患者信息，保证结果页与数据库主数据字段一致。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichResultPatientInfo(Map<String, Object> result, MockQualityTaskSnapshot snapshot) {
        Map<String, Object> enriched = result == null ? new HashMap<>() : new HashMap<>(result);
        Map<String, Object> patientInfo = enriched.get("patientInfo") instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();

        if (StringUtils.hasText(snapshot.getPatientName())) {
            patientInfo.put("name", snapshot.getPatientName().trim());
        }
        if (StringUtils.hasText(snapshot.getPatientId())) {
            patientInfo.put("patientId", snapshot.getPatientId().trim());
        }
        if (StringUtils.hasText(snapshot.getExamId())) {
            patientInfo.put("studyId", snapshot.getExamId().trim());
            patientInfo.put("accessionNumber", snapshot.getExamId().trim());
        }
        if (StringUtils.hasText(snapshot.getGender())) {
            patientInfo.put("gender", snapshot.getGender().trim());
        }
        if (snapshot.getAge() != null) {
            patientInfo.put("age", snapshot.getAge());
        }
        if (snapshot.getStudyDate() != null) {
            patientInfo.put("studyDate", snapshot.getStudyDate().toString());
        }
        if (StringUtils.hasText(snapshot.getSourceMode())) {
            patientInfo.put("sourceMode", snapshot.getSourceMode().trim());
            patientInfo.put(
                    "sourceLabel",
                    MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(snapshot.getSourceMode()) ? "PACS 调取" : "本地上传");
        }
        if (StringUtils.hasText(snapshot.getOriginalFilename())) {
            patientInfo.put("originalFilename", snapshot.getOriginalFilename().trim());
        }

        enriched.put("patientInfo", patientInfo);
        return enriched;
    }

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> enrichGenericMockResult(Map<String, Object> result,
                                                        String taskType,
                                                        String patientName,
                                                        String examId,
                                                        String sourceMode,
                                                        String originalFilename) {
        Map<String, Object> enriched = result == null ? new HashMap<>() : new HashMap<>(result);
        Map<String, Object> patientInfo = enriched.get("patientInfo") instanceof Map<?, ?> map
                ? new HashMap<>((Map<String, Object>) map)
                : new HashMap<>();

        if (StringUtils.hasText(patientName)) {
            patientInfo.put("name", patientName.trim());
        }
        if (StringUtils.hasText(examId)) {
            patientInfo.put("studyId", examId.trim());
            patientInfo.put("accessionNumber", examId.trim());
        }
        if (StringUtils.hasText(sourceMode)) {
            patientInfo.put("sourceMode", sourceMode.trim());
            patientInfo.put("sourceLabel", MockQualityAnalysisSupport.SOURCE_MODE_PACS.equals(sourceMode) ? "PACS 调取" : "本地上传");
        }
        if (StringUtils.hasText(originalFilename)) {
            patientInfo.put("originalFilename", originalFilename.trim());
        }

        enriched.put("patientInfo", patientInfo);
        enriched.put("taskType", taskType);
        enriched.put("taskTypeName", MockQualityAnalysisSupport.resolveTaskTypeName(taskType));
        enriched.put("mock", true);
        return enriched;
    }
}
