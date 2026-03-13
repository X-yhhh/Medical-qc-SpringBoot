package com.medical.qc.modules.dashboard.application;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 仪表盘应用服务。
 */
@Service
public class DashboardApplicationService {
    private final DashboardServiceImpl dashboardService;

    public DashboardApplicationService(DashboardServiceImpl dashboardService) {
        this.dashboardService = dashboardService;
    }

    public Map<String, Object> getOverview(User user) {
        return dashboardService.getOverview(user);
    }

    public Map<String, Object> getTrend(User user, String period) {
        return dashboardService.getTrend(user, period);
    }
}

