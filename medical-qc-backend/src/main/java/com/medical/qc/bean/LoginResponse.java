package com.medical.qc.bean;

public class LoginResponse {
    private String access_token;
    private String token_type;
    private UserInfo user;

    public LoginResponse(String access_token, String token_type, UserInfo user) {
        this.access_token = access_token;
        this.token_type = token_type;
        this.user = user;
    }

    public String getAccess_token() { return access_token; }
    public void setAccess_token(String access_token) { this.access_token = access_token; }
    public String getToken_type() { return token_type; }
    public void setToken_type(String token_type) { this.token_type = token_type; }
    public UserInfo getUser() { return user; }
    public void setUser(UserInfo user) { this.user = user; }

    public static class UserInfo {
        private String username;
        private String full_name;
        private String role;

        public UserInfo(String username, String full_name, String role) {
            this.username = username;
            this.full_name = full_name;
            this.role = role;
        }

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getFull_name() { return full_name; }
        public void setFull_name(String full_name) { this.full_name = full_name; }
        public String getRole() { return role; }
        public void setRole(String role) { this.role = role; }
    }
}
