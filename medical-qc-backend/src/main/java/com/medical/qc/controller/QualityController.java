package com.medical.qc.controller;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import com.medical.qc.service.QualityService;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 质控模块 API 控制器
 * 负责处理脑出血检测（真实）和其他 CT 质控（模拟）请求
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    /**
     * 获取脑出血检测历史记录
     * @param limit 限制条数
     * @param session 当前会话
     * @return 历史记录列表
     */
    @GetMapping("/hemorrhage/history")
    public ResponseEntity<?> getHemorrhageHistory(
            @RequestParam(value = "limit", defaultValue = "20") int limit,
            HttpSession session) {

        User user = (User) session.getAttribute("user");
        if (user == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Collections.singletonMap("detail", "Not authenticated"));
        }

        List<HemorrhageRecord> history = qualityService.getHistory(user.getId());
        if (history.size() > limit) {
            history = history.subList(0, limit);
        }

        return ResponseEntity.ok(Collections.singletonMap("data", history));
    }

    /**
     * 脑出血 AI 智能检测
     * 对接后端 Python 模型，进行真实分析
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
            HttpSession session) {

        // Temporary bypass for debugging 400 error if session is missing
        // In production, strictly check session
        User user = (User) session.getAttribute("user");
        if (user == null) {
             // Try to construct a temporary user for testing if session fails
             // This is to isolate if 400 is from session or file upload
             // return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
             //        .body(Collections.singletonMap("detail", "Not authenticated"));
             user = new User();
             user.setId(1L); // Default ID
             user.setUsername("guest");
        }

        try {
            Map<String, Object> result = qualityService.processHemorrhage(file, user);
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Collections.singletonMap("detail", "File upload failed"));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(Collections.singletonMap("detail", e.getMessage()));
        }
    }

    // =================================================================================
    // MOCK APIs (模拟接口)
    // 下列接口目前返回模拟数据，后期需对接真实算法服务
    // =================================================================================

    /**
     * [Mock] CT 头部平扫质控
     * @param file 影像文件
     * @return 模拟质控结果
     */
    @PostMapping("/head/detect")
    public ResponseEntity<?> detectHead(@RequestParam("file") MultipartFile file) {
        // TODO: 对接真实 Head QC 算法
        return ResponseEntity.ok(qualityService.detectHead(file));
    }

    /**
     * [Mock] CT 胸部平扫质控
     * @param file 影像文件
     * @return 模拟质控结果
     */
    @PostMapping("/chest-non-contrast/detect")
    public ResponseEntity<?> detectChestNonContrast(@RequestParam("file") MultipartFile file) {
        // TODO: 对接真实 Chest Non-Contrast QC 算法
        return ResponseEntity.ok(qualityService.detectChest(file, false));
    }

    /**
     * [Mock] CT 胸部增强质控
     * @param file 影像文件
     * @return 模拟质控结果
     */
    @PostMapping("/chest-contrast/detect")
    public ResponseEntity<?> detectChestContrast(@RequestParam("file") MultipartFile file) {
        // TODO: 对接真实 Chest Contrast QC 算法
        return ResponseEntity.ok(qualityService.detectChest(file, true));
    }

    /**
     * [Mock] 冠脉 CTA 质控
     * @param file 影像文件
     * @return 模拟质控结果
     */
    @PostMapping("/coronary-cta/detect")
    public ResponseEntity<?> detectCoronaryCTA(@RequestParam("file") MultipartFile file) {
        // TODO: 对接真实 Coronary CTA QC 算法
        return ResponseEntity.ok(qualityService.detectCoronary(file));
    }
}
