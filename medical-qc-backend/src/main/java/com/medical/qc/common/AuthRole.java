package com.medical.qc.common;

import java.util.Arrays;

/**
 * 认证角色枚举。
 * 数据链路：前端 role 编码 -> AuthRole -> role_id -> 用户查询、会话鉴权与菜单权限控制。
 */
public enum AuthRole {
    // 管理员：负责账号治理、规则配置和全局查看。
    ADMIN(1, "admin", "管理员"),
    // 医生：负责影像质控与异常处理。
    DOCTOR(2, "doctor", "医生");

    // 数据库存储的角色主键值。
    private final int id;
    // 前后端交互统一使用的角色编码。
    private final String code;
    // 页面和接口展示使用的中文角色名。
    private final String displayName;

    /**
     * 初始化角色枚举元数据。
     */
    AuthRole(int id, String code, String displayName) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
    }

    /**
     * 返回数据库中的角色 ID。
     */
    public int getId() {
        return id;
    }

    /**
     * 返回前后端统一角色编码。
     */
    public String getCode() {
        return code;
    }

    /**
     * 返回中文展示名。
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 判断当前枚举是否与数据库角色 ID 匹配。
     */
    public boolean matchesRoleId(Integer roleId) {
        return roleId != null && roleId == id;
    }

    /**
     * 根据前端角色编码解析枚举。
     * 解析失败返回 null，交由调用方决定提示信息。
     */
    public static AuthRole fromCode(String code) {
        if (code == null) {
            return null;
        }

        // 统一忽略大小写和首尾空格，避免前端传值差异导致角色识别失败。
        return Arrays.stream(values())
                .filter(role -> role.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(null);
    }

    /**
     * 根据数据库 role_id 解析枚举；空值默认回退到医生。
     */
    public static AuthRole fromRoleId(Integer roleId) {
        if (roleId == null) {
            return DOCTOR;
        }

        // 这里返回默认医生角色，保证旧数据或脏数据在展示时仍有可解释的角色标签。
        return Arrays.stream(values())
                .filter(role -> role.id == roleId)
                .findFirst()
                .orElse(DOCTOR);
    }
}

