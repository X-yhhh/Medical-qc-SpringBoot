package com.medical.qc.modules.qcresult.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.messaging.HemorrhageIssueSyncDispatcher;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.modules.qcresult.application.support.HemorrhagePreparationService;
import com.medical.qc.modules.qcresult.application.support.HemorrhagePreparedContext;
import com.medical.qc.modules.qcresult.application.support.HemorrhageResultAssembler;
import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.unified.application.UnifiedHemorrhageQueryService;
import com.medical.qc.modules.unified.application.support.UnifiedHemorrhageWriteService;
import com.medical.qc.shared.ai.AiGateway;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 脑出血检测服务实现。
 *
 * <p>统一使用新模型表承接写入与历史查询，对外继续保持原有接口返回结构。</p>
 */
@Service
public class QualityServiceImpl {
    // 异常工单同步调度器，用于把不合格脑出血记录送入异常汇总链路。
    private final HemorrhageIssueSyncDispatcher hemorrhageIssueSyncDispatcher;
    private final ObjectMapper objectMapper;
    private final AiGateway aiGateway;
    private final HemorrhagePreparationService hemorrhagePreparationService;
    private final HemorrhageResultAssembler hemorrhageResultAssembler;
    private final UnifiedHemorrhageQueryService unifiedHemorrhageQueryService;
    private final UnifiedHemorrhageWriteService unifiedHemorrhageWriteService;

    public QualityServiceImpl(HemorrhageIssueSyncDispatcher hemorrhageIssueSyncDispatcher,
                              ObjectMapper objectMapper,
                              AiGateway aiGateway,
                              HemorrhagePreparationService hemorrhagePreparationService,
                              HemorrhageResultAssembler hemorrhageResultAssembler,
                              UnifiedHemorrhageQueryService unifiedHemorrhageQueryService,
                              UnifiedHemorrhageWriteService unifiedHemorrhageWriteService) {
        this.hemorrhageIssueSyncDispatcher = hemorrhageIssueSyncDispatcher;
        this.objectMapper = objectMapper;
        this.aiGateway = aiGateway;
        this.hemorrhagePreparationService = hemorrhagePreparationService;
        this.hemorrhageResultAssembler = hemorrhageResultAssembler;
        this.unifiedHemorrhageQueryService = unifiedHemorrhageQueryService;
        this.unifiedHemorrhageWriteService = unifiedHemorrhageWriteService;
    }

    /**
     * 查询历史记录，默认不限制条数。
     */
    public List<HemorrhageRecord> getHistory(Long userId) {
        return getHistory(userId, null);
    }

    /**
     * 查询历史记录，并对前端传入的 limit 做保护性归一化。
     */
    public List<HemorrhageRecord> getHistory(Long userId, Integer limit) {
        return unifiedHemorrhageQueryService.getHistory(userId, normalizeHistoryLimit(limit));
    }

    /**
     * 查询指定历史记录。
     */
    public HemorrhageRecord getHistoryRecord(Long userId, Long recordId) {
        return unifiedHemorrhageQueryService.getHistoryRecord(userId, recordId);
    }

    /**
     * 执行脑出血检测完整链路。
     * 数据链路：输入预处理 -> AI 推理 -> 结果装配 -> 持久化 -> 异常工单同步 -> 响应前端。
     */
    public Map<String, Object> processHemorrhage(MultipartFile file,
                                                 User user,
                                                 String patientName,
                                                 String patientCode,
                                                 String examId,
                                                 String gender,
                                                 Integer age,
                                                 LocalDate studyDate,
                                                 String sourceMode) throws IOException {
        // 统一处理本地上传/PACS 两种输入模式，并补齐患者与设备上下文。
        HemorrhagePreparedContext preparedContext = hemorrhagePreparationService.prepare(
                file,
                patientName,
                patientCode,
                examId,
                gender,
                age,
                studyDate,
                sourceMode);

        // AI 网关只关心待分析图片绝对路径。
        Map<String, Object> predictionResult = aiGateway.analyzeHemorrhage(preparedContext.analysisImagePath());
        if (predictionResult.containsKey("error")) {
            throw hemorrhageResultAssembler.buildModelServiceException(String.valueOf(predictionResult.get("error")));
        }

        // 将推理结果映射为数据库记录实体。
        HemorrhageRecord record = hemorrhageResultAssembler.buildRecord(user, examId, preparedContext, predictionResult);
        record.setPatientImagePath(preparedContext.savedImagePath());
        // 同时把前端需要的附加字段直接写回响应对象。
        hemorrhageResultAssembler.enrichResponse(predictionResult, record, preparedContext);
        hemorrhageResultAssembler.appendPreview(
                predictionResult,
                preparedContext.analysisImagePath(),
                preparedContext.savedImagePath());
        // rawResultJson 保存去除大体积 base64 后的结构化原始结果，供历史详情回放。
        record.setRawResultJson(objectMapper.writeValueAsString(
                hemorrhageResultAssembler.buildPersistedResult(predictionResult)));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());

        // 将记录写入统一模型，并返回新记录主键。
        Long recordId = unifiedHemorrhageWriteService.persist(
                user,
                record,
                sourceMode,
                preparedContext.scannerModel(),
                preparedContext.analysisImagePath(),
                preparedContext.savedImagePath());

        // 持久化成功后异步触发异常工单同步。
        hemorrhageIssueSyncDispatcher.dispatch(recordId, user.getId());

        // 继续补充前端页面直接依赖的基础字段。
        predictionResult.put("record_id", recordId);
        predictionResult.put("patient_name", record.getPatientName());
        predictionResult.put("patient_code", record.getPatientCode());
        predictionResult.put("exam_id", record.getExamId());
        predictionResult.put("gender", record.getGender());
        predictionResult.put("age", record.getAge());
        predictionResult.put("study_date", record.getStudyDate());
        predictionResult.put("created_at", record.getCreatedAt());
        predictionResult.put("updated_at", record.getUpdatedAt());
        return predictionResult;
    }

    /**
     * 归一化历史记录条数上限，防止前端传入异常值。
     */
    private Integer normalizeHistoryLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return null;
        }
        return Math.max(1, Math.min(limit, 20));
    }
}
