package com.start.agent.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.start.agent.agent.NovelGenerationAgent;
import com.start.agent.model.NovelCharacterState;
import com.start.agent.repository.NovelCharacterStateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 本章登场调度（Cast + focus）解析与提示词注入，以及书本级 {@link NovelCharacterState} 读写在章节成功后的增量合并。
 */
@Slf4j
@Service
public class CharacterNarrativeStateService {

    private static final String UNKNOWN_NAME = "未知角色";

    private final NovelCharacterStateRepository stateRepository;
    private final ObjectMapper objectMapper;
    private final NovelGenerationAgent generationAgent;

    private final boolean castEnabled;
    private final int castMaxNames;
    private final boolean castRestrictBackground;
    private final boolean stateInjectEnabled;
    private final int stateInjectMaxChars;
    private final boolean deltaEnabled;

    public CharacterNarrativeStateService(
            NovelCharacterStateRepository stateRepository,
            ObjectMapper objectMapper,
            NovelGenerationAgent generationAgent,
            @Value("${novel.generation.character-cast-enabled:false}") boolean castEnabled,
            @Value("${novel.generation.character-cast-max-names:5}") int castMaxNames,
            @Value("${novel.generation.character-cast-restrict-background:true}") boolean castRestrictBackground,
            @Value("${novel.generation.character-state-inject-enabled:false}") boolean stateInjectEnabled,
            @Value("${novel.generation.character-state-inject-max-chars:3500}") int stateInjectMaxChars,
            @Value("${novel.generation.character-state-delta-enabled:false}") boolean deltaEnabled) {
        this.stateRepository = stateRepository;
        this.objectMapper = objectMapper;
        this.generationAgent = generationAgent;
        this.castEnabled = castEnabled;
        this.castMaxNames = Math.max(1, Math.min(12, castMaxNames));
        this.castRestrictBackground = castRestrictBackground;
        this.stateInjectEnabled = stateInjectEnabled;
        this.stateInjectMaxChars = Math.max(500, Math.min(20000, stateInjectMaxChars));
        this.deltaEnabled = deltaEnabled;
    }

    public boolean isCastEnabled() {
        return castEnabled;
    }

    public boolean isStateInjectEnabled() {
        return stateInjectEnabled;
    }

    public boolean isDeltaEnabled() {
        return deltaEnabled;
    }

    /**
     * @param chapterSettingRaw 本章附加设定：可为纯文本，或 JSON（notes/text + chapterCast）。
     */
    public ChapterNarrationResolution resolveForChapter(Long novelId, int chapterNumber, String chapterSettingRaw,
                                                        String writingStyleParamsJson) {
        ParsedChapterSetting parsed = ParsedChapterSetting.parse(chapterSettingRaw, objectMapper);
        JsonNode bookDefaultCast = readChapterCastDefault(writingStyleParamsJson);

        JsonNode effectiveCast = mergeCastConfig(bookDefaultCast, parsed.chapterCastNode());
        List<CastEntry> entries = extractCastEntries(effectiveCast);
        if (entries.size() > castMaxNames) {
            entries = entries.subList(0, castMaxNames);
        }
        boolean restrict = castRestrictBackground;
        if (effectiveCast != null && effectiveCast.hasNonNull("restrictOthersToBackground")) {
            restrict = effectiveCast.path("restrictOthersToBackground").asBoolean(restrict);
        }

        String inject = null;
        if (castEnabled || stateInjectEnabled) {
            StringBuilder sb = new StringBuilder();
            if (castEnabled && !entries.isEmpty()) {
                sb.append("- **本章建议显性登场与台词分量**：优先下列角色（读者应能明确感知其差异化口吻，见档案「对白声纹」）：\n");
                for (CastEntry e : entries) {
                    sb.append("  · ").append(e.name());
                    if (!e.focus().isEmpty()) {
                        sb.append(" —— 本章表现侧重：").append(String.join("、", e.focus()));
                    }
                    sb.append("\n");
                }
                if (restrict) {
                    sb.append("- **次要角色**：未列入者仅允许侧写或功能性带过，避免抢戏、避免共用同一套指挥口令模板。\n");
                }
            } else if (castEnabled) {
                sb.append("- **本章登场调度**：未配置 chapterCast；仍须遵守档案中的对白声纹，避免全员同声。\n");
            }
            if (stateInjectEnabled && novelId != null) {
                String stateBlock = buildStateInjectBlock(novelId, entries);
                if (!stateBlock.isBlank()) {
                    if (sb.length() > 0) {
                        sb.append("\n");
                    }
                    sb.append(stateBlock);
                }
            }
            String rawInject = sb.toString().strip();
            inject = rawInject.isEmpty() ? null : rawInject;
        }

        log.info("【角色叙事调度】novelId={} chapter={} castEnabled={} stateInject={} deltaLater={} castCount={} injectChars={}",
                novelId, chapterNumber, castEnabled, stateInjectEnabled, deltaEnabled,
                entries.size(), inject == null ? 0 : inject.length());

        return new ChapterNarrationResolution(parsed.notesForChapterSetting(), inject, entries.stream().map(CastEntry::name).toList(), restrict);
    }

