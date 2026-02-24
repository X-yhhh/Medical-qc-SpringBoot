package com.medical.qc.controller;

import com.medical.qc.bean.LoginReq;
import com.medical.qc.bean.RegisterReq;
import com.medical.qc.entity.User;
import com.medical.qc.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginReq req, HttpSession session) {
        User user = authService.login(req.getUsername(), req.getPassword());
        if (user != null) {
            user.setPasswordHash(null);
            user.setAccessToken(null);
            // Store user in session
            session.setAttribute("user", user);
            
            // Return user info (no token needed)
            Map<String, Object> response = new HashMap<>();
            response.put("username", user.getUsername());
            response.put("fullName", user.getFullName());
            response.put("role", "doctor");
            
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("detail", "用户名或密码错误"));
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpSession session) {
        session.invalidate();
        return ResponseEntity.ok(Collections.singletonMap("message", "Logged out successfully"));
    }

    @GetMapping("/current")
    public ResponseEntity<?> getCurrentUser(HttpSession session) {
        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Collections.singletonMap("detail", "Not authenticated"));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("username", user.getUsername());
        response.put("fullName", user.getFullName());
        response.put("role", "doctor");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterReq req) {
        String result = authService.register(
                req.getUsername(),
                req.getEmail(),
                req.getPassword(),
                req.getFullName(),
                req.getHospital(),
                req.getDepartment()
        );

        if ("success".equals(result)) {
            return ResponseEntity.ok(Collections.singletonMap("message", "Register successful"));
        }
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Collections.singletonMap("detail", result));
    }
}
