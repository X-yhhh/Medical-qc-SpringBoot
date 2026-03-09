package com.medical.qc.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * ActiveMQ 业务配置。
 */
@Component
@ConfigurationProperties(prefix = "app.messaging.activemq")
public class ActiveMqProperties {
    private boolean enabled = true;
    private boolean autostart = true;
    private String home = "D:\\activemq\\apache-activemq-5.16.6-bin\\apache-activemq-5.16.6";
    private long startupTimeoutMs = 60000L;
    private final QueueProperties queue = new QueueProperties();

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

    public static class QueueProperties {
        private String hemorrhageIssueSync = "qc.hemorrhage.issue.sync";
        private String mockQualityTask = "qc.mock.quality.task";

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
