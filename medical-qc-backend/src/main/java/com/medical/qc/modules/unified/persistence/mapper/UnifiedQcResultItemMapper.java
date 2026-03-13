package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultItem;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一质控结果明细项 Mapper。
 * 数据链路：UnifiedIssueQueryService 等查询服务 -> qc_result_items 表。
 */
@Mapper
public interface UnifiedQcResultItemMapper extends BaseMapper<UnifiedQcResultItem> {
}

