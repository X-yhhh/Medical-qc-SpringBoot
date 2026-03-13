package com.medical.qc.shared.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * LocalFileStorageGateway 单元测试。
 * 校验上传、拷贝和路径穿越防护三类文件存储行为。
 */
class LocalFileStorageGatewayTest {

    // JUnit 为每个测试方法生成独立临时目录，避免文件状态相互污染。
    @TempDir
    Path tempDir;

    @Test
    void storeShouldPersistFileAndReturnPublicPath() throws IOException {
        // 配置受管 uploads 根目录，模拟本地文件存储网关。
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRoot(tempDir.resolve("uploads").toString());
        properties.setPublicPrefix("uploads");

        LocalFileStorageGateway gateway = new LocalFileStorageGateway(properties);
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", "demo-image".getBytes());

        StoredFile storedFile = gateway.store(file, "quality/scan.png");

        // 断言写盘成功，且 publicPath 与前端访问路径约定一致。
        assertThat(storedFile.getPublicPath()).isEqualTo("uploads/quality/scan.png");
        assertThat(Files.readString(storedFile.getAbsolutePath())).isEqualTo("demo-image");
    }

    @Test
    void copyShouldPlaceFileUnderManagedRoot() throws IOException {
        // 拷贝场景常用于把 PACS 图片纳入系统托管目录。
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRoot(tempDir.resolve("uploads").toString());
        properties.setPublicPrefix("uploads");

        LocalFileStorageGateway gateway = new LocalFileStorageGateway(properties);
        Path sourceFile = tempDir.resolve("source.png");
        Files.writeString(sourceFile, "copied-image");

        StoredFile storedFile = gateway.copy(sourceFile, "patient-info/head/source.png");

        // 断言源文件内容已被复制到受管目录。
        assertThat(storedFile.getPublicPath()).isEqualTo("uploads/patient-info/head/source.png");
        assertThat(Files.readString(storedFile.getAbsolutePath())).isEqualTo("copied-image");
    }

    @Test
    void storeShouldRejectPathTraversal() {
        // 非法相对路径必须被拒绝，避免写出受管目录之外。
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRoot(tempDir.resolve("uploads").toString());
        properties.setPublicPrefix("uploads");

        LocalFileStorageGateway gateway = new LocalFileStorageGateway(properties);
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", "demo".getBytes());

        assertThatThrownBy(() -> gateway.store(file, "../escape.png"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("非法文件相对路径");
    }
}
