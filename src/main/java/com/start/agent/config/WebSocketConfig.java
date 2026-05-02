package com.start.agent.config;

import com.start.agent.service.NapCatMessageService;
import com.start.agent.qq.QqMessageFacade;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.net.URI;
import java.util.concurrent.TimeUnit;

/** 连接 NapCat WebSocket，并在就绪时注入 {@link com.start.agent.service.NapCatMessageService}。 */
@Slf4j
@Configuration
public class WebSocketConfig {

    @Value("${napcat.ws-url:ws://127.0.0.1:5701}")
    private String wsUrl;

    @Value("${napcat.access-token:}")
    private String accessToken;

    private final NapCatMessageService messageService;
    private final QqMessageFacade qqMessageFacade;
    private NapCatWebSocketClient webSocketClient;

    public WebSocketConfig(NapCatMessageService messageService, QqMessageFacade qqMessageFacade) {
        this.messageService = messageService;
        this.qqMessageFacade = qqMessageFacade;
    }

    @PostConstruct
    public void init() {
        log.info("【系统启动】开始初始化WebSocket连接...");

        try {
            URI uri = URI.create(wsUrl);
            webSocketClient = new NapCatWebSocketClient(uri, qqMessageFacade);

            if (accessToken != null && !accessToken.isEmpty()) {
                webSocketClient.addHeader("Authorization", "Bearer " + accessToken);
                log.info("【系统启动】已配置AccessToken");
            }

            webSocketClient.setConnectionLostTimeout(30);

            boolean connected = webSocketClient.connectBlocking(5, TimeUnit.SECONDS);
            if (connected) {
                log.info("【✅ WebSocket连接】成功连接到NapCat: {}", wsUrl);
                messageService.setWebSocketClient(webSocketClient);
                log.info("【系统启动】WebSocketClient已注册到NapCatMessageService");
            } else {
                log.error("【❌ WebSocket连接】连接NapCat失败: {}", wsUrl);
            }
        } catch (Exception e) {
            log.error("【❌ WebSocket连接】初始化异常", e);
        }

        log.info("【系统启动】WebSocket配置初始化完成");
    }

    @PreDestroy
    public void destroy() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            log.info("【系统关闭】WebSocket连接已关闭");
        }
    }

    public static class NapCatWebSocketClient extends WebSocketClient {

        private final QqMessageFacade qqMessageFacade;

        public NapCatWebSocketClient(URI serverUri, QqMessageFacade qqMessageFacade) {
            super(serverUri);
            this.qqMessageFacade = qqMessageFacade;
        }

        @Override
        public void onOpen(ServerHandshake handshakedata) {
            log.info("【🔌 WebSocket】连接已建立");
        }

        @Override
        public void onMessage(String message) {
            try {
                log.debug("【📨 WebSocket】收到原始消息: {}", message);
                qqMessageFacade.handle(message);
            } catch (Exception e) {
                log.error("【❌ WebSocket】处理消息异常", e);
            }
        }

        @Override
        public void onClose(int code, String reason, boolean remote) {
            log.warn("【🔌 WebSocket】连接已关闭: code={}, reason={}, remote={}", code, reason, remote);
            log.info("【💡 提示】连接已断开，请检查 NapCat 服务状态或等待其重新推送");
        }

        @Override
        public void onError(Exception ex) {
            log.error("【❌ WebSocket】连接异常", ex);
        }
    }
}
