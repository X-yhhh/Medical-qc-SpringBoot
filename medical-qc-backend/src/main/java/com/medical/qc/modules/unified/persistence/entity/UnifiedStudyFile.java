package com.medical.qc.modules.unified.persistence.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 统一检查文件实体。
 */
@TableName("study_files")
public class UnifiedStudyFile {
    // 文件主键。
    @TableId(value = "id", type = IdType.AUTO)
    private Long id;
    // 关联检查 ID 与文件角色。
    private Long studyId;
    private String fileRole;
    // 存储类型、本地路径和公开访问路径。
    private String storageType;
    private String filePath;
    private String publicPath;
    // 文件元信息。
    private String fileName;
    private String contentType;
    private Long fileSize;
    private String checksum;
    // 是否主文件，以及创建更新时间。
    private Boolean isPrimary;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // 以下访问器供检查上下文、详情查询和前端预览复用。
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public String getFileRole() {
        return fileRole;
    }

    public void setFileRole(String fileRole) {
        this.fileRole = fileRole;
    }

    public String getStorageType() {
        return storageType;
    }

    /**
     * 设置存储类型。
     */
    public void setStorageType(String storageType) {
        this.storageType = storageType;
    }

    /**
     * 返回文件物理路径。
     */
    public String getFilePath() {
        return filePath;
    }

    /**
     * 设置文件物理路径。
     */
    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    /**
     * 返回文件公开访问路径。
     */
    public String getPublicPath() {
        return publicPath;
    }

    /**
     * 设置文件公开访问路径。
     */
    public void setPublicPath(String publicPath) {
        this.publicPath = publicPath;
    }

    /**
     * 返回文件名。
     */
    public String getFileName() {
        return fileName;
    }

    /**
     * 设置文件名。
     */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    /**
     * 返回内容类型。
     */
    public String getContentType() {
        return contentType;
    }

    /**
     * 设置内容类型。
     */
    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    /**
     * 返回文件大小。
     */
    public Long getFileSize() {
        return fileSize;
    }

    /**
     * 设置文件大小。
     */
    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }

    /**
     * 返回文件校验和。
     */
    public String getChecksum() {
        return checksum;
    }

    /**
     * 设置文件校验和。
     */
    public void setChecksum(String checksum) {
        this.checksum = checksum;
    }

    /**
     * 返回是否主文件。
     */
    public Boolean getIsPrimary() {
        return isPrimary;
    }

    /**
     * 设置是否主文件。
     */
    public void setIsPrimary(Boolean primary) {
        isPrimary = primary;
    }

    /**
     * 返回创建时间。
     */
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /**
     * 设置创建时间。
     */
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 返回更新时间。
     */
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /**
     * 设置更新时间。
     */
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}

