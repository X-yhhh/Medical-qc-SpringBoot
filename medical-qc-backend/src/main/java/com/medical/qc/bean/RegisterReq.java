package com.medical.qc.bean;

import com.fasterxml.jackson.annotation.JsonAlias;

/**
 * 注册请求载体。
 * 数据链路：注册页表单 -> RegisterReq -> AuthController.register -> AuthApplicationService -> AuthServiceImpl。
 */
public class RegisterReq {
    // 登录用户名，注册后与角色一起形成唯一登录标识。
    private String username;
    // 注册邮箱，同时用于邮箱唯一性校验与后续登录。
    private String email;
    // 原始密码，后续在服务层做 SHA-256 哈希。
    private String password;
    // 兼容前端 full_name 字段和后端 fullName 属性的映射。
    @JsonAlias("full_name")
    private String fullName;
    // 用户所属医院，作为基础机构信息保存。
    private String hospital;
    // 医生账号必填，管理员账号可选。
    private String department;
    // 前端选择的身份编码，决定 role_id 和校验规则。
    private String role;

    /**
     * 返回注册用户名。
     */
    public String getUsername() {
        return username;
    }

    /**
     * 接收注册用户名。
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 返回注册邮箱。
     */
    public String getEmail() {
        return email;
    }

    /**
     * 接收注册邮箱。
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * 返回原始密码，供服务层做哈希处理。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 接收前端填写的密码。
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 返回真实姓名字段。
     */
    public String getFullName() {
        return fullName;
    }

    /**
     * 接收真实姓名，兼容 full_name 别名映射。
     */
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    /**
     * 返回医院名称。
     */
    public String getHospital() {
        return hospital;
    }

    /**
     * 接收医院名称。
     */
    public void setHospital(String hospital) {
        this.hospital = hospital;
    }

    /**
     * 返回科室或管理单元信息。
     */
    public String getDepartment() {
        return department;
    }

    /**
     * 接收科室或管理单元信息。
     */
    public void setDepartment(String department) {
        this.department = department;
    }

    /**
     * 返回注册身份编码。
     */
    public String getRole() {
        return role;
    }

    /**
     * 接收前端选择的医生/管理员身份。
     */
    public void setRole(String role) {
        this.role = role;
    }
}

