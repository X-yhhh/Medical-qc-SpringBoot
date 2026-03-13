package com.medical.qc.bean;

/**
 * 登录请求载体。
 * 数据链路：登录页表单 -> Jackson 反序列化为 LoginReq -> AuthController -> AuthApplicationService -> AuthServiceImpl。
 */
public class LoginReq {
    // 用户在登录页输入的标识，可为用户名或邮箱。
    private String username;
    // 原始登录密码，后续会在认证服务中做哈希比对。
    private String password;
    // 前端选择的身份编码，用于限定 role_id 查询范围。
    private String role;

    /**
     * 返回登录标识，供认证服务按用户名/邮箱双通道检索账号。
     */
    public String getUsername() {
        return username;
    }

    /**
     * 接收前端提交的用户名或邮箱。
     */
    public void setUsername(String username) {
        this.username = username;
    }

    /**
     * 返回前端输入的明文密码。
     */
    public String getPassword() {
        return password;
    }

    /**
     * 记录登录表单提交的密码值。
     */
    public void setPassword(String password) {
        this.password = password;
    }

    /**
     * 返回当前登录尝试选择的身份编码。
     */
    public String getRole() {
        return role;
    }

    /**
     * 记录登录页传入的身份编码，避免医生/管理员串用账号。
     */
    public void setRole(String role) {
        this.role = role;
    }
}

