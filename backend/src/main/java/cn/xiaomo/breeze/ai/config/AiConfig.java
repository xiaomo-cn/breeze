package cn.xiaomo.breeze.ai.config;

import cn.xiaomo.breeze.ai.tool.ReadTools;
import cn.xiaomo.breeze.ai.tool.TaskTools;
import cn.xiaomo.breeze.ai.tool.WriteTools;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
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
        log.info("ChatClient configured with tools from TaskTools, ReadTools, WriteTools");

        return builder
            .defaultTools(taskTools, readTools, writeTools)
            .build();
    }
}
