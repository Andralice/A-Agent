package com.start.agent.qq;

/** 单条 QQ 斜杠指令的结构化结果（类型与参数）。 */
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
