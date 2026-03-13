package com.medical.qc.modules.qcrule.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.qc.bean.QcRuleConfigSaveReq;
import com.medical.qc.modules.qcrule.model.QcRuleConfig;
import com.medical.qc.modules.qcrule.persistence.mapper.QcRuleConfigMapper;
import com.medical.qc.support.MockQualityAnalysisSupport;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 质控规则配置服务实现。
 */
@Service
public class QcRuleConfigServiceImpl {
    // DEFAULT 规则用于异常项未命中精确规则时的兜底配置。
    private static final String DEFAULT_ISSUE_TYPE = "DEFAULT";
    // 统一格式化规则创建/更新时间。
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QcRuleConfigMapper qcRuleConfigMapper;

    public QcRuleConfigServiceImpl(QcRuleConfigMapper qcRuleConfigMapper) {
        this.qcRuleConfigMapper = qcRuleConfigMapper;
    }

    /**
     * 分页查询规则列表。
     * 数据链路：筛选参数 -> QueryWrapper -> qcrule 表 -> 前端表格/摘要数据。
     */
    public Map<String, Object> getRulePage(int page, int limit, String keyword, String taskType, Boolean enabled) {
        // 先归一化分页参数，再执行结构化查询。
        Page<QcRuleConfig> rulePage = new Page<>(normalizePage(page), normalizeLimit(limit));
        QueryWrapper<QcRuleConfig> pageQueryWrapper = buildQueryWrapper(keyword, taskType, enabled);
        pageQueryWrapper.orderByAsc("task_type").orderByAsc("issue_type");

        Page<QcRuleConfig> pageResult = qcRuleConfigMapper.selectPage(rulePage, pageQueryWrapper);
        List<QcRuleConfig> allRules = qcRuleConfigMapper.selectList(new QueryWrapper<QcRuleConfig>().orderByAsc("task_type").orderByAsc("issue_type"));

        Map<String, Object> response = new HashMap<>();
        response.put("items", pageResult.getRecords().stream().map(this::toRuleItem).toList());
        response.put("total", pageResult.getTotal());
        response.put("page", (int) pageResult.getCurrent());
        response.put("limit", (int) pageResult.getSize());
        response.put("pages", pageResult.getPages());
        response.put("summary", buildSummary(allRules));
        return response;
    }

    /**
     * 新增规则。
     */
    public Map<String, Object> createRule(Long operatorId, QcRuleConfigSaveReq request) {
        QcRuleConfig entity = buildValidatedEntity(request, null);
        entity.setUpdatedBy(operatorId);
        qcRuleConfigMapper.insert(entity);
        return toRuleItem(qcRuleConfigMapper.selectById(entity.getId()));
    }

    /**
     * 更新规则。
     */
    public Map<String, Object> updateRule(Long operatorId, Long ruleId, QcRuleConfigSaveReq request) {
        if (ruleId == null) {
            throw new IllegalArgumentException("规则 ID 不能为空");
        }

        QcRuleConfig existingRule = qcRuleConfigMapper.selectById(ruleId);
        if (existingRule == null) {
            throw new IllegalArgumentException("质控规则不存在");
        }

        QcRuleConfig entity = buildValidatedEntity(request, ruleId);
        entity.setId(ruleId);
        entity.setUpdatedBy(operatorId);
        entity.setCreatedAt(existingRule.getCreatedAt());
        qcRuleConfigMapper.updateById(entity);
        return toRuleItem(qcRuleConfigMapper.selectById(ruleId));
    }

    /**
     * 解析指定 taskType + issueType 应命中的规则。
     * 先查精确规则，未命中再回退到 DEFAULT。
     */
    public QcRuleConfig resolveRule(String taskType, String issueType) {
        String normalizedTaskType = normalizeTaskType(taskType);
        String normalizedIssueType = normalizeText(issueType);
        if (normalizedTaskType == null) {
            return null;
        }

        if (normalizedIssueType != null) {
            QcRuleConfig exactRule = qcRuleConfigMapper.selectOne(new QueryWrapper<QcRuleConfig>()
                    .eq("task_type", normalizedTaskType)
                    .eq("issue_type", normalizedIssueType)
                    .last("LIMIT 1"));
            if (exactRule != null) {
                return exactRule;
            }
        }

        return qcRuleConfigMapper.selectOne(new QueryWrapper<QcRuleConfig>()
                .eq("task_type", normalizedTaskType)
                .eq("issue_type", DEFAULT_ISSUE_TYPE)
                .last("LIMIT 1"));
    }

