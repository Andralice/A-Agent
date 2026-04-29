package com.start.agent.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.java_websocket.client.WebSocketClient;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@Service
public class NapCatMessageService {

    private final ObjectMapper objectMapper;
    private WebSocketClient webSocketClient;

    public NapCatMessageService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void setWebSocketClient(WebSocketClient client) {
        this.webSocketClient = client;
    }

    public void sendGroupMessage(Long groupId, String message) {
        if (webSocketClient == null || !webSocketClient.isOpen()) {
            log.error("【❌ 发送失败】WebSocket连接未建立");
            throw new RuntimeException("WebSocket连接未建立");
        }

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("action", "send_group_msg");

            Map<String, Object> params = new HashMap<>();
            params.put("group_id", groupId);
            params.put("message", message);
            payload.put("params", params);

            String jsonPayload = objectMapper.writeValueAsString(payload);
            webSocketClient.send(jsonPayload);
            log.info("【✅ WebSocket发送】消息已发送至群: {}, 内容长度: {}", groupId, message.length());
        } catch (Exception e) {
            log.error("【❌ WebSocket发送】发送消息失败 - 群: {}", groupId, e);
            throw new RuntimeException("WebSocket发送消息失败", e);
        }
    }
}
