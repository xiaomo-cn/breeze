package cn.xiaomo.breeze.config;

import java.net.URI;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration
@Conditional(S3StorageCondition.class)
public class S3Config {

    private final StorageProperties.S3Config config;

    public S3Config(StorageProperties storageProperties) {
        this.config = storageProperties.s3();
    }

    @Bean
    public S3Client s3Client() {
        return S3Client.builder()
            .endpointOverride(URI.create(config.endpoint()))
            .region(Region.of(config.region()))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(config.accessKey(), config.secretKey())))
            .forcePathStyle(true)
            .build();
    }
}
