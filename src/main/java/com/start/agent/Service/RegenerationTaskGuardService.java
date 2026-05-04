package com.start.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内章节区间互斥锁：防止同一小说的重叠重生/续写任务并发踩同一批章节。
 */
@Service
public class RegenerationTaskGuardService {
    private final Map<Long, List<Range>> runningRangesByNovel = new ConcurrentHashMap<>();
    /** 进程内：大纲重写占用期间禁止章节区间锁（与 DB 租约配合）。 */
    private final Set<Long> outlineRegenerationNovelIds = Collections.newSetFromMap(new ConcurrentHashMap<>());

    /**
     * 大纲重写开始前占用；与 {@link #tryAcquireRange} 互斥，也与已有章节区间互斥。
     */
    public synchronized boolean tryAcquireOutlineRegenerationLock(Long novelId) {
        if (outlineRegenerationNovelIds.contains(novelId)) {
            return false;
        }
        List<Range> ranges = runningRangesByNovel.get(novelId);
        if (ranges != null && !ranges.isEmpty()) {
            return false;
        }
        outlineRegenerationNovelIds.add(novelId);
        return true;
    }

    public synchronized void releaseOutlineRegenerationLock(Long novelId) {
        outlineRegenerationNovelIds.remove(novelId);
    }

    public synchronized boolean hasOutlineRegenerationLock(Long novelId) {
        return outlineRegenerationNovelIds.contains(novelId);
    }

    public synchronized boolean tryAcquireRange(Long novelId, int fromChapter, int toChapter) {
        if (outlineRegenerationNovelIds.contains(novelId)) {
            return false;
        }
        List<Range> ranges = runningRangesByNovel.computeIfAbsent(novelId, id -> new ArrayList<>());
        for (Range range : ranges) {
            if (range.overlaps(fromChapter, toChapter)) {
                return false;
            }
        }
        ranges.add(new Range(fromChapter, toChapter));
        return true;
    }

    public synchronized void releaseRange(Long novelId, int fromChapter, int toChapter) {
        List<Range> ranges = runningRangesByNovel.get(novelId);
        if (ranges == null) return;
        ranges.removeIf(range -> range.from == fromChapter && range.to == toChapter);
        if (ranges.isEmpty()) runningRangesByNovel.remove(novelId);
    }

    public synchronized List<String> getRunningRanges(Long novelId) {
        List<Range> ranges = runningRangesByNovel.getOrDefault(novelId, List.of());
        List<String> result = new ArrayList<>();
        for (Range range : ranges) result.add(range.from + "-" + range.to);
        return result;
    }

    public synchronized boolean hasRunningTask(Long novelId) {
        List<Range> ranges = runningRangesByNovel.get(novelId);
        return ranges != null && !ranges.isEmpty();
    }

    public synchronized boolean hasOverlap(Long novelId, int fromChapter, int toChapter) {
        List<Range> ranges = runningRangesByNovel.get(novelId);
        if (ranges == null || ranges.isEmpty()) return false;
        for (Range range : ranges) {
            if (range.overlaps(fromChapter, toChapter)) return true;
        }
        return false;
    }

    private static class Range {
        private final int from;
        private final int to;

        private Range(int from, int to) {
            this.from = from;
            this.to = to;
        }

        private boolean overlaps(int otherFrom, int otherTo) {
            return !(otherTo < this.from || otherFrom > this.to);
        }
    }
}
