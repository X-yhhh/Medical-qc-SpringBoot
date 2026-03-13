package com.medical.qc.modules.qcrule.web;

import com.medical.qc.bean.QcRuleConfigSaveReq;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qcrule.application.QcRuleApplicationService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员质控规则中心控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/qc-rules")
public class AdminQcRuleController {
    // 应用服务负责编排规则分页、创建和更新。
    private final QcRuleApplicationService qcRuleApplicationService;
    // Session 辅助组件负责管理员权限校验。
    private final SessionUserSupport sessionUserSupport;

    public AdminQcRuleController(QcRuleApplicationService qcRuleApplicationService,
                                 SessionUserSupport sessionUserSupport) {
        this.qcRuleApplicationService = qcRuleApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 分页查询质控规则。
     */
    @GetMapping
    public ResponseEntity<?> getRulePage(@RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "limit", defaultValue = "10") int limit,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "task_type", required = false) String taskType,
                                         @RequestParam(value = "enabled", required = false) Boolean enabled,
                                         HttpSession session) {
        requireAdmin(session);
        // 筛选条件直接透传给应用服务，由服务层统一做分页和 taskType 归一化。
        return ResponseEntity.ok(qcRuleApplicationService.getRulePage(page, limit, keyword, taskType, enabled));
    }

    /**
     * 新增规则。
     */
    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody(required = false) QcRuleConfigSaveReq request,
                                        HttpSession session) {
        User currentUser = requireAdmin(session);
        // updatedBy 取当前管理员 ID，用于规则维护审计。
        return ResponseEntity.ok(qcRuleApplicationService.createRule(currentUser.getId(), request));
    }

    /**
     * 更新规则。
     */
    @PutMapping("/{ruleId}")
    public ResponseEntity<?> updateRule(@PathVariable("ruleId") Long ruleId,
                                        @RequestBody(required = false) QcRuleConfigSaveReq request,
                                        HttpSession session) {
        User currentUser = requireAdmin(session);
        return ResponseEntity.ok(qcRuleApplicationService.updateRule(currentUser.getId(), ruleId, request));
    }

    /**
     * 校验当前会话属于管理员。
     */
    private User requireAdmin(HttpSession session) {
        User currentUser = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireAdmin(currentUser);
        return currentUser;
    }
}

