package cn.xiaomo.breeze.ai.config;

import cn.xiaomo.breeze.ai.tool.ReadTools;
import cn.xiaomo.breeze.ai.tool.TaskTools;
import cn.xiaomo.breeze.ai.tool.WriteTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    public ChatClient chatClient(ChatClient.Builder builder,
                                  TaskTools taskTools,
                                  ReadTools readTools,
                                  WriteTools writeTools) {
        // 使用 MethodToolCallbackProvider 显式扫描 @Tool 方法，
        // 避免 CGLIB 代理导致的注解丢失问题（Spring AI #3485）
        var toolProvider = MethodToolCallbackProvider.builder()
            .toolObjects(taskTools, readTools, writeTools)
            .build();

        log.info("ChatClient configured with {} tools from TaskTools, ReadTools, WriteTools",
            toolProvider.getToolCallbacks().length);

        return builder
            .defaultTools(toolProvider)
            .build();
    }
}
