package com.start.agent.qq;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** 把群聊文本解析为 {@link QqCommand}（写书、续写、列表等斜杠指令）。 */
@Slf4j
@Component
public class QqCommandParser {

    private static final String COMMAND_WRITE = "/写";
    private static final String COMMAND_CONTINUE = "/续写";
    private static final String COMMAND_OUTLINE = "/大纲";
    private static final String COMMAND_LIST = "/列表";
    private static final String COMMAND_READ = "/看小说";
    private static final String COMMAND_AUTO = "/自动续写";
    private static final String DEFAULT_TOPIC = "系统 重生";
    private static final int DEFAULT_TARGET_CHAPTERS = 20;

    public QqCommand parse(String rawMessage) {
        if (rawMessage == null || rawMessage.trim().isEmpty()) {
            return unknown(rawMessage);
        }

        String trimmed = rawMessage.trim();
        if (trimmed.startsWith(COMMAND_AUTO)) {
            String param = extractParam(trimmed, COMMAND_AUTO);
            return new QqCommand(
                    QqCommandType.AUTO_CONTINUE,
                    null,
                    parseNovelId(param),
                    null,
                    parseTargetChapters(param),
                    rawMessage
            );
        }

        if (trimmed.startsWith(COMMAND_WRITE)) {
            return new QqCommand(
                    QqCommandType.WRITE,
                    extractTopic(trimmed),
                    null,
                    null,
                    null,
                    rawMessage
            );
        }

        if (trimmed.startsWith(COMMAND_CONTINUE)) {
            String param = extractParam(trimmed, COMMAND_CONTINUE);
            return new QqCommand(
                    QqCommandType.CONTINUE,
                    null,
                    parseNovelId(param),
                    parseChapterNumber(param),
                    null,
                    rawMessage
            );
        }

        if (trimmed.startsWith(COMMAND_OUTLINE)) {
            String param = extractParam(trimmed, COMMAND_OUTLINE);
            return new QqCommand(
                    QqCommandType.OUTLINE,
                    null,
                    parseNovelId(param),
                    null,
                    null,
                    rawMessage
            );
        }

        if (COMMAND_LIST.equals(trimmed)) {
            return new QqCommand(QqCommandType.LIST, null, null, null, null, rawMessage);
        }

        if (trimmed.startsWith(COMMAND_READ)) {
            String param = extractParam(trimmed, COMMAND_READ);
            return new QqCommand(
                    QqCommandType.READ,
                    null,
                    parseNovelId(param),
                    parseChapterNumber(param),
                    null,
                    rawMessage
            );
        }

        return unknown(rawMessage);
    }

    private QqCommand unknown(String rawMessage) {
        return new QqCommand(QqCommandType.UNKNOWN, null, null, null, null, rawMessage);
    }

    private String extractTopic(String rawMessage) {
        String content = extractParam(rawMessage, COMMAND_WRITE);
        if (content.isEmpty()) {
            log.debug("【参数处理】未指定题材，使用默认题材: {}", DEFAULT_TOPIC);
            return DEFAULT_TOPIC;
        }
        return content;
    }

    private String extractParam(String rawMessage, String command) {
        return rawMessage.substring(command.length()).trim();
    }

    private Integer parseNovelId(String param) {
        if (param == null || param.trim().isEmpty()) {
            return null;
        }

        String[] parts = param.trim().split("\\s+");
        try {
            return Integer.parseInt(parts[0]);
        } catch (NumberFormatException e) {
            log.warn("【参数处理】小说ID格式错误: {}", parts[0]);
            return null;
        }
    }

    private Integer parseChapterNumber(String param) {
        if (param == null || param.trim().isEmpty()) {
            return null;
        }

        String[] parts = param.trim().split("\\s+");
        if (parts.length < 2) {
            return null;
        }

        try {
            return Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            log.warn("【参数处理】章节号格式错误: {}", parts[1]);
            return null;
        }
    }

    private Integer parseTargetChapters(String param) {
        if (param == null || param.trim().isEmpty()) {
            return DEFAULT_TARGET_CHAPTERS;
        }

        String[] parts = param.trim().split("\\s+");
        String target = parts.length >= 2 ? parts[1] : parts[0];
        try {
            return Integer.parseInt(target);
        } catch (NumberFormatException e) {
            log.warn("【参数处理】目标章节格式错误: {}", target);
            return DEFAULT_TARGET_CHAPTERS;
        }
    }
}
