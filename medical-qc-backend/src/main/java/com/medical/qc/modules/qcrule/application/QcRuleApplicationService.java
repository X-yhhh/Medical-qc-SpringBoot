package com.medical.qc.modules.qcrule.application;

import com.medical.qc.bean.QcRuleConfigSaveReq;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 质控规则中心应用服务。
 */
@Service
public class QcRuleApplicationService {
    private final QcRuleConfigServiceImpl qcRuleConfigService;

    public QcRuleApplicationService(QcRuleConfigServiceImpl qcRuleConfigService) {
        this.qcRuleConfigService = qcRuleConfigService;
    }

    public Map<String, Object> getRulePage(int page, int limit, String keyword, String taskType, Boolean enabled) {
        return qcRuleConfigService.getRulePage(page, limit, keyword, taskType, enabled);
    }

    public Map<String, Object> createRule(Long operatorId, QcRuleConfigSaveReq request) {
        return qcRuleConfigService.createRule(operatorId, request);
    }

    public Map<String, Object> updateRule(Long operatorId, Long ruleId, QcRuleConfigSaveReq request) {
        return qcRuleConfigService.updateRule(operatorId, ruleId, request);
    }
}

