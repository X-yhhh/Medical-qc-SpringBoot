package com.medical.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.entity.QcIssueRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一异常工单 Mapper。
 */
@Mapper
public interface QcIssueRecordMapper extends BaseMapper<QcIssueRecord> {
}
