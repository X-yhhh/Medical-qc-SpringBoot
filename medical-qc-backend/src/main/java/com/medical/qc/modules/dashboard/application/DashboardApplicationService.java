package com.medical.qc.modules.dashboard.application;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 仪表盘应用服务。
 */
@Service
public class DashboardApplicationService {
    // 具体仪表盘聚合逻辑由 DashboardServiceImpl 承担。
    private final DashboardServiceImpl dashboardService;

    public DashboardApplicationService(DashboardServiceImpl dashboardService) {
        this.dashboardService = dashboardService;
    }

    /**
     * 获取首页总览数据。
     */
    public Map<String, Object> getOverview(User user) {
        return dashboardService.getOverview(user);
    }

    /**
     * 获取首页趋势图数据。
     */
    public Map<String, Object> getTrend(User user, String period) {
        return dashboardService.getTrend(user, period);
    }
}

