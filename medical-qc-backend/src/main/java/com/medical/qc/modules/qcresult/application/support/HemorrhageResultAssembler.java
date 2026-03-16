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
import java.util.List;
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
    // 这些常量直接供前端结果页展示。
    private static final String MODEL_NAME = "AdvancedHemorrhageModel";
    private static final String SCAN_REGION = "头颅平扫";
    private static final String INFERENCE_DEVICE = "cuda";

    /**
     * 将模型输出和预处理上下文装配为数据库记录实体。
     */
    public HemorrhageRecord buildRecord(User user,
                                        String examId,
                                        HemorrhagePreparedContext context,
                                        Map<String, Object> predictionResult) {
        HemorrhageRecord record = new HemorrhageRecord();
        // 患者、检查与来源基础字段来自预处理上下文。
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

        // 以下字段来自模型返回的原始载荷，按类型安全地映射到实体。
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

        // 主异常项和质控结论由统一支持类根据映射后的记录字段推导。
        record.setPrimaryIssue(HemorrhageIssueSupport.resolvePrimaryIssue(record));
        record.setQcStatus(HemorrhageIssueSupport.resolveQcStatus(record));
        return record;
    }

    /**
     * 向前端响应补充统一展示字段。
     */
    public void enrichResponse(Map<String, Object> predictionResult,
                               HemorrhageRecord record,
                               HemorrhagePreparedContext context) {
        // 这些字段供前端结果页和详情弹窗直接消费。
        predictionResult.put("prediction", record.getPrediction());
        predictionResult.put("primary_issue", record.getPrimaryIssue());
        predictionResult.put("primaryIssue", record.getPrimaryIssue());
        predictionResult.put("qc_status", record.getQcStatus());
        predictionResult.put("qcStatus", record.getQcStatus());
        predictionResult.put("patient_code", record.getPatientCode());
        predictionResult.put("gender", record.getGender());
        predictionResult.put("age", record.getAge());
        predictionResult.put("study_date", record.getStudyDate());
        predictionResult.put("source_mode", context.sourceMode());
        predictionResult.put("device", INFERENCE_DEVICE);
        predictionResult.put("model_name", MODEL_NAME);
        predictionResult.put("scan_region", SCAN_REGION);
        predictionResult.put("scanner_model", context.scannerModel());
        predictionResult.put("taskType", "hemorrhage");
        predictionResult.put("taskTypeName", "头部出血检测");
        predictionResult.put("mock", false);
        predictionResult.put("analysisMode", "real-model");
        predictionResult.put("analysisLabel", "真实模型推理");

        // 脑出血链路也补齐 patientInfo/qcItems/summary，便于统一做结果校验与任务中心展示。
        predictionResult.put("patientInfo", buildPatientInfo(record, context));
        predictionResult.put("qcItems", buildQcItems(record));
        predictionResult.put("summary", buildSummary(record));
    }

    /**
     * 追加图片尺寸、base64 预览和图片 URL。
     */
    public void appendPreview(Map<String, Object> predictionResult,
                              String analysisImagePath,
                              String savedImagePath) {
        try {
            Path imagePath = Paths.get(analysisImagePath);
            BufferedImage image = ImageIO.read(imagePath.toFile());
            if (image != null) {
                // BBox 叠加需要依赖图片宽高做比例换算。
                predictionResult.put("image_width", image.getWidth());
                predictionResult.put("image_height", image.getHeight());
            }

            // 结果页首屏优先使用 base64，避免额外图片请求。
            byte[] fileContent = Files.readAllBytes(imagePath);
            predictionResult.put("image_base64", Base64.getEncoder().encodeToString(fileContent));
        } catch (Exception ignore) {
            // 预览补充失败不影响主流程，前端可回退到 image_url。
        }

        predictionResult.put("image_url", "/" + savedImagePath);
    }

    /**
     * 构造写入 rawResultJson 的结果体，剔除过大的 base64 字段。
     */
    public Map<String, Object> buildPersistedResult(Map<String, Object> predictionResult) {
        Map<String, Object> persistedResult = new HashMap<>(predictionResult);
        persistedResult.remove("image_base64");
        return persistedResult;
    }

    /**
     * 将模型网关错误转换为更贴近业务语义的异常。
     */
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

    /**
     * 把模型的英文判定值转换为前端使用的中文结果。
     */
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

    /**
     * 优先使用患者编号，缺失时回退到检查号。
     */
    private String resolvePatientCode(String patientCode, String examId) {
        String normalizedPatientCode = normalizeText(patientCode);
        if (normalizedPatientCode != null) {
            return normalizedPatientCode;
        }
        return examId;
    }

    /**
     * 标准化文本输入。
     */
    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 构造统一患者信息结构，供任务中心、历史详情和前端结果页复用。
     */
    private Map<String, Object> buildPatientInfo(HemorrhageRecord record, HemorrhagePreparedContext context) {
        Map<String, Object> patientInfo = new HashMap<>();
        patientInfo.put("name", record.getPatientName());
        patientInfo.put("patientId", record.getPatientCode());
        patientInfo.put("patientCode", record.getPatientCode());
        patientInfo.put("studyId", record.getExamId());
        patientInfo.put("accessionNumber", record.getExamId());
        patientInfo.put("gender", record.getGender());
        patientInfo.put("age", record.getAge());
        patientInfo.put("studyDate", record.getStudyDate() == null ? null : record.getStudyDate().toString());
        patientInfo.put("device", context.scannerModel());
        patientInfo.put("sourceMode", context.sourceMode());
        patientInfo.put("sourceLabel", "pacs".equals(context.sourceMode()) ? "PACS 调取" : "本地上传");
        patientInfo.put("originalFilename", Paths.get(context.savedImagePath()).getFileName().toString());
        patientInfo.put("scanRegion", SCAN_REGION);
        return patientInfo;
    }

    /**
     * 把脑出血原始多任务输出转成统一质控项列表。
     */
    private List<Map<String, Object>> buildQcItems(HemorrhageRecord record) {
        return List.of(
                buildQcItem(
                        "HEMORRHAGE_DETECTION",
                        "脑出血检测",
                        "检测是否存在脑实质内高密度出血灶。",
                        "出血".equals(record.getPrediction()) ? "不合格" : "合格",
                        "出血".equals(record.getPrediction())
                                ? "检测到疑似脑出血表现，请立即复核原始影像。"
                                : "未检测到明显脑出血表现。"),
                buildQcItem(
                        "MIDLINE_SHIFT",
                        "中线偏移",
                        "检测脑中线结构是否发生明显偏移。",
                        Boolean.TRUE.equals(record.getMidlineShift()) ? "不合格" : "合格",
                        Boolean.TRUE.equals(record.getMidlineShift())
                                ? defaultText(record.getMidlineDetail(), "检测到中线偏移表现。")
                                : "中线结构居中。"),
                buildQcItem(
                        "VENTRICLE_STRUCTURE",
                        "脑室结构",
                        "检测脑室系统形态及密度是否存在异常。",
                        Boolean.TRUE.equals(record.getVentricleIssue()) ? "不合格" : "合格",
                        Boolean.TRUE.equals(record.getVentricleIssue())
                                ? defaultText(record.getVentricleDetail(), "检测到脑室结构异常。")
                                : "脑室系统形态正常。"));
    }

    /**
     * 依据统一质控项重建摘要，避免页面和历史记录再各自推导一遍。
     */
    private Map<String, Object> buildSummary(HemorrhageRecord record) {
        List<Map<String, Object>> qcItems = buildQcItems(record);
        int failCount = (int) qcItems.stream().filter(item -> "不合格".equals(item.get("status"))).count();
        int abnormalCount = failCount;
        int totalItems = qcItems.size();
        int qualityScore = totalItems == 0 ? 0 : (int) Math.round((totalItems - abnormalCount) * 100.0D / totalItems);

        Map<String, Object> summary = new HashMap<>();
        summary.put("totalItems", totalItems);
        summary.put("abnormalCount", abnormalCount);
        summary.put("failCount", failCount);
        summary.put("reviewCount", 0);
        summary.put("qualityScore", qualityScore);
        summary.put("result", record.getQcStatus());
        summary.put("primaryIssue", record.getPrimaryIssue());
        return summary;
    }

    /**
     * 统一构造脑出血检测的单个质控项。
     */
    private Map<String, Object> buildQcItem(String key,
                                            String name,
                                            String description,
                                            String status,
                                            String detail) {
        Map<String, Object> qcItem = new HashMap<>();
        qcItem.put("key", key);
        qcItem.put("name", name);
        qcItem.put("description", description);
        qcItem.put("status", status);
        qcItem.put("detail", detail);
        return qcItem;
    }

    /**
     * 返回第一个可用文案。
     */
    private String defaultText(String value, String fallback) {
        String normalizedValue = normalizeText(value);
        return normalizedValue == null ? fallback : normalizedValue;
    }
}

