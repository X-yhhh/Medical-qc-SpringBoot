package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueActionLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常工单动作日志 Mapper。
 * 数据链路：UnifiedIssueWriteService / UnifiedIssueQueryService -> issue_action_logs 表。
 */
@Mapper
public interface UnifiedIssueActionLogMapper extends BaseMapper<UnifiedIssueActionLog> {
}

