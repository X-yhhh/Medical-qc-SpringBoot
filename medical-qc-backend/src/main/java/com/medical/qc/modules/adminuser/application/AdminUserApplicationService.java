package com.medical.qc.modules.adminuser.application;

import com.medical.qc.bean.AdminUserUpdateReq;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * 管理员用户管理应用服务。
 */
@Service
public class AdminUserApplicationService {
    private final AdminUserServiceImpl adminUserService;

    public AdminUserApplicationService(AdminUserServiceImpl adminUserService) {
        this.adminUserService = adminUserService;
    }

    public Map<String, Object> getUserPage(int page, int limit, String keyword, String role, Boolean active) {
        return adminUserService.getUserPage(page, limit, keyword, role, active);
    }

    public Map<String, Object> updateUser(Long operatorId, Long userId, AdminUserUpdateReq request) {
        return adminUserService.updateUser(operatorId, userId, request);
    }
}

