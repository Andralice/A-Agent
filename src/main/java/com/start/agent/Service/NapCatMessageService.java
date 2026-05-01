package com.start.agent.service;

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
    public NapCatMessageService(ObjectMapper objectMapper) { this.objectMapper = objectMapper; }
    public void setWebSocketClient(WebSocketClient client) { this.webSocketClient = client; }
    public boolean isWebSocketConnected() { return webSocketClient != null && webSocketClient.isOpen(); }
    public void sendGroupMessage(Long groupId, String message) {
        if (!isWebSocketConnected()) throw new RuntimeException("WebSocket连接不可用，请检查NapCat服务状态");
        try {
            Map<String, Object> payload = new HashMap<>(); payload.put("action", "send_group_msg");
            Map<String, Object> params = new HashMap<>(); params.put("group_id", groupId); params.put("message", message); payload.put("params", params);
            webSocketClient.send(objectMapper.writeValueAsString(payload));
        } catch (Exception e) { throw new RuntimeException("WebSocket发送消息失败", e); }
    }
}
