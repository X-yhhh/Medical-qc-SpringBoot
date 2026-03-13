package com.medical.qc.modules.dashboard.application;

import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.dashboard.application.support.DashboardReadSupport;
import com.medical.qc.modules.qcresult.application.QualityServiceImpl;
import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.qctask.model.QcTaskRecord;
import com.medical.qc.modules.unified.application.UnifiedIssueQueryService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * 首页仪表盘服务实现。
 *
 * <p>首页聚合统一读取新模型数据，避免再依赖旧任务与旧工单表。</p>
 */
@Service
public class DashboardServiceImpl {
    // 脑出血检测历史仍通过质量服务统一读取。
    private final QualityServiceImpl qualityService;
    // 异常统计与风险预警统一走工单查询服务。
    private final UnifiedIssueQueryService unifiedIssueQueryService;
    // 仪表盘读侧支持类负责把多源数据组装成前端视图模型。
    private final DashboardReadSupport dashboardReadSupport;

    public DashboardServiceImpl(QualityServiceImpl qualityService,
                                UnifiedIssueQueryService unifiedIssueQueryService,
                                DashboardReadSupport dashboardReadSupport) {
        this.qualityService = qualityService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
        this.dashboardReadSupport = dashboardReadSupport;
    }

    /**
     * 获取首页总览。
     * 数据链路：用户 -> 范围解析 -> 脑出血历史 + 异步质控任务 + 工单预警 -> 仪表盘总览 DTO。
     */
    public Map<String, Object> getOverview(User user) {
        // 管理员看全局；医生仅看自己提交的任务与检测记录。
        Long scopedUserId = dashboardReadSupport.resolveScopedUserId(user);
        List<HemorrhageRecord> hemorrhageHistory = qualityService.getHistory(scopedUserId);
        List<QcTaskRecord> qualityTasks = dashboardReadSupport.listQualityTasks(scopedUserId);

        return dashboardReadSupport.buildOverview(
                user,
                hemorrhageHistory,
                qualityTasks,
                unifiedIssueQueryService.countPendingIssues(scopedUserId),
                unifiedIssueQueryService.getRiskAlerts(scopedUserId, 5),
                unifiedIssueQueryService.countHighRiskIssues(scopedUserId));
    }

    /**
     * 获取首页趋势图。
     */
    public Map<String, Object> getTrend(User user, String period) {
        Long scopedUserId = dashboardReadSupport.resolveScopedUserId(user);
        List<HemorrhageRecord> hemorrhageHistory = qualityService.getHistory(scopedUserId);
        List<QcTaskRecord> qualityTasks = dashboardReadSupport.listQualityTasks(scopedUserId);
        // 趋势图在统一时间窗口内同时融合脑出血检测与异步质控任务结果。
        return dashboardReadSupport.buildTrend(period, hemorrhageHistory, qualityTasks);
    }
}
