package com.medical.qc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ActiveMQ 业务配置。
 */
@Component
@ConfigurationProperties(prefix = "app.messaging.activemq")
public class ActiveMqProperties {
    // 是否启用 ActiveMQ 消息链路。
    private boolean enabled = true;
    // broker 不可达时是否允许后端尝试自动拉起本机 ActiveMQ。
    private boolean autostart = true;
    // 本机 ActiveMQ 安装目录。
    private String home = "D:\\activemq\\apache-activemq-5.16.6-bin\\apache-activemq-5.16.6";
    // 启动后等待 broker ready 的超时时间。
    private long startupTimeoutMs = 60000L;
    // 各业务消息主题/队列名称。
    private final QueueProperties queue = new QueueProperties();

    // 以下访问器供配置绑定和生命周期管理器读取。
    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isAutostart() {
        return autostart;
    }

    public void setAutostart(boolean autostart) {
        this.autostart = autostart;
    }

    public String getHome() {
        return home;
    }

    public void setHome(String home) {
        this.home = home;
    }

    public long getStartupTimeoutMs() {
        return startupTimeoutMs;
    }

    public void setStartupTimeoutMs(long startupTimeoutMs) {
        this.startupTimeoutMs = startupTimeoutMs;
    }

    public QueueProperties getQueue() {
        return queue;
    }

    /**
     * 队列名称配置。
     */
    public static class QueueProperties {
        // 脑出血异常工单同步消息。
        private String hemorrhageIssueSync = "qc.hemorrhage.issue.sync";
        // 异步质控任务执行消息。
        private String mockQualityTask = "qc.mock.quality.task";

        // 以下访问器供发送方和监听器读取目标地址。
        public String getHemorrhageIssueSync() {
            return hemorrhageIssueSync;
        }

        public void setHemorrhageIssueSync(String hemorrhageIssueSync) {
            this.hemorrhageIssueSync = hemorrhageIssueSync;
        }

        public String getMockQualityTask() {
            return mockQualityTask;
        }

        public void setMockQualityTask(String mockQualityTask) {
            this.mockQualityTask = mockQualityTask;
        }
    }
}

