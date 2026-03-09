package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.messaging.HemorrhageIssueSyncDispatcher;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.PacsStudyCache;
import com.medical.qc.entity.User;
import com.medical.qc.mapper.HemorrhageRecordMapper;
import com.medical.qc.service.PacsService;
import com.medical.qc.service.QualityPatientInfoService;
import com.medical.qc.service.QualityService;
import com.medical.qc.support.HemorrhageIssueSupport;
import com.medical.qc.support.MockQualityAnalysisSupport;
import com.medical.qc.support.QualityPatientTaskSupport;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
public class QualityServiceImpl implements QualityService {
    private static final String HEMORRHAGE_MODEL_NAME = "AdvancedHemorrhageModel";
    private static final String HEMORRHAGE_SCAN_REGION = "头颅平扫";
    private static final String HEMORRHAGE_SCANNER_MODEL = "头颅 CT 标准采集设备";
    private static final String HEMORRHAGE_INFERENCE_DEVICE = "cuda";

    @Autowired
    private HemorrhageRecordMapper hemorrhageRecordMapper;

    @Autowired
    private HemorrhageIssueSyncDispatcher hemorrhageIssueSyncDispatcher;

    @Autowired
    private PacsService pacsService;

    @Autowired
    private QualityPatientInfoService qualityPatientInfoService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${python.model_server.url:ws://localhost:8765}")
    private String modelServerUrl;

    private final Path rootLocation = Paths.get("uploads");

    @Override
    public List<HemorrhageRecord> getHistory(Long userId) {
        return getHistory(userId, null);
    }

    /**
     * 获取用户脑出血检测历史记录。
     * limit 为空时返回全部记录，否则仅返回最近 N 条。
     *
     * @param userId 用户 ID
     * @param limit 返回数量上限
     * @return 历史记录列表
     */
    @Override
    public List<HemorrhageRecord> getHistory(Long userId, Integer limit) {
        List<HemorrhageRecord> history;
        if (userId == null) {
            QueryWrapper<HemorrhageRecord> queryWrapper = new QueryWrapper<HemorrhageRecord>()
                    .orderByDesc("created_at");

            if (limit != null && limit > 0) {
                queryWrapper.last("LIMIT " + normalizeHistoryLimit(limit));
            }

            history = hemorrhageRecordMapper.selectList(queryWrapper);
        } else if (limit == null || limit <= 0) {
            history = hemorrhageRecordMapper.findByUserId(userId);
        } else {
            history = hemorrhageRecordMapper.findRecentByUserId(userId, normalizeHistoryLimit(limit));
        }

        history.forEach(this::populatePrimaryIssue);
        return history;
    }

    /**
     * ???????????????????
     *
     * @param userId   ?? ID
     * @param recordId ???? ID
     * @return ??????
     */
    @Override
    public HemorrhageRecord getHistoryRecord(Long userId, Long recordId) {
        if (userId == null || recordId == null) {
            return null;
        }

        HemorrhageRecord record = hemorrhageRecordMapper.selectOne(new QueryWrapper<HemorrhageRecord>()
                .eq("user_id", userId)
                .eq("id", recordId)
                .last("LIMIT 1"));

        populatePrimaryIssue(record);
        return record;
    }

    /**
     * 处理脑出血检测上传与模型分析流程。
     *
     * @param file        上传的影像文件
     * @param user        当前登录用户
     * @param patientName 患者姓名
     * @param patientCode 患者编号
     * @param examId      检查 ID
     * @param gender      性别
     * @param age         年龄
     * @param studyDate   检查日期
     * @return 模型分析结果（包含 prediction、概率、耗时、影像回显信息等）
     * @throws IOException      文件保存或读取失败时抛出
     * @throws RuntimeException 模型服务返回错误或解析失败时抛出
     */
    @Override
    public Map<String, Object> processHemorrhage(MultipartFile file,
                                                 User user,
                                                 String patientName,
                                                 String patientCode,
                                                 String examId,
                                                 String gender,
                                                 Integer age,
                                                 LocalDate studyDate,
                                                 String sourceMode)
            throws IOException {

        String normalizedSourceMode = MockQualityAnalysisSupport.normalizeSourceMode(sourceMode);
        String imagePathForAnalysis;
        String savedImagePath;
        String resolvedPatientName = normalizeText(patientName);
        String resolvedPatientCode = normalizeText(patientCode);
        String resolvedGender = normalizeText(gender);
        Integer resolvedAge = normalizeAge(age);
        LocalDate resolvedStudyDate = studyDate;
        String resolvedScannerModel = HEMORRHAGE_SCANNER_MODEL;

        // PACS模式：从数据库查询图片路径
        if ("pacs".equalsIgnoreCase(normalizedSourceMode)) {
            if (examId == null || examId.trim().isEmpty()) {
                throw new IllegalArgumentException("PACS模式下必须提供检查号(examId)");
            }

            // 查询PACS记录
            List<PacsStudyCache> studies = pacsService.searchStudies(
                    QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE,
                    null,
                    null,
                    examId,
                    null,
                    null);
            if (studies.isEmpty()) {
                throw new IllegalArgumentException("未找到检查号为 " + examId + " 的PACS记录");
            }

            PacsStudyCache pacsStudy = studies.get(0);
            if (pacsStudy.getImageFilePath() == null || pacsStudy.getImageFilePath().trim().isEmpty()) {
                throw new IllegalArgumentException("PACS记录中未配置影像文件路径");
            }

            imagePathForAnalysis = pacsStudy.getImageFilePath();

            // 验证文件是否存在
            Path pacsImagePath = Paths.get(imagePathForAnalysis);
            if (!Files.exists(pacsImagePath)) {
                throw new IllegalArgumentException("PACS影像文件不存在: " + imagePathForAnalysis);
            }

            // 将 PACS 原始影像复制到 uploads 目录，保证历史记录回显时可通过 /uploads/** 访问。
            savedImagePath = copyPacsImageToUploads(pacsImagePath, examId);

            // 当请求参数缺失时，自动回填 PACS 缓存中的患者信息，避免前端仅传 examId 时落库字段为空。
            resolvedPatientName = firstNonBlank(resolvedPatientName, normalizeText(pacsStudy.getPatientName()));
            resolvedPatientCode = firstNonBlank(resolvedPatientCode,
                    normalizeText(pacsStudy.getPatientId()),
                    normalizeText(examId));
            resolvedGender = firstNonBlank(resolvedGender, normalizeText(pacsStudy.getGender()));
            resolvedAge = resolvedAge != null ? resolvedAge : normalizeAge(pacsStudy.getAge());
            resolvedStudyDate = resolvedStudyDate != null ? resolvedStudyDate : pacsStudy.getStudyDate();
            resolvedScannerModel = resolvePacsScannerModel(pacsStudy);
        }
        // 本地上传模式
        else {
            if (file == null || file.isEmpty()) {
                throw new IllegalArgumentException("本地上传模式下必须提供影像文件");
            }

            validateHemorrhageFile(file);

            if (Files.notExists(rootLocation)) {
                Files.createDirectories(rootLocation);
            }

            String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
            Path destinationFile = rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath();
            file.transferTo(destinationFile.toFile());

            imagePathForAnalysis = destinationFile.toString();
            savedImagePath = "uploads/" + filename;
        }

        // Call Python Script via WebSocket
        Map<String, Object> predictionResult = callPythonModelViaWebSocket(imagePathForAnalysis);

        if (predictionResult.containsKey("error")) {
            throw buildModelServiceException(String.valueOf(predictionResult.get("error")));
        }

        // Save to DB
        HemorrhageRecord record = new HemorrhageRecord();
        record.setUserId(user.getId());
        String normalizedExamId = normalizeText(examId);
        record.setPatientName(resolvedPatientName);
        record.setPatientCode(resolvePatientCode(resolvedPatientCode, normalizedExamId));
        record.setExamId(normalizedExamId);
        record.setGender(resolvedGender);
        record.setAge(resolvedAge);
        record.setStudyDate(resolvedStudyDate);
        record.setImagePath(savedImagePath);

        // Translate Prediction to Chinese (Front-end expectation)
        String rawPrediction = String.valueOf(predictionResult.get("prediction"));
        String translatedPrediction = translatePrediction(rawPrediction);

        record.setPrediction(translatedPrediction);
        predictionResult.put("prediction", translatedPrediction); // Update map for immediate return

        if (predictionResult.get("confidence_level") != null) {
            record.setConfidenceLevel(String.valueOf(predictionResult.get("confidence_level")));
        }

        if (predictionResult.get("hemorrhage_probability") instanceof Number) {
            record.setHemorrhageProbability(((Number) predictionResult.get("hemorrhage_probability")).floatValue());
        }

        if (predictionResult.get("no_hemorrhage_probability") instanceof Number) {
            record.setNoHemorrhageProbability(
                    ((Number) predictionResult.get("no_hemorrhage_probability")).floatValue());
        }

        if (predictionResult.get("analysis_duration") instanceof Number) {
            record.setAnalysisDuration(((Number) predictionResult.get("analysis_duration")).floatValue());
        }

        if (predictionResult.get("midline_shift") instanceof Boolean) {
            record.setMidlineShift((Boolean) predictionResult.get("midline_shift"));
        }
        if (predictionResult.get("shift_score") instanceof Number) {
            record.setShiftScore(((Number) predictionResult.get("shift_score")).floatValue());
        }
        if (predictionResult.get("midline_detail") != null) {
            record.setMidlineDetail(String.valueOf(predictionResult.get("midline_detail")));
        }
        if (predictionResult.get("ventricle_issue") instanceof Boolean) {
            record.setVentricleIssue((Boolean) predictionResult.get("ventricle_issue"));
        }
        if (predictionResult.get("ventricle_detail") != null) {
            record.setVentricleDetail(String.valueOf(predictionResult.get("ventricle_detail")));
        }
        record.setDevice(HEMORRHAGE_INFERENCE_DEVICE);

        populatePrimaryIssue(record);
        String qcStatus = HemorrhageIssueSupport.resolveQcStatus(record);
        record.setQcStatus(qcStatus);
        predictionResult.put("primary_issue", record.getPrimaryIssue());
        predictionResult.put("qc_status", qcStatus); // 供首页最近访问和历史列表直接复用
        predictionResult.put("patient_code", record.getPatientCode());
        predictionResult.put("gender", record.getGender());
        predictionResult.put("age", record.getAge());
        predictionResult.put("study_date", record.getStudyDate());
        predictionResult.put("source_mode", normalizedSourceMode);
        predictionResult.put("device", HEMORRHAGE_INFERENCE_DEVICE);
        predictionResult.put("model_name", HEMORRHAGE_MODEL_NAME);
        predictionResult.put("scan_region", HEMORRHAGE_SCAN_REGION);
        predictionResult.put("scanner_model", resolvedScannerModel);

        // Append image meta and URL for frontend rendering
        try {
            Path imagePath = Paths.get(imagePathForAnalysis);
            BufferedImage img = ImageIO.read(imagePath.toFile());
            if (img != null) {
                predictionResult.put("image_width", img.getWidth());
                predictionResult.put("image_height", img.getHeight());
            }
            // Convert to Base64 for immediate display without static resource config issues
            byte[] fileContent = Files.readAllBytes(imagePath);
            String encodedString = Base64.getEncoder().encodeToString(fileContent);
            predictionResult.put("image_base64", encodedString);
        } catch (Exception e) {
            e.printStackTrace();
        }
        predictionResult.put("image_url", "/" + savedImagePath);

        // Exclude base64 preview from persisted raw result to keep DB payload compact.
        Map<String, Object> persistedResult = new HashMap<>(predictionResult);
        persistedResult.remove("image_base64");
        record.setRawResultJson(objectMapper.writeValueAsString(persistedResult));

        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(record.getCreatedAt());
        hemorrhageRecordMapper.insert(record);

        hemorrhageIssueSyncDispatcher.dispatch(record);

        // 返回数据库真实落库后的补充信息，便于前端严格按后端结果回显。
        predictionResult.put("record_id", record.getId());
        predictionResult.put("patient_name", record.getPatientName());
        predictionResult.put("patient_code", record.getPatientCode());
        predictionResult.put("exam_id", record.getExamId());
        predictionResult.put("gender", record.getGender());
        predictionResult.put("age", record.getAge());
        predictionResult.put("study_date", record.getStudyDate());
        predictionResult.put("created_at", record.getCreatedAt());
        predictionResult.put("updated_at", record.getUpdatedAt());
        predictionResult.put("device", HEMORRHAGE_INFERENCE_DEVICE);

        qualityPatientInfoService.upsertPatientByAccessionNumber(
                QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE,
                record.getPatientCode(),
                record.getPatientName(),
                record.getExamId(),
                record.getGender(),
                record.getAge(),
                record.getStudyDate(),
                record.getImagePath());

        return predictionResult;
    }

    /**
     * 统一限制历史记录查询数量，避免首页等轻量场景拉取过多数据。
     *
     * @param limit 原始数量
     * @return 清洗后的安全数量
     */
    private int normalizeHistoryLimit(Integer limit) {
        return Math.max(1, Math.min(limit, 20));
    }

    /**
     * 将 PACS 原始影像复制到本地 uploads 目录，保证历史记录能够稳定回显预览图。
     *
     * @param pacsImagePath PACS 原始影像绝对路径
     * @param examId 检查号
     * @return 可被前端静态资源访问的相对路径，如 uploads/pacs/xxx.png
     * @throws IOException 文件复制失败时抛出
     */
    private String copyPacsImageToUploads(Path pacsImagePath, String examId) throws IOException {
        Path pacsUploadDir = rootLocation.resolve("pacs");
        if (Files.notExists(pacsUploadDir)) {
            Files.createDirectories(pacsUploadDir);
        }

        String safeExamId = normalizeText(examId);
        if (safeExamId == null) {
            safeExamId = UUID.randomUUID().toString();
        }
        safeExamId = safeExamId.replaceAll("[^a-zA-Z0-9_-]", "_");

        String extension = resolveFileExtension(pacsImagePath.getFileName() == null
                ? ""
                : pacsImagePath.getFileName().toString());
        String targetFilename = safeExamId + "_" + UUID.randomUUID() + extension;
        Path destinationFile = pacsUploadDir.resolve(targetFilename).normalize().toAbsolutePath();

        Files.copy(pacsImagePath, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        return "uploads/pacs/" + targetFilename;
    }

    /**
     * 解析文件扩展名，无法识别时默认回退到 .png。
     *
     * @param filename 原始文件名
     * @return 安全的文件扩展名
     */
    private String resolveFileExtension(String filename) {
        if (filename == null || filename.isBlank() || !filename.contains(".")) {
            return ".png";
        }

        String extension = filename.substring(filename.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        if (extension.matches("\\.(png|jpg|jpeg|bmp)")) {
            return extension;
        }
        return ".png";
    }

    /**
     * 返回第一个非空白字符串，用于优先采用请求参数，否则回退到 PACS 缓存字段。
     *
     * @param values 候选字符串列表
     * @return 第一个有效字符串；若均为空则返回 null
     */
    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            String normalizedValue = normalizeText(value);
            if (normalizedValue != null) {
                return normalizedValue;
            }
        }
        return null;
    }

    /**
     * 将 PACS 缓存中的厂商与型号拼接为前端展示用扫描设备名称。
     *
     * @param pacsStudy PACS 检查缓存记录
     * @return 设备名称
     */
    private String resolvePacsScannerModel(PacsStudyCache pacsStudy) {
        if (pacsStudy == null) {
            return HEMORRHAGE_SCANNER_MODEL;
        }

        String manufacturer = normalizeText(pacsStudy.getManufacturer());
        String modelName = normalizeText(pacsStudy.getModelName());
        String mergedName = Stream.of(manufacturer, modelName)
                .filter(Objects::nonNull)
                .reduce((left, right) -> left + " " + right)
                .orElse(null);
        return mergedName == null ? HEMORRHAGE_SCANNER_MODEL : mergedName;
    }

    private String normalizeText(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String resolvePatientCode(String patientCode, String examId) {
        String normalizedPatientCode = normalizeText(patientCode);
        if (normalizedPatientCode != null) {
            return normalizedPatientCode;
        }

        return examId;
    }

    private Integer normalizeAge(Integer age) {
        if (age == null) {
            return null;
        }

        return age < 0 ? null : age;
    }

    /**
     * 为脑出血历史记录补充首页展示所需的“主异常项”。
     * 优先级按严重程度从高到低为：脑出血 > 中线偏移 > 脑室结构异常 > 未见明显异常。
     *
     * @param record 脑出血历史记录
     */
    private void populatePrimaryIssue(HemorrhageRecord record) {
        if (record == null) {
            return;
        }

        record.setPrimaryIssue(HemorrhageIssueSupport.resolvePrimaryIssue(record));
        record.setQcStatus(HemorrhageIssueSupport.resolveQcStatus(record));
        record.setPatientImagePath(resolvePatientImagePath(record.getExamId(), record.getImagePath()));
    }

    /**
     * 根据检查号解析患者信息表中的患者图片路径；若未维护则回退到检测记录自身图片。
     *
     * @param examId 检查号
     * @param fallbackImagePath 检测记录自身图片路径
     * @return 患者图片路径
     */
    private String resolvePatientImagePath(String examId, String fallbackImagePath) {
        if (examId != null && !examId.isBlank()) {
            var patientInfo = qualityPatientInfoService.getByAccessionNumber(
                    QualityPatientTaskSupport.TASK_TYPE_HEMORRHAGE,
                    examId);
            if (patientInfo != null && patientInfo.getImagePath() != null && !patientInfo.getImagePath().isBlank()) {
                return patientInfo.getImagePath();
            }
        }

        return fallbackImagePath;
    }


    /**
     * 校验当前脑出血检测上传文件是否满足后端模型支持范围。
     *
     * <p>当前 Python 模型仅支持常见位图图片，尚未接入 DICOM 解析链路，
     * 因此前端若误传 .dcm 文件，需要在后端显式拦截并返回可读错误。</p>
     *
     * @param file 上传文件
     */
    private void validateHemorrhageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("请先上传待检测影像文件");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("影像文件名不能为空");
        }

        String normalizedName = originalFilename.toLowerCase(Locale.ROOT);
        if (!(normalizedName.endsWith(".png")
                || normalizedName.endsWith(".jpg")
                || normalizedName.endsWith(".jpeg")
                || normalizedName.endsWith(".bmp"))) {
            throw new IllegalArgumentException("当前脑出血模型仅支持 PNG/JPG/JPEG/BMP 图片，暂不支持 DICOM 文件");
        }
    }

