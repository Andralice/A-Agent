package com.start.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class AiConfig {

    @Value("${spring.ai.openai.api-key}")
    private String apiKey;

    @Value("${spring.ai.openai.base-url}")
    private String baseUrl;

    @Value("${spring.ai.openai.chat.options.model:glm-5.1}")
    private String model;

    @Value("${spring.ai.openai.chat.options.temperature:0.8}")
    private Double temperature;

    @Value("${spring.ai.openai.chat.options.max-tokens:4096}")
    private Integer maxTokens;

    @Bean
    public OpenAiApi openAiApi() {
        log.info("【AI配置】初始化 OpenAiApi - baseUrl: {}, model: {}", baseUrl, model);
        return new OpenAiApi(baseUrl, apiKey);
    }

    @Bean
    public OpenAiChatModel openAiChatModel(OpenAiApi openAiApi) {
        log.info("【AI配置】初始化 OpenAiChatModel - model: {}, temperature: {}, maxTokens: {}",
                model, temperature, maxTokens);
        OpenAiChatOptions defaultOptions = OpenAiChatOptions.builder()
                .model(model)
                .temperature(temperature)
                .maxTokens(maxTokens)
                .build();
        return new OpenAiChatModel(openAiApi, defaultOptions);
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel chatModel) {
        log.info("【AI配置】初始化 ChatClient");
        return ChatClient.builder(chatModel).build();
    }
}
