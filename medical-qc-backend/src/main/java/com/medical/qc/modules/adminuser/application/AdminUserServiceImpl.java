package com.medical.qc.modules.adminuser.application;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.medical.qc.bean.AdminUserUpdateReq;
import com.medical.qc.common.AuthRole;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.auth.persistence.mapper.UserMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 管理员用户与权限管理服务实现。
 */
@Service
public class AdminUserServiceImpl {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Autowired
    private UserMapper userMapper;

    public Map<String, Object> getUserPage(int page, int limit, String keyword, String role, Boolean active) {
        Page<User> userPage = new Page<>(normalizePage(page), normalizeLimit(limit));
        QueryWrapper<User> queryWrapper = buildUserQueryWrapper(keyword, role, active);
        queryWrapper.orderByDesc("created_at");

        Page<User> pageResult = userMapper.selectPage(userPage, queryWrapper);
        List<User> allUsers = userMapper.selectList(new QueryWrapper<User>().orderByDesc("created_at"));

        Map<String, Object> response = new HashMap<>();
        response.put("items", pageResult.getRecords().stream().map(this::toUserItem).toList());
        response.put("total", pageResult.getTotal());
        response.put("page", (int) pageResult.getCurrent());
        response.put("limit", (int) pageResult.getSize());
        response.put("pages", pageResult.getPages());
        response.put("summary", buildSummary(allUsers));
        return response;
    }

    public Map<String, Object> updateUser(Long operatorId, Long userId, AdminUserUpdateReq request) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }

        if (request == null) {
            throw new IllegalArgumentException("更新内容不能为空");
        }

        AuthRole nextRole = resolveTargetRole(request.getRole(), user.getRoleId());
        boolean nextActive = request.getIsActive() == null ? Boolean.TRUE.equals(user.getIsActive()) : request.getIsActive();

        validateSelfProtection(operatorId, user, nextActive);
        validateUsernameAvailability(user.getUsername(), nextRole.getId(), user.getId());

        user.setFullName(trimToNull(request.getFullName()));
        user.setHospital(trimToNull(request.getHospital()));
        user.setDepartment(trimToNull(request.getDepartment()));
        user.setRoleId(nextRole.getId());
        user.setIsActive(nextActive);
        userMapper.updateById(user);

        return toUserItem(userMapper.selectById(userId));
    }

    private QueryWrapper<User> buildUserQueryWrapper(String keyword, String role, Boolean active) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper.like("username", normalizedKeyword)
                    .or().like("full_name", normalizedKeyword)
                    .or().like("email", normalizedKeyword)
                    .or().like("hospital", normalizedKeyword)
                    .or().like("department", normalizedKeyword));
        }

        AuthRole authRole = AuthRole.fromCode(role);
        if (authRole != null) {
            queryWrapper.eq("role_id", authRole.getId());
        }

        if (active != null) {
            queryWrapper.eq("is_active", active);
        }

        return queryWrapper;
    }

    private Map<String, Object> buildSummary(List<User> users) {
        long totalUsers = users.size();
        long activeUsers = users.stream().filter(user -> Boolean.TRUE.equals(user.getIsActive())).count();
        long adminUsers = users.stream().filter(user -> AuthRole.ADMIN.matchesRoleId(user.getRoleId())).count();
        long doctorUsers = users.stream().filter(user -> AuthRole.DOCTOR.matchesRoleId(user.getRoleId())).count();

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalUsers", totalUsers);
        summary.put("activeUsers", activeUsers);
        summary.put("inactiveUsers", totalUsers - activeUsers);
        summary.put("adminUsers", adminUsers);
        summary.put("doctorUsers", doctorUsers);
        return summary;
    }

    private Map<String, Object> toUserItem(User user) {
        AuthRole authRole = AuthRole.fromRoleId(user.getRoleId());

        Map<String, Object> item = new HashMap<>();
        item.put("id", user.getId());
        item.put("username", user.getUsername());
        item.put("email", user.getEmail());
        item.put("fullName", user.getFullName());
        item.put("hospital", user.getHospital());
        item.put("department", user.getDepartment());
        item.put("role", authRole.getCode());
        item.put("roleId", authRole.getId());
        item.put("roleLabel", authRole.getDisplayName());
        item.put("isActive", Boolean.TRUE.equals(user.getIsActive()));
        item.put("createdAt", user.getCreatedAt() == null ? "--" : user.getCreatedAt().format(DATE_TIME_FORMATTER));
        item.put("updatedAt", user.getUpdatedAt() == null ? "--" : user.getUpdatedAt().format(DATE_TIME_FORMATTER));
        return item;
    }

    private AuthRole resolveTargetRole(String role, Integer currentRoleId) {
        if (!StringUtils.hasText(role)) {
            return AuthRole.fromRoleId(currentRoleId);
        }

        AuthRole authRole = AuthRole.fromCode(role);
        if (authRole == null) {
            throw new IllegalArgumentException("目标角色不合法");
        }

        return authRole;
    }

    private void validateSelfProtection(Long operatorId, User user, boolean nextActive) {
        if (!Objects.equals(operatorId, user.getId())) {
            return;
        }

        if (!nextActive) {
            throw new IllegalArgumentException("不能停用当前登录账号");
        }
    }

    private void validateUsernameAvailability(String username, Integer roleId, Long excludeUserId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username)
                .eq("role_id", roleId)
                .ne(excludeUserId != null, "id", excludeUserId);

        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new IllegalArgumentException("目标身份下已存在同名用户名");
        }
    }

    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

