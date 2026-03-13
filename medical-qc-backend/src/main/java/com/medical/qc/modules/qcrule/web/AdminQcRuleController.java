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
    private final QcRuleApplicationService qcRuleApplicationService;
    private final SessionUserSupport sessionUserSupport;

    public AdminQcRuleController(QcRuleApplicationService qcRuleApplicationService,
                                 SessionUserSupport sessionUserSupport) {
        this.qcRuleApplicationService = qcRuleApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    @GetMapping
    public ResponseEntity<?> getRulePage(@RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "limit", defaultValue = "10") int limit,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @RequestParam(value = "task_type", required = false) String taskType,
                                         @RequestParam(value = "enabled", required = false) Boolean enabled,
                                         HttpSession session) {
        requireAdmin(session);
        return ResponseEntity.ok(qcRuleApplicationService.getRulePage(page, limit, keyword, taskType, enabled));
    }

    @PostMapping
    public ResponseEntity<?> createRule(@RequestBody(required = false) QcRuleConfigSaveReq request,
                                        HttpSession session) {
        User currentUser = requireAdmin(session);
        return ResponseEntity.ok(qcRuleApplicationService.createRule(currentUser.getId(), request));
    }

    @PutMapping("/{ruleId}")
    public ResponseEntity<?> updateRule(@PathVariable("ruleId") Long ruleId,
                                        @RequestBody(required = false) QcRuleConfigSaveReq request,
                                        HttpSession session) {
        User currentUser = requireAdmin(session);
        return ResponseEntity.ok(qcRuleApplicationService.updateRule(currentUser.getId(), ruleId, request));
    }

    private User requireAdmin(HttpSession session) {
        User currentUser = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireAdmin(currentUser);
        return currentUser;
    }
}

