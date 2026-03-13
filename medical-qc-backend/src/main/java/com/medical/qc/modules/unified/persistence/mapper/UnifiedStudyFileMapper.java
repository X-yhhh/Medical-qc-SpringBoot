package com.medical.qc.modules.unified.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.unified.persistence.entity.UnifiedStudyFile;
import org.apache.ibatis.annotations.Mapper;

/**
 * 统一检查文件 Mapper。
 * 数据链路：患者图片、预览图和检查附件读写 -> study_files 表。
 */
@Mapper
public interface UnifiedStudyFileMapper extends BaseMapper<UnifiedStudyFile> {
}

