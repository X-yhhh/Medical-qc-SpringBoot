package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueCapaRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常工单 CAPA 记录 Mapper。
 * 数据链路：UnifiedIssueWriteService / UnifiedIssueQueryService -> issue_capa_records 表。
 */
@Mapper
public interface UnifiedIssueCapaRecordMapper extends BaseMapper<UnifiedIssueCapaRecord> {
}

