package com.medical.qc.modules.pacs.web;

import com.medical.qc.modules.pacs.model.PacsStudyCache;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.pacs.application.PacsQueryApplicationService;
import com.medical.qc.modules.pacs.application.query.PacsStudySearchQuery;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

/**
 * PACS查询控制器
 * 提供PACS检查记录查询接口
 */
@RestController
@RequestMapping("/api/v1/pacs")
public class PacsController {

    // 应用服务负责封装检索参数并调用 PACS 服务。
    private final PacsQueryApplicationService pacsQueryApplicationService;
    // 会话辅助组件负责登录态与医生权限校验。
    private final SessionUserSupport sessionUserSupport;

    public PacsController(PacsQueryApplicationService pacsQueryApplicationService,
                          SessionUserSupport sessionUserSupport) {
        this.pacsQueryApplicationService = pacsQueryApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 查询PACS检查记录
     * 支持多条件组合查询：患者ID、患者姓名、检查号、日期范围
     *
     * @param taskType 质控任务类型（可选，用于补齐统一患者主数据）
     * @param patientId 患者ID（可选，精确匹配）
     * @param patientName 患者姓名（可选，模糊匹配）
     * @param accessionNumber 检查号（可选，精确匹配）
     * @param startDate 开始日期（可选）
     * @param endDate 结束日期（可选）
     * @param session 当前会话
     * @return 检查记录列表
     */
    @GetMapping("/search")
    public ResponseEntity<?> searchStudies(
            @RequestParam(value = "task_type", required = false) String taskType,
            @RequestParam(value = "patient_id", required = false) String patientId,
            @RequestParam(value = "patient_name", required = false) String patientName,
            @RequestParam(value = "accession_number", required = false) String accessionNumber,
            @RequestParam(value = "start_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(value = "end_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            HttpSession session) {

        // 验证用户身份（仅医生可访问）
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireDoctor(user);

        // 查询PACS检查记录，并在应用层补齐 taskType 相关上下文。
        List<PacsStudyCache> studies = pacsQueryApplicationService.searchStudies(
                new PacsStudySearchQuery(
                        taskType,
                        patientId,
                        patientName,
                        accessionNumber,
                        startDate,
                        endDate));

        return ResponseEntity.ok(Collections.singletonMap("data", studies));
    }
}

