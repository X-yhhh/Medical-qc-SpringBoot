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
    // 应用层仅做入口编排，实际认证和注册规则由 AuthServiceImpl 承担。
    private final AuthServiceImpl authService;

    public AuthApplicationService(AuthServiceImpl authService) {
        this.authService = authService;
    }

    /**
     * 登录应用层入口。
     * 数据链路：AuthController -> AuthApplicationService.login -> AuthServiceImpl.login -> UserMapper。
     */
    public User login(String username, String password, String role) {
        return authService.login(username, password, role);
    }

    /**
     * 注册应用层入口。
     * 这里保持参数展开，方便控制器层直接传入请求对象中的字段。
     */
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

