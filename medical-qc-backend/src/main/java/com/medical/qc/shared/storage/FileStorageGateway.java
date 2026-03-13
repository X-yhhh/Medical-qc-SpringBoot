package com.medical.qc.shared.storage;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

/**
 * 文件存储网关。
 *
 * <p>当前默认实现为本地磁盘存储，后续可替换为对象存储或共享文件系统。</p>
 */
public interface FileStorageGateway {

    /**
     * 保存上传文件。
     *
     * @param file 上传文件
     * @param relativePath 相对于存储根目录的相对路径
     * @return 存储结果
     * @throws IOException 文件写入失败
     */
    StoredFile store(MultipartFile file, String relativePath) throws IOException;

    /**
     * 复制已有文件到受管存储目录。
     *
     * @param sourcePath 原始文件绝对路径
     * @param relativePath 相对于存储根目录的相对路径
     * @return 存储结果
     * @throws IOException 文件复制失败
     */
    StoredFile copy(Path sourcePath, String relativePath) throws IOException;
}