    /**
     * 章节已成功落库后调用：可选 LLM 产出增量并合并写入 {@link NovelCharacterState}。
     */
    @Transactional
    public void maybeApplyStateDelta(Long novelId, int chapterNumber, String chapterContent,
                                     ChapterNarrationResolution resolution) {
        if (!deltaEnabled || novelId == null || chapterContent == null || chapterContent.isBlank()) {
            return;
        }
        if (resolution.castNames().isEmpty()) {
            log.debug("【角色状态增量】跳过：本章无 cast 名单 novelId={} ch={}", novelId, chapterNumber);
            return;
        }
        try {
            String excerpt = clipChapterForDelta(chapterContent);
            String castLines = resolution.castNames().stream().map(n -> "- " + n).collect(Collectors.joining("\n"));
            String existing = buildExistingStatesSummary(novelId, resolution.castNames());
            String raw = generationAgent.generateCharacterStateDeltaJson(chapterNumber, excerpt, castLines, existing);
            if (raw == null || raw.isBlank()) {
                log.warn("【角色状态增量】模型返回空 novelId={} ch={}", novelId, chapterNumber);
                return;
            }
            JsonNode root = objectMapper.readTree(extractJsonObject(raw));
            JsonNode deltas = root.path("deltas");
            if (!deltas.isArray() || deltas.isEmpty()) {
                log.info("【角色状态增量】无 deltas 数组 novelId={} ch={}", novelId, chapterNumber);
                return;
            }
            int applied = 0;
            for (JsonNode d : deltas) {
                String keyRaw = textOrEmpty(d, "characterKey");
                if (keyRaw.isBlank()) {
                    keyRaw = textOrEmpty(d, "name");
                }
                String key = normalizeCharacterKey(keyRaw);
                if (key == null) {
                    continue;
                }
                JsonNode patch = d.path("patch");
                if (!patch.isObject()) {
                    continue;
                }
                mergePatch(novelId, key, patch, chapterNumber);
                applied++;
            }
            log.info("【角色状态增量】novelId={} ch={} appliedPatches={}", novelId, chapterNumber, applied);
        } catch (Exception e) {
            log.warn("【角色状态增量】失败（已忽略，不影响章节）novelId={} ch={}: {}", novelId, chapterNumber, e.getMessage());
        }
    }

    public List<NovelCharacterState> listStates(Long novelId) {
        return stateRepository.findByNovelId(novelId);
    }

