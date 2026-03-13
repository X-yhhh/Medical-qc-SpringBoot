package com.medical.qc.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.jms.config.JmsListenerEndpointRegistry;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

/**
 * ActiveMQ Broker 生命周期管理器。
 *
 * <p>职责：</p>
 * <ul>
 *     <li>在后端启动时检测 broker 是否已就绪。</li>
 *     <li>当 broker 未启动且允许自动拉起时，调用本机 ActiveMQ 安装目录中的脚本启动 broker。</li>
 *     <li>broker 就绪后再手动启动 JMS 监听器，避免 Spring 在 broker 未就绪时提前拉起消费者。</li>
 * </ul>
 *
 * <p>说明：当前实现面向 Windows 本地开发环境；后续若迁移到 Linux / Docker，可将这里替换为容器编排或服务管理。</p>
 */
@Component
@Order(0)
public class ActiveMqRunner implements org.springframework.boot.CommandLineRunner, DisposableBean {
    private static final Logger logger = LoggerFactory.getLogger(ActiveMqRunner.class);

    private final ActiveMqProperties activeMqProperties;
    private final JmsListenerEndpointRegistry jmsListenerEndpointRegistry;

    @Value("${spring.activemq.broker-url:tcp://127.0.0.1:61616}")
    private String brokerUrl;

    private boolean managedBroker;

    public ActiveMqRunner(ActiveMqProperties activeMqProperties,
                          JmsListenerEndpointRegistry jmsListenerEndpointRegistry) {
        this.activeMqProperties = activeMqProperties;
        this.jmsListenerEndpointRegistry = jmsListenerEndpointRegistry;
    }

    @Override
    public void run(String... args) throws Exception {
        if (!activeMqProperties.isEnabled()) {
            logger.info("ActiveMQ messaging is disabled.");
            return;
        }

        URI brokerUri = parseBrokerUri();
        if (brokerUri == null) {
            logger.error("Invalid spring.activemq.broker-url: {}", brokerUrl);
            return;
        }

        if (isBrokerReachable(brokerUri, 1500)) {
            logger.info("ActiveMQ broker is already reachable at {}", brokerUrl);
            startListeners();
            return;
        }

        if (!activeMqProperties.isAutostart()) {
            logger.warn("ActiveMQ broker is not reachable and autostart is disabled: {}", brokerUrl);
            return;
        }

        Path activemqBat = Paths.get(activeMqProperties.getHome(), "bin", "activemq.bat");
        if (!Files.exists(activemqBat)) {
            logger.error("ActiveMQ startup script not found: {}", activemqBat);
            return;
        }

        logger.info("Starting ActiveMQ broker from {}", activemqBat);
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe", "/c", activemqBat.toString(), "start");
        processBuilder.directory(new File(activeMqProperties.getHome(), "bin"));
        processBuilder.redirectErrorStream(true);

        Process startProcess = processBuilder.start();
        drainProcessOutput(startProcess, "[ActiveMQ Bootstrap]");
        startProcess.waitFor(15, TimeUnit.SECONDS);

        if (waitForBroker(brokerUri, activeMqProperties.getStartupTimeoutMs(), 1000L)) {
            managedBroker = true;
            logger.info("ActiveMQ broker is ready at {}", brokerUrl);
            startListeners();
            return;
        }

        logger.error("ActiveMQ broker did not become ready within {} ms: {}",
                activeMqProperties.getStartupTimeoutMs(), brokerUrl);
    }

    @Override
    public void destroy() throws Exception {
        if (!managedBroker) {
            return;
        }

        Path activemqBat = Paths.get(activeMqProperties.getHome(), "bin", "activemq.bat");
        if (!Files.exists(activemqBat)) {
            logger.warn("ActiveMQ stop script not found, skip stop: {}", activemqBat);
            return;
        }

        logger.info("Stopping managed ActiveMQ broker...");
        ProcessBuilder processBuilder = new ProcessBuilder(
                "cmd.exe", "/c", activemqBat.toString(), "stop");
        processBuilder.directory(new File(activeMqProperties.getHome(), "bin"));
        processBuilder.redirectErrorStream(true);

        Process stopProcess = processBuilder.start();
        drainProcessOutput(stopProcess, "[ActiveMQ Shutdown]");
        stopProcess.waitFor(15, TimeUnit.SECONDS);
    }

    private void startListeners() {
        if (!jmsListenerEndpointRegistry.isRunning()) {
            jmsListenerEndpointRegistry.start();
            logger.info("JMS listeners started.");
        }
    }

    private URI parseBrokerUri() {
        try {
            return new URI(brokerUrl);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean waitForBroker(URI brokerUri, long timeoutMs, long intervalMs) {
        long deadline = System.currentTimeMillis() + timeoutMs;
        while (System.currentTimeMillis() < deadline) {
            if (isBrokerReachable(brokerUri, 1500)) {
                return true;
            }

            try {
                Thread.sleep(intervalMs);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }

    private boolean isBrokerReachable(URI brokerUri, int timeoutMs) {
        String host = brokerUri.getHost() == null ? "127.0.0.1" : brokerUri.getHost();
        int port = brokerUri.getPort();
        if (port <= 0) {
            port = 61616;
        }

        try (Socket socket = new Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), timeoutMs);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private void drainProcessOutput(Process process, String prefix) {
        Thread outputReader = new Thread(() -> {
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    logger.info("{} {}", prefix, line);
                }
            } catch (Exception ignore) {
            }
        });
        outputReader.setDaemon(true);
        outputReader.start();
    }
}

