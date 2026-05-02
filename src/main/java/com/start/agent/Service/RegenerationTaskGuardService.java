package com.start.agent.service;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 进程内章节区间互斥锁：防止同一小说的重叠重生/续写任务并发踩同一批章节。
 */
@Service
public class RegenerationTaskGuardService {
    private final Map<Long, List<Range>> runningRangesByNovel = new ConcurrentHashMap<>();

    public synchronized boolean tryAcquireRange(Long novelId, int fromChapter, int toChapter) {
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
