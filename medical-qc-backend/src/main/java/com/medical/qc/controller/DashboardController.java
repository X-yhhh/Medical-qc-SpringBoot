package com.medical.qc.controller;

import com.medical.qc.entity.User;
import com.medical.qc.service.DashboardService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private SessionUserSupport sessionUserSupport;

    /**
     * 获取首页总览数据。
     *
     * @param session 当前会话
     * @return 首页欢迎信息、统计卡片、风险预警、待办事项等
     */
    @GetMapping("/overview")
    public ResponseEntity<?> getOverview(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        return ResponseEntity.ok(dashboardService.getOverview(user));
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
        return ResponseEntity.ok(dashboardService.getTrend(user, period));
    }
}
