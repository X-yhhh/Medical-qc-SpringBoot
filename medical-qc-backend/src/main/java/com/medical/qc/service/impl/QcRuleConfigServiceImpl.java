package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.qc.bean.QcRuleConfigSaveReq;
import com.medical.qc.entity.QcRuleConfig;
import com.medical.qc.mapper.QcRuleConfigMapper;
import com.medical.qc.service.QcRuleConfigService;
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
public class QcRuleConfigServiceImpl implements QcRuleConfigService {
    private static final String DEFAULT_ISSUE_TYPE = "DEFAULT";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final QcRuleConfigMapper qcRuleConfigMapper;

    public QcRuleConfigServiceImpl(QcRuleConfigMapper qcRuleConfigMapper) {
        this.qcRuleConfigMapper = qcRuleConfigMapper;
    }

    @Override
    public Map<String, Object> getRulePage(int page, int limit, String keyword, String taskType, Boolean enabled) {
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

    @Override
    public Map<String, Object> createRule(Long operatorId, QcRuleConfigSaveReq request) {
        QcRuleConfig entity = buildValidatedEntity(request, null);
        entity.setUpdatedBy(operatorId);
        qcRuleConfigMapper.insert(entity);
        return toRuleItem(qcRuleConfigMapper.selectById(entity.getId()));
    }

    @Override
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

    @Override
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

    private void validateUniqueness(String taskType, String issueType, Long excludeRuleId) {
        QueryWrapper<QcRuleConfig> queryWrapper = new QueryWrapper<QcRuleConfig>()
                .eq("task_type", taskType)
                .eq("issue_type", issueType)
                .ne(excludeRuleId != null, "id", excludeRuleId);
        if (qcRuleConfigMapper.selectCount(queryWrapper) > 0) {
            throw new IllegalArgumentException("相同模块和异常项的规则已存在");
        }
    }

    private String resolvePriority(String priority) {
        String normalizedPriority = normalizeText(priority);
        if (!Objects.equals("高", normalizedPriority)
                && !Objects.equals("中", normalizedPriority)
                && !Objects.equals("低", normalizedPriority)) {
            throw new IllegalArgumentException("优先级仅支持 高/中/低");
        }
        return normalizedPriority;
    }

    private String resolveResponsibleRole(String responsibleRole) {
        String normalizedRole = normalizeText(responsibleRole);
        if (!Objects.equals("doctor", normalizedRole) && !Objects.equals("admin", normalizedRole)) {
            throw new IllegalArgumentException("责任角色仅支持 doctor/admin");
        }
        return normalizedRole;
    }

    private int resolveSlaHours(Integer slaHours) {
        if (slaHours == null || slaHours < 1 || slaHours > 168) {
            throw new IllegalArgumentException("SLA 时限需在 1-168 小时之间");
        }
        return slaHours;
    }

    private String resolveTaskTypeName(String taskType, String customTaskTypeName) {
        if (StringUtils.hasText(customTaskTypeName)) {
            return customTaskTypeName.trim();
        }

        if ("hemorrhage".equals(taskType)) {
            return "头部出血检测";
        }

        return MockQualityAnalysisSupport.resolveTaskTypeName(taskType);
    }

    private String resolveRoleLabel(String role) {
        return "admin".equals(role) ? "管理员" : "医生";
    }

    private String normalizeTaskType(String taskType) {
        String normalizedTaskType = normalizeText(taskType);
        if ("hemorrhage".equals(normalizedTaskType) || MockQualityAnalysisSupport.isSupportedTaskType(normalizedTaskType)) {
            return normalizedTaskType;
        }
        return null;
    }

    private String normalizeIssueType(String issueType) {
        String normalizedIssueType = normalizeText(issueType);
        if (normalizedIssueType == null) {
            throw new IllegalArgumentException("异常项名称不能为空");
        }
        return normalizedIssueType;
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String trimToNull(String value) {
        return normalizeText(value);
    }
}
