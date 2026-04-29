package com.start.agent.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;

@Slf4j
@Configuration
public class OpenAiRequestLoggerConfig {

    @Value("${app.ai-request-log.enabled:false}")
    private boolean requestLogEnabled;

    @Value("${app.ai-request-log.body-enabled:false}")
    private boolean requestBodyLogEnabled;

    @Value("${app.ai-request-log.max-body-length:2000}")
    private int maxBodyLogLength;

    @Bean
    public RestClient.Builder restClientBuilder() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(120000);
        requestFactory.setReadTimeout(300000);
        
        return RestClient.builder()
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    if (requestLogEnabled) {
                        HttpHeaders safeHeaders = new HttpHeaders();
                        safeHeaders.putAll(request.getHeaders());
                        safeHeaders.remove(HttpHeaders.AUTHORIZATION);

                        log.debug("【AI请求】URL: {}", request.getURI());
                        log.debug("【AI请求】Headers: {}", safeHeaders);

                        if (requestBodyLogEnabled && body != null && body.length > 0) {
                            String bodyText = new String(body, StandardCharsets.UTF_8);
                            if (bodyText.length() > maxBodyLogLength) {
                                bodyText = bodyText.substring(0, maxBodyLogLength) + "...(truncated)";
                            }
                            log.debug("【AI请求】Body: {}", bodyText);
                        }
                    }
                    return execution.execute(request, body);
                });
    }
}
