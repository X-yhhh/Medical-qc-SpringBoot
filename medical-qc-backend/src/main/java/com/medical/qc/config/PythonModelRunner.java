package com.medical.qc.config;

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
import java.util.Map;
import java.util.concurrent.TimeUnit;

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
    private Process pythonProcess;

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

        if (isWebSocketReady(serverUri, 2)) {
            logger.info("Python Model Server is already listening on {}:{}, skip autostart.", host, port);
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

    private boolean isWebSocketReady(URI uri, int connectTimeoutSeconds) {
        WebSocketClient client = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
            }

            @Override
            public void onMessage(String message) {
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
            }

            @Override
            public void onError(Exception ex) {
            }
        };
        try {
            return client.connectBlocking(connectTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            return false;
        } finally {
            try {
                client.close();
            } catch (Exception ignore) {
            }
        }
    }

    private boolean waitForWebSocketReady(URI uri, long timeoutMs, long intervalMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isWebSocketReady(uri, 2)) {
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
