package com.medical.qc.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
/**
 * Python 模型服务进程管理器。
 *
 * 作用:
 * - 在后端启动时按配置自动启动 python_model/model_server.py
 * - 在后端停止时尽最大努力回收 Python 进程及其子进程，避免端口占用导致 Connection refused
 *
 * 配置:
 * - python.model.autostart: 是否自动启动模型服务
 */
public class PythonModelRunner implements CommandLineRunner, DisposableBean {

    private static final Logger logger = LoggerFactory.getLogger(PythonModelRunner.class);
    private static final Pattern NETSTAT_PID_PATTERN = Pattern.compile("(\\d+)\\s*$");
    private Process pythonProcess;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${python.model.autostart:true}")
    private boolean autoStart;

    @Value("${python.model_server.url:ws://localhost:8765}")
    private String modelServerUrl;

    @Value("${python.executable:}")
    private String pythonExecutable;

    /**
     * Spring Boot 启动完成后回调。
     *
     * @param args 启动参数
     * @throws Exception 启动模型服务失败时可能抛出
     */
    @Override
    public void run(String... args) throws Exception {
        if (!autoStart) {
            logger.info("Python Model Server autostart is disabled.");
            return;
        }

        URI serverUri;
        try {
            serverUri = new URI(modelServerUrl);
        } catch (Exception e) {
            logger.error("Invalid python.model_server.url: {}", modelServerUrl, e);
            return;
        }

        String host = serverUri.getHost() == null ? "localhost" : serverUri.getHost();
        int port = serverUri.getPort();
        if (port <= 0) {
            port = "wss".equalsIgnoreCase(serverUri.getScheme()) ? 443 : 80;
        }

        if (isWebSocketHealthy(serverUri, 2)) {
            logger.info("Python Model Server is already listening on {}:{}, skip autostart.", host, port);
            return;
        }

        logger.info("Detected unhealthy Python Model Server on {}:{}, attempting cleanup and restart.", host, port);
        cleanupUnhealthyModelServer(port);

        if (isWebSocketHealthy(serverUri, 2)) {
            logger.info("Python Model Server recovered before autostart on {}:{}, skip creating a new process.", host, port);
            return;
        }

        List<Long> listeningProcessIds = findListeningProcessIds(port);
        if (!listeningProcessIds.isEmpty()) {
            logger.warn("Port {} is still occupied by PID(s) {} after cleanup, skip Python Model Server autostart.",
                    port, listeningProcessIds);
            return;
        }

        logger.info("Starting Python Model Server...");

        // Determine the location of the python_model directory
        // Check current directory and subdirectories
        String projectDir = System.getProperty("user.dir");
        File scriptDir = new File(projectDir, "python_model");

        if (!scriptDir.exists() || !scriptDir.isDirectory()) {
            // Try checking if we are in root and need to go into backend
            File backendDir = new File(projectDir, "medical-qc-backend");
            if (backendDir.exists() && backendDir.isDirectory()) {
                scriptDir = new File(backendDir, "python_model");
            }
        }

        if (!scriptDir.exists() || !scriptDir.isDirectory()) {
            logger.error("Python model directory not found. Searched in: {} and subdirectories.", projectDir);
            return;
        }

        logger.info("Found Python model directory at: {}", scriptDir.getAbsolutePath());

        String pythonExe = resolvePythonExecutable(projectDir);
        logger.info("Using Python executable: {}", pythonExe);

        ProcessBuilder pb = new ProcessBuilder(pythonExe, "-u", "model_server.py");
        pb.directory(scriptDir);
        pb.redirectErrorStream(true); // Merge stderr into stdout
        Map<String, String> env = pb.environment();
        env.putIfAbsent("PYTHONUNBUFFERED", "1");

        try {
            pythonProcess = pb.start();

            // Consume output in a separate thread to prevent blocking
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(pythonProcess.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        logger.info("[Python Model]: {}", line);
                    }
                } catch (Exception e) {
                    // Process likely ended
                }
            });
            outputReader.setDaemon(true);
            outputReader.start();

            logger.info("Python Model Server process started with PID: {}", pythonProcess.pid());

            if (!waitForWebSocketReady(serverUri, 120_000, 1_000)) {
                logger.error("Python Model Server did not become ready on {}:{} within timeout.", host, port);
                try {
                    pythonProcess.destroyForcibly();
                } catch (Exception ignore) {
                }
            }

        } catch (Exception e) {
            logger.error("Failed to start Python Model Server", e);
        }
    }

    /**
     * Spring 容器关闭时回调。
     *
     * @throws Exception 进程销毁等待时可能抛出
     */
    @Override
    public void destroy() throws Exception {
        if (pythonProcess != null && pythonProcess.isAlive()) {
            logger.info("Stopping Python Model Server...");

            // Kill all descendants first (Java 9+)
            pythonProcess.descendants().forEach(ph -> {
                logger.info("Killing descendant process: {}", ph.pid());
                ph.destroy();
            });

            pythonProcess.destroy();
            // Allow some time for graceful shutdown
            if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                logger.warn("Python Model Server did not stop gracefully, forcing shutdown...");

                pythonProcess.descendants().forEach(ph -> ph.destroyForcibly());
                pythonProcess.destroyForcibly();
            }
            logger.info("Python Model Server stopped.");
        } else if (pythonProcess != null) {
            // Even if the main process is dead, descendants might be alive
            pythonProcess.descendants().forEach(ph -> {
                logger.info("Cleaning up orphaned descendant process: {}", ph.pid());
                ph.destroyForcibly();
            });
        }
    }

    /**
     * 通过健康检查请求确认模型服务已经真正可用，而不是仅仅端口打开。
     *
     * @param uri 模型服务地址
     * @param connectTimeoutSeconds 建连超时时间（秒）
     * @return true 表示模型服务可正常响应该健康检查
     */
    private boolean isWebSocketHealthy(URI uri, int connectTimeoutSeconds) {
        CompletableFuture<Boolean> future = new CompletableFuture<>();

        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                try {
                    send(objectMapper.writeValueAsString(Map.of("health_check", true)));
                } catch (Exception e) {
                    future.complete(false);
                }
            }

            @Override
            public void onMessage(String message) {
                future.complete(parseHealthCheckResponse(message));
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                if (!future.isDone()) {
                    future.complete(false);
                }
            }

            @Override
            public void onError(Exception ex) {
                future.complete(false);
            }
        };
        try {
            if (!client.connectBlocking(connectTimeoutSeconds, TimeUnit.SECONDS)) {
                return false;
            }
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                client.close();
            } catch (Exception ignore) {
            }
        }
    }

    /**
     * 轮询等待模型服务通过健康检查。
     *
     * @param uri 模型服务地址
     * @param timeoutMs 总超时时间（毫秒）
     * @param intervalMs 轮询间隔（毫秒）
     * @return true 表示模型服务已经可用
     */
    private boolean waitForWebSocketReady(URI uri, long timeoutMs, long intervalMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isWebSocketHealthy(uri, 2)) {
                logger.info("Python Model Server is ready at {}", uri);
                return true;
            }
            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    /**
     * 解析 Python 模型服务的健康检查响应。
     *
     * @param message WebSocket 返回的 JSON 文本
     * @return true 表示响应格式正确且模型已完成加载
     */
    private boolean parseHealthCheckResponse(String message) {
        try {
            Map<?, ?> response = objectMapper.readValue(message, Map.class);
            Object status = response.get("status");
            Object modelLoaded = response.get("model_loaded");
            return "ok".equals(String.valueOf(status)) && Boolean.TRUE.equals(modelLoaded);
        } catch (Exception e) {
            logger.warn("Failed to parse Python Model Server health response: {}", message);
            return false;
        }
    }

    /**
     * 清理占用模型端口但健康检查失败的旧进程，避免后端误连到错误 Python 环境。
     *
     * @param port 模型服务端口
     */
    private void cleanupUnhealthyModelServer(int port) {
        List<Long> pidList = findListeningProcessIds(port);
        if (pidList.isEmpty()) {
            return;
        }

        for (Long pid : pidList) {
            try {
                ProcessHandle.of(pid).ifPresent(processHandle -> {
                    String commandLine = processHandle.info().commandLine().orElse("");
                    String command = processHandle.info().command().orElse("");
                    String processSummary = (commandLine + " " + command).toLowerCase(Locale.ROOT);
                    String processDescriptor = commandLine.isBlank() ? command : commandLine;
                    boolean looksLikePythonExecutable = command.toLowerCase(Locale.ROOT).endsWith("python.exe")
                            || command.toLowerCase(Locale.ROOT).endsWith("python");

                    if (!processSummary.contains("model_server.py") && !looksLikePythonExecutable) {
                        logger.warn("Port {} is occupied by non-model process PID={}, skip cleanup. command={}",
                                port, pid, processDescriptor);
                        return;
                    }

                    if (!processSummary.contains("model_server.py") && looksLikePythonExecutable) {
                        logger.info("Port {} is occupied by a Python process with incomplete command metadata, treat PID={} as a stale model process. command={}",
                                port, pid, processDescriptor);
                    }

                    logger.info("Stopping unhealthy Python Model Server process PID={}, command={}", pid, processDescriptor);
                    processHandle.destroy();
                    try {
                        processHandle.onExit().get(5, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        processHandle.destroyForcibly();
                    }
                });
            } catch (Exception e) {
                logger.warn("Failed to cleanup unhealthy Python Model Server PID={}", pid, e);
            }
        }
    }

    /**
     * 在 Windows 环境下通过 netstat 查询指定端口的监听进程。
     *
     * @param port 端口号
     * @return 监听该端口的进程 ID 列表
     */
    private List<Long> findListeningProcessIds(int port) {
        List<Long> pidList = new ArrayList<>();
        String osName = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (!osName.contains("win")) {
            return pidList;
        }

        Process process = null;
        try {
            process = new ProcessBuilder("cmd", "/c", "netstat -ano -p tcp | findstr LISTENING | findstr :" + port)
                    .redirectErrorStream(true)
                    .start();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.contains(":" + port)) {
                        continue;
                    }

                    Matcher matcher = NETSTAT_PID_PATTERN.matcher(line.trim());
                    if (matcher.find()) {
                        pidList.add(Long.parseLong(matcher.group(1)));
                    }
                }
            }

            process.waitFor(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            logger.warn("Failed to query listening processes for port {}", port, e);
        } finally {
            if (process != null) {
                process.destroy();
            }
        }

        return pidList;
    }

    private String resolvePythonExecutable(String projectDir) {
        if (pythonExecutable != null && !pythonExecutable.isBlank()) {
            return pythonExecutable;
        }

        File currentDir = new File(projectDir);
        File[] candidates = new File[] {
                new File(currentDir, ".venv\\Scripts\\python.exe"),
                new File(currentDir, ".venv\\bin\\python"),
                currentDir.getParentFile() == null ? null
                        : new File(currentDir.getParentFile(), ".venv\\Scripts\\python.exe"),
                currentDir.getParentFile() == null ? null : new File(currentDir.getParentFile(), ".venv\\bin\\python"),
        };

        for (File f : candidates) {
            if (f != null && f.exists() && f.isFile()) {
                return f.getAbsolutePath();
            }
        }

        return "python";
    }
}
