package com.medical.qc.modules.unified.application.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.bean.IssueWorkflowUpdateReq;
import com.medical.qc.common.AuthRole;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import com.medical.qc.modules.qcrule.application.QcRuleConfigServiceImpl;
import com.medical.qc.modules.qcrule.model.QcRuleConfig;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueActionLog;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueCapaRecord;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueTicket;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueActionLogMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueCapaRecordMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedIssueTicketMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcResultMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedQcTaskMapper;
import com.medical.qc.modules.unified.persistence.mapper.UnifiedStudyMapper;
import com.medical.qc.support.HemorrhageIssueSupport;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 统一模型异常工单写服务。
 *
 * <p>负责自动建单、状态流转、处理人指派和 CAPA 写入。</p>
 */
@Service
public class UnifiedIssueWriteService {
    // 工单状态在整个异常汇总链路中保持统一中文枚举。
    private static final String STATUS_PENDING = "待处理";
    private static final String STATUS_PROCESSING = "处理中";
    private static final String STATUS_RESOLVED = "已解决";

    private final UnifiedIssueTicketMapper unifiedIssueTicketMapper;
    private final UnifiedIssueActionLogMapper unifiedIssueActionLogMapper;
    private final UnifiedIssueCapaRecordMapper unifiedIssueCapaRecordMapper;
    private final UnifiedQcTaskMapper unifiedQcTaskMapper;
    private final UnifiedQcResultMapper unifiedQcResultMapper;
    private final UnifiedStudyMapper unifiedStudyMapper;
    private final QcRuleConfigServiceImpl qcRuleConfigService;
    private final UserMapper userMapper;

    public UnifiedIssueWriteService(UnifiedIssueTicketMapper unifiedIssueTicketMapper,
                                    UnifiedIssueActionLogMapper unifiedIssueActionLogMapper,
                                    UnifiedIssueCapaRecordMapper unifiedIssueCapaRecordMapper,
                                    UnifiedQcTaskMapper unifiedQcTaskMapper,
                                    UnifiedQcResultMapper unifiedQcResultMapper,
                                    UnifiedStudyMapper unifiedStudyMapper,
                                    QcRuleConfigServiceImpl qcRuleConfigService,
                                    UserMapper userMapper) {
        this.unifiedIssueTicketMapper = unifiedIssueTicketMapper;
        this.unifiedIssueActionLogMapper = unifiedIssueActionLogMapper;
        this.unifiedIssueCapaRecordMapper = unifiedIssueCapaRecordMapper;
        this.unifiedQcTaskMapper = unifiedQcTaskMapper;
        this.unifiedQcResultMapper = unifiedQcResultMapper;
        this.unifiedStudyMapper = unifiedStudyMapper;
        this.qcRuleConfigService = qcRuleConfigService;
        this.userMapper = userMapper;
    }

    /**
     * 根据脑出血检测结果同步异常工单。
     */
    public void syncHemorrhageIssue(Long taskId) {
        UnifiedQcTask task = loadTask(taskId);
        UnifiedQcResult result = loadResult(task);
        // 仅不合格或存在异常项的结果才需要建单。
        if (task == null || result == null || !isAbnormalResult(result)) {
            return;
        }

        // 规则中心可关闭自动建单，因此这里要先判断规则是否允许。
        String issueType = firstNonBlank(result.getPrimaryIssueName(), "未见明显异常");
        QcRuleConfig appliedRule = qcRuleConfigService.resolveRule("hemorrhage", issueType);
        if (shouldSkipIssueCreation(appliedRule)) {
            return;
        }

        UnifiedStudy study = loadStudy(task);
        LocalDateTime detectedAt = firstNonNull(result.getCreatedAt(), task.getCompletedAt(), task.getRequestedAt(), LocalDateTime.now());
        upsertTicket(
                task,
                result,
                study,
                issueType,
                HemorrhageIssueSupport.buildIssueDescription(issueType),
                resolveIssuePriority(issueType, appliedRule),
                resolveResponsibleRole(appliedRule),
                resolveSlaHours(appliedRule),
                detectedAt,
                "由脑出血检测结果自动生成异常工单");
    }

