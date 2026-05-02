package com.start.agent.service;

import com.start.agent.model.Chapter;
import com.start.agent.model.Novel;
import org.springframework.stereotype.Component;

/** 将新书/新章格式化为适合推送到 QQ 群的文案。 */
@Component
public class NovelMessageFormatter {
    public String formatNovelMessage(Novel novel, Chapter chapter) {
        return String.format("""
            📖【新书发布】%s
            
            %s
            
            ---
            💡 回复 /续写%d 继续阅读下一章
            📝 回复 /大纲 查看完整大纲
            """, novel.getTitle(), chapter.getContent(), chapter.getChapterNumber() + 1);
    }
    public String formatExistingChapter(Chapter chapter) {
        return String.format("""
            📖【%s】
            
            %s
            
            ---
            💡 回复 /续写 继续阅读
            """, chapter.getTitle(), chapter.getContent());
    }
}
