package com.medical.qc.modules.auth.persistence.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户持久化实体。
 * 数据链路：AuthServiceImpl / SessionUserSupport -> UserMapper -> users 表 -> 控制器脱敏后返回前端。
 */
@TableName("users")
public class User implements Serializable {
    // 序列化版本号，保证 Session 中缓存用户对象时结构兼容。
    private static final long serialVersionUID = 1L;

    // 用户主键。
    private Long id;
    // 登录用户名。
    private String username;
    // 登录邮箱，同时承担全局唯一标识之一。
    private String email;
    // SHA-256 密码摘要，不保存明文密码。
    private String passwordHash;
    // 真实姓名，供页面展示和流程追踪。
    private String fullName;
    // 所属医院。
    private String hospital;
    // 所属科室或管理单元。
    private String department;
    // 角色 ID，对应 AuthRole。
    private Integer roleId;
    // 账号是否启用。
    private Boolean isActive;
    // 创建时间。
    private LocalDateTime createdAt;
    // 最近更新时间。
    private LocalDateTime updatedAt;
    // 历史 token 字段，当前 Session 模式下通常为空。
    private String accessToken;

    // 以下标准访问器供 MyBatis 映射、Session 序列化与控制器脱敏处理复用。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

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

    public Integer getRoleId() {
        return roleId;
    }

    public void setRoleId(Integer roleId) {
        this.roleId = roleId;
    }

    public Boolean getIsActive() {
        return isActive;
    }

    public void setIsActive(Boolean isActive) {
        this.isActive = isActive;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }
}

