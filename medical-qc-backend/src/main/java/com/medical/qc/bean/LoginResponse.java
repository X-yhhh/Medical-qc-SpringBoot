package com.medical.qc.bean;

/**
 * 登录响应载体。
 * 说明：当前会话模式主要返回用户信息，保留 token 字段是为了兼容历史接口结构。
 */
public class LoginResponse {
    // 历史接口中的访问令牌字段，Session 模式下通常为空或兼容值。
    private String access_token;
    // 令牌类型占位字段，便于前端兼容旧的 Bearer 结构。
    private String token_type;
    // 对外暴露的用户概要信息，避免直接返回完整用户实体。
    private UserInfo user;

    /**
     * 构造统一登录响应。
     */
    public LoginResponse(String access_token, String token_type, UserInfo user) {
        this.access_token = access_token;
        this.token_type = token_type;
        this.user = user;
    }

    // 以下访问器主要服务于 Jackson 序列化和历史接口兼容。
    public String getAccess_token() { return access_token; }
    public void setAccess_token(String access_token) { this.access_token = access_token; }
    public String getToken_type() { return token_type; }
    public void setToken_type(String token_type) { this.token_type = token_type; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }

    /**
     * 登录成功后返回给前端的最小用户视图。
     */
    public static class UserInfo {
        // 用于页面展示和后续会话标识的登录名。
        private String username;
        // 页面显示的真实姓名。
        private String full_name;
        // 当前用户的角色编码。
        private String role;

        /**
         * 构造用户概要信息。
         */
        public UserInfo(String username, String full_name, String role) {
            this.username = username;
            this.full_name = full_name;
            this.role = role;
        }

        // 以下访问器供响应序列化与兼容旧字段命名使用。
        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFull_name() { return full_name; }
        public void setFull_name(String full_name) { this.full_name = full_name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}

