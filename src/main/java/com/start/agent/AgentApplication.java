package com.start.agent;

import com.start.agent.config.AppSecurityProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * Spring Boot 入口：小说生成 Agent 后端（REST、QQ Webhook、定时任务、JPA）。
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(AppSecurityProperties.class)
public class AgentApplication {
    public static void main(String[] args) throws UnknownHostException {
        log.info("╔═══════════════════════════════════════════════════════════╗");
        log.info("║                                                           ║");
        log.info("║       🚀 爽文生成Agent系统 正在启动...                    ║");
        log.info("║                                                           ║");
        log.info("╚═══════════════════════════════════════════════════════════╝");
        
        try {
            SpringApplication app = new SpringApplication(AgentApplication.class);
            app.run(args);
            
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            log.info("╔═══════════════════════════════════════════════════════════╗");
            log.info("║                                                           ║");
            log.info("║       ✅ 系统启动成功！                                   ║");
            log.info("║                                                           ║");
            log.info("║       📡 服务地址: http://{}:8080                        ║", hostAddress);
            log.info("║       📝 API端点: /api/qq/message                        ║");
            log.info("║       🎯 可用指令: /写, /续写, /大纲                      ║");
            log.info("║       🧹 定时清理: 每天凌晨2点清理空白小说                ║");
            log.info("║                                                           ║");
            log.info("╚═══════════════════════════════════════════════════════════╝");
        } catch (Exception e) {
            log.error("╔═══════════════════════════════════════════════════════════╗");
            log.error("║                                                           ║");
            log.error("║       ❌ 系统启动失败！                                   ║");
            log.error("║                                                           ║");
            log.error("╚═══════════════════════════════════════════════════════════╝", e);
            throw e;
        }
    }
}