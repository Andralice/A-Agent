package com.start.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.concurrent.TimeUnit;

/** Spring AI {@link org.springframework.ai.chat.client.ChatClient} 与 OpenAI 兼容接口的 Bean 装配（模型、温度等）。 */
@Slf4j
@Configuration(proxyBeanMethods = false)
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

    /** 连接池最大连接数（总）。 */
    @Value("${novel.ai.http.max-total:50}")
    private int maxTotal;

    /** 每个路由最大连接数。 */
    @Value("${novel.ai.http.max-per-route:20}")
    private int maxPerRoute;

    @Bean(destroyMethod = "close")
    public CloseableHttpClient pooledHttpClient() {
        PoolingHttpClientConnectionManager cm = new PoolingHttpClientConnectionManager();
        cm.setMaxTotal(maxTotal);
        cm.setDefaultMaxPerRoute(maxPerRoute);
        cm.setValidateAfterInactivity(TimeValue.ofMilliseconds(TimeUnit.SECONDS.toMillis(30)));
        return HttpClients.custom()
                .setConnectionManager(cm)
                .evictIdleConnections(TimeValue.ofMilliseconds(TimeUnit.SECONDS.toMillis(120)))
                .build();
    }

    @Bean
    public OpenAiApi openAiApi(CloseableHttpClient pooledHttpClient) {
        log.info("【AI配置】初始化 OpenAiApi (连接池 maxTotal={} maxPerRoute={}) - baseUrl: {}, model: {}",
                maxTotal, maxPerRoute, baseUrl, model);
        RestClient.Builder restClientBuilder = RestClient.builder()
                .requestFactory(new HttpComponentsClientHttpRequestFactory(pooledHttpClient));
        return new OpenAiApi(baseUrl, apiKey, restClientBuilder, WebClient.builder());
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

    /** 任务恢复：手动注册以绕过 CGLIB 代理在中文路径下的 classloader 问题。 */
    @Bean
    public GenerationTaskRecoveryRunner generationTaskRecoveryRunner(com.start.agent.service.GenerationTaskService svc) {
        return new GenerationTaskRecoveryRunner(svc);
    }
}
