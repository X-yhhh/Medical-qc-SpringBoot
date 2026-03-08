package com.medical.qc.support;

import com.medical.qc.common.AuthRole;
import com.medical.qc.common.exception.ForbiddenException;
import com.medical.qc.common.exception.UnauthorizedException;
import com.medical.qc.entity.User;
import com.medical.qc.mapper.UserMapper;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Objects;

/**
 * 会话用户辅助组件。
 * 负责从 Session 中解析当前用户，并提供角色判断、访问范围计算等通用能力。
 */
@Component
public class SessionUserSupport {
    private static final String SESSION_USER_KEY = "user";

    @Autowired
    private UserMapper userMapper;

    /**
     * 获取当前登录用户；若会话无用户则抛出未认证异常。
     *
     * @param session 当前会话
     * @return 当前登录用户
     */
    public User requireAuthenticatedUser(HttpSession session) {
        Object sessionUser = session == null ? null : session.getAttribute(SESSION_USER_KEY);
        if (!(sessionUser instanceof User user)) {
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }

        if (user.getId() == null) {
            invalidateSession(session);
            throw new UnauthorizedException("登录状态已失效，请重新登录");
        }

        User latestUser = userMapper.selectById(user.getId());
        if (latestUser == null || Boolean.FALSE.equals(latestUser.getIsActive())) {
            invalidateSession(session);
            throw new UnauthorizedException("账号状态已变更，请重新登录");
        }

        if (isPermissionChanged(user, latestUser)) {
            invalidateSession(session);
            throw new UnauthorizedException("账号权限已变更，请重新登录");
        }

        session.setAttribute(SESSION_USER_KEY, latestUser);
        return latestUser;
    }

    /**
     * 判断数据库中的最新权限是否与当前会话快照一致。
     * 若角色或启停状态发生变更，则要求重新登录，避免旧权限继续生效。
     *
     * @param sessionUser 会话中的用户快照
     * @param latestUser 数据库中的最新用户信息
     * @return 是否发生权限相关变更
     */
    private boolean isPermissionChanged(User sessionUser, User latestUser) {
        return !Objects.equals(sessionUser.getRoleId(), latestUser.getRoleId())
                || !Objects.equals(Boolean.TRUE.equals(sessionUser.getIsActive()), Boolean.TRUE.equals(latestUser.getIsActive()));
    }

    /**
     * 安全销毁会话，避免旧权限继续被后续请求复用。
     *
     * @param session 当前会话
     */
    private void invalidateSession(HttpSession session) {
        if (session == null) {
            return;
        }

        try {
            session.invalidate();
        } catch (IllegalStateException ignored) {
            // 会话已失效时无需额外处理
        }
    }

    /**
     * 校验当前用户是否具备管理员权限。
     *
     * @param user 当前用户
     */
    public void requireAdmin(User user) {
        if (!isAdmin(user)) {
            throw new ForbiddenException("当前账号无权访问管理员功能");
        }
    }

    /**
     * 校验当前用户是否具备医生权限。
     *
     * @param user 当前用户
     */
    public void requireDoctor(User user) {
        if (!isDoctor(user)) {
            throw new ForbiddenException("当前账号无权访问医生功能");
        }
    }

    /**
     * 判断用户是否为管理员。
     *
     * @param user 当前用户
     * @return 是否管理员
     */
    public boolean isAdmin(User user) {
        return user != null && AuthRole.ADMIN.matchesRoleId(user.getRoleId());
    }

    /**
     * 判断用户是否为医生。
     *
     * @param user 当前用户
     * @return 是否医生
     */
    public boolean isDoctor(User user) {
        return user != null && AuthRole.DOCTOR.matchesRoleId(user.getRoleId());
    }

    /**
     * 解析当前用户的数据访问范围。
     * 管理员返回 null，表示全局范围；医生返回自身用户 ID，表示仅个人范围。
     *
     * @param user 当前用户
     * @return 数据范围用户 ID；管理员返回 null
     */
    public Long resolveScopedUserId(User user) {
        return isAdmin(user) ? null : user.getId();
    }
}
