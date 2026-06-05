package cn.xiaomo.breeze;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
@EnableCaching
@EnableConfigurationProperties(cn.xiaomo.breeze.config.StorageProperties.class)
@MapperScan(basePackages = "cn.xiaomo.breeze", annotationClass = org.apache.ibatis.annotations.Mapper.class)
public class BreezeApplication {

    public static void main(String[] args) {
        SpringApplication.run(BreezeApplication.class, args);
    }
}
