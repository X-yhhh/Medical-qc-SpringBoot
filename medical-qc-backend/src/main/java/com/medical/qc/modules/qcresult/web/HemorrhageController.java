package com.medical.qc.modules.qcresult.web;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qcresult.application.HemorrhageRecordApplicationService;
import com.medical.qc.modules.qcresult.application.command.HemorrhageAnalysisCommand;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 脑出血检测控制器。
 */
@RestController
@RequestMapping("/api/v1/quality")
public class HemorrhageController {
    private final HemorrhageRecordApplicationService hemorrhageRecordApplicationService;
    private final SessionUserSupport sessionUserSupport;

    public HemorrhageController(HemorrhageRecordApplicationService hemorrhageRecordApplicationService,
                                SessionUserSupport sessionUserSupport) {
        this.hemorrhageRecordApplicationService = hemorrhageRecordApplicationService;
        this.sessionUserSupport = sessionUserSupport;
    }

    @GetMapping("/hemorrhage/history/{recordId}")
    public ResponseEntity<?> getHemorrhageHistoryDetail(@PathVariable("recordId") Long recordId,
                                                        HttpSession session) {
        User user = requireDoctorSession(session);
        HemorrhageRecord record = hemorrhageRecordApplicationService.getHistoryDetail(user.getId(), recordId);
        if (record == null) {
            throw new IllegalArgumentException("历史记录不存在");
        }
        return ResponseEntity.ok(Collections.singletonMap("data", record));
    }

    @GetMapping("/hemorrhage/history")
    public ResponseEntity<?> getHemorrhageHistory(@RequestParam(value = "limit", defaultValue = "20") int limit,
                                                  HttpSession session) {
        User user = requireDoctorSession(session);
        List<HemorrhageRecord> history = hemorrhageRecordApplicationService.getHistory(user.getId(), limit);
        return ResponseEntity.ok(Collections.singletonMap("data", history));
    }

    @PostMapping("/hemorrhage")
    public ResponseEntity<?> analyzeHemorrhage(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "patient_name", required = false) String patientName,
            @RequestParam(value = "patient_code", required = false) String patientCode,
            @RequestParam(value = "exam_id", required = false) String examId,
            @RequestParam(value = "gender", required = false) String gender,
            @RequestParam(value = "age", required = false) Integer age,
            @RequestParam(value = "study_date", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate studyDate,
            @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
            HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        Map<String, Object> result = hemorrhageRecordApplicationService.analyzeHemorrhage(
                new HemorrhageAnalysisCommand(
                        file,
                        user,
                        patientName,
                        patientCode,
                        examId,
                        gender,
                        age,
                        studyDate,
                        sourceMode));
        return ResponseEntity.ok(result);
    }

    private User requireDoctorSession(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireDoctor(user);
        return user;
    }
}

