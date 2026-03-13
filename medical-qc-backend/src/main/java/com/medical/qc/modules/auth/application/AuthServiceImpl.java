package com.medical.qc.modules.auth.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.medical.qc.common.AuthRole;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Service
public class AuthServiceImpl {

    @Autowired
    private UserMapper userMapper;

    public User login(String username, String password, String role) {
        AuthRole authRole = AuthRole.fromCode(role);
        String usernameOrEmail = trimToNull(username);
        String rawPassword = password;

        if (authRole == null || usernameOrEmail == null || rawPassword == null || rawPassword.isBlank()) {
            return null;
        }

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_id", authRole.getId())
                .and(wrapper -> wrapper.eq("username", usernameOrEmail)
                .or()
                .eq("email", usernameOrEmail));
        User user = userMapper.selectOne(queryWrapper);

        if (user == null) {
            return null;
        }

        if (Boolean.FALSE.equals(user.getIsActive())) {
            return null;
        }

        if (!authRole.matchesRoleId(user.getRoleId())) {
            return null;
        }

        if (!verifyPassword(rawPassword, user.getPasswordHash())) {
            return null;
        }

        return user;
    }

    public String register(String username, String email, String password, String fullName, String hospital,
            String department, String role) {
        AuthRole authRole = AuthRole.fromCode(role);
        String normalizedUsername = trimToNull(username);
        String normalizedEmail = trimToNull(email);
        String normalizedPassword = password;
        String normalizedFullName = trimToNull(fullName);
        String normalizedHospital = trimToNull(hospital);
        String normalizedDepartment = trimToNull(department);

        if (authRole == null) {
            return "请选择正确的身份";
        }

        if (normalizedUsername == null || normalizedEmail == null || normalizedPassword == null || normalizedPassword.isBlank()) {
            return "请完整填写用户名、邮箱和密码";
        }

        if (normalizedFullName == null) {
            return "请输入真实姓名";
        }

        if (normalizedHospital == null) {
            return "请输入医院名称";
        }

        if (AuthRole.DOCTOR == authRole && normalizedDepartment == null) {
            return "医生身份需要填写科室";
        }

        QueryWrapper<User> usernameWrapper = new QueryWrapper<>();
        usernameWrapper.eq("username", normalizedUsername)
                .eq("role_id", authRole.getId());
        if (userMapper.selectOne(usernameWrapper) != null) {
            return "当前身份下用户名已存在";
        }

        QueryWrapper<User> emailWrapper = new QueryWrapper<>();
        emailWrapper.eq("email", normalizedEmail);
        if (userMapper.selectOne(emailWrapper) != null) {
            return "邮箱已存在";
        }

        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        user.setPasswordHash(hashPassword(normalizedPassword));
        user.setFullName(normalizedFullName);
        user.setHospital(normalizedHospital);
        user.setDepartment(normalizedDepartment);
        user.setRoleId(authRole.getId());
        user.setIsActive(true);

        userMapper.insert(user);
        return "success";
    }

    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private boolean verifyPassword(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }

        return hashPassword(raw).equals(hashed);
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

