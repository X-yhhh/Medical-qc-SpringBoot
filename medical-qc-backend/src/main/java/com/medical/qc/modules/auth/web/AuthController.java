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

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final AuthApplicationService authApplicationService;
    private final SessionUserSupport sessionUserSupport;

    public AuthController(AuthApplicationService authApplicationService,
                          SessionUserSupport sessionUserSupport) {
        this.authApplicationService = authApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req, HttpSession session) {
        User user = authApplicationService.login(req.getUsername(), req.getPassword(), req.getRole());
        if (user != null) {
            user.setPasswordHash(null);
            user.setAccessToken(null);
            session.setAttribute("user", user);

            return ResponseEntity.ok(buildUserResponse(user));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Collections.singletonMap("detail", "账号、密码或身份不正确"));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out successfully"));
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        return ResponseEntity.ok(buildUserResponse(user));
    }

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

    private Map<String, Object> buildUserResponse(User user) {
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