    private void mergePatch(Long novelId, String characterKey, JsonNode patch, int sourceChapter) {
        Optional<NovelCharacterState> opt = stateRepository.findByNovelIdAndCharacterKey(novelId, characterKey);
        ObjectNode base;
        try {
            if (opt.isPresent()) {
                base = (ObjectNode) objectMapper.readTree(opt.get().getStateJson());
            } else {
                base = objectMapper.createObjectNode();
            }
        } catch (Exception e) {
            base = objectMapper.createObjectNode();
        }
        Iterator<String> it = patch.fieldNames();
        while (it.hasNext()) {
            String field = it.next();
            if ("recentMemory_add".equals(field) && patch.path(field).isArray()) {
                ArrayNode mem = (ArrayNode) base.withArray("recentMemory");
                for (JsonNode x : patch.withArray(field)) {
                    if (x.isTextual() && !x.asText().isBlank()) {
                        mem.add(x.asText().trim());
                    }
                }
                trimArray(mem, 12);
            } else if (!field.endsWith("_add")) {
                base.set(field, patch.get(field));
            }
        }
        try {
            String json = objectMapper.writeValueAsString(base);
            NovelCharacterState row = opt.orElseGet(NovelCharacterState::new);
            row.setNovelId(novelId);
            row.setCharacterKey(characterKey);
            row.setStateJson(json);
            row.setSourceChapter(sourceChapter);
            stateRepository.save(row);
        } catch (Exception e) {
            log.warn("【角色状态增量】写库失败 key={}: {}", characterKey, e.getMessage());
        }
    }

    private static void trimArray(ArrayNode arr, int max) {
        while (arr.size() > max) {
            arr.remove(0);
        }
    }

    private String buildExistingStatesSummary(Long novelId, List<String> names) {
        Set<String> keys = new LinkedHashSet<>();
        for (String n : names) {
            String k = normalizeCharacterKey(n);
            if (k != null) {
                keys.add(k);
            }
        }
        if (keys.isEmpty()) {
            return "（无）";
        }
        List<NovelCharacterState> rows = stateRepository.findByNovelIdAndCharacterKeyIn(novelId, keys);
        if (rows.isEmpty()) {
            return "（尚无动态状态记录）";
        }
        StringBuilder sb = new StringBuilder();
        for (NovelCharacterState r : rows) {
            sb.append("- ").append(r.getCharacterKey()).append(": ").append(clipOneLine(r.getStateJson(), 400)).append("\n");
        }
        return sb.toString().trim();
    }

