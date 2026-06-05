package cn.xiaomo.breeze.attachment;

import cn.xiaomo.breeze.config.S3StorageCondition;
import cn.xiaomo.breeze.config.StorageProperties;
import jakarta.annotation.PostConstruct;
import java.io.InputStream;
import java.time.Duration;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Conditional;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Slf4j
@Service
@Conditional(S3StorageCondition.class)
public class S3FileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final S3Presigner presigner;
    private final String bucketName;
    private final Duration urlExpiration;

    public S3FileStorageService(S3Client s3Client, StorageProperties storageProperties) {
        this.s3Client = s3Client;
        StorageProperties.S3Config config = storageProperties.s3();
        this.bucketName = config.bucketName();
        this.urlExpiration = Duration.ofMinutes(config.urlExpirationMinutes());
        this.presigner = S3Presigner.builder()
            .region(s3Client.serviceClientConfiguration().region())
            .credentialsProvider(s3Client.serviceClientConfiguration().credentialsProvider())
            .endpointOverride(s3Client.serviceClientConfiguration().endpointOverride().orElse(null))
            .build();
    }

    @PostConstruct
    public void init() {
        try {
            s3Client.headBucket(HeadBucketRequest.builder().bucket(bucketName).build());
            log.info("S3 bucket '{}' exists", bucketName);
        } catch (NoSuchBucketException e) {
            try {
                s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
                log.info("Created S3 bucket: {}", bucketName);
            } catch (Exception ce) {
                log.warn("Cannot create S3 bucket '{}': {}. Bucket must already exist.", bucketName, ce.getMessage());
            }
        } catch (Exception e) {
            log.warn("Cannot verify S3 bucket '{}': {}. Assuming bucket exists.", bucketName, e.getMessage());
        }
    }

    @Override
    public String store(String originalFileName, String contentType, long fileSize, InputStream inputStream) {
        String ext = "";
        int dot = originalFileName.lastIndexOf('.');
        if (dot > 0) ext = originalFileName.substring(dot);
        String key = UUID.randomUUID() + ext;

        PutObjectRequest request = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(contentType)
            .contentLength(fileSize)
            .build();

        s3Client.putObject(
            request,
            software.amazon.awssdk.core.sync.RequestBody.fromInputStream(inputStream, fileSize));
        return key;
    }

    @Override
    public InputStream retrieve(String storageKey) {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(storageKey)
            .build();
        return s3Client.getObject(request);
    }

    @Override
    public void delete(String storageKey) {
        DeleteObjectRequest request = DeleteObjectRequest.builder()
            .bucket(bucketName)
            .key(storageKey)
            .build();
        s3Client.deleteObject(request);
    }

    @Override
    public String getUrl(String storageKey, String fileName) {
        GetObjectRequest getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(storageKey)
            .responseContentDisposition("inline; filename=\"" + fileName + "\"")
            .build();

        var presigned = presigner.presignGetObject(
            GetObjectPresignRequest.builder()
                .getObjectRequest(getObjectRequest)
                .signatureDuration(urlExpiration)
                .build());

        return presigned.url().toString();
    }

    @Override
    public boolean supportsDirectUrl() {
        return true;
    }
}
