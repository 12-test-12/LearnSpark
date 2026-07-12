package com.learnspark.knowledge.service;

/**
 * 文件存储抽象接口。
 *
 * <p>支持本地文件系统（{@code local}）和 MinIO 对象存储（{@code minio}）两种实现。
 * 通过 {@code app.storage.type} 配置项切换。
 */
public interface FileStorageService {

    /**
     * 上传文件到存储。
     *
     * @param userId           用户 ID（用于分目录/前缀隔离）
     * @param originalFilename 原始文件名
     * @param content          文件内容
     * @param contentType      MIME 类型
     * @return 存储路径（如 {@code userId/uuid.md}），供后续下载使用
     */
    String upload(String userId, String originalFilename, byte[] content, String contentType);

    /**
     * 下载文件。
     *
     * @param filePath upload 返回的存储路径
     * @return 文件内容字节数组
     */
    byte[] download(String filePath);
}
