package com.medical.qc.modules.qcresult.application.support;

import com.medical.qc.modules.qcresult.model.HemorrhageRecord;
import com.medical.qc.modules.auth.persistence.entity.User;
import com.medical.qc.support.HemorrhageIssueSupport;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 脑出血检测结果装配服务。
 *
 * <p>负责将推理结果映射为历史记录实体和前端回显载荷。</p>
 */
@Component
public class HemorrhageResultAssembler {
    private static final String MODEL_NAME = "AdvancedHemorrhageModel";
    private static final String SCAN_REGION = "头颅平扫";
    private static final String INFERENCE_DEVICE = "cuda";

    public HemorrhageRecord buildRecord(User user,
                                        String examId,
                                        HemorrhagePreparedContext context,
                                        Map<String, Object> predictionResult) {
        HemorrhageRecord record = new HemorrhageRecord();
        record.setUserId(user.getId());
        record.setPatientName(context.patientName());
        record.setPatientCode(resolvePatientCode(context.patientCode(), normalizeText(examId)));
        record.setExamId(normalizeText(examId));
        record.setGender(context.gender());
        record.setAge(context.age());
        record.setStudyDate(context.studyDate());
        record.setImagePath(context.savedImagePath());
        record.setPrediction(translatePrediction(String.valueOf(predictionResult.get("prediction"))));
        record.setDevice(INFERENCE_DEVICE);

        if (predictionResult.get("confidence_level") != null) {
            record.setConfidenceLevel(String.valueOf(predictionResult.get("confidence_level")));
        }
        if (predictionResult.get("hemorrhage_probability") instanceof Number value) {
            record.setHemorrhageProbability(value.floatValue());
        }
        if (predictionResult.get("no_hemorrhage_probability") instanceof Number value) {
            record.setNoHemorrhageProbability(value.floatValue());
        }
        if (predictionResult.get("analysis_duration") instanceof Number value) {
            record.setAnalysisDuration(value.floatValue());
        }
        if (predictionResult.get("midline_shift") instanceof Boolean value) {
            record.setMidlineShift(value);
        }
        if (predictionResult.get("shift_score") instanceof Number value) {
            record.setShiftScore(value.floatValue());
        }
        if (predictionResult.get("midline_detail") != null) {
            record.setMidlineDetail(String.valueOf(predictionResult.get("midline_detail")));
        }
        if (predictionResult.get("ventricle_issue") instanceof Boolean value) {
            record.setVentricleIssue(value);
        }
        if (predictionResult.get("ventricle_detail") != null) {
            record.setVentricleDetail(String.valueOf(predictionResult.get("ventricle_detail")));
        }

        record.setPrimaryIssue(HemorrhageIssueSupport.resolvePrimaryIssue(record));
        record.setQcStatus(HemorrhageIssueSupport.resolveQcStatus(record));
        return record;
    }

    public void enrichResponse(Map<String, Object> predictionResult,
                               HemorrhageRecord record,
                               HemorrhagePreparedContext context) {
        predictionResult.put("prediction", record.getPrediction());
        predictionResult.put("primary_issue", record.getPrimaryIssue());
        predictionResult.put("qc_status", record.getQcStatus());
        predictionResult.put("patient_code", record.getPatientCode());
        predictionResult.put("gender", record.getGender());
        predictionResult.put("age", record.getAge());
        predictionResult.put("study_date", record.getStudyDate());
        predictionResult.put("source_mode", context.sourceMode());
        predictionResult.put("device", INFERENCE_DEVICE);
        predictionResult.put("model_name", MODEL_NAME);
        predictionResult.put("scan_region", SCAN_REGION);
        predictionResult.put("scanner_model", context.scannerModel());
    }

    public void appendPreview(Map<String, Object> predictionResult,
                              String analysisImagePath,
                              String savedImagePath) {
        try {
            Path imagePath = Paths.get(analysisImagePath);
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image != null) {
                predictionResult.put("image_width", image.getWidth());
                predictionResult.put("image_height", image.getHeight());
            }

            byte[] fileContent = Files.readAllBytes(imagePath);
            predictionResult.put("image_base64", Base64.getEncoder().encodeToString(fileContent));
        } catch (Exception ignore) {
        }

        predictionResult.put("image_url", "/" + savedImagePath);
    }

    public Map<String, Object> buildPersistedResult(Map<String, Object> predictionResult) {
        Map<String, Object> persistedResult = new HashMap<>(predictionResult);
        persistedResult.remove("image_base64");
        return persistedResult;
    }

    public RuntimeException buildModelServiceException(String errorMessage) {
        String normalizedMessage = errorMessage == null ? "未知错误" : errorMessage.trim();
        String lowerCaseMessage = normalizedMessage.toLowerCase(Locale.ROOT);

        if (lowerCaseMessage.contains("failed to connect")
                || lowerCaseMessage.contains("model not loaded")
                || lowerCaseMessage.contains("timed out")
                || lowerCaseMessage.contains("cuda")
                || lowerCaseMessage.contains("1011")
                || lowerCaseMessage.contains("internal error")) {
            return new IllegalStateException("脑出血模型服务暂不可用，请检查模型服务、GPU/CUDA 或稍后重试");
        }

        if (lowerCaseMessage.contains("cannot identify image file")
                || lowerCaseMessage.contains("failed to read image")) {
            return new IllegalArgumentException("上传文件不是可识别的图片，请使用 PNG/JPG/JPEG/BMP 格式");
        }

        return new IllegalStateException("脑出血模型推理失败：" + normalizedMessage);
    }

    private String translatePrediction(String rawPrediction) {
        if (rawPrediction == null) {
            return "未出血";
        }

        String normalizedPrediction = rawPrediction.trim();
        if ("Hemorrhage".equalsIgnoreCase(normalizedPrediction) || "出血".equals(normalizedPrediction)) {
            return "出血";
        }

        return "未出血";
    }

    private String resolvePatientCode(String patientCode, String examId) {
        String normalizedPatientCode = normalizeText(patientCode);
        if (normalizedPatientCode != null) {
            return normalizedPatientCode;
        }
        return examId;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

