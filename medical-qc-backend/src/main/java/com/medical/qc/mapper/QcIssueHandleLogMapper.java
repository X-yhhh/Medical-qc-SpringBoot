package com.medical.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.entity.QcIssueHandleLog;
import org.apache.ibatis.annotations.Mapper;

/**
 * 异常工单处理日志 Mapper。
 */
@Mapper
public interface QcIssueHandleLogMapper extends BaseMapper<QcIssueHandleLog> {
}