    private String buildStateInjectBlock(Long novelId, List<CastEntry> entries) {
        if (entries.isEmpty()) {
            return "";
        }
        Set<String> keys = new LinkedHashSet<>();
        for (CastEntry e : entries) {
            String k = normalizeCharacterKey(e.name());
            if (k != null) {
                keys.add(k);
            }
        }
        if (keys.isEmpty()) {
            return "";
        }
        List<NovelCharacterState> rows = stateRepository.findByNovelIdAndCharacterKeyIn(novelId, keys);
        if (rows.isEmpty()) {
            return "**动态状态**：书本尚无记录；本章可依大纲与档案自由推演，后续章节将累积状态。\n";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("**动态状态摘录（优先用于态度与短期记忆，勿与大纲事实冲突）**：\n");
        int budget = stateInjectMaxChars;
        for (NovelCharacterState r : rows) {
            String line = "- **" + r.getCharacterKey() + "**：" + clipOneLine(r.getStateJson(), 600) + "\n";
            if (budget - line.length() < 0) {
                sb.append("…（以下省略）\n");
                break;
            }
            sb.append(line);
            budget -= line.length();
        }
        return sb.toString();
    }

    private static String clipChapterForDelta(String content) {
        String t = content.strip();
        if (t.length() <= 9000) {
            return t;
        }
        String head = t.substring(0, 4500);
        String tail = t.substring(t.length() - 4500);
        return head + "\n…(中略)…\n" + tail;
    }

    private static String clipOneLine(String s, int max) {
        if (s == null) {
            return "";
        }
        String one = s.replace("\r", "").replace("\n", " ").trim();
        return one.length() <= max ? one : one.substring(0, max) + "…";
    }

    private static String extractJsonObject(String raw) {
        String t = raw.strip();
        if (t.startsWith("```")) {
            t = t.replaceFirst("^```(?:json)?\\s*", "");
            t = t.replaceFirst("\\s*```\\s*$", "");
        }
        int a = t.indexOf('{');
        int b = t.lastIndexOf('}');
        if (a >= 0 && b > a) {
            return t.substring(a, b + 1);
        }
        return t;
    }

    private static String textOrEmpty(JsonNode n, String field) {
        JsonNode v = n.path(field);
        return v.isMissingNode() || !v.isTextual() ? "" : v.asText("").trim();
    }

    private JsonNode readChapterCastDefault(String writingStyleParamsJson) {
        if (writingStyleParamsJson == null || writingStyleParamsJson.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(writingStyleParamsJson);
            JsonNode c = root.path("chapterCastDefault");
            return c.isMissingNode() || c.isNull() ? null : c;
        } catch (Exception e) {
            return null;
        }
    }

    private static JsonNode mergeCastConfig(JsonNode bookDefault, JsonNode chapterOverride) {
        if (chapterOverride != null && chapterOverride.isObject()) {
            JsonNode chChars = chapterOverride.path("characters");
            if (chChars.isArray() && !chChars.isEmpty()) {
                return chapterOverride;
            }
        }
        return bookDefault;
    }

    private static List<CastEntry> extractCastEntries(JsonNode castRoot) {
        List<CastEntry> out = new ArrayList<>();
        if (castRoot == null || !castRoot.isObject()) {
            return out;
        }
        JsonNode arr = castRoot.path("characters");
        if (!arr.isArray()) {
            return out;
        }
        for (JsonNode n : arr) {
            String name = n.path("name").asText("").trim();
            if (name.isEmpty()) {
                continue;
            }
            List<String> focus = new ArrayList<>();
            JsonNode f = n.path("focus");
            if (f.isArray()) {
                for (JsonNode x : f) {
                    if (x.isTextual() && !x.asText().isBlank()) {
                        focus.add(x.asText().trim());
                    }
                }
            } else if (f.isTextual() && !f.asText().isBlank()) {
                focus.add(f.asText().trim());
            }
            out.add(new CastEntry(name, focus));
        }
        return out;
    }

    /**
     * 与 {@link CharacterProfileService} 姓名规范化保持一致口径。
     */
    public static String normalizeCharacterKey(String candidate) {
        if (candidate == null) {
            return null;
        }
        String normalized = candidate.trim().replaceAll("[^\\p{IsHan}A-Za-z0-9·_]", "");
        if (normalized.isEmpty() || UNKNOWN_NAME.equals(normalized) || "完整设定".equals(normalized)) {
            return null;
        }
        if (normalized.length() > 100) {
            normalized = normalized.substring(0, 100);
        }
        return normalized;
    }

    private record ParsedChapterSetting(String notesForChapterSetting, JsonNode chapterCastNode) {

        static ParsedChapterSetting parse(String raw, ObjectMapper mapper) {
            if (raw == null || raw.strip().isEmpty()) {
                return new ParsedChapterSetting("", null);
            }
            String t = raw.strip();
            if (!t.startsWith("{")) {
                return new ParsedChapterSetting(t, null);
            }
            try {
                JsonNode root = mapper.readTree(t);
                if (!root.isObject()) {
                    return new ParsedChapterSetting(t, null);
                }
                String notes = "";
                if (root.path("notes").isTextual()) {
                    notes = root.path("notes").asText("").strip();
                }
                if (root.path("text").isTextual()) {
                    String tx = root.path("text").asText("").strip();
                    notes = notes.isEmpty() ? tx : notes + "\n" + tx;
                }
                JsonNode cast = root.path("chapterCast");
                if (cast.isMissingNode() || cast.isNull()) {
                    return new ParsedChapterSetting(notes.isEmpty() ? "" : notes, null);
                }
                return new ParsedChapterSetting(notes, cast);
            } catch (Exception e) {
                return new ParsedChapterSetting(t, null);
            }
        }
    }

    public record CastEntry(String name, List<String> focus) {
    }

    public record ChapterNarrationResolution(
            String sanitizedChapterSetting,
            String narrativeInjectBlock,
            List<String> castNames,
            boolean restrictOthersToBackground
    ) {
    }
}
