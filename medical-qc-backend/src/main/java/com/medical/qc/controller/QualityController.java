package com.medical.qc.controller;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import com.medical.qc.service.QualityService;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 质控模块 API 控制器。
 * 负责处理脑出血检测（真实）和其他 CT 质控（模拟）请求。
 *
 * <p>该模块属于医生工作台能力，管理员不可直接访问。</p>
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    @Autowired
    private SessionUserSupport sessionUserSupport;

    /**
     * 获取指定的脑出血检测历史记录详情。
     *
     * @param recordId 历史记录 ID
     * @param session 当前会话
     * @return 历史记录详情
     */
    @GetMapping("/hemorrhage/history/{recordId}")
    public ResponseEntity<?> getHemorrhageHistoryDetail(
            @PathVariable("recordId") Long recordId,
            HttpSession session) {

        User user = requireDoctorSession(session);
        HemorrhageRecord record = qualityService.getHistoryRecord(user.getId(), recordId);
        if (record == null) {
            throw new IllegalArgumentException("历史记录不存在");
        }

        return ResponseEntity.ok(Collections.singletonMap("data", record));
    }

    /**
     * 获取脑出血检测历史记录。
     *
     * @param limit 限制条数
     * @param session 当前会话
     * @return 历史记录列表
     */
    @GetMapping("/hemorrhage/history")
    public ResponseEntity<?> getHemorrhageHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            HttpSession session) {

        User user = requireDoctorSession(session);
        List<HemorrhageRecord> history = qualityService.getHistory(user.getId(), limit);
        return ResponseEntity.ok(Collections.singletonMap("data", history));
    }

    /**
     * 脑出血 AI 智能检测。
     *
     * @param file 上传的 CT 影像
     * @param patientName 患者姓名
     * @param examId 检查 ID
     * @param session 当前会话
     * @return 检测结果（包含是否出血、概率、中线偏移等）
     */
    @PostMapping("/hemorrhage")
    public ResponseEntity<?> analyzeHemorrhage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "patient_name", required = false) String patientName,
            @RequestParam(value = "exam_id", required = false) String examId,
            HttpSession session) throws IOException {

        User user = requireDoctorSession(session);
        Map<String, Object> result = qualityService.processHemorrhage(file, user, patientName, examId);
        return ResponseEntity.ok(result);
    }

    // =================================================================================
    // MOCK APIs (模拟接口)
    // 下列接口目前返回模拟数据，后期需对接真实算法服务
    // =================================================================================

    /**
     * [Mock] CT 头部平扫质控。
     *
     * @param file 影像文件
     * @param session 当前会话
     * @return 模拟质控结果
     */
    @PostMapping("/head/detect")
    public ResponseEntity<?> detectHead(@RequestParam("file") MultipartFile file, HttpSession session) {
        requireDoctorSession(session);
        return ResponseEntity.ok(qualityService.detectHead(file));
    }

    /**
     * [Mock] CT 胸部平扫质控。
     *
     * @param file 影像文件
     * @param session 当前会话
     * @return 模拟质控结果
     */
    @PostMapping("/chest-non-contrast/detect")
    public ResponseEntity<?> detectChestNonContrast(@RequestParam("file") MultipartFile file, HttpSession session) {
        requireDoctorSession(session);
        return ResponseEntity.ok(qualityService.detectChest(file, false));
    }

    /**
     * [Mock] CT 胸部增强质控。
     *
     * @param file 影像文件
     * @param session 当前会话
     * @return 模拟质控结果
     */
    @PostMapping("/chest-contrast/detect")
    public ResponseEntity<?> detectChestContrast(@RequestParam("file") MultipartFile file, HttpSession session) {
        requireDoctorSession(session);
        return ResponseEntity.ok(qualityService.detectChest(file, true));
    }

    /**
     * [Mock] 冠脉 CTA 质控。
     *
     * @param file 影像文件
     * @param session 当前会话
     * @return 模拟质控结果
     */
    @PostMapping("/coronary-cta/detect")
    public ResponseEntity<?> detectCoronaryCTA(@RequestParam("file") MultipartFile file, HttpSession session) {
        requireDoctorSession(session);
        return ResponseEntity.ok(qualityService.detectCoronary(file));
    }

    /**
     * 校验当前会话是否为医生身份。
     *
     * @param session 当前会话
     * @return 当前登录医生
     */
    private User requireDoctorSession(HttpSession session) {
        User user = sessionUserSupport.requireAuthenticatedUser(session);
        sessionUserSupport.requireDoctor(user);
        return user;
    }
}
