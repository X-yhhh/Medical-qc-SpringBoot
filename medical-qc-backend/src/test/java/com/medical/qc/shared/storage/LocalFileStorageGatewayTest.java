package com.medical.qc.shared.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LocalFileStorageGatewayTest {

    @TempDir
    Path tempDir;

    @Test
    void storeShouldPersistFileAndReturnPublicPath() throws IOException {
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRoot(tempDir.resolve("uploads").toString());
        properties.setPublicPrefix("uploads");

        LocalFileStorageGateway gateway = new LocalFileStorageGateway(properties);
        MockMultipartFile file = new MockMultipartFile("file", "scan.png", "image/png", "demo-image".getBytes());

        StoredFile storedFile = gateway.store(file, "quality/scan.png");

        assertThat(storedFile.getPublicPath()).isEqualTo("uploads/quality/scan.png");
        assertThat(Files.readString(storedFile.getAbsolutePath())).isEqualTo("demo-image");
    }

    @Test
    void copyShouldPlaceFileUnderManagedRoot() throws IOException {
        LocalFileStorageProperties properties = new LocalFileStorageProperties();
        properties.setRoot(tempDir.resolve("uploads").toString());
        properties.setPublicPrefix("uploads");

        LocalFileStorageGateway gateway = new LocalFileStorageGateway(properties);
        Path sourceFile = tempDir.resolve("source.png");
        Files.writeString(sourceFile, "copied-image");

        StoredFile storedFile = gateway.copy(sourceFile, "patient-info/head/source.png");

        assertThat(storedFile.getPublicPath()).isEqualTo("uploads/patient-info/head/source.png");
        assertThat(Files.readString(storedFile.getAbsolutePath())).isEqualTo("copied-image");
    }

    @Test
    void storeShouldRejectPathTraversal() {
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
