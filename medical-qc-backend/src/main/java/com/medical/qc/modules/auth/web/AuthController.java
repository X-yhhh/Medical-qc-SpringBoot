package com.medical.qc.modules.auth.web;

import com.medical.qc.bean.LoginReq;
import com.medical.qc.bean.RegisterReq;
import com.medical.qc.common.AuthRole;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.application.AuthApplicationService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证模块控制器。
 * 数据链路：登录/注册页面 -> /api/v1/auth/* -> AuthApplicationService / SessionUserSupport -> Session 与用户数据 -> JSON 响应。
 */
@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    // 登录与注册都通过应用服务编排，控制器只处理 HTTP 和 Session。
    private final AuthApplicationService authApplicationService;
    // 当前会话用户解析与权限检查由公共辅助组件统一完成。
    private final SessionUserSupport sessionUserSupport;

    public AuthController(AuthApplicationService authApplicationService,
                          SessionUserSupport sessionUserSupport) {
        this.authApplicationService = authApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 处理登录请求。
     * 成功后把用户快照写入 Session，失败则返回 400 提示。
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req, HttpSession session) {
        // 应用服务负责账号、身份和密码三者的联合校验。
        User user = authApplicationService.login(req.getUsername(), req.getPassword(), req.getRole());
        if (user != null) {
            // 会话中不保留敏感字段，避免后续序列化或日志误暴露密码与旧 token。
            user.setPasswordHash(null);
            user.setAccessToken(null);
            // 认证成功后把脱敏用户快照写入 Session，供后续接口识别登录态。
            session.setAttribute("user", user);

            return ResponseEntity.ok(buildUserResponse(user));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("detail", "账号、密码或身份不正确"));
    }
    
    /**
     * 主动登出并销毁当前 Session。
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out successfully"));
    }

    /**
     * 返回当前登录用户。
     * 这里会触发 SessionUserSupport 的二次校验，确保禁用或改权账号无法继续使用旧会话。
     */
    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        return ResponseEntity.ok(buildUserResponse(user));
    }

    /**
     * 处理注册请求。
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        String result = authApplicationService.register(
                req.getUsername(),
                req.getEmail(),
                req.getPassword(),
                req.getFullName(),
                req.getHospital(),
                req.getDepartment(),
                req.getRole()
        );

        if ("success".equals(result)) {
            return ResponseEntity.ok(Collections.singletonMap("message", "Register successful"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("detail", result));
    }

    /**
     * 构造前端需要的最小用户视图。
     * 不直接返回 User 实体，避免把数据库字段暴露给页面。
     */
    private Map<String, Object> buildUserResponse(User user) {
        // 根据 role_id 反查展示所需的角色编码和中文标签。
        AuthRole authRole = AuthRole.fromRoleId(user.getRoleId());
        Map<String, Object> response = new HashMap<>();
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("role", authRole.getCode());
        response.put("roleId", authRole.getId());
        response.put("roleLabel", authRole.getDisplayName());
        return response;
    }
}

