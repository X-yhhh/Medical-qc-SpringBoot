package com.medical.qc.shared.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 本地文件存储配置。
 */
@Component
@ConfigurationProperties(prefix = "app.storage.local")
public class LocalFileStorageProperties {
    /**
     * 存储根目录。
     */
    private String root = "uploads";

    /**
     * 对外静态资源访问前缀。
     */
    private String publicPrefix = "uploads";

    public String getRoot() {
        return root;
    }

    public void setRoot(String root) {
        this.root = root;
    }

    public String getPublicPrefix() {
        return publicPrefix;
    }

    public void setPublicPrefix(String publicPrefix) {
        this.publicPrefix = publicPrefix;
    }
}

