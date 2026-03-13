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
    private final QualityServiceImpl qualityService;
    private final UnifiedIssueQueryService unifiedIssueQueryService;
    private final DashboardReadSupport dashboardReadSupport;

    public DashboardServiceImpl(QualityServiceImpl qualityService,
                                UnifiedIssueQueryService unifiedIssueQueryService,
                                DashboardReadSupport dashboardReadSupport) {
        this.qualityService = qualityService;
        this.unifiedIssueQueryService = unifiedIssueQueryService;
        this.dashboardReadSupport = dashboardReadSupport;
    }

    public Map<String, Object> getOverview(User user) {
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

    public Map<String, Object> getTrend(User user, String period) {
        Long scopedUserId = dashboardReadSupport.resolveScopedUserId(user);
        List<HemorrhageRecord> hemorrhageHistory = qualityService.getHistory(scopedUserId);
        List<QcTaskRecord> qualityTasks = dashboardReadSupport.listQualityTasks(scopedUserId);
        return dashboardReadSupport.buildTrend(period, hemorrhageHistory, qualityTasks);
    }
}