    /**
     * 将模型原始英文判定统一转换为前端展示文案。
     *
     * @param rawPrediction 模型原始 prediction 字段
     * @return 中文判定文案
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
     * 将模型服务错误转换为更准确的后端异常类型。
     *
     * @param errorMessage 模型服务返回的错误信息
     * @return 业务异常
     */
    private RuntimeException buildModelServiceException(String errorMessage) {
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
     * 通过 WebSocket 调用 Python 模型服务进行推理。
     *
     * @param imagePath 服务器本地图片绝对路径
     * @return Python 服务返回的 JSON 结果映射；若发生异常则返回包含 error 字段的映射
     */
    private Map<String, Object> callPythonModelViaWebSocket(String imagePath) {
        URI serverUri;
        try {
            serverUri = new URI(modelServerUrl);
        } catch (Exception e) {
            return Collections.singletonMap("error", "Invalid python.model_server.url");
        }

        int port = serverUri.getPort();
        if (port <= 0) {
            port = "wss".equalsIgnoreCase(serverUri.getScheme()) ? 443 : 80;
        }

        int connectAttempts = 6;
        long connectBackoffMs = 1000;

        for (int attempt = 1; attempt <= connectAttempts; attempt++) {
            CompletableFuture<String> future = new CompletableFuture<>();
            WebSocketClient client = null;
            try {
                client = new WebSocketClient(serverUri) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        Map<String, String> req = new HashMap<>();
                        req.put("image_path", imagePath);
                        try {
                            send(objectMapper.writeValueAsString(req));
                        } catch (Exception e) {
                            future.completeExceptionally(e);
                        }
                    }

                    @Override
                    public void onMessage(String message) {
                        future.complete(message);
                        close();
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        if (!future.isDone()) {
                            future.completeExceptionally(new RuntimeException("Connection closed: " + reason));
                        }
                    }

                    @Override
                    public void onError(Exception ex) {
                        future.completeExceptionally(ex);
                    }
                };

                if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                    if (attempt == connectAttempts) {
                        return Collections.singletonMap("error",
                                "Failed to connect to Python Model Server (Port " + port + ")");
                    }
                    Thread.sleep(connectBackoffMs);
                    continue;
                }

                String resultJson = future.get(60, TimeUnit.SECONDS); // Increased timeout for initial CUDA load
                return objectMapper.readValue(resultJson, Map.class);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.singletonMap("error", "Connection interrupted");
            } catch (java.util.concurrent.TimeoutException e) {
                return Collections.singletonMap("error", "Analysis timed out (Check if Python server is running)");
            } catch (Exception e) {
                e.printStackTrace();
                return Collections.singletonMap("error", e.getMessage());
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        return Collections.singletonMap("error", "Failed to connect to Python Model Server (Port " + port + ")");
    }

    // Mock Implementations matching frontend expectations
    @Override
    public Map<String, Object> detectHead(MultipartFile file) {
        return MockQualityAnalysisSupport.createMockResult(MockQualityAnalysisSupport.TASK_TYPE_HEAD);
    }

    @Override
    public Map<String, Object> detectChest(MultipartFile file, boolean contrast) {
        return MockQualityAnalysisSupport.createMockResult(
                contrast ? MockQualityAnalysisSupport.TASK_TYPE_CHEST_CONTRAST
                        : MockQualityAnalysisSupport.TASK_TYPE_CHEST_NON_CONTRAST);
    }

    @Override
    public Map<String, Object> detectCoronary(MultipartFile file) {
        return MockQualityAnalysisSupport.createMockResult(MockQualityAnalysisSupport.TASK_TYPE_CORONARY_CTA);
    }
}


