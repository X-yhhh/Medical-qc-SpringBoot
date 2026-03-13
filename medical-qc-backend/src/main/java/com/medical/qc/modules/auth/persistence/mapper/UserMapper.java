package com.medical.qc.modules.auth.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.auth.persistence.entity.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户持久化接口。
 * 数据链路：AuthServiceImpl / SessionUserSupport -> UserMapper -> users 表。
 */
@Mapper
public interface UserMapper extends BaseMapper<User> {
}

