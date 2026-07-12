package com.learnspark.knowledge.service;

import com.learnspark.common.exception.BusinessException;
import com.learnspark.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地文件系统存储实现（默认）。
 *
 * <p>文件保存到 {@code app.storage.local.base-dir}/{userId}/{uuid}.{ext}。
 * 不依赖 Docker / MinIO，适合开发环境快速启动。
 */
@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path baseDir;

    public LocalFileStorageService(@Value("${app.storage.local.base-dir:./uploads}") String baseDir) {
        this.baseDir = Paths.get(baseDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            log.info("本地文件存储根目录: {}", this.baseDir);
        } catch (IOException e) {
            throw new IllegalStateException("无法创建存储目录: " + this.baseDir, e);
        }
    }

    @Override
    public String upload(String userId, String originalFilename, byte[] content, String contentType) {
        try {
            Path userDir = baseDir.resolve(userId);
            Files.createDirectories(userDir);

            String ext = extractExtension(originalFilename);
            String fileName = UUID.randomUUID() + (ext.isEmpty() ? "" : "." + ext);
            Path filePath = userDir.resolve(fileName);

            Files.write(filePath, content);
            log.debug("文件已保存: {} ({} bytes)", filePath, content.length);

            // 返回相对路径：userId/uuid.ext
            return userId + "/" + fileName;
        } catch (IOException e) {
            log.error("文件上传失败", e);
            throw new BusinessException(ErrorCode.KB_UPLOAD_FAILED);
        }
    }

    @Override
    public byte[] download(String filePath) {
        try {
            Path fullPath = baseDir.resolve(filePath).normalize();
            // 安全检查：防止路径穿越
            if (!fullPath.startsWith(baseDir)) {
                throw new BusinessException(ErrorCode.BAD_REQUEST);
            }
            return Files.readAllBytes(fullPath);
        } catch (IOException e) {
            log.error("文件下载失败: {}", filePath, e);
            throw new BusinessException(ErrorCode.KB_ENTRY_NOT_FOUND);
        }
    }

    private String extractExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int dotIndex = filename.lastIndexOf('.');
        return dotIndex >= 0 ? filename.substring(dotIndex + 1).toLowerCase() : "";
    }
}
