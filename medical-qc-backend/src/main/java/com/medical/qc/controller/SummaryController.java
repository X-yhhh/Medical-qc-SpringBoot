package com.medical.qc.controller;

import org.springframework.web.bind.annotation.*;
import java.util.*;

/**
 * 异常汇总与统计 API 控制器 (Mock)
 * 提供系统整体的质控统计数据，目前为模拟数据，
 * 后期需对接数据库进行实时统计。
 */
@RestController
@RequestMapping("/api/v1/summary")
public class SummaryController {

    /**
     * 获取总体统计指标
     * @return 包含扫描总量、今日扫描、待处理问题、质量评分的 Map
     */
    @GetMapping("/stats")
    public Map<String, Object> getSummaryStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total_scans", 12580);
        stats.put("today_scans", 145);
        stats.put("pending_issues", 23);
        stats.put("quality_score", 98.5);
        return stats;
    }

    /**
     * 获取问题趋势数据 (图表)
     * @param days 统计天数 (默认7天)
     * @return 日期列表和对应的问题数量
     */
    @GetMapping("/trend")
    public Map<String, Object> getIssueTrend(@RequestParam(value = "days", defaultValue = "7") int days) {
        Map<String, Object> response = new HashMap<>();
        List<String> dates = new ArrayList<>();
        List<Integer> issues = new ArrayList<>();
        
        // Mock data for the last 'days' days
        // TODO: 从数据库聚合查询每日异常数量
        for (int i = 0; i < days; i++) {
            dates.add("Day " + (i + 1));
            issues.add((int) (Math.random() * 10)); // Random issues count 0-10
        }
        
        response.put("dates", dates);
        response.put("issues", issues);
        return response;
    }

    /**
     * 获取问题类型分布 (饼图)
     * @return 各类问题的名称和数量
     */
    @GetMapping("/distribution")
    public List<Map<String, Object>> getIssueDistribution() {
        // TODO: 从数据库统计各类型异常的占比
        List<Map<String, Object>> distribution = new ArrayList<>();
        
        Map<String, Object> item1 = new HashMap<>();
        item1.put("name", "运动伪影");
        item1.put("value", 35);
        distribution.add(item1);
        
        Map<String, Object> item2 = new HashMap<>();
        item2.put("name", "金属伪影");
        item2.put("value", 20);
        distribution.add(item2);
        
        Map<String, Object> item3 = new HashMap<>();
        item3.put("name", "对比度低");
        item3.put("value", 15);
        distribution.add(item3);
        
        Map<String, Object> item4 = new HashMap<>();
        item4.put("name", "其他");
        item4.put("value", 10);
        distribution.add(item4);
        
        return distribution;
    }

    /**
     * 获取近期异常记录列表 (分页)
     * @param page 页码
     * @param limit 每页数量
     * @param query 搜索关键词
     * @param status 状态过滤
     * @return 分页列表数据
     */
    @GetMapping("/recent")
    public Map<String, Object> getRecentIssues(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "query", required = false) String query,
            @RequestParam(value = "status", required = false) String status) {
        
        // TODO: 实现基于 MyBatis Plus 的分页查询
        Map<String, Object> response = new HashMap<>();
        List<Map<String, Object>> items = new ArrayList<>();
        
        // Mock 10 items
        for (int i = 0; i < limit; i++) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", i + 1);
            item.put("patient_name", "张三 " + (char)('A' + i));
            item.put("exam_id", "EXAM-" + (1000 + i));
            item.put("scan_type", "头部 CT");
            item.put("issue_type", "运动伪影");
            item.put("status", "待处理");
            item.put("timestamp", new Date());
            items.add(item);
        }
        
        response.put("total", 100); // Mock total count
        response.put("items", items);
        return response;
    }
}
