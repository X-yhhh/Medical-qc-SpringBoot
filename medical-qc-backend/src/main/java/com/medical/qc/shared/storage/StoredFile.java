package com.medical.qc.shared.storage;

import java.nio.file.Path;

/**
 * 受管文件存储结果。
 */
public class StoredFile {
    private final Path absolutePath;
    private final String publicPath;

    public StoredFile(Path absolutePath, String publicPath) {
        this.absolutePath = absolutePath;
        this.publicPath = publicPath;
    }

    public Path getAbsolutePath() {
        return absolutePath;
    }

    public String getPublicPath() {
        return publicPath;
    }
}

