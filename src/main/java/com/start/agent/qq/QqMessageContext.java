package com.start.agent.qq;

public record QqMessageContext(long groupId, long userId, String rawMessage) {
}
