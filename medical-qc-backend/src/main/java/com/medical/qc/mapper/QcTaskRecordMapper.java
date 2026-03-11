package com.medical.qc.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.entity.QcTaskRecord;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一质控任务记录 Mapper。
 */
@Mapper
public interface QcTaskRecordMapper extends BaseMapper<QcTaskRecord> {
}
