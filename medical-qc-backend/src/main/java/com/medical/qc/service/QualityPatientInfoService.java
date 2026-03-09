package com.medical.qc.service;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.entity.QualityPatientInfo;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Map;

/**
 * 质控项患者信息服务接口。
 *
 * <p>提供五个质控项患者信息的统一管理能力，
 * 同时向 PACS 查询和脑出血检测流程提供患者主数据支持。</p>
 */
public interface QualityPatientInfoService {

    /**
     * 分页查询患者信息。
     *
     * @param taskType 质控任务类型
     * @param keyword 关键字
     * @param patientId 患者 ID
     * @param patientName 患者姓名
     * @param accessionNumber 检查号
     * @param page 页码
     * @param limit 每页数量
     * @return 列表、分页信息与统计摘要
     */
    Map<String, Object> getPatientPage(String taskType,
                                       String keyword,
                                       String patientId,
                                       String patientName,
                                       String accessionNumber,
                                       Integer page,
                                       Integer limit);

    /**
     * 新增患者信息。
     *
     * @param taskType 质控任务类型
     * @param request 请求体
     * @return 新增后的患者信息
     */
    QualityPatientInfo createPatient(String taskType, QualityPatientInfoSaveReq request, MultipartFile imageFile);

    /**
     * 更新患者信息。
     *
     * @param taskType 质控任务类型
     * @param id 主键 ID
     * @param request 请求体
     * @return 更新后的患者信息
     */
    QualityPatientInfo updatePatient(String taskType, Long id, QualityPatientInfoSaveReq request, MultipartFile imageFile);

    /**
     * 删除患者信息。
     *
     * @param taskType 质控任务类型
     * @param id 主键 ID
     */
    void deletePatient(String taskType, Long id);

    /**
     * 根据检查号查询患者信息。
     *
     * @param taskType 质控任务类型
     * @param accessionNumber 检查号
     * @return 患者信息；不存在时返回 null
     */
    QualityPatientInfo getByAccessionNumber(String taskType, String accessionNumber);

    /**
     * 按检查号执行新增或更新。
     *
     * @param taskType 质控任务类型
     * @param patientId 患者 ID
     * @param patientName 患者姓名
     * @param accessionNumber 检查号
     * @param gender 性别
     * @param age 年龄
     * @param studyDate 检查日期
     * @return 最新患者信息
     */
    QualityPatientInfo upsertPatientByAccessionNumber(String taskType,
                                                      String patientId,
                                                      String patientName,
                                                      String accessionNumber,
                                                      String gender,
                                                      Integer age,
                                                      LocalDate studyDate,
                                                      String imagePath);

    /**
     * 从 PACS 缓存批量初始化当前质控项的患者信息表。
     *
     * @param taskType 质控任务类型
     * @return 同步统计信息
     */
    Map<String, Object> syncPatientsFromPacs(String taskType);
}
