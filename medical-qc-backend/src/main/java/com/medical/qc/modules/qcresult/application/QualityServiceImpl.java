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

    public List<HemorrhageRecord> getHistory(Long userId) {
        return getHistory(userId, null);
    }

    public List<HemorrhageRecord> getHistory(Long userId, Integer limit) {
        return unifiedHemorrhageQueryService.getHistory(userId, normalizeHistoryLimit(limit));
    }

    public HemorrhageRecord getHistoryRecord(Long userId, Long recordId) {
        return unifiedHemorrhageQueryService.getHistoryRecord(userId, recordId);
    }

    public Map<String, Object> processHemorrhage(MultipartFile file,
                                                 User user,
                                                 String patientName,
                                                 String patientCode,
                                                 String examId,
                                                 String gender,
                                                 Integer age,
                                                 LocalDate studyDate,
                                                 String sourceMode) throws IOException {
        HemorrhagePreparedContext preparedContext = hemorrhagePreparationService.prepare(
                file,
                patientName,
                patientCode,
                examId,
                gender,
                age,
                studyDate,
                sourceMode);

        Map<String, Object> predictionResult = aiGateway.analyzeHemorrhage(preparedContext.analysisImagePath());
        if (predictionResult.containsKey("error")) {
            throw hemorrhageResultAssembler.buildModelServiceException(String.valueOf(predictionResult.get("error")));
        }

        HemorrhageRecord record = hemorrhageResultAssembler.buildRecord(user, examId, preparedContext, predictionResult);
        record.setPatientImagePath(preparedContext.savedImagePath());
        hemorrhageResultAssembler.enrichResponse(predictionResult, record, preparedContext);
        hemorrhageResultAssembler.appendPreview(
                predictionResult,
                preparedContext.analysisImagePath(),
                preparedContext.savedImagePath());
        record.setRawResultJson(objectMapper.writeValueAsString(
                hemorrhageResultAssembler.buildPersistedResult(predictionResult)));
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());

        Long recordId = unifiedHemorrhageWriteService.persist(
                user,
                record,
                sourceMode,
                preparedContext.scannerModel(),
                preparedContext.analysisImagePath(),
                preparedContext.savedImagePath());

        hemorrhageIssueSyncDispatcher.dispatch(recordId, user.getId());

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

    private Integer normalizeHistoryLimit(Integer limit) {
        if (limit == null || limit <= 0) {
            return null;
        }
        return Math.max(1, Math.min(limit, 20));
    }
}
