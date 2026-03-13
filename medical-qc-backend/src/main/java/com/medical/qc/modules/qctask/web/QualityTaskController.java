package com.medical.qc.modules.qctask.web;

import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qctask.application.QualityTaskApplicationService;
import com.medical.qc.modules.qctask.application.command.QualityTaskSubmitCommand;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * 异步质控任务控制器。
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityTaskController {
    private final QualityTaskApplicationService qualityTaskApplicationService;
    private final SessionUserSupport sessionUserSupport;

    public QualityTaskController(QualityTaskApplicationService qualityTaskApplicationService,
                                 SessionUserSupport sessionUserSupport) {
        this.qualityTaskApplicationService = qualityTaskApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    @PostMapping("/head/detect")
    public ResponseEntity<?> detectHead(@RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam("patient_name") String patientName,
                                        @RequestParam("exam_id") String examId,
                                        @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                        HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(MockQualityAnalysisSupport.TASK_TYPE_HEAD, file, patientName, examId, sourceMode, user)));
    }

    @PostMapping("/chest-non-contrast/detect")
    public ResponseEntity<?> detectChestNonContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                    @RequestParam("patient_name") String patientName,
                                                    @RequestParam("exam_id") String examId,
                                                    @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                    HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST, file, patientName, examId, sourceMode, user)));
    }

    @PostMapping("/chest-contrast/detect")
    public ResponseEntity<?> detectChestContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                 @RequestParam("patient_name") String patientName,
                                                 @RequestParam("exam_id") String examId,
                                                 @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                 HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST, file, patientName, examId, sourceMode, user)));
    }

    @PostMapping("/coronary-cta/detect")
    public ResponseEntity<?> detectCoronaryCTA(@RequestParam(value = "file", required = false) MultipartFile file,
                                               @RequestParam("patient_name") String patientName,
                                               @RequestParam("exam_id") String examId,
                                               @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                               HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(qualityTaskApplicationService.submitTask(
                new QualityTaskSubmitCommand(MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA, file, patientName, examId, sourceMode, user)));
    }

    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getMockTaskDetail(@PathVariable("taskId") String taskId, HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.ok(qualityTaskApplicationService.getTaskDetail(taskId, sessionUserSupport.resolveScopedUserId(user)));
    }

    @GetMapping("/tasks")
    public ResponseEntity<?> getTaskPage(@RequestParam(value = "page", defaultValue = "1") int page,
                                         @RequestParam(value = "limit", defaultValue = "10") int limit,
                                         @RequestParam(value = "query", required = false) String query,
                                         @RequestParam(value = "task_type", required = false) String taskType,
                                         @RequestParam(value = "status", required = false) String status,
                                         @RequestParam(value = "source_mode", required = false) String sourceMode,
                                         HttpSession session) {
        User user = requireAuthenticatedSession(session);
        return ResponseEntity.ok(qualityTaskApplicationService.getTaskPage(
                sessionUserSupport.resolveScopedUserId(user), page, limit, query, taskType, status, sourceMode));
    }

    private User requireDoctorSession(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireDoctor(user);
        return user;
    }

    private User requireAuthenticatedSession(HttpSession session) {
        return sessionUserSupport.requireAuthenticatedUser(session);
    }
}

