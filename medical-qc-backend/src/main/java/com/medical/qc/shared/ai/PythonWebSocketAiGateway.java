package com.medical.qc.shared.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medical.qc.shared.JsonObjectMapReader;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 基于 Python WebSocket 服务的 AI 推理网关实现。
 */
@Component
public class PythonWebSocketAiGateway implements AiGateway {
    // Jackson 用于把请求/响应在 JSON 与 Map 之间转换。
    private final ObjectMapper objectMapper;

    @Value("${python.model_server.url:ws://localhost:8765}")
    private String modelServerUrl;

    public PythonWebSocketAiGateway(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> analyzeHemorrhage(String imagePath) {
        // 模型服务地址来自配置，默认连本机 8765 端口。
        URI serverUri;
        try {
            serverUri = new URI(modelServerUrl);
        } catch (Exception exception) {
            return Collections.singletonMap("error", "Invalid python.model_server.url");
        }

        int port = serverUri.getPort();
        if (port <= 0) {
            port = "wss".equalsIgnoreCase(serverUri.getScheme()) ? 443 : 80;
        }

        // 启动阶段 Python 模型服务可能尚未就绪，因此允许有限次重试。
        int connectAttempts = 6;
        long connectBackoffMs = 1000;

        for (int attempt = 1; attempt <= connectAttempts; attempt++) {
            CompletableFuture<String> future = new CompletableFuture<>();
            WebSocketClient client = null;
            try {
                client = new WebSocketClient(serverUri) {
                    @Override
                    public void onOpen(ServerHandshake handshakedata) {
                        // WebSocket 建立后立即发送图片路径请求。
                        Map<String, String> request = new HashMap<>();
                        request.put("image_path", imagePath);
                        try {
                            send(objectMapper.writeValueAsString(request));
                        } catch (Exception exception) {
                            future.completeExceptionally(exception);
                        }
                    }

                    @Override
                    public void onMessage(String message) {
                        // 模型服务返回结果后完成 future，并关闭连接。
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
                    public void onError(Exception exception) {
                        future.completeExceptionally(exception);
                    }
                };

                if (!client.connectBlocking(5, TimeUnit.SECONDS)) {
                    if (attempt == connectAttempts) {
                        return Collections.singletonMap("error",
                                "Failed to connect to Python Model Server (Port " + port + ")");
                    }
                    // 短暂退避后继续重试，给 Python 服务留出启动时间。
                    Thread.sleep(connectBackoffMs);
                    continue;
                }

                String resultJson = future.get(60, TimeUnit.SECONDS);
                return JsonObjectMapReader.read(objectMapper, resultJson);
            } catch (InterruptedException exception) {
                // 线程被中断时要恢复中断标记，避免吞掉上层取消信号。
                Thread.currentThread().interrupt();
                return Collections.singletonMap("error", "Connection interrupted");
            } catch (java.util.concurrent.TimeoutException exception) {
                return Collections.singletonMap("error", "Analysis timed out (Check if Python server is running)");
            } catch (Exception exception) {
                return Collections.singletonMap("error", exception.getMessage());
            } finally {
                if (client != null) {
                    try {
                        // 无论成功失败都关闭连接，避免 WebSocket 句柄泄露。
                        client.close();
                    } catch (Exception ignore) {
                    }
                }
            }
        }

        return Collections.singletonMap("error", "Failed to connect to Python Model Server (Port " + port + ")");
    }
}

