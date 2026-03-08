package com.medical.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.entity.HemorrhageRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HemorrhageRecordMapper extends BaseMapper<HemorrhageRecord> {
    /**
     * 查询指定用户的全部脑出血检测历史记录。
     *
     * @param userId 用户 ID
     * @return 历史记录列表（按创建时间倒序）
     */
    List<HemorrhageRecord> findByUserId(@Param("userId") Long userId);

    /**
     * 查询指定用户最近的脑出血检测历史记录。
     *
     * @param userId 用户 ID
     * @param limit 返回数量上限
     * @return 历史记录列表（按创建时间倒序）
     */
    List<HemorrhageRecord> findRecentByUserId(@Param("userId") Long userId, @Param("limit") int limit);
}
