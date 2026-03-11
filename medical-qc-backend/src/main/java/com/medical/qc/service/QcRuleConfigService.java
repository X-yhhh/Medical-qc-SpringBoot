package com.medical.qc.service;

import com.medical.qc.bean.QcRuleConfigSaveReq;
import com.medical.qc.entity.QcRuleConfig;

import java.util.Map;

/**
 * 质控规则配置服务。
 */
public interface QcRuleConfigService {
    Map<String, Object> getRulePage(int page, int limit, String keyword, String taskType, Boolean enabled);

    Map<String, Object> createRule(Long operatorId, QcRuleConfigSaveReq request);

    Map<String, Object> updateRule(Long operatorId, Long ruleId, QcRuleConfigSaveReq request);

    QcRuleConfig resolveRule(String taskType, String issueType);
}
