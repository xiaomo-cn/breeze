package cn.xiaomo.breeze.attachment;

import java.io.InputStream;

public interface FileStorageService {
    String store(String originalFileName, String contentType, long fileSize, InputStream inputStream);
    InputStream retrieve(String storageKey);
    void delete(String storageKey);

    /** 返回文件的直接访问 URL。S3 模式返回预签名 URL，local 模式返回 null */
    String getUrl(String storageKey, String fileName);

    /** 是否支持直接 URL 访问（S3 预签名），false 则需要后端代理下载 */
    boolean supportsDirectUrl();
}
