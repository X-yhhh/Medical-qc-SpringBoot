package com.medical.qc.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.entity.HemorrhageRecord;
import com.medical.qc.entity.User;
import com.medical.qc.mapper.HemorrhageRecordMapper;
import com.medical.qc.service.QualityService;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;

@Service
public class QualityServiceImpl implements QualityService {

    @Autowired
    private HemorrhageRecordMapper hemorrhageRecordMapper;

    @Value("${python.model_server.url:ws://localhost:8765}")
    private String modelServerUrl;

    private final Path rootLocation = Paths.get("uploads");

    @Override
    public List<HemorrhageRecord> getHistory(Long userId) {
        QueryWrapper<HemorrhageRecord> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.orderByDesc("created_at");
        return hemorrhageRecordMapper.selectList(queryWrapper);
    }

    /**
     * 处理脑出血检测上传与模型分析流程。
     *
     * @param file        上传的影像文件
     * @param user        当前登录用户
     * @param patientName 患者姓名
     * @param examId      检查 ID
     * @return 模型分析结果（包含 prediction、概率、耗时、影像回显信息等）
     * @throws IOException      文件保存或读取失败时抛出
     * @throws RuntimeException 模型服务返回错误或解析失败时抛出
     */
    @Override
    public Map<String, Object> processHemorrhage(MultipartFile file, User user, String patientName, String examId)
            throws IOException {
        if (Files.notExists(rootLocation)) {
            Files.createDirectories(rootLocation);
        }

        String filename = UUID.randomUUID().toString() + "_" + file.getOriginalFilename();
        Path destinationFile = rootLocation.resolve(Paths.get(filename)).normalize().toAbsolutePath();
        file.transferTo(destinationFile.toFile());

        // Call Python Script via WebSocket
        Map<String, Object> predictionResult = callPythonModelViaWebSocket(destinationFile.toString());

        if (predictionResult.containsKey("error")) {
            throw new RuntimeException("AI Error: " + predictionResult.get("error"));
        }

        // Save to DB
        HemorrhageRecord record = new HemorrhageRecord();
        record.setUserId(user.getId());
        record.setPatientName(patientName);
        record.setExamId(examId);
        record.setImagePath("uploads/" + filename);
        record.setRawResultJson(new ObjectMapper().writeValueAsString(predictionResult));

        // Translate Prediction to Chinese (Front-end expectation)
        String rawPrediction = (String) predictionResult.get("prediction");
        String translatedPrediction = "Hemorrhage".equals(rawPrediction) ? "出血" : "未出血";

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
        if (predictionResult.get("device") != null) {
            record.setDevice(String.valueOf(predictionResult.get("device")));
        }

        // Append image meta and URL for frontend rendering
        try {
            BufferedImage img = ImageIO.read(destinationFile.toFile());
            if (img != null) {
                predictionResult.put("image_width", img.getWidth());
                predictionResult.put("image_height", img.getHeight());
            }
            // Convert to Base64 for immediate display without static resource config issues
            byte[] fileContent = Files.readAllBytes(destinationFile);
            String encodedString = Base64.getEncoder().encodeToString(fileContent);
            predictionResult.put("image_base64", encodedString);
        } catch (Exception e) {
            e.printStackTrace();
        }
        predictionResult.put("image_url", "/uploads/" + filename);

        hemorrhageRecordMapper.insert(record);

        return predictionResult;
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
                            send(new ObjectMapper().writeValueAsString(req));
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
                return new ObjectMapper().readValue(resultJson, Map.class);
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
        return createMockResponse(Arrays.asList("运动伪影", "金属伪影", "FOV过大", "FOV过小", "层厚不当"));
    }

    @Override
    public Map<String, Object> detectChest(MultipartFile file, boolean contrast) {
        if (contrast) {
            return createMockResponse(Arrays.asList("分期错误", "增强时机不当", "FOV过小"));
        } else {
            return createMockResponse(Arrays.asList("呼吸伪影", "体外金属", "扫描范围不全"));
        }
    }

    @Override
    public Map<String, Object> detectCoronary(MultipartFile file) {
        return createMockResponse(Arrays.asList("心率过快", "呼吸伪影", "钙化伪影", "血管充盈不佳"));
    }

    private Map<String, Object> createMockResponse(List<String> possibleIssues) {
        List<Map<String, String>> issues = new ArrayList<>();
        Random random = new Random();
        for (String item : possibleIssues) {
            Map<String, String> issue = new HashMap<>();
            issue.put("item", item);
            issue.put("status", random.nextDouble() > 0.7 ? "不合格" : "合格");
            issues.add(issue);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("issues", issues);
        response.put("duration", 800 + random.nextInt(500));
        return response;
    }
}
