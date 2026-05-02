package com.start.agent.service;

import com.start.agent.model.Chapter;
import com.start.agent.model.Novel;
import com.start.agent.repository.ChapterRepository;
import com.start.agent.repository.NovelRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** 将小说与章节导出为纯文本等格式，供下载或外部分发。 */
@Slf4j
@Service
public class NovelExportService {
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("^第[\\d一二三四五六七八九十百千零]+章");
    private final NovelRepository novelRepository;
    private final ChapterRepository chapterRepository;
    public NovelExportService(NovelRepository novelRepository, ChapterRepository chapterRepository) { this.novelRepository = novelRepository; this.chapterRepository = chapterRepository; }

    public String exportNovelToTxt(Long novelId) {
        try {
            Optional<Novel> novelOpt = novelRepository.findById(novelId);
            if (novelOpt.isEmpty()) throw new RuntimeException("小说不存在，ID: " + novelId);
            Novel novel = novelOpt.get();
            List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
            if (chapters.isEmpty()) throw new RuntimeException("该小说还没有章节内容");
            StringBuilder txtContent = new StringBuilder();
            txtContent.append(novel.getTitle()).append("\n\n");
            txtContent.append("题材：").append(novel.getTopic()).append("\n");
            txtContent.append("总章节：").append(chapters.size()).append("章\n");
            txtContent.append("生成时间：").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("\n\n");
            txtContent.append("=".repeat(50)).append("\n\n");
            for (Chapter chapter : chapters) {
                txtContent.append(chapter.getTitle()).append("\n\n");
                String content = chapter.getContent();
                if (content != null && !content.isEmpty()) for (String paragraph : content.split("\n+")) if (!paragraph.trim().isEmpty()) txtContent.append(paragraph.trim()).append("\n");
                txtContent.append("\n");
            }
            return txtContent.toString();
        } catch (Exception e) { throw new RuntimeException("导出失败: " + e.getMessage(), e); }
    }

    public Map<String, Object> checkExportHealth(Long novelId) {
        Optional<Novel> novelOpt = novelRepository.findById(novelId);
        if (novelOpt.isEmpty()) throw new RuntimeException("小说不存在，ID: " + novelId);
        List<Chapter> chapters = chapterRepository.findByNovelIdOrderByChapterNumberAsc(novelId);
        Map<String, Object> report = new HashMap<>();
        report.put("novelId", novelId);
        report.put("chapterCount", chapters.size());
        List<String> issues = new ArrayList<>();
        String lastTitle = null;
        int expected = 1;
        for (Chapter chapter : chapters) {
            String title = chapter.getTitle() == null ? "" : chapter.getTitle().trim();
            if (title.equals(lastTitle)) issues.add("重复标题: " + title);
            Matcher matcher = CHAPTER_PATTERN.matcher(title);
            if (!matcher.find()) issues.add("标题格式异常: 第" + chapter.getChapterNumber() + "章 -> " + title);
            if (!chapter.getChapterNumber().equals(expected)) issues.add("章节号不连续: 期望 " + expected + " 实际 " + chapter.getChapterNumber());
            if (chapter.getContent() == null || chapter.getContent().isBlank()) issues.add("空章节: " + title);
            expected = chapter.getChapterNumber() + 1;
            lastTitle = title;
        }
        report.put("healthy", issues.isEmpty());
        report.put("issues", issues);
        return report;
    }
}
