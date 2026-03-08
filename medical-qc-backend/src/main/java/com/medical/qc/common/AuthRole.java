package com.medical.qc.common;

import java.util.Arrays;

public enum AuthRole {
    ADMIN(1, "admin", "管理员"),
    DOCTOR(2, "doctor", "医生");

    private final int id;
    private final String code;
    private final String displayName;

    AuthRole(int id, String code, String displayName) {
        this.id = id;
        this.code = code;
        this.displayName = displayName;
    }

    public int getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean matchesRoleId(Integer roleId) {
        return roleId != null && roleId == id;
    }

    public static AuthRole fromCode(String code) {
        if (code == null) {
            return null;
        }

        return Arrays.stream(values())
                .filter(role -> role.code.equalsIgnoreCase(code.trim()))
                .findFirst()
                .orElse(null);
    }

    public static AuthRole fromRoleId(Integer roleId) {
        if (roleId == null) {
            return DOCTOR;
        }

        return Arrays.stream(values())
                .filter(role -> role.id == roleId)
                .findFirst()
                .orElse(DOCTOR);
    }
}