    /**
     * 构建列表查询条件。
     */
    private QueryWrapper<QcRuleConfig> buildQueryWrapper(String keyword, String taskType, Boolean enabled) {
        QueryWrapper<QcRuleConfig> queryWrapper = new QueryWrapper<>();
        String normalizedKeyword = normalizeText(keyword);
        if (normalizedKeyword != null) {
            queryWrapper.and(wrapper -> wrapper.like("task_type_name", normalizedKeyword)
                    .or().like("task_type", normalizedKeyword)
                    .or().like("issue_type", normalizedKeyword)
                    .or().like("description", normalizedKeyword));
        }

        String normalizedTaskType = normalizeTaskType(taskType);
        if (normalizedTaskType != null) {
            queryWrapper.eq("task_type", normalizedTaskType);
        }

        if (enabled != null) {
            queryWrapper.eq("enabled", enabled);
        }
        return queryWrapper;
    }

    /**
     * 统计规则摘要。
     */
    private Map<String, Object> buildSummary(List<QcRuleConfig> allRules) {
        long totalRules = allRules.size();
        long enabledRules = allRules.stream().filter(rule -> Boolean.TRUE.equals(rule.getEnabled())).count();
        long autoIssueRules = allRules.stream()
                .filter(rule -> Boolean.TRUE.equals(rule.getEnabled()))
                .filter(rule -> Boolean.TRUE.equals(rule.getAutoCreateIssue()))
                .count();
        double averageSlaHours = Math.round(allRules.stream()
                .filter(rule -> rule.getSlaHours() != null)
                .mapToInt(QcRuleConfig::getSlaHours)
                .average()
                .orElse(0D) * 10.0D) / 10.0D;

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalRules", totalRules);
        summary.put("enabledRules", enabledRules);
        summary.put("disabledRules", totalRules - enabledRules);
        summary.put("autoIssueRules", autoIssueRules);
        summary.put("averageSlaHours", averageSlaHours);
        return summary;
    }

    /**
     * 将规则实体映射为前端可直接消费的视图对象。
     */
    private Map<String, Object> toRuleItem(QcRuleConfig entity) {
        Map<String, Object> item = new HashMap<>();
        item.put("id", entity.getId());
        item.put("taskType", entity.getTaskType());
        item.put("taskTypeName", entity.getTaskTypeName());
        item.put("issueType", entity.getIssueType());
        item.put("priority", entity.getPriority());
        item.put("responsibleRole", entity.getResponsibleRole());
        item.put("responsibleRoleLabel", resolveRoleLabel(entity.getResponsibleRole()));
        item.put("slaHours", entity.getSlaHours());
        item.put("autoCreateIssue", Boolean.TRUE.equals(entity.getAutoCreateIssue()));
        item.put("enabled", Boolean.TRUE.equals(entity.getEnabled()));
        item.put("description", entity.getDescription());
        item.put("updatedBy", entity.getUpdatedBy());
        item.put("createdAt", entity.getCreatedAt() == null ? "--" : entity.getCreatedAt().format(DATE_TIME_FORMATTER));
        item.put("updatedAt", entity.getUpdatedAt() == null ? "--" : entity.getUpdatedAt().format(DATE_TIME_FORMATTER));
        return item;
    }

