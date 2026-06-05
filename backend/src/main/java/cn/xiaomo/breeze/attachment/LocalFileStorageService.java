package cn.xiaomo.breeze.attachment;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@ConditionalOnProperty(name = "app.storage.provider", havingValue = "local", matchIfMissing = true)
public class LocalFileStorageService implements FileStorageService {

    private final Path uploadDir;

    public LocalFileStorageService(@Value("${app.upload-dir:./uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.uploadDir);
        } catch (IOException e) {
            throw new RuntimeException("Cannot create upload directory: " + this.uploadDir, e);
        }
    }

    @Override
    public String store(String originalFileName, String contentType, long fileSize, InputStream inputStream) {
        String ext = "";
        int dot = originalFileName.lastIndexOf('.');
        if (dot > 0) ext = originalFileName.substring(dot);
        String key = UUID.randomUUID() + ext;

        try {
            Path target = uploadDir.resolve(key);
            Files.copy(inputStream, target, StandardCopyOption.REPLACE_EXISTING);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("Failed to store file", e);
        }
    }

    @Override
    public InputStream retrieve(String storageKey) {
        try {
            return Files.newInputStream(uploadDir.resolve(storageKey));
        } catch (IOException e) {
            throw new RuntimeException("File not found: " + storageKey, e);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(uploadDir.resolve(storageKey));
        } catch (IOException e) {
            log.warn("Failed to delete file: {}", storageKey);
        }
    }

    @Override
    public String getUrl(String storageKey, String fileName) {
        return null;
    }

    @Override
    public boolean supportsDirectUrl() {
        return false;
    }
}