    /**
     * 根据异步质控任务结果同步异常工单。
     */
    public void syncQualityTaskIssue(Long taskId) {
        UnifiedQcTask task = loadTask(taskId);
        UnifiedQcResult result = loadResult(task);
        if (task == null || result == null || !isAbnormalResult(result)) {
            return;
        }

        String issueType = firstNonBlank(result.getPrimaryIssueName(), "图像质量异常");
        QcRuleConfig appliedRule = qcRuleConfigService.resolveRule(task.getTaskTypeCode(), issueType);
        if (shouldSkipIssueCreation(appliedRule)) {
            return;
        }

        UnifiedStudy study = loadStudy(task);
        LocalDateTime detectedAt = firstNonNull(result.getUpdatedAt(), task.getCompletedAt(), task.getRequestedAt(), LocalDateTime.now());
        upsertTicket(
                task,
                result,
                study,
                issueType,
                buildQualityTaskDescription(task, result, issueType),
                resolveQualityTaskPriority(task, result, appliedRule),
                resolveResponsibleRole(appliedRule),
                resolveSlaHours(appliedRule),
                detectedAt,
                "由异步质控任务结果自动生成异常工单");
    }

    /**
     * 更新工单状态。
     */
    public void updateIssueStatus(Long scopedUserId,
                                  Long operatorId,
                                  Long issueId,
                                  String status,
                                  String remark) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }
        if (!isSupportedStatus(status)) {
            throw new IllegalArgumentException("不支持的工单状态");
        }

        // 先校验当前用户是否有权访问该工单。
        UnifiedIssueTicket ticket = requireAccessibleTicket(scopedUserId, issueId);
        String beforeStatus = ticket.getStatus();
        LocalDateTime now = LocalDateTime.now();
        ticket.setStatus(status);
        if (StringUtils.hasText(remark)) {
            ticket.setLastRemark(remark.trim());
        }
        ticket.setUpdatedAt(now);
        ticket.setResolvedAt(STATUS_RESOLVED.equals(status) ? now : null);
        unifiedIssueTicketMapper.updateById(ticket);

        // 状态变更会写入动作日志，供详情页时间轴展示。
        insertActionLog(ticket.getId(), operatorId, "update_status", beforeStatus, status, remark, now);
    }

    /**
     * 更新工单工作流，包括状态、处理人和 CAPA。
     */
    public void updateIssueWorkflow(Long scopedUserId,
                                    Long operatorId,
                                    Long issueId,
                                    IssueWorkflowUpdateReq request) {
        if (issueId == null) {
            throw new IllegalArgumentException("异常工单 ID 不能为空");
        }

        UnifiedIssueTicket ticket = requireAccessibleTicket(scopedUserId, issueId);
        IssueWorkflowUpdateReq normalizedRequest = request == null ? new IssueWorkflowUpdateReq() : request;
        String nextStatus = StringUtils.hasText(normalizedRequest.getStatus())
                ? normalizedRequest.getStatus().trim()
                : ticket.getStatus();
        if (!isSupportedStatus(nextStatus)) {
            throw new IllegalArgumentException("不支持的工单状态");
        }

        // 指派人和备注需要在状态写入前先归一化。
        Long assigneeUserId = resolveAssigneeUserId(normalizedRequest.getAssigneeUserId());
        String remark = trimToNull(normalizedRequest.getRemark());
        String beforeStatus = ticket.getStatus();
        Long beforeAssigneeUserId = ticket.getAssigneeUserId();
        LocalDateTime now = LocalDateTime.now();

        ticket.setStatus(nextStatus);
        ticket.setAssigneeUserId(assigneeUserId);
        if (remark != null) {
            ticket.setLastRemark(remark);
        }
        ticket.setUpdatedAt(now);
        ticket.setResolvedAt(STATUS_RESOLVED.equals(nextStatus) ? now : null);
        unifiedIssueTicketMapper.updateById(ticket);

        // CAPA 记录单独存表，和工单主记录分离维护。
        upsertCapaRecord(issueId, operatorId, normalizedRequest);

        if (!Objects.equals(beforeAssigneeUserId, assigneeUserId)) {
            insertActionLog(
                    issueId,
                    operatorId,
                    "assign",
                    beforeStatus,
                    ticket.getStatus(),
                    buildAssignmentRemark(beforeAssigneeUserId, assigneeUserId),
                    now);
        }

        if (!Objects.equals(beforeStatus, nextStatus) || remark != null) {
            insertActionLog(issueId, operatorId, "update_status", beforeStatus, nextStatus, remark, now);
        }

        if (hasCapaContent(normalizedRequest)) {
            insertActionLog(issueId, operatorId, "update_capa", ticket.getStatus(), ticket.getStatus(), "更新 CAPA 整改记录", now);
        }
    }

    /**
     * 获取可分派人员列表。
     */
    public List<Map<String, Object>> getAssignableUsers() {
        return userMapper.selectList(new QueryWrapper<User>()
                        .eq("is_active", true)
                        .orderByAsc("role_id")
                        .orderByAsc("username"))
                .stream()
                .map(this::toAssignableUser)
                .toList();
    }

    /**
     * 新增或更新工单主记录。
     */
    private void upsertTicket(UnifiedQcTask task,
                              UnifiedQcResult result,
                              UnifiedStudy study,
                              String issueType,
                              String description,
                              String priority,
                              String responsibleRole,
                              Integer slaHours,
                              LocalDateTime detectedAt,
                              String createRemark) {
        UnifiedIssueTicket ticket = unifiedIssueTicketMapper.selectOne(new QueryWrapper<UnifiedIssueTicket>()
                .eq("task_id", task.getId())
                .last("LIMIT 1"));

        // SLA 截止时间以检测完成时间为起点，加规则里的小时数计算。
        LocalDateTime dueAt = calculateDueAt(detectedAt, slaHours);
        if (ticket == null) {
            ticket = new UnifiedIssueTicket();
            ticket.setTicketNo("issue-" + task.getId());
            ticket.setTaskId(task.getId());
            ticket.setResultId(result.getId());
            ticket.setStudyId(study == null ? null : study.getId());
            ticket.setPatientId(study == null ? null : study.getPatientId());
            ticket.setStatus(STATUS_PENDING);
            ticket.setCreatedAt(detectedAt);
        }

        ticket.setResultId(result.getId());
        ticket.setStudyId(study == null ? null : study.getId());
        ticket.setPatientId(study == null ? null : study.getPatientId());
        ticket.setIssueCode(issueType);
        ticket.setIssueName(issueType);
        ticket.setDescription(description);
        ticket.setPriority(priority);
        ticket.setResponsibleRole(responsibleRole);
        ticket.setSlaHours(slaHours);
        ticket.setDueAt(dueAt);
        ticket.setUpdatedAt(firstNonNull(result.getUpdatedAt(), LocalDateTime.now()));

        if (ticket.getId() == null) {
            unifiedIssueTicketMapper.insert(ticket);
            // 首次建单时写 create 动作日志；更新已有工单则只刷新主表字段。
            insertActionLog(ticket.getId(), task.getSubmittedBy(), "create", null, STATUS_PENDING, createRemark, detectedAt);
            return;
        }

        unifiedIssueTicketMapper.updateById(ticket);
    }

    /**
     * 校验工单存在且当前用户有权访问。
     */
    private UnifiedIssueTicket requireAccessibleTicket(Long scopedUserId, Long issueId) {
        UnifiedIssueTicket ticket = unifiedIssueTicketMapper.selectById(issueId);
        if (ticket == null) {
            throw new IllegalArgumentException("异常工单不存在");
        }

        UnifiedQcTask task = ticket.getTaskId() == null ? null : unifiedQcTaskMapper.selectById(ticket.getTaskId());
        if (scopedUserId != null && task != null && task.getSubmittedBy() != null && !Objects.equals(scopedUserId, task.getSubmittedBy())) {
            throw new IllegalArgumentException("异常工单不存在");
        }
        return ticket;
    }

    /**
     * 加载任务主记录。
     */
    private UnifiedQcTask loadTask(Long taskId) {
        return taskId == null ? null : unifiedQcTaskMapper.selectById(taskId);
    }

    /**
     * 加载任务结果主记录。
     */
    private UnifiedQcResult loadResult(UnifiedQcTask task) {
        if (task == null) {
            return null;
        }
        return unifiedQcResultMapper.selectOne(new QueryWrapper<UnifiedQcResult>()
                .eq("task_id", task.getId())
                .eq("result_version", 1)
                .last("LIMIT 1"));
    }

    /**
     * 加载任务关联的检查实例。
     */
    private UnifiedStudy loadStudy(UnifiedQcTask task) {
        return task == null || task.getStudyId() == null ? null : unifiedStudyMapper.selectById(task.getStudyId());
    }

    /**
     * 新增或更新 CAPA 记录。
     */
    private void upsertCapaRecord(Long issueId, Long operatorId, IssueWorkflowUpdateReq request) {
        if (issueId == null || request == null || !hasCapaContent(request)) {
            return;
        }

        UnifiedIssueCapaRecord capaRecord = unifiedIssueCapaRecordMapper.selectOne(new QueryWrapper<UnifiedIssueCapaRecord>()
                .eq("ticket_id", issueId)
                .last("LIMIT 1"));
        if (capaRecord == null) {
            capaRecord = new UnifiedIssueCapaRecord();
            capaRecord.setTicketId(issueId);
            capaRecord.setCreatedAt(LocalDateTime.now());
        }

        capaRecord.setRootCauseCategory(trimToNull(request.getRootCauseCategory()));
        capaRecord.setRootCauseDetail(trimToNull(request.getRootCauseDetail()));
        capaRecord.setCorrectiveAction(trimToNull(request.getCorrectiveAction()));
        capaRecord.setPreventiveAction(trimToNull(request.getPreventiveAction()));
        capaRecord.setVerificationNote(trimToNull(request.getVerificationNote()));
        capaRecord.setUpdatedBy(operatorId);
        capaRecord.setUpdatedAt(LocalDateTime.now());

        if (capaRecord.getId() == null) {
            unifiedIssueCapaRecordMapper.insert(capaRecord);
            return;
        }
        unifiedIssueCapaRecordMapper.updateById(capaRecord);
    }

    /**
     * 写入工单动作日志。
     */
    private void insertActionLog(Long ticketId,
                                 Long operatorId,
                                 String actionType,
                                 String beforeStatus,
                                 String afterStatus,
                                 String remark,
                                 LocalDateTime createdAt) {
        UnifiedIssueActionLog actionLog = new UnifiedIssueActionLog();
        actionLog.setTicketId(ticketId);
        actionLog.setOperatorId(operatorId);
        actionLog.setActionType(actionType);
        actionLog.setBeforeStatus(beforeStatus);
        actionLog.setAfterStatus(afterStatus);
        actionLog.setRemark(trimToNull(remark));
        actionLog.setCreatedAt(firstNonNull(createdAt, LocalDateTime.now()));
        unifiedIssueActionLogMapper.insert(actionLog);
    }

    /**
     * 判断规则是否要求跳过自动建单。
     */
    private boolean shouldSkipIssueCreation(QcRuleConfig appliedRule) {
        return appliedRule != null
                && (!Boolean.TRUE.equals(appliedRule.getEnabled())
                || Boolean.FALSE.equals(appliedRule.getAutoCreateIssue()));
    }

    /**
     * 判断结果是否属于异常结果。
     */
    private boolean isAbnormalResult(UnifiedQcResult result) {
        return result != null
                && ("不合格".equals(result.getQcStatus())
                || (result.getAbnormalCount() != null && result.getAbnormalCount() > 0));
    }

    /**
     * 构造异步质控任务的工单描述。
     */
    private String buildQualityTaskDescription(UnifiedQcTask task, UnifiedQcResult result, String issueType) {
        String taskLabel = MockQualityAnalysisSupport.resolveTaskTypeName(task.getTaskTypeCode());
        int abnormalCount = result.getAbnormalCount() == null ? 0 : result.getAbnormalCount();
        String qualityScoreText = result.getQualityScore() == null ? "--" : String.valueOf(result.getQualityScore().doubleValue());

        if (abnormalCount > 1) {
            return taskLabel + "发现 " + abnormalCount + " 项异常，主异常为“" + issueType + "”，当前质控分为 " + qualityScoreText;
        }
        return taskLabel + "发现异常：“" + issueType + "”，当前质控分为 " + qualityScoreText;
    }

    /**
     * 解析脑出血工单优先级。
     */
    private String resolveIssuePriority(String issueType, QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getPriority())) {
            return appliedRule.getPriority();
        }
        return HemorrhageIssueSupport.resolvePriority(issueType);
    }

    /**
     * 解析异步质控任务工单优先级。
     */
    private String resolveQualityTaskPriority(UnifiedQcTask task, UnifiedQcResult result, QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getPriority())) {
            return appliedRule.getPriority();
        }

        double qualityScore = result.getQualityScore() == null ? 0D : result.getQualityScore().doubleValue();
        int abnormalCount = result.getAbnormalCount() == null ? 0 : result.getAbnormalCount();

        if (MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA.equals(task.getTaskTypeCode()) && abnormalCount > 0) {
            return "高";
        }
        if (qualityScore > 0D && qualityScore < 70D) {
            return "高";
        }
        if (abnormalCount >= 2) {
            return "高";
        }
        if (qualityScore > 0D && qualityScore < 85D) {
            return "中";
        }
        if (abnormalCount >= 1) {
            return "中";
        }
        return "低";
    }

    /**
     * 解析责任角色。
     */
    private String resolveResponsibleRole(QcRuleConfig appliedRule) {
        if (appliedRule != null && StringUtils.hasText(appliedRule.getResponsibleRole())) {
            return appliedRule.getResponsibleRole();
        }
        return "doctor";
    }

    /**
     * 解析 SLA 小时数。
     */
    private Integer resolveSlaHours(QcRuleConfig appliedRule) {
        if (appliedRule != null && appliedRule.getSlaHours() != null && appliedRule.getSlaHours() > 0) {
            return appliedRule.getSlaHours();
        }
        return 24;
    }

    /**
     * 校验并解析指派用户 ID。
     */
    private Long resolveAssigneeUserId(Long assigneeUserId) {
        if (assigneeUserId == null) {
            return null;
        }

        User assignee = userMapper.selectById(assigneeUserId);
        if (assignee == null || Boolean.FALSE.equals(assignee.getIsActive())) {
            throw new IllegalArgumentException("指派人员不存在或已停用");
        }
        return assignee.getId();
    }

    private Map<String, Object> toAssignableUser(User user) {
        return Map.of(
                "id", user.getId(),
                "username", user.getUsername(),
                "fullName", user.getFullName(),
                "displayName", resolveUserDisplayName(user),
                "role", AuthRole.fromRoleId(user.getRoleId()).getCode(),
                "roleLabel", AuthRole.fromRoleId(user.getRoleId()).getDisplayName(),
                "department", user.getDepartment(),
                "hospital", user.getHospital());
    }

    private String resolveUserDisplayName(User user) {
        if (user == null) {
            return "--";
        }
        if (StringUtils.hasText(user.getFullName())) {
            return user.getFullName().trim();
        }
        return StringUtils.hasText(user.getUsername()) ? user.getUsername().trim() : "--";
    }

    private String resolveUserDisplayName(Long userId) {
        return resolveUserDisplayName(userId == null ? null : userMapper.selectById(userId));
    }

    private boolean isSupportedStatus(String status) {
        return STATUS_PENDING.equals(status) || STATUS_PROCESSING.equals(status) || STATUS_RESOLVED.equals(status);
    }

    private boolean hasCapaContent(IssueWorkflowUpdateReq request) {
        return request != null
                && (StringUtils.hasText(request.getRootCauseCategory())
                || StringUtils.hasText(request.getRootCauseDetail())
                || StringUtils.hasText(request.getCorrectiveAction())
                || StringUtils.hasText(request.getPreventiveAction())
                || StringUtils.hasText(request.getVerificationNote()));
    }

    private String buildAssignmentRemark(Long beforeAssigneeUserId, Long afterAssigneeUserId) {
        String beforeName = resolveUserDisplayName(beforeAssigneeUserId);
        String afterName = resolveUserDisplayName(afterAssigneeUserId);
        if (beforeAssigneeUserId == null && afterAssigneeUserId != null) {
            return "指派给 " + afterName;
        }
        if (beforeAssigneeUserId != null && afterAssigneeUserId == null) {
            return "取消指派（原处理人：" + beforeName + "）";
        }
        return "处理人由 " + beforeName + " 调整为 " + afterName;
    }

    private LocalDateTime calculateDueAt(LocalDateTime createdAt, Integer slaHours) {
        if (createdAt == null || slaHours == null || slaHours <= 0) {
            return null;
        }
        return createdAt.plusHours(slaHours);
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

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
