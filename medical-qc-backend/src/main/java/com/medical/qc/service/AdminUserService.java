package com.medical.qc.service;

import com.medical.qc.bean.AdminUserUpdateReq;

import java.util.Map;

/**
 * 管理员用户与权限管理服务。
 */
public interface AdminUserService {
    /**
     * 获取用户分页列表与概览统计。
     *
     * @param page 页码
     * @param limit 每页数量
     * @param keyword 搜索关键字
     * @param role 角色筛选
     * @param active 启用状态筛选
     * @return 分页结果与统计概览
     */
    Map<String, Object> getUserPage(int page, int limit, String keyword, String role, Boolean active);

    /**
     * 更新指定用户的身份与档案信息。
     *
     * @param operatorId 当前管理员 ID
     * @param userId 目标用户 ID
     * @param request 更新请求
     * @return 更新后的用户摘要
     */
    Map<String, Object> updateUser(Long operatorId, Long userId, AdminUserUpdateReq request);
}
