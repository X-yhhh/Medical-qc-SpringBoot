package com.medical.qc.mapper;

import com.medical.qc.entity.QualityPatientInfo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 质控项患者信息 Mapper。
 *
 * <p>由于五个质控项使用相同字段但不同表名，
 * 本 Mapper 通过动态表名复用一套 SQL。</p>
 */
@Mapper
public interface QualityPatientInfoMapper {

    /**
     * 分页查询患者信息列表。
     *
     * @param tableName 患者信息表名
     * @param keyword 关键字
     * @param patientId 患者 ID
     * @param patientName 患者姓名
     * @param accessionNumber 检查号
     * @param offset 偏移量
     * @param limit 每页数量
     * @return 患者信息列表
     */
    List<QualityPatientInfo> selectPage(@Param("tableName") String tableName,
                                        @Param("keyword") String keyword,
                                        @Param("patientId") String patientId,
                                        @Param("patientName") String patientName,
                                        @Param("accessionNumber") String accessionNumber,
                                        @Param("offset") int offset,
                                        @Param("limit") int limit);

    /**
     * 统计符合条件的患者数量。
     *
     * @param tableName 患者信息表名
     * @param keyword 关键字
     * @param patientId 患者 ID
     * @param patientName 患者姓名
     * @param accessionNumber 检查号
     * @return 数量
     */
    long countPage(@Param("tableName") String tableName,
                   @Param("keyword") String keyword,
                   @Param("patientId") String patientId,
                   @Param("patientName") String patientName,
                   @Param("accessionNumber") String accessionNumber);

    /**
     * 根据主键查询患者信息。
     *
     * @param tableName 患者信息表名
     * @param id 主键 ID
     * @return 患者信息
     */
    QualityPatientInfo selectById(@Param("tableName") String tableName, @Param("id") Long id);

    /**
     * 根据检查号查询患者信息。
     *
     * @param tableName 患者信息表名
     * @param accessionNumber 检查号
     * @return 患者信息
     */
    QualityPatientInfo selectByAccessionNumber(@Param("tableName") String tableName,
                                               @Param("accessionNumber") String accessionNumber);

    /**
     * 新增患者信息。
     *
     * @param tableName 患者信息表名
     * @param entity 患者信息实体
     * @return 影响行数
     */
    int insertPatient(@Param("tableName") String tableName, @Param("entity") QualityPatientInfo entity);

    /**
     * 更新患者信息。
     *
     * @param tableName 患者信息表名
     * @param entity 患者信息实体
     * @return 影响行数
     */
    int updatePatient(@Param("tableName") String tableName, @Param("entity") QualityPatientInfo entity);

    /**
     * 删除患者信息。
     *
     * @param tableName 患者信息表名
     * @param id 主键 ID
     * @return 影响行数
     */
    int deletePatient(@Param("tableName") String tableName, @Param("id") Long id);
}
