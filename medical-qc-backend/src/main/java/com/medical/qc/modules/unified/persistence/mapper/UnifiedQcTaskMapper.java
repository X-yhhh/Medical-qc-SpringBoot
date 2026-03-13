package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcTask;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一质控任务 Mapper。
 * 数据链路：任务写入、工单查询、看板统计 -> qc_tasks 表。
 */
@Mapper
public interface UnifiedQcTaskMapper extends BaseMapper<UnifiedQcTask> {
}

