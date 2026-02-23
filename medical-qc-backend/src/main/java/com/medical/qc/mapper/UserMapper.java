package com.medical.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
