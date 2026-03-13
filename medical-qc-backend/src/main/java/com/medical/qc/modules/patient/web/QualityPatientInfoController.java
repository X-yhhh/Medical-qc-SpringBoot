package com.medical.qc.modules.patient.web;

import com.medical.qc.bean.QualityPatientInfoSaveReq;
import com.medical.qc.modules.patient.model.QualityPatientInfo;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.patient.application.PatientInfoApplicationService;
import com.medical.qc.modules.patient.application.command.PatientInfoSaveCommand;
import com.medical.qc.modules.patient.application.query.PatientInfoPageQuery;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Map;

/**
 * 质控项患者信息管理控制器。
 *
 * <p>管理员和医生均可访问，用于管理五个质控项的患者信息基础档案。</p>
 */
@RestController
@RequestMapping("/api/v1/patient-info")
public class QualityPatientInfoController {

    // 应用服务负责编排患者分页、保存和 PACS 同步流程。
    private final PatientInfoApplicationService patientInfoApplicationService;
    // 会话用户辅助组件用于校验登录态和角色。
    private final SessionUserSupport sessionUserSupport;

    public QualityPatientInfoController(PatientInfoApplicationService patientInfoApplicationService,
                                        SessionUserSupport sessionUserSupport) {
        this.patientInfoApplicationService = patientInfoApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 分页查询患者信息列表。
     */
    @GetMapping("/{taskType}")
    public ResponseEntity<?> getPatientPage(@PathVariable("taskType") String taskType,
                                            @RequestParam(value = "keyword", required = false) String keyword,
                                            @RequestParam(value = "patient_id", required = false) String patientId,
                                            @RequestParam(value = "patient_name", required = false) String patientName,
                                            @RequestParam(value = "accession_number", required = false) String accessionNumber,
                                            @RequestParam(value = "page", required = false) Integer page,
                                            @RequestParam(value = "limit", required = false) Integer limit,
                                            HttpSession session) {
        requireAuthenticatedOperator(session);
        // 前端筛选项全部封装为查询对象，避免控制器层拼接分页逻辑。
        Map<String, Object> response = patientInfoApplicationService.getPatientPage(
                new PatientInfoPageQuery(
                        taskType,
                        keyword,
                        patientId,
                        patientName,
                        accessionNumber,
                        page,
                        limit));
        return ResponseEntity.ok(response);
    }

    /**
     * 新增患者信息。
     */
    @PostMapping(value = "/{taskType}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createPatient(@PathVariable("taskType") String taskType,
                                           @RequestParam(value = "patient_id", required = false) String patientId,
                                           @RequestParam(value = "patient_name") String patientName,
                                           @RequestParam(value = "accession_number") String accessionNumber,
                                           @RequestParam(value = "gender", required = false) String gender,
                                           @RequestParam(value = "age", required = false) Integer age,
                                           @RequestParam(value = "study_date", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                           @RequestParam(value = "remark", required = false) String remark,
                                           @RequestParam(value = "image_file", required = false) MultipartFile imageFile,
                                           HttpSession session) {
        requireAuthenticatedOperator(session);
        // multipart 表单中的文本字段和图片在这里汇总为统一保存命令。
        QualityPatientInfo createdPatient = patientInfoApplicationService.createPatient(
                new PatientInfoSaveCommand(
                        taskType,
                        null,
                        buildSaveRequest(patientId, patientName, accessionNumber, gender, age, studyDate, remark),
                        imageFile));
        return ResponseEntity.ok(Collections.singletonMap("data", createdPatient));
    }

    /**
     * 更新患者信息。
     */
    @PutMapping(value = "/{taskType}/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> updatePatient(@PathVariable("taskType") String taskType,
                                           @PathVariable("id") Long id,
                                           @RequestParam(value = "patient_id", required = false) String patientId,
                                           @RequestParam(value = "patient_name") String patientName,
                                           @RequestParam(value = "accession_number") String accessionNumber,
                                           @RequestParam(value = "gender", required = false) String gender,
                                           @RequestParam(value = "age", required = false) Integer age,
                                           @RequestParam(value = "study_date", required = false)
                                           @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                           @RequestParam(value = "remark", required = false) String remark,
                                           @RequestParam(value = "image_file", required = false) MultipartFile imageFile,
                                           HttpSession session) {
        requireAuthenticatedOperator(session);
        // 编辑场景显式带上记录 ID，写服务会据此执行更新而不是新增。
        QualityPatientInfo updatedPatient = patientInfoApplicationService.updatePatient(
                new PatientInfoSaveCommand(
                        taskType,
                        id,
                        buildSaveRequest(patientId, patientName, accessionNumber, gender, age, studyDate, remark),
                        imageFile));
        return ResponseEntity.ok(Collections.singletonMap("data", updatedPatient));
    }

    /**
     * 删除患者信息。
     */
    @DeleteMapping("/{taskType}/{id}")
    public ResponseEntity<?> deletePatient(@PathVariable("taskType") String taskType,
                                           @PathVariable("id") Long id,
                                           HttpSession session) {
        requireAuthenticatedOperator(session);
        patientInfoApplicationService.deletePatient(taskType, id);
        return ResponseEntity.ok(Collections.singletonMap("message", "患者信息删除成功"));
    }

    /**
     * 从 PACS 缓存批量初始化当前质控项的统一患者主数据。
     */
    @PostMapping("/{taskType}/sync-from-pacs")
    public ResponseEntity<?> syncPatientsFromPacs(@PathVariable("taskType") String taskType,
                                                  HttpSession session) {
        requireAuthenticatedOperator(session);
        Map<String, Object> response = patientInfoApplicationService.syncPatientsFromPacs(taskType);
        return ResponseEntity.ok(Collections.singletonMap("data", response));
    }

    /**
     * 校验当前会话已登录，且仅允许管理员或医生访问。
     */
    private User requireAuthenticatedOperator(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        // 患者信息管理目前允许医生和管理员访问，其他角色统一拦截。
        if (!sessionUserSupport.isAdmin(user) && !sessionUserSupport.isDoctor(user)) {
            throw new IllegalArgumentException("当前账号无权访问患者信息管理功能");
        }
        return user;
    }

    /**
     * 将 multipart 表单参数组装为统一请求对象。
     */
    private QualityPatientInfoSaveReq buildSaveRequest(String patientId,
                                                       String patientName,
                                                       String accessionNumber,
                                                       String gender,
                                                       Integer age,
                                                       LocalDate studyDate,
                                                       String remark) {
        // 保存请求体同时供手工录入和自动同步逻辑复用。
        QualityPatientInfoSaveReq request = new QualityPatientInfoSaveReq();
        request.setPatientId(patientId);
        request.setPatientName(patientName);
        request.setAccessionNumber(accessionNumber);
        request.setGender(gender);
        request.setAge(age);
        request.setStudyDate(studyDate);
        request.setRemark(remark);
        return request;
    }
}

