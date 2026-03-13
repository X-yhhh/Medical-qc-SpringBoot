package com.medical.qc.modules.dashboard.web;

import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.dashboard.application.DashboardApplicationService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页仪表盘控制器。
 */
@RestController
@RequestMapping("/api/v1/dashboard")
public class DashboardController {
    // 应用服务负责任务、异常、历史记录的聚合编排。
    private final DashboardApplicationService dashboardApplicationService;
    // 会话辅助组件负责解析登录用户和权限范围。
    private final SessionUserSupport sessionUserSupport;

    public DashboardController(DashboardApplicationService dashboardApplicationService,
                               SessionUserSupport sessionUserSupport) {
        this.dashboardApplicationService = dashboardApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 获取首页总览数据。
     *
     * @param session 当前会话
     * @return 首页欢迎信息、统计卡片、风险预警、待办事项等
     */
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 总览数据按当前用户角色决定全局视图或个人视图。
        return ResponseEntity.ok(dashboardApplicationService.getOverview(user));
    }

    /**
     * 获取首页质控合格率趋势。
     *
     * @param period 统计周期：week / month
     * @param session 当前会话
     * @return 趋势图数据
     */
    @GetMapping("/trend")
    public ResponseEntity<?> getTrend(
            @RequestParam(value = "period", defaultValue = "week") String period,
            HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 趋势图同样按当前用户角色收敛查询范围。
        return ResponseEntity.ok(dashboardApplicationService.getTrend(user, period));
    }
}

