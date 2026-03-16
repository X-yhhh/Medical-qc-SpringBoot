package com.medical.qc.modules.qctask.web;

import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qctask.application.QualityTaskApplicationService;
import com.medical.qc.modules.qctask.application.QualityTaskWorkflowApplicationService;
import com.medical.qc.modules.qctask.application.command.QualityTaskBatchRetryCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskBatchReviewCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskRepairCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskReviewCommand;
import com.medical.qc.modules.qctask.application.command.QualityTaskSubmitCommand;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

/**
 * 异步质控任务控制器。
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityTaskController {
    // 应用服务负责任务提交编排和读模型查询。
    private final QualityTaskApplicationService qualityTaskApplicationService;
    // 工作流服务负责复核、批量重跑、导出和指标统计。
    private final QualityTaskWorkflowApplicationService qualityTaskWorkflowApplicationService;
    // Session 解析与角色校验统一由辅助组件完成。
    private final SessionUserSupport sessionUserSupport;

    public QualityTaskController(QualityTaskApplicationService qualityTaskApplicationService,
                                 QualityTaskWorkflowApplicationService qualityTaskWorkflowApplicationService,
                                 SessionUserSupport sessionUserSupport) {
        this.qualityTaskApplicationService = qualityTaskApplicationService;
        this.qualityTaskWorkflowApplicationService = qualityTaskWorkflowApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    /**
     * 提交 CT 头部平扫异步质控任务。
     */
    @PostMapping("/head/detect")
    public ResponseEntity<?> detectHead(@RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam("patient_name") String patientName,
                                        @RequestParam("exam_id") String examId,
                                        @RequestParam(value = "patient_id", required = false) String patientId,
                                        @RequestParam("gender") String gender,
                                        @RequestParam("age") Integer age,
                                        @RequestParam("study_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                        @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                        HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        // 各任务入口只负责固定 taskType，其余字段统一封装为提交命令。
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(
                        MockQualityAnalysisSupport.TASK_TYPE_HEAD,
                        file,
                        patientName,
                        examId,
                        patientId,
                        gender,
                        age,
                        studyDate,
                        sourceMode,
                        Map.of(),
                        user)));
    }

    /**
     * 提交 CT 胸部平扫异步质控任务。
     */
    @PostMapping("/chest-non-contrast/detect")
    public ResponseEntity<?> detectChestNonContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                    @RequestParam("patient_name") String patientName,
                                                    @RequestParam("exam_id") String examId,
                                                    @RequestParam(value = "patient_id", required = false) String patientId,
                                                    @RequestParam("gender") String gender,
                                                    @RequestParam("age") Integer age,
                                                    @RequestParam("study_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                                    @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                    HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(
                        MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST,
                        file,
                        patientName,
                        examId,
                        patientId,
                        gender,
                        age,
                        studyDate,
                        sourceMode,
                        Map.of(),
                        user)));
    }

    /**
     * 提交 CT 胸部增强异步质控任务。
     */
    @PostMapping("/chest-contrast/detect")
    public ResponseEntity<?> detectChestContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                 @RequestParam("patient_name") String patientName,
                                                 @RequestParam("exam_id") String examId,
                                                 @RequestParam(value = "patient_id", required = false) String patientId,
                                                 @RequestParam(value = "gender", required = false) String gender,
                                                 @RequestParam(value = "age", required = false) Integer age,
                                                 @RequestParam(value = "study_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                                 @RequestParam(value = "flow_rate", required = false) Double flowRate,
                                                 @RequestParam(value = "contrast_volume", required = false) Integer contrastVolume,
                                                 @RequestParam(value = "injection_site", required = false) String injectionSite,
                                                 @RequestParam(value = "slice_thickness", required = false) Double sliceThickness,
                                                 @RequestParam(value = "bolus_tracking_hu", required = false) Integer bolusTrackingHu,
                                                 @RequestParam(value = "scan_delay_sec", required = false) Integer scanDelaySec,
                                                 @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                 HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        Map<String, Object> metadata = new HashMap<>();
        if (flowRate != null) {
            metadata.put("flow_rate", flowRate);
        }
        if (contrastVolume != null) {
            metadata.put("contrast_volume", contrastVolume);
        }
        if (injectionSite != null && !injectionSite.isBlank()) {
            metadata.put("injection_site", injectionSite.trim());
        }
        if (sliceThickness != null) {
            metadata.put("slice_thickness", sliceThickness);
        }
        if (bolusTrackingHu != null) {
            metadata.put("bolus_tracking_hu", bolusTrackingHu);
        }
        if (scanDelaySec != null) {
            metadata.put("scan_delay_sec", scanDelaySec);
        }
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(
                        MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST,
                        file,
                        patientName,
                        examId,
                        patientId,
                        gender,
                        age,
                        studyDate,
                        sourceMode,
                        metadata,
                        user)));
    }

    /**
     * 提交冠脉 CTA 异步质控任务。
     */
    @PostMapping("/coronary-cta/detect")
    public ResponseEntity<?> detectCoronaryCTA(@RequestParam(value = "file", required = false) MultipartFile file,
                                               @RequestParam("patient_name") String patientName,
                                               @RequestParam("exam_id") String examId,
                                               @RequestParam(value = "patient_id", required = false) String patientId,
                                               @RequestParam(value = "gender", required = false) String gender,
                                               @RequestParam(value = "age", required = false) Integer age,
                                               @RequestParam(value = "study_date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
                                                @RequestParam(value = "heart_rate", required = false) Integer heartRate,
                                                @RequestParam(value = "hr_variability", required = false) Integer hrVariability,
                                                @RequestParam(value = "recon_phase", required = false) String reconPhase,
                                                @RequestParam(value = "kvp", required = false) String kVp,
                                                @RequestParam(value = "slice_thickness", required = false) Double sliceThickness,
                                                @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        Map<String, Object> metadata = new HashMap<>();
        if (heartRate != null) {
            metadata.put("heart_rate", heartRate);
        }
        if (hrVariability != null) {
            metadata.put("hr_variability", hrVariability);
        }
        if (reconPhase != null && !reconPhase.isBlank()) {
            metadata.put("recon_phase", reconPhase.trim());
        }
        if (kVp != null && !kVp.isBlank()) {
            metadata.put("kvp", kVp.trim());
        }
        if (sliceThickness != null) {
            metadata.put("slice_thickness", sliceThickness);
        }
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(
                        MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA,
                        file,
                        patientName,
                        examId,
                        patientId,
                        gender,
                        age,
                        studyDate,
                        sourceMode,
                        metadata,
                        user)));
    }

    /**
     * 查询单个异步任务详情。
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getMockTaskDetail(@PathVariable("taskId") String taskId, HttpSession session) {
        User user = requireAuthenticatedSession(session);
        // 管理员查看全局任务，医生仅查看自己提交的任务。
        return ResponseEntity.ok(qualityTaskApplicationService.getTaskDetail(taskId, sessionUserSupport.resolveScopedUserId(user)));
    }

    /**
     * 分页查询异步质控任务列表。
     */
    @GetMapping("/tasks")
    public ResponseEntity<?> getTaskPage(@RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "limit", defaultValue = "10") int limit,
                                         @RequestParam(value = "query", required = false) String query,
                                         @RequestParam(value = "task_type", required = false) String taskType,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "source_mode", required = false) String sourceMode,
                                         HttpSession session) {
        User user = requireAuthenticatedSession(session);
        // 列表过滤条件全部交给应用服务透传到统一查询层处理。
        return ResponseEntity.ok(qualityTaskApplicationService.getTaskPage(
                sessionUserSupport.resolveScopedUserId(user), page, limit, query, taskType, status, sourceMode));
    }

    /**
     * 更新单条任务的人工复核状态。
     */
    @PatchMapping("/tasks/{taskId}/review")
    public ResponseEntity<?> updateTaskReview(@PathVariable("taskId") String taskId,
                                              @RequestBody(required = false) QualityTaskReviewCommand requestBody,
                                              HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.ok(qualityTaskWorkflowApplicationService.updateReview(
                taskId,
                sessionUserSupport.resolveScopedUserId(user),
                user.getId(),
                requestBody == null ? new QualityTaskReviewCommand("PENDING", null, false, null) : requestBody));
    }

    /**
     * 批量更新任务人工复核状态。
     */
    @PatchMapping("/tasks/batch/review")
    public ResponseEntity<?> batchUpdateTaskReview(@RequestBody(required = false) QualityTaskBatchReviewCommand requestBody,
                                                   HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.ok(qualityTaskWorkflowApplicationService.batchUpdateReview(
                sessionUserSupport.resolveScopedUserId(user),
                user.getId(),
                requestBody == null ? new QualityTaskBatchReviewCommand(java.util.List.of(), "PENDING", null, false) : requestBody));
    }

    /**
     * 批量重新分析历史任务。
     */
    @PostMapping("/tasks/batch/retry")
    public ResponseEntity<?> batchRetryTasks(@RequestBody(required = false) QualityTaskBatchRetryCommand requestBody,
                                             HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.accepted().body(qualityTaskWorkflowApplicationService.batchRetry(
                user,
                requestBody == null ? new QualityTaskBatchRetryCommand(java.util.List.of()) : requestBody));
    }

    /**
     * 导出单条任务的 DOCX 报告。
     */
    @GetMapping("/tasks/{taskId}/report")
    public ResponseEntity<byte[]> exportTaskReport(@PathVariable("taskId") String taskId,
                                                   HttpSession session) throws IOException {
        User user = requireAuthenticatedSession(session);
        byte[] payload = qualityTaskWorkflowApplicationService.exportTaskReport(
                taskId,
                sessionUserSupport.resolveScopedUserId(user));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quality-task-" + taskId + ".docx\"")
                .body(payload);
    }

    /**
     * 批量导出任务 CSV 摘要。
     */
    @PostMapping("/tasks/export")
    public ResponseEntity<byte[]> exportTaskCsv(@RequestBody(required = false) QualityTaskBatchRetryCommand requestBody,
                                                HttpSession session) {
        User user = requireAuthenticatedSession(session);
        byte[] payload = qualityTaskWorkflowApplicationService.exportTaskCsv(
                requestBody == null ? java.util.List.of() : requestBody.taskIds(),
                sessionUserSupport.resolveScopedUserId(user));
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "text/csv;charset=UTF-8")
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"quality-task-summary.csv\"")
                .body(payload);
    }

    /**
     * 获取任务中心概览指标。
     */
    @GetMapping("/tasks/metrics")
    public ResponseEntity<?> getTaskMetrics(HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.ok(qualityTaskWorkflowApplicationService.getTaskMetrics(
                sessionUserSupport.resolveScopedUserId(user)));
    }

    /**
     * 修复历史任务结果与异常工单中的结构化字段。
     */
    @PostMapping("/tasks/repair")
    public ResponseEntity<?> repairHistoricalTasks(@RequestBody(required = false) QualityTaskRepairCommand requestBody,
                                                   HttpSession session) {
        User user = requireAdminSession(session);
        return ResponseEntity.ok(qualityTaskWorkflowApplicationService.repairTaskResults(
                requestBody == null ? new QualityTaskRepairCommand(java.util.List.of(), true) : requestBody,
                user.getId()));
    }

    /**
     * 校验医生会话，用于任务提交入口。
     */
    private User requireDoctorSession(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireDoctor(user);
        return user;
    }

    /**
     * 校验管理员会话，用于历史修复等全局维护入口。
     */
    private User requireAdminSession(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireAdmin(user);
        return user;
    }

    /**
     * 校验已登录会话，用于任务查询入口。
     */
    private User requireAuthenticatedSession(HttpSession session) {
        return sessionUserSupport.requireAuthenticatedUser(session);
    }
}

