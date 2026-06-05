package cn.xiaomo.breeze.config;

import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.core.type.AnnotatedTypeMetadata;

/** 当 app.storage.provider 为 s3、minio 或 oss 时激活 S3 相关 Bean */
public class S3StorageCondition implements Condition {

    @Override
    public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
        String provider = context.getEnvironment().getProperty("app.storage.provider");
        return "s3".equals(provider) || "minio".equals(provider) || "oss".equals(provider);
    }
}
