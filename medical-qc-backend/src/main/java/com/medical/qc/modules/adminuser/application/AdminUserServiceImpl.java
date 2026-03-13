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
    // 管理页面统一使用这个格式回显用户创建/更新时间。
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    // 用户数据全部复用认证模块的 users 表。
    @Autowired
    private UserMapper userMapper;

    /**
     * 获取用户分页列表。
     * 数据链路：控制器筛选参数 -> QueryWrapper -> users 表分页查询 -> 前端表格与摘要数据。
     */
    public Map<String, Object> getUserPage(int page, int limit, String keyword, String role, Boolean active) {
        // 页码和页大小先归一化，避免管理员传入极端值影响查询。
        Page<User> userPage = new Page<>(normalizePage(page), normalizeLimit(limit));
        QueryWrapper<User> queryWrapper = buildUserQueryWrapper(keyword, role, active);
        queryWrapper.orderByDesc("created_at");

        // 分页结果用于表格；全量结果用于统计摘要。
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

    /**
     * 更新指定用户。
     * 数据链路：管理员修改表单 -> updateUser -> 自保护校验 -> users 表 update -> 返回最新用户视图。
     */
    public Map<String, Object> updateUser(Long operatorId, Long userId, AdminUserUpdateReq request) {
        if (userId == null) {
            throw new IllegalArgumentException("用户 ID 不能为空");
        }

        // 先确认目标用户存在。
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new IllegalArgumentException("目标用户不存在");
        }

        // 后端不接受空更新体，避免误提交导致角色/状态推导混乱。
        if (request == null) {
            throw new IllegalArgumentException("更新内容不能为空");
        }

        // 未传 role / isActive 时保留原值，只更新管理员明确修改的内容。
        AuthRole nextRole = resolveTargetRole(request.getRole(), user.getRoleId());
        boolean nextActive = request.getIsActive() == null ? Boolean.TRUE.equals(user.getIsActive()) : request.getIsActive();

        // 当前登录管理员不能把自己停用，避免会话被锁死。
        validateSelfProtection(operatorId, user, nextActive);
        // 若切换用户角色，需要保证目标角色下用户名仍唯一。
        validateUsernameAvailability(user.getUsername(), nextRole.getId(), user.getId());

        // 仅更新用户管理页允许维护的字段。
        user.setFullName(trimToNull(request.getFullName()));
        user.setHospital(trimToNull(request.getHospital()));
        user.setDepartment(trimToNull(request.getDepartment()));
        user.setRoleId(nextRole.getId());
        user.setIsActive(nextActive);
        userMapper.updateById(user);

        return toUserItem(userMapper.selectById(userId));
    }

    /**
     * 构建分页查询条件。
     */
    private QueryWrapper<User> buildUserQueryWrapper(String keyword, String role, Boolean active) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();

        if (StringUtils.hasText(keyword)) {
            // 关键字支持命中用户名、姓名、邮箱、医院和科室。
            String normalizedKeyword = keyword.trim();
            queryWrapper.and(wrapper -> wrapper.like("username", normalizedKeyword)
                    .or().like("full_name", normalizedKeyword)
                    .or().like("email", normalizedKeyword)
                    .or().like("hospital", normalizedKeyword)
                    .or().like("department", normalizedKeyword));
        }

        AuthRole authRole = AuthRole.fromCode(role);
        if (authRole != null) {
            // 角色筛选优先转换为 role_id，保证和数据库一致。
            queryWrapper.eq("role_id", authRole.getId());
        }

        if (active != null) {
            queryWrapper.eq("is_active", active);
        }

        return queryWrapper;
    }

    /**
     * 统计用户列表摘要。
     */
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

    /**
     * 将 User 实体转换为前端用户管理页所需字段。
     */
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

    /**
     * 解析目标角色；空值时沿用当前角色。
     */
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

    /**
     * 防止当前登录管理员把自己停用。
     */
    private void validateSelfProtection(Long operatorId, User user, boolean nextActive) {
        if (!Objects.equals(operatorId, user.getId())) {
            return;
        }

        if (!nextActive) {
            throw new IllegalArgumentException("不能停用当前登录账号");
        }
    }

    /**
     * 校验目标角色下用户名唯一。
     */
    private void validateUsernameAvailability(String username, Integer roleId, Long excludeUserId) {
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("username", username)
                .eq("role_id", roleId)
                .ne(excludeUserId != null, "id", excludeUserId);

        if (userMapper.selectCount(queryWrapper) > 0) {
            throw new IllegalArgumentException("目标身份下已存在同名用户名");
        }
    }

    /**
     * 归一化页码。
     */
    private int normalizePage(int page) {
        return Math.max(page, 1);
    }

    /**
     * 归一化分页大小，防止一次取太多用户。
     */
    private int normalizeLimit(int limit) {
        return Math.max(1, Math.min(limit, 50));
    }

    /**
     * 去空格并把空字符串转为 null。
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

