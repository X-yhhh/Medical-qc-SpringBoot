package com.medical.qc.shared.storage;

import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

/**
 * 本地磁盘文件存储实现。
 */
@Component
public class LocalFileStorageGateway implements FileStorageGateway {
    private final LocalFileStorageProperties properties;

    public LocalFileStorageGateway(LocalFileStorageProperties properties) {
        this.properties = properties;
    }

    @Override
    public StoredFile store(MultipartFile file, String relativePath) throws IOException {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("待保存文件不能为空");
        }

        Path targetPath = resolveManagedPath(relativePath);
        createParentDirectories(targetPath);
        file.transferTo(targetPath.toFile());
        return new StoredFile(targetPath, buildPublicPath(relativePath));
    }

    @Override
    public StoredFile copy(Path sourcePath, String relativePath) throws IOException {
        if (sourcePath == null) {
            throw new IllegalArgumentException("源文件路径不能为空");
        }

        Path targetPath = resolveManagedPath(relativePath);
        createParentDirectories(targetPath);
        Files.copy(sourcePath, targetPath, StandardCopyOption.REPLACE_EXISTING);
        return new StoredFile(targetPath, buildPublicPath(relativePath));
    }

    private Path resolveManagedPath(String relativePath) {
        String normalizedRelativePath = normalizeRelativePath(relativePath);
        Path rootPath = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        Path targetPath = rootPath.resolve(normalizedRelativePath).normalize();
        if (!targetPath.startsWith(rootPath)) {
            throw new IllegalArgumentException("非法文件存储路径: " + relativePath);
        }
        return targetPath;
    }

    private void createParentDirectories(Path targetPath) throws IOException {
        Path parentPath = targetPath.getParent();
        if (parentPath != null && Files.notExists(parentPath)) {
            Files.createDirectories(parentPath);
        }
    }

    private String buildPublicPath(String relativePath) {
        String normalizedRelativePath = normalizeRelativePath(relativePath);
        String publicPrefix = StringUtils.hasText(properties.getPublicPrefix())
                ? properties.getPublicPrefix().trim().replace('\\', '/')
                : "uploads";
        return publicPrefix + "/" + normalizedRelativePath;
    }

    private String normalizeRelativePath(String relativePath) {
        if (!StringUtils.hasText(relativePath)) {
            throw new IllegalArgumentException("文件相对路径不能为空");
        }

        String normalizedPath = relativePath.trim().replace('\\', '/');
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }

        if (normalizedPath.isBlank() || normalizedPath.contains("..")) {
            throw new IllegalArgumentException("非法文件相对路径: " + relativePath);
        }

        return normalizedPath;
    }
}

