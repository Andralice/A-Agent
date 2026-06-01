package com.start.agent.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 为 electron-updater 提供静态文件服务：把 /opt/qq-bot/updates/ 映射到 /updates/** URL */
@Configuration
public class StaticResourceConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/updates/**")
                .addResourceLocations("file:/opt/qq-bot/updates/");
    }
}
