package com.example.airouter;

import com.alibaba.cloud.ai.autoconfigure.dashscope.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.ai.model.deepseek.autoconfigure.DeepSeekChatAutoConfiguration;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(exclude = {
        // 排除Openai自动配置
        OpenAiChatAutoConfiguration.class,
        OpenAiEmbeddingAutoConfiguration.class,
        OpenAiImageAutoConfiguration.class,
        OpenAiAudioSpeechAutoConfiguration.class,
        OpenAiAudioTranscriptionAutoConfiguration.class,
        OpenAiModerationAutoConfiguration.class,
        //排除deepseek自动配置
        DeepSeekChatAutoConfiguration.class,
        // 排除dashscope自动配置
        DashScopeChatAutoConfiguration.class,
        DashScopeAgentAutoConfiguration.class,
        DashScopeEmbeddingAutoConfiguration.class,
        DashScopeImageAutoConfiguration.class,
        DashScopeAudioSpeechAutoConfiguration.class,
        DashScopeAudioTranscriptionAutoConfiguration.class,
        DashScopeRerankAutoConfiguration.class,
        DashScopeVideoAutoConfiguration.class
})
@MapperScan("com.example.airouter.mapper")
@EnableScheduling
public class AirouterApplication {

    public static void main(String[] args) {
        SpringApplication.run(AirouterApplication.class, args);
    }

}
