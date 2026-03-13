package com.medical.qc.modules.auth.application;

import com.medical.qc.modules.auth.persistence.entity.User;
import org.springframework.stereotype.Service;

/**
 * 认证应用服务。
 *
 * <p>当前统一承接登录与注册入口，
 * 后续可在此层扩展认证策略、审计和会话编排。</p>
 */
@Service
public class AuthApplicationService {
    private final AuthServiceImpl authService;

    public AuthApplicationService(AuthServiceImpl authService) {
        this.authService = authService;
    }

    public User login(String username, String password, String role) {
        return authService.login(username, password, role);
    }

    public String register(String username,
                           String email,
                           String password,
                           String fullName,
                           String hospital,
                           String department,
                           String role) {
        return authService.register(username, email, password, fullName, hospital, department, role);
    }
}

