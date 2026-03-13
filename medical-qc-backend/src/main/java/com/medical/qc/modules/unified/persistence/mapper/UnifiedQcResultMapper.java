package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResult;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一质控结果主表 Mapper。
 * 数据链路：质控写服务、工单查询服务 -> qc_results 表。
 */
@Mapper
public interface UnifiedQcResultMapper extends BaseMapper<UnifiedQcResult> {
}

