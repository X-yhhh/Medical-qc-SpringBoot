package com.medical.qc.modules.qcrule.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.qcrule.model.QcRuleConfig;
import org.apache.ibatis.annotations.Mapper;

/**
 * 质控规则配置 Mapper。
 */
@Mapper
public interface QcRuleConfigMapper extends BaseMapper<QcRuleConfig> {
}

