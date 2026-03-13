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

    // 认证模块唯一的数据入口，负责账号查询、去重校验和用户落库。
    @Autowired
    private UserMapper userMapper;

    /**
     * 执行登录认证。
     * 数据链路：登录表单字段 -> 角色解析 -> users 表查询 -> 密码哈希比对 -> 返回脱敏前用户实体。
     */
    public User login(String username, String password, String role) {
        // 先把前端角色编码转换为统一枚举，后续查询直接对齐数据库 role_id。
        AuthRole authRole = AuthRole.fromCode(role);
        // 用户名和邮箱共用一个输入框，这里统一裁剪空格后复用同一变量。
        String usernameOrEmail = trimToNull(username);
        // 保留原始密码，后续统一走哈希校验逻辑。
        String rawPassword = password;

        // 角色、登录标识或密码任一无效时，直接拒绝登录，避免继续访问数据库。
        if (authRole == null || usernameOrEmail == null || rawPassword == null || rawPassword.isBlank()) {
            return null;
        }

        // 登录时同时约束身份和用户名/邮箱，避免相同账号在不同角色间串用。
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("role_id", authRole.getId())
                .and(wrapper -> wrapper.eq("username", usernameOrEmail)
                .or()
                .eq("email", usernameOrEmail));
        User user = userMapper.selectOne(queryWrapper);

        // 未查到用户说明账号不存在或身份不匹配。
        if (user == null) {
            return null;
        }

        // 禁用账号不允许继续登录。
        if (Boolean.FALSE.equals(user.getIsActive())) {
            return null;
        }

        // 双重校验角色，防止脏数据或非法请求绕过前置约束。
        if (!authRole.matchesRoleId(user.getRoleId())) {
            return null;
        }

        // 使用统一哈希逻辑校验密码，避免控制器层接触密码算法细节。
        if (!verifyPassword(rawPassword, user.getPasswordHash())) {
            return null;
        }

        // 返回数据库用户快照，控制器层会继续脱敏并写入 Session。
        return user;
    }

    /**
     * 执行注册。
     * 数据链路：注册表单 -> 字段规范化 -> 唯一性校验 -> 密码哈希 -> users 表 insert。
     */
    public String register(String username, String email, String password, String fullName, String hospital,
            String department, String role) {
        // 注册流程先固定角色规则，后续必填项和 role_id 都依赖这个枚举。
        AuthRole authRole = AuthRole.fromCode(role);
        // 所有文本字段先统一去空格，确保唯一性校验和空值判断一致。
        String normalizedUsername = trimToNull(username);
        String normalizedEmail = trimToNull(email);
        String normalizedPassword = password;
        String normalizedFullName = trimToNull(fullName);
        String normalizedHospital = trimToNull(hospital);
        String normalizedDepartment = trimToNull(department);

        // 角色无效时无需继续执行后续校验。
        if (authRole == null) {
            return "请选择正确的身份";
        }

        // 用户名、邮箱和密码是所有身份的最小注册字段。
        if (normalizedUsername == null || normalizedEmail == null || normalizedPassword == null || normalizedPassword.isBlank()) {
            return "请完整填写用户名、邮箱和密码";
        }

        // 真实姓名用于审核和质控流程中的人员标识。
        if (normalizedFullName == null) {
            return "请输入真实姓名";
        }

        // 医疗场景下医院信息是必填主数据。
        if (normalizedHospital == null) {
            return "请输入医院名称";
        }

        // 医生角色需要明确科室，管理员则允许为空。
        if (AuthRole.DOCTOR == authRole && normalizedDepartment == null) {
            return "医生身份需要填写科室";
        }

        // 用户名在同一角色下唯一，允许不同角色存在同名账号。
        QueryWrapper<User> usernameWrapper = new QueryWrapper<>();
        usernameWrapper.eq("username", normalizedUsername)
                .eq("role_id", authRole.getId());
        if (userMapper.selectOne(usernameWrapper) != null) {
            return "当前身份下用户名已存在";
        }

        // 邮箱在全系统范围内唯一，避免一个邮箱绑定多个账号。
        QueryWrapper<User> emailWrapper = new QueryWrapper<>();
        emailWrapper.eq("email", normalizedEmail);
        if (userMapper.selectOne(emailWrapper) != null) {
            return "邮箱已存在";
        }

        // 构建待落库的用户实体，默认启用账号。
        User user = new User();
        user.setUsername(normalizedUsername);
        user.setEmail(normalizedEmail);
        // 密码只以哈希形式保存，避免明文进入数据库。
        user.setPasswordHash(hashPassword(normalizedPassword));
        user.setFullName(normalizedFullName);
        user.setHospital(normalizedHospital);
        user.setDepartment(normalizedDepartment);
        user.setRoleId(authRole.getId());
        user.setIsActive(true);

        userMapper.insert(user);
        return "success";
    }

    /**
     * 使用 SHA-256 生成密码摘要。
     */
    private String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(encodedhash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 校验原始密码与数据库哈希是否匹配。
     */
    private boolean verifyPassword(String raw, String hashed) {
        if (raw == null || hashed == null) {
            return false;
        }

        return hashPassword(raw).equals(hashed);
    }

    /**
     * 去掉字符串首尾空格，并把空字符串统一转为 null。
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * 把字节数组转换为十六进制字符串，供数据库持久化密码摘要。
     */
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder(2 * hash.length);
        for (int i = 0; i < hash.length; i++) {
            // 每个字节都转成两位十六进制，不足两位时左补 0。
            String hex = Integer.toHexString(0xff & hash[i]);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}

