package com.medical.qc.modules.adminuser.web;

import com.medical.qc.bean.AdminUserUpdateReq;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.adminuser.application.AdminUserApplicationService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 管理员用户与权限管理控制器。
 */
@RestController
@RequestMapping("/api/v1/admin/users")
public class AdminUserController {
    private final AdminUserApplicationService adminUserApplicationService;
    private final SessionUserSupport sessionUserSupport;

    public AdminUserController(AdminUserApplicationService adminUserApplicationService,
                               SessionUserSupport sessionUserSupport) {
        this.adminUserApplicationService = adminUserApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 获取用户分页列表。
     *
     * @param page 页码
     * @param limit 每页数量
     * @param keyword 搜索关键字
     * @param role 角色筛选
     * @param active 启用状态筛选
     * @param session 当前会话
     * @return 用户分页结果
     */
    @GetMapping
    public ResponseEntity<?> getUsers(
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "limit", defaultValue = "10") int limit,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "active", required = false) Boolean active,
            HttpSession session) {
        User currentUser = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireAdmin(currentUser);
        return ResponseEntity.ok(adminUserApplicationService.getUserPage(page, limit, keyword, role, active));
    }

    /**
     * 更新指定用户信息。
     *
     * @param userId 目标用户 ID
     * @param request 更新请求体
     * @param session 当前会话
     * @return 更新结果
     */
    @RequestMapping(value = "/{userId}", method = {RequestMethod.PATCH, RequestMethod.PUT})
    public ResponseEntity<?> updateUser(
            @PathVariable("userId") Long userId,
            @RequestBody(required = false) AdminUserUpdateReq request,
            HttpSession session) {
        User currentUser = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireAdmin(currentUser);
        return ResponseEntity.ok(adminUserApplicationService.updateUser(currentUser.getId(), userId, request));
    }
}

