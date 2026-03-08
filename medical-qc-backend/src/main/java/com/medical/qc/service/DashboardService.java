package com.medical.qc.service;

import com.medical.qc.entity.User;

import java.util.Map;

/**
 * 首页仪表盘服务。
 * 负责聚合首页所需的欢迎信息、统计卡片、风险预警、待办时间轴及趋势图数据。
 */
public interface DashboardService {
    /**
     * 获取首页总览数据。
     *
     * @param user 当前登录用户
     * @return 首页总览数据
     */
    Map<String, Object> getOverview(User user);

    /**
     * 获取首页质控合格率趋势数据。
     *
     * @param user   当前登录用户
     * @param period 统计周期：week / month
     * @return 趋势图数据
     */
    Map<String, Object> getTrend(User user, String period);
}