    /**
     * 校验并构造待保存实体。
     */
    private QcRuleConfig buildValidatedEntity(QcRuleConfigSaveReq request, Long excludeRuleId) {
        if (request == null) {
            throw new IllegalArgumentException("规则内容不能为空");
        }

        String normalizedTaskType = normalizeTaskType(request.getTaskType());
        if (normalizedTaskType == null) {
            throw new IllegalArgumentException("任务类型不能为空或不合法");
        }

        String normalizedIssueType = normalizeIssueType(request.getIssueType());
        validateUniqueness(normalizedTaskType, normalizedIssueType, excludeRuleId);

        QcRuleConfig entity = new QcRuleConfig();
        entity.setTaskType(normalizedTaskType);
        entity.setTaskTypeName(resolveTaskTypeName(normalizedTaskType, request.getTaskTypeName()));
        entity.setIssueType(normalizedIssueType);
        entity.setPriority(resolvePriority(request.getPriority()));
        entity.setResponsibleRole(resolveResponsibleRole(request.getResponsibleRole()));
        entity.setSlaHours(resolveSlaHours(request.getSlaHours()));
        entity.setAutoCreateIssue(request.getAutoCreateIssue() == null || request.getAutoCreateIssue());
        entity.setEnabled(request.getEnabled() == null || request.getEnabled());
        entity.setDescription(trimToNull(request.getDescription()));
        return entity;
    }

    /**
     * 校验同一模块下异常项唯一。
     */
    private void validateUniqueness(String taskType, String issueType, Long excludeRuleId) {
        QueryWrapper<QcRuleConfig> queryWrapper = new QueryWrapper<QcRuleConfig>()
                .eq("task_type", taskType)
                .eq("issue_type", issueType)
                .ne(excludeRuleId != null, "id", excludeRuleId);
        if (qcRuleConfigMapper.selectCount(queryWrapper) > 0) {
            throw new IllegalArgumentException("相同模块和异常项的规则已存在");
        }
    }

    /**
     * 校验优先级枚举。
     */
    private String resolvePriority(String priority) {
        String normalizedPriority = normalizeText(priority);
        if (!Objects.equals("高", normalizedPriority)
                && !Objects.equals("中", normalizedPriority)
                && !Objects.equals("低", normalizedPriority)) {
            throw new IllegalArgumentException("优先级仅支持 高/中/低");
        }
        return normalizedPriority;
    }

    /**
     * 校验责任角色枚举。
     */
    private String resolveResponsibleRole(String responsibleRole) {
        String normalizedRole = normalizeText(responsibleRole);
        if (!Objects.equals("doctor", normalizedRole) && !Objects.equals("admin", normalizedRole)) {
            throw new IllegalArgumentException("责任角色仅支持 doctor/admin");
        }
        return normalizedRole;
    }

    /**
     * 校验 SLA 范围。
     */
    private int resolveSlaHours(Integer slaHours) {
        if (slaHours == null || slaHours < 1 || slaHours > 168) {
            throw new IllegalArgumentException("SLA 时限需在 1-168 小时之间");
        }
        return slaHours;
    }

    /**
     * 解析模块展示名。
     */
    private String resolveTaskTypeName(String taskType, String customTaskTypeName) {
        if (StringUtils.hasText(customTaskTypeName)) {
            return customTaskTypeName.trim();
        }

        if ("hemorrhage".equals(taskType)) {
            return "头部出血检测";
        }

        return MockQualityAnalysisSupport.resolveTaskTypeName(taskType);
    }

    /**
     * 将角色编码转换为中文标签。
     */
    private String resolveRoleLabel(String role) {
        return "admin".equals(role) ? "管理员" : "医生";
    }

    /**
     * 归一化任务类型，只允许已知模块或 hemorrhage。
     */
    private String normalizeTaskType(String taskType) {
        String normalizedTaskType = normalizeText(taskType);
        if ("hemorrhage".equals(normalizedTaskType) || MockQualityAnalysisSupport.isSupportedTaskType(normalizedTaskType)) {
            return normalizedTaskType;
        }
        return null;
    }

    /**
     * 归一化异常项名称。
     */
    private String normalizeIssueType(String issueType) {
        String normalizedIssueType = normalizeText(issueType);
        if (normalizedIssueType == null) {
            throw new IllegalArgumentException("异常项名称不能为空");
        }
        return normalizedIssueType;
    }

    /**
     * 归一化页码。
     */
    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 归一化分页大小。
     */
    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    /**
     * 去空格并把空字符串转为 null。
     */
    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    /**
     * 与 normalizeText 同义，保留给描述字段使用。
     */
    private String trimToNull(String value) {
        return normalizeText(value);
    }
}

