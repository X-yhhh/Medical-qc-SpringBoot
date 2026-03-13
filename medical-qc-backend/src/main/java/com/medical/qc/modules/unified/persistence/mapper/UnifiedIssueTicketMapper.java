package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedIssueTicket;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常工单主表 Mapper。
 * 数据链路：UnifiedIssueWriteService / UnifiedIssueQueryService -> issue_tickets 表。
 */
@Mapper
public interface UnifiedIssueTicketMapper extends BaseMapper<UnifiedIssueTicket> {
}

