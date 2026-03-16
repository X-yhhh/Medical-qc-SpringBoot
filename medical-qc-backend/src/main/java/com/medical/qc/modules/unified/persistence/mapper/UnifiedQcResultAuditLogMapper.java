package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedQcResultAuditLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一质控结果审计日志 Mapper。
 */
@Mapper
public interface UnifiedQcResultAuditLogMapper extends BaseMapper<UnifiedQcResultAuditLog> {
}
