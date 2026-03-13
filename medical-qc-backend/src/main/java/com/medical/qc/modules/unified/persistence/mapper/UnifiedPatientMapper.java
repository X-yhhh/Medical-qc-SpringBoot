package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedPatient;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UnifiedPatientMapper extends BaseMapper<UnifiedPatient> {
}

