package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一患者主数据 Mapper。
 * 数据链路：UnifiedPatientInfoWriteService / UnifiedPatientInfoQueryService -> patients 表。
 */
@Mapper
public interface UnifiedPatientMapper extends BaseMapper<UnifiedPatient> {
}

