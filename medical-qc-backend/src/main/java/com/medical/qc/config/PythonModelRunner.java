package com.medical.qc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
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

        logger.info("Starting Python Model Server...");

        // Determine the location of the python_model directory
        // Assuming running from project root (medical-qc-backend)
        String projectDir = System.getProperty("user.dir");
        File scriptDir = new File(projectDir, "python_model");
        
        if (!scriptDir.exists() || !scriptDir.isDirectory()) {
            logger.error("Python model directory not found at: {}", scriptDir.getAbsolutePath());
            return;
        }

        // Use "python" command assuming it's in the PATH (and correct venv if applicable)
        ProcessBuilder pb = new ProcessBuilder("python", "model_server.py");
        pb.directory(scriptDir);
        pb.redirectErrorStream(true); // Merge stderr into stdout

        try {
            pythonProcess = pb.start();
            
            // Consume output in a separate thread to prevent blocking
            Thread outputReader = new Thread(() -> {
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(pythonProcess.getInputStream()))) {
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
}
