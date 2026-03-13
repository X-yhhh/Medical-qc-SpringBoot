package com.medical.qc.modules.pacs.persistence.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.medical.qc.modules.pacs.model.PacsStudyCache;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * PACS检查记录Mapper接口
 * 提供PACS检查记录的数据库访问功能
 */
@Mapper
public interface PacsStudyMapper extends BaseMapper<PacsStudyCache> {

    /**
     * 基于缓存表本身查询 PACS 检查记录。
     *
     * @param patientId 患者 ID
     * @param patientName 患者姓名
     * @param accessionNumber 检查号
     * @param startDate 开始日期
     * @param endDate 结束日期
     * @return PACS 检查记录列表
     */
    List<PacsStudyCache> searchStudiesFromCache(@Param("patientId") String patientId,
                                                @Param("patientName") String patientName,
                                                @Param("accessionNumber") String accessionNumber,
                                                @Param("startDate") LocalDate startDate,
                                                @Param("endDate") LocalDate endDate);

    /**
     * 查询用于批量初始化的 PACS 缓存记录。
     *
     * @return PACS 缓存记录列表
     */
    List<PacsStudyCache> selectStudiesForSync();
}

