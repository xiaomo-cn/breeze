package cn.xiaomo.breeze.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.storage")
public record StorageProperties(
    String provider,
    LocalConfig local,
    S3Config s3
) {
    public record LocalConfig(String uploadDir) {}
    public record S3Config(
        String endpoint,
        String region,
        String accessKey,
        String secretKey,
        String bucketName,
        String cdnDomain,
        int urlExpirationMinutes
    ) {}
}
