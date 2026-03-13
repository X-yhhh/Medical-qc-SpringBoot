package com.medical.qc.bean;

/**
 * 管理员更新用户信息请求体。
 */
public class AdminUserUpdateReq {
    // 用户真实姓名。
    private String fullName;
    // 所属医院。
    private String hospital;
    // 科室或管理单元。
    private String department;
    // 目标角色编码。
    private String role;
    // 目标启用状态。
    private Boolean isActive;

    // 以下访问器供管理员页面权限编辑表单绑定。
    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getHospital() {
        return hospital;
    }

    public void setHospital(String hospital) {
        this.hospital = hospital;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean active) {
        isActive = active;
    }
}

