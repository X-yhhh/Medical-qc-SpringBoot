package com.medical.qc.controller;

import com.medical.qc.entity.PacsStudyCache;
import com.medical.qc.entity.User;
import com.medical.qc.service.PacsService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
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

    @Autowired
    private PacsService pacsService;

    @Autowired
    private SessionUserSupport sessionUserSupport;

    /**
     * 查询PACS检查记录
     * 支持多条件组合查询：患者ID、患者姓名、检查号、日期范围
     *
     * @param taskType 质控任务类型（可选，用于关联对应患者信息表）
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

        // 查询PACS检查记录
        List<PacsStudyCache> studies = pacsService.searchStudies(
            taskType, patientId, patientName, accessionNumber, startDate, endDate);

        return ResponseEntity.ok(Collections.singletonMap("data", studies));
    }
}
