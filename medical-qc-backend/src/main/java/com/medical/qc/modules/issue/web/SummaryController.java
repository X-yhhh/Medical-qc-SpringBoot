package com.medical.qc.modules.issue.web;

import com.medical.qc.bean.IssueWorkflowUpdateReq;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.issue.application.IssueSummaryApplicationService;
import com.medical.qc.modules.issue.application.command.IssueStatusUpdateCommand;
import com.medical.qc.modules.issue.application.command.IssueWorkflowUpdateCommand;
import com.medical.qc.modules.issue.application.query.IssuePageQuery;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 异常汇总与工单处理 API 控制器。
 * 当前优先基于脑出血检测历史记录生成统一异常工单，
 * 其他质控项后续按相同方式逐步接入。
 */
@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {
    // 应用服务负责读写编排，控制器只承接 HTTP 参数和会话上下文。
    private final IssueSummaryApplicationService issueSummaryApplicationService;
    // 会话用户辅助组件负责校验登录态并计算数据访问范围。
    private final SessionUserSupport sessionUserSupport;

    public SummaryController(IssueSummaryApplicationService issueSummaryApplicationService,
                             SessionUserSupport sessionUserSupport) {
        this.issueSummaryApplicationService = issueSummaryApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 获取异常汇总页顶部统计卡片数据。
     *
     * @param session 当前会话
     * @return 统计结果
     */
    @GetMapping("/stats")
    public ResponseEntity<?> getSummaryStats(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 医生只看自己提交的任务异常，管理员查看全局统计。
        return ResponseEntity.ok(issueSummaryApplicationService.getSummaryStats(
                sessionUserSupport.resolveScopedUserId(user)));
    }

    /**
     * 获取异常趋势图数据。
     *
     * @param days 统计天数
     * @param session 当前会话
     * @return 趋势图数据
     */
    @GetMapping("/trend")
    public ResponseEntity<?> getIssueTrend(
            @RequestParam(value = "days", defaultValue = "7") int days,
            HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 趋势图沿用同一 scopedUserId 过滤逻辑，避免统计与列表口径不一致。
        return ResponseEntity.ok(issueSummaryApplicationService.getIssueTrend(
                sessionUserSupport.resolveScopedUserId(user),
                days));
    }

    /**
     * 获取异常类型分布数据。
     *
     * @param session 当前会话
     * @return 分布数据
     */
    @GetMapping("/distribution")
    public ResponseEntity<?> getIssueDistribution(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        return ResponseEntity.ok(issueSummaryApplicationService.getIssueDistribution(
                sessionUserSupport.resolveScopedUserId(user)));
    }

    /**
     * 获取工单可分派人员列表。
     *
     * @param session 当前会话
     * @return 用户列表
     */
    @GetMapping("/operators")
    public ResponseEntity<?> getAssignableUsers(HttpSession session) {
        sessionUserSupport.requireAuthenticatedUser(session);
        // 可分派人员是全局启用账号列表，不受当前用户数据范围限制。
        return ResponseEntity.ok(issueSummaryApplicationService.getAssignableUsers());
    }

    /**
     * 获取异常工单分页列表。
     *
     * @param page 页码
     * @param limit 每页数量
     * @param query 搜索关键字
     * @param status 状态筛选
     * @param session 当前会话
     * @return 分页结果
     */
    @GetMapping("/recent")
    public ResponseEntity<?> getRecentIssues(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status,
            HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 把分页和筛选参数封装为查询对象，便于应用层保持入参稳定。
        return ResponseEntity.ok(issueSummaryApplicationService.getIssuePage(
                new IssuePageQuery(
                        sessionUserSupport.resolveScopedUserId(user),
                        page,
                        limit,
                        query,
                        status)));
    }

    /**
     * 导出当前筛选条件下的异常工单摘要。
     */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportIssues(@RequestParam(value = "query", required = false) String query,
                                               @RequestParam(value = "status", required = false) String status,
                                               HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        byte[] payload = issueSummaryApplicationService.exportIssueCsv(
                sessionUserSupport.resolveScopedUserId(user),
                query,
                status);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"issues-summary.csv\"")
                .body(payload);
    }

    /**
     * 获取异常工单详情及其来源记录明细。
     *
     * @param issueId 工单 ID
     * @param session 当前会话
     * @return 工单详情
     */
    @GetMapping("/issues/{issueId}")
    public ResponseEntity<?> getIssueDetail(
            @PathVariable("issueId") Long issueId,
            HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        return ResponseEntity.ok(issueSummaryApplicationService.getIssueDetail(
                sessionUserSupport.resolveScopedUserId(user),
                issueId));
    }

    /**
     * 更新异常工单状态。
     *
     * @param issueId 工单 ID
     * @param requestBody 请求体，包含 status 和 remark
     * @param session 当前会话
     * @return 更新后的工单信息
     */
    @PatchMapping("/issues/{issueId}/status")
    public ResponseEntity<?> updateIssueStatus(
            @PathVariable("issueId") Long issueId,
            @RequestBody(required = false) Map<String, String> requestBody,
            HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // status 和 remark 来自详情弹窗或列表快捷处理按钮。
        String status = requestBody == null ? null : requestBody.get("status");
        String remark = requestBody == null ? null : requestBody.get("remark");

        return ResponseEntity.ok(issueSummaryApplicationService.updateIssueStatus(
                new IssueStatusUpdateCommand(
                        sessionUserSupport.resolveScopedUserId(user),
                        user.getId(),
                        issueId,
                        status,
                        remark)));
    }

    /**
     * 更新异常工单工作流字段，包括指派、CAPA 和状态流转。
     *
     * @param issueId 工单 ID
     * @param requestBody 工作流更新请求
     * @param session 当前会话
     * @return 更新后的工单详情
     */
    @PatchMapping("/issues/{issueId}/workflow")
    public ResponseEntity<?> updateIssueWorkflow(@PathVariable("issueId") Long issueId,
                                                 @RequestBody(required = false) IssueWorkflowUpdateReq requestBody,
                                                 HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 工作流更新会同时携带状态、处理人和 CAPA 字段，因此直接透传完整请求对象。
        return ResponseEntity.ok(issueSummaryApplicationService.updateIssueWorkflow(
                new IssueWorkflowUpdateCommand(
                        sessionUserSupport.resolveScopedUserId(user),
                        user.getId(),
                        issueId,
                        requestBody)));
    }
}

