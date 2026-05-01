package com.start.agent.qq;

public record QqCommand(
        QqCommandType type,
        String topic,
        Integer novelId,
        Integer chapterNumber,
        Integer targetChapters,
        String rawMessage
) {
    public boolean isUnknown() {
        return type == QqCommandType.UNKNOWN;
    }
}
