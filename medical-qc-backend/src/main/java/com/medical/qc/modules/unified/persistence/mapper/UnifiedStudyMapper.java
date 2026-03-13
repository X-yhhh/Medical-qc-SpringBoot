package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudy;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一检查实例 Mapper。
 * 数据链路：患者信息、质控任务、工单查询 -> studies 表。
 */
@Mapper
public interface UnifiedStudyMapper extends BaseMapper<UnifiedStudy> {
}

