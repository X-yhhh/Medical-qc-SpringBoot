package com.medical.qc.controller;

import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import com.medical.qc.service.MockQualityTaskService;
import com.medical.qc.service.QualityService;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.SessionUserSupport;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
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
 * 质控模块 API 控制器。
 * 负责处理脑出血检测（真实）和其他 CT 质控请求。
 * 其中其余四个质控模块当前已改为“提交异步任务 + 轮询结果”的 mock 流程。
 *
 * <p>该模块属于医生工作台能力，管理员不可直接访问。</p>
 */
@RestController
@RequestMapping("/api/v1/quality")
public class QualityController {

    @Autowired
    private QualityService qualityService;

    @Autowired
    private MockQualityTaskService mockQualityTaskService;

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
     * @param file 上传的 CT 影像（本地模式必传）
     * @param patientName 患者姓名
     * @param patientCode 患者编号
     * @param examId 检查 ID
     * @param gender 性别
     * @param age 年龄
     * @param studyDate 检查日期
     * @param sourceMode 数据来源模式：local/pacs
     * @param session 当前会话
     * @return 检测结果（包含是否出血、概率、中线偏移等）
     */
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
        Map<String, Object> result = qualityService.processHemorrhage(
                file,
                user,
                patientName,
                patientCode,
                examId,
                gender,
                age,
                studyDate,
                sourceMode);
        return ResponseEntity.ok(result);
    }

    // =================================================================================
    // MOCK Async APIs (模拟异步接口)
    // 下列接口当前返回“任务受理回执”，最终结果需通过任务查询接口轮询获取。
    // 后期接入真实算法时，优先替换 MQ 消费者中的任务执行逻辑，无需改动控制层协议。
    // =================================================================================

    /**
     * [Mock] CT 头部平扫质控。
     *
     * @param file 影像文件；本地上传模式必传，PACS 模式可为空
     * @param patientName 患者姓名
     * @param examId 检查 ID
     * @param sourceMode 任务来源模式：local / pacs
     * @param session 当前会话
     * @return 异步任务受理结果（包含 taskId 和轮询地址）
     */
    @PostMapping("/head/detect")
    public ResponseEntity<?> detectHead(@RequestParam(value = "file", required = false) MultipartFile file,
                                        @RequestParam("patient_name") String patientName,
                                        @RequestParam("exam_id") String examId,
                                        @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                        HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(
                mockQualityTaskService.submitTask(MockQualityAnalysisSupport.TASK_TYPE_HEAD,
                        file,
                        patientName,
                        examId,
                        sourceMode,
                        user));
    }

    /**
     * [Mock] CT 胸部平扫质控。
     *
     * @param file 影像文件；本地上传模式必传，PACS 模式可为空
     * @param patientName 患者姓名
     * @param examId 检查 ID
     * @param sourceMode 任务来源模式：local / pacs
     * @param session 当前会话
     * @return 异步任务受理结果（包含 taskId 和轮询地址）
     */
    @PostMapping("/chest-non-contrast/detect")
    public ResponseEntity<?> detectChestNonContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                    @RequestParam("patient_name") String patientName,
                                                    @RequestParam("exam_id") String examId,
                                                    @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                    HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(
                mockQualityTaskService.submitTask(MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST,
                        file,
                        patientName,
                        examId,
                        sourceMode,
                        user));
    }

    /**
     * [Mock] CT 胸部增强质控。
     *
     * @param file 影像文件；本地上传模式必传，PACS 模式可为空
     * @param patientName 患者姓名
     * @param examId 检查 ID
     * @param sourceMode 任务来源模式：local / pacs
     * @param session 当前会话
     * @return 异步任务受理结果（包含 taskId 和轮询地址）
     */
    @PostMapping("/chest-contrast/detect")
    public ResponseEntity<?> detectChestContrast(@RequestParam(value = "file", required = false) MultipartFile file,
                                                @RequestParam("patient_name") String patientName,
                                                @RequestParam("exam_id") String examId,
                                                @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                                HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(
                mockQualityTaskService.submitTask(MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST,
                        file,
                        patientName,
                        examId,
                        sourceMode,
                        user));
    }

    /**
     * [Mock] 冠脉 CTA 质控。
     *
     * @param file 影像文件；本地上传模式必传，PACS 模式可为空
     * @param patientName 患者姓名
     * @param examId 检查 ID
     * @param sourceMode 任务来源模式：local / pacs
     * @param session 当前会话
     * @return 异步任务受理结果（包含 taskId 和轮询地址）
     */
    @PostMapping("/coronary-cta/detect")
    public ResponseEntity<?> detectCoronaryCTA(@RequestParam(value = "file", required = false) MultipartFile file,
                                               @RequestParam("patient_name") String patientName,
                                               @RequestParam("exam_id") String examId,
                                               @RequestParam(value = "source_mode", defaultValue = "local") String sourceMode,
                                               HttpSession session) throws IOException {
        User user = requireDoctorSession(session);
        return ResponseEntity.accepted().body(
                mockQualityTaskService.submitTask(MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA,
                        file,
                        patientName,
                        examId,
                        sourceMode,
                        user));
    }

    /**
     * 查询 mock 质控异步任务状态与结果。
     *
     * <p>当前提供统一的轮询接口，后续前端接入时只需在提交后轮询该地址即可。</p>
     *
     * @param taskId 任务 ID
     * @param session 当前会话
     * @return 任务状态、时间戳和最终结果
     */
    @GetMapping("/tasks/{taskId}")
    public ResponseEntity<?> getMockTaskDetail(@PathVariable("taskId") String taskId, HttpSession session) {
        User user = requireDoctorSession(session);
        return ResponseEntity.ok(mockQualityTaskService.getTaskDetail(taskId, user.getId()));
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
