package com.start.agent.qq;

import com.start.agent.service.NovelAgentService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;

@Slf4j
@Service
public class QqCommandDispatcher {

    private final NovelAgentService agentService;
    private final ExecutorService asyncExecutor;

    public QqCommandDispatcher(NovelAgentService agentService) {
        this.agentService = agentService;
        this.asyncExecutor = new ThreadPoolExecutor(
            10, 20, 60L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.LinkedBlockingQueue<>(100),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
        log.info("【系统初始化】命令分发器线程池已就绪 - 核心线程: 10, 最大线程: 20, 队列容量: 100");
    }

    public void dispatch(QqMessageContext context, QqCommand command) {
        switch (command.type()) {
            case WRITE -> processNovelAsync(context, command.topic());
            case CONTINUE -> continueNovelAsync(context, command.novelId(), command.chapterNumber());
            case OUTLINE -> showOutlineAsync(context, command.novelId());
            case LIST -> listNovelsAsync(context);
            case READ -> readNovelAsync(context, command.novelId(), command.chapterNumber());
            case AUTO_CONTINUE -> autoContinueAsync(context, command.novelId(), command.targetChapters());
            case UNKNOWN -> log.debug("【指令识别】未识别的指令，已忽略");
        }
    }

    private void processNovelAsync(QqMessageContext context, String topic) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】新建小说 - 群: {}, 用户: {}, 题材: {}", context.groupId(), context.userId(), topic);
                agentService.processAndSend(context.groupId(), topic);
                log.info("【✅ 任务完成】小说生成并发送成功 - 群: {}, 题材: {}", context.groupId(), topic);
            } catch (Exception e) {
                log.error("【❌ 任务失败】小说生成异常 - 群: {}, 题材: {}", context.groupId(), topic, e);
            }
        }, asyncExecutor);
    }

    private void continueNovelAsync(QqMessageContext context, Integer novelId, Integer nextChapter) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】续写小说 - 群: {}, 用户: {}, 小说ID: {}, 目标章节: {}",
                        context.groupId(), context.userId(), novelId, nextChapter == null ? "自动下一章" : nextChapter);
                agentService.continueChapter(context.groupId(), novelId, nextChapter);
                log.info("【✅ 任务完成】续写完成 - 群: {}", context.groupId());
            } catch (Exception e) {
                log.error("【❌ 任务失败】续写异常 - 群: {}", context.groupId(), e);
            }
        }, asyncExecutor);
    }

    private void showOutlineAsync(QqMessageContext context, Integer novelId) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】查看大纲 - 群: {}, 用户: {}, 小说ID: {}", context.groupId(), context.userId(), novelId);
                agentService.showOutline(context.groupId(), novelId);
                log.info("【✅ 任务完成】大纲发送完成 - 群: {}", context.groupId());
            } catch (Exception e) {
                log.error("【❌ 任务失败】发送大纲异常 - 群: {}", context.groupId(), e);
            }
        }, asyncExecutor);
    }

    private void listNovelsAsync(QqMessageContext context) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】查看小说列表 - 群: {}, 用户: {}", context.groupId(), context.userId());
                agentService.listNovels(context.groupId());
                log.info("【✅ 任务完成】列表发送完成 - 群: {}", context.groupId());
            } catch (Exception e) {
                log.error("【❌ 任务失败】发送列表异常 - 群: {}", context.groupId(), e);
            }
        }, asyncExecutor);
    }

    private void readNovelAsync(QqMessageContext context, Integer novelId, Integer chapterNum) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】看小说 - 群: {}, 用户: {}, 小说ID: {}, 章节: {}",
                        context.groupId(), context.userId(), novelId, chapterNum);
                agentService.readNovel(context.groupId(), novelId, chapterNum);
                log.info("【✅ 任务完成】小说发送完成 - 群: {}", context.groupId());
            } catch (Exception e) {
                log.error("【❌ 任务失败】发送小说异常 - 群: {}", context.groupId(), e);
            }
        }, asyncExecutor);
    }

    private void autoContinueAsync(QqMessageContext context, Integer novelId, Integer targetChapters) {
        CompletableFuture.runAsync(() -> {
            try {
                log.info("【🚀 开始任务】自动续写 - 群: {}, 用户: {}, 小说ID: {}, 目标章节: {}",
                        context.groupId(), context.userId(), novelId, targetChapters);
                agentService.autoContinueChapter(context.groupId(), novelId, targetChapters);
                log.info("【✅ 任务完成】自动续写完成 - 群: {}", context.groupId());
            } catch (Exception e) {
                log.error("【❌ 任务失败】自动续写异常 - 群: {}", context.groupId(), e);
            }
        }, asyncExecutor);
    }
}
