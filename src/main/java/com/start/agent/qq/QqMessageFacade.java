package com.start.agent.qq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class QqMessageFacade {

    private final ObjectMapper objectMapper;
    private final QqGroupAccessService groupAccessService;
    private final QqCommandParser commandParser;
    private final QqCommandDispatcher commandDispatcher;

    public QqMessageFacade(ObjectMapper objectMapper,
                           QqGroupAccessService groupAccessService,
                           QqCommandParser commandParser,
                           QqCommandDispatcher commandDispatcher) {
        this.objectMapper = objectMapper;
        this.groupAccessService = groupAccessService;
        this.commandParser = commandParser;
        this.commandDispatcher = commandDispatcher;
    }

    public boolean handle(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            String postType = root.path("post_type").asText();
            if (!"message".equals(postType)) {
                log.debug("【消息过滤】非消息类型事件，post_type={}, 已忽略", postType);
                return false;
            }

            String messageType = root.path("message_type").asText();
            if (!"group".equals(messageType)) {
                log.debug("【消息过滤】非群消息，message_type={}, 已忽略", messageType);
                return false;
            }

            long groupId = root.path("group_id").asLong();
            long userId = root.path("user_id").asLong();
            String rawMessage = root.path("raw_message").asText();

            if (rawMessage == null || rawMessage.trim().isEmpty()) {
                log.warn("【消息验证】收到空消息，群ID={}, 用户ID={}", groupId, userId);
                return false;
            }

            if (!groupAccessService.isAllowed(groupId)) {
                log.info("【权限拦截】未授权的群尝试访问，群ID={}, 用户ID={}, 消息内容={}", groupId, userId, rawMessage);
                return false;
            }

            log.info("【✅ 收到指令】群[{}] 用户[{}] 发送: {}", groupId, userId, rawMessage);
            QqMessageContext context = new QqMessageContext(groupId, userId, rawMessage);
            QqCommand command = commandParser.parse(rawMessage);
            commandDispatcher.dispatch(context, command);
            return true;
        } catch (Exception e) {
            log.error("【❌ 异常】处理QQ/NapCat消息时发生错误", e);
            throw new RuntimeException("处理消息失败", e);
        }
    }
}
