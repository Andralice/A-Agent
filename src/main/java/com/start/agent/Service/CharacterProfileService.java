package com.start.agent.service;

import com.start.agent.model.CharacterProfile;
import com.start.agent.repository.CharacterProfileRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 角色档案解析与规范化：从大模型文本中提取结构化字段、去重合并、写入 {@link com.start.agent.model.CharacterProfile}。
 */
@Slf4j
@Service
public class CharacterProfileService {
    private static final String UNKNOWN_NAME = "未知角色";
    private static final String RAW_TYPE = "RAW";
    private static final Pattern SECTION_PATTERN = Pattern.compile("【(.+?)】\\s*(.+?)(?=\\n\\s*【|$)", Pattern.DOTALL);
    private static final Pattern NAME_LINE_PATTERN = Pattern.compile("(?:姓名|名字|角色名|主角|女主|男主|反派|角色)\\s*[：:]\\s*([\\p{IsHan}A-Za-z0-9·_]{1,20})");
    private static final Pattern CJK_NAME_PATTERN = Pattern.compile("([\\p{IsHan}]{2,4})");
    private static final Set<String> NAME_BLACKLIST = new HashSet<>(Arrays.asList(
            "身份", "能力", "初期", "行为模式", "信息差", "背景故事", "特殊能力", "外貌特征", "性格特征",
            "人际关系", "内在矛盾", "目标", "恐惧", "知识", "基本信息", "角色", "主角", "配角", "反派"
    ));

    private final CharacterProfileRepository characterProfileRepository;
    private final ObjectMapper objectMapper;

    public CharacterProfileService(CharacterProfileRepository characterProfileRepository, ObjectMapper objectMapper) {
        this.characterProfileRepository = characterProfileRepository;
        this.objectMapper = objectMapper;
    }

    public String getCharacterProfileFromDatabase(Long novelId) {
        List<CharacterProfile> profiles = characterProfileRepository.findByNovelId(novelId);
        if (profiles.isEmpty()) return "暂无角色设定";
        repairProfilesIfNeeded(profiles);
        StringBuilder profileBuilder = new StringBuilder("【角色设定档案】\n\n");
        for (CharacterProfile profile : profiles) profileBuilder.append(String.format("【%s - %s】\n%s\n\n", profile.getCharacterType(), profile.getCharacterName(), profile.getProfileContent()));
        return profileBuilder.toString();
    }

    public String getStableCharacterProfileBlock(Long novelId) {
        List<CharacterProfile> profiles = characterProfileRepository.findByNovelId(novelId);
        if (profiles.isEmpty()) return "【角色锚点】\n无";
        repairProfilesIfNeeded(profiles);
        StringBuilder builder = new StringBuilder("【角色锚点】\n");
        int index = 1;
        for (CharacterProfile profile : profiles) {
            if (profile == null || RAW_TYPE.equalsIgnoreCase(profile.getCharacterType())) continue;
            String name = normalizeName(profile.getCharacterName());
            if (name == null) continue;
            builder.append(index++).append(". ").append(name)
                    .append(" | 类型: ").append(profile.getCharacterType() == null ? "未标注" : profile.getCharacterType())
                    .append(" | 关键设定: ").append(firstLine(profile.getProfileContent()))
                    .append("\n");
        }
        return index == 1 ? "【角色锚点】\n无" : builder.toString().trim();
    }

    @Transactional
    public void saveCharacterProfilesToDatabase(Long novelId, String profileText) {
        if (profileText == null || profileText.isEmpty()) return;
        List<CharacterProfile> profiles = parseCharacterProfiles(profileText, novelId);
        if (profiles.isEmpty()) {
            CharacterProfile raw = new CharacterProfile(); raw.setNovelId(novelId); raw.setCharacterName("完整设定"); raw.setCharacterType(RAW_TYPE); raw.setProfileContent(sanitizeProfileContent(profileText)); characterProfileRepository.save(raw); return;
        }
        for (CharacterProfile profile : profiles) characterProfileRepository.save(profile);
    }

    @Transactional
    public int saveCharacterProfilesJsonOnly(Long novelId, String profileText) {
        if (profileText == null || profileText.isBlank()) return 0;
        List<CharacterProfile> profiles = parseJsonProfilesStrict(profileText, novelId);
        if (profiles.isEmpty()) return 0;
        characterProfileRepository.deleteByNovelId(novelId);
        for (CharacterProfile profile : profiles) characterProfileRepository.save(profile);
        return profiles.size();
    }

    @Transactional
    public int saveCharacterProfilesJsonWithMode(Long novelId, String profileText, boolean replaceAll, List<String> targetCharacterNames) {
        if (profileText == null || profileText.isBlank()) return 0;
        List<CharacterProfile> profiles = parseJsonProfilesStrict(profileText, novelId);
        if (profiles.isEmpty()) return 0;

        Set<String> targetNames = new HashSet<>();
        if (targetCharacterNames != null) {
            for (String name : targetCharacterNames) {
                String normalized = normalizeName(name);
                if (normalized != null) targetNames.add(normalized);
            }
        }

        if (replaceAll) {
            characterProfileRepository.deleteByNovelId(novelId);
            for (CharacterProfile profile : profiles) characterProfileRepository.save(profile);
            return profiles.size();
        }

        int savedCount = 0;
        for (CharacterProfile profile : profiles) {
            String name = normalizeName(profile.getCharacterName());
            if (name == null) continue;
            if (!targetNames.isEmpty() && !targetNames.contains(name)) continue;
            CharacterProfile existing = characterProfileRepository.findByNovelIdAndCharacterName(novelId, name).orElse(null);
            if (existing == null) {
                characterProfileRepository.save(profile);
            } else {
                existing.setCharacterType(profile.getCharacterType());
                existing.setProfileContent(profile.getProfileContent());
                characterProfileRepository.save(existing);
            }
            savedCount++;
        }
        return savedCount;
    }

    public List<String> getCoreCharacterNames(Long novelId) {
        List<CharacterProfile> profiles = characterProfileRepository.findByNovelId(novelId);
        Set<String> names = new LinkedHashSet<>();
        for (CharacterProfile profile : profiles) {
            if (profile == null) continue;
            if (RAW_TYPE.equalsIgnoreCase(profile.getCharacterType())) continue;
            addValidName(names, profile.getCharacterName());
            addValidName(names, extractCharacterName(profile.getProfileContent()));
        }
        return new ArrayList<>(names);
    }

    public List<CharacterProfile> getProfiles(Long novelId) {
        List<CharacterProfile> profiles = characterProfileRepository.findProfilesByNovelIdOrdered(novelId);
        repairProfilesIfNeeded(profiles);
        return profiles;
    }

    public boolean hasUsableProfiles(Long novelId) {
        List<CharacterProfile> profiles = getProfiles(novelId);
        for (CharacterProfile profile : profiles) {
            if (profile == null) continue;
            if (RAW_TYPE.equalsIgnoreCase(profile.getCharacterType())) continue;
            String name = normalizeName(profile.getCharacterName());
            String content = sanitizeProfileContent(profile.getProfileContent());
            if (name != null && !UNKNOWN_NAME.equals(name) && !content.isBlank()) return true;
        }
        return false;
    }

    @Transactional
    public void deleteProfilesByNovelId(Long novelId) {
        characterProfileRepository.deleteByNovelId(novelId);
    }

    private List<CharacterProfile> parseCharacterProfiles(String profileText, Long novelId) {
        if (looksLikeJson(profileText)) {
            List<CharacterProfile> fromJson = parseJsonProfiles(profileText, novelId);
            if (!fromJson.isEmpty()) return fromJson;
        }

        List<CharacterProfile> profiles = new ArrayList<>();
        Matcher matcher = SECTION_PATTERN.matcher(profileText);
        while (matcher.find()) {
            CharacterProfile profile = new CharacterProfile();
            String header = matcher.group(1).trim();
            String content = sanitizeProfileContent(matcher.group(2));
            profile.setNovelId(novelId);
            profile.setCharacterType(extractCharacterType(header));
            profile.setProfileContent(content);
            String nameFromHeader = extractCharacterNameFromHeader(header);
            String finalName = nameFromHeader == null ? extractCharacterName(content) : nameFromHeader;
            if (!isLikelyCharacterName(finalName)) continue;
            profile.setCharacterName(finalName);
            profiles.add(profile);
        }
        if (!profiles.isEmpty()) return profiles;
        return parsePlainTextProfiles(profileText, novelId);
    }

    private String extractCharacterName(String content) {
        if (content == null || content.isBlank()) return UNKNOWN_NAME;

        Matcher explicit = Pattern.compile("(?:姓名|名字|角色名)[：:]\\s*([\\p{IsHan}A-Za-z0-9·_]{1,20})").matcher(content);
        if (explicit.find()) {
            String candidate = explicit.group(1).trim();
            if (isLikelyCharacterName(candidate)) return candidate;
        }

        Matcher lineMatcher = NAME_LINE_PATTERN.matcher(content);
        if (lineMatcher.find()) {
            String candidate = lineMatcher.group(1).trim();
            if (isLikelyCharacterName(candidate)) return candidate;
        }

        Matcher titleLike = Pattern.compile("^([\\p{IsHan}A-Za-z0-9·_]{1,20})\\s*(?:\\(|（|，|。|\\n|$)").matcher(content.trim());
        if (titleLike.find()) {
            String candidate = titleLike.group(1).trim();
            if (isLikelyCharacterName(candidate)) return candidate;
        }

        Matcher cjkMatcher = CJK_NAME_PATTERN.matcher(content);
        while (cjkMatcher.find()) {
            String candidate = cjkMatcher.group(1);
            if (isLikelyCharacterName(candidate)) return candidate;
        }

        return UNKNOWN_NAME;
    }

    private List<CharacterProfile> parseJsonProfiles(String profileText, Long novelId) {
        List<CharacterProfile> profiles = new ArrayList<>();
        try {
            JsonNode root = objectMapper.readTree(profileText);
            JsonNode characters = root.isArray() ? root : root.path("characters");
            if (!characters.isArray()) return profiles;
            for (JsonNode node : characters) {
                String name = normalizeName(node.path("name").asText(null));
                if (name == null) continue;
                CharacterProfile profile = new CharacterProfile();
                profile.setNovelId(novelId);
                profile.setCharacterName(name);
                profile.setCharacterType(normalizeType(node.path("type").asText("角色")));
                profile.setProfileContent(sanitizeProfileContent(node.toPrettyString()));
                profiles.add(profile);
            }
        } catch (Exception e) {
            log.warn("parse json profiles failed, fallback to text parser: {}", e.getMessage());
        }
        return profiles;
    }

    private List<CharacterProfile> parseJsonProfilesStrict(String profileText, Long novelId) {
        List<CharacterProfile> profiles = new ArrayList<>();
        try {
            String jsonText = extractJsonBody(profileText);
            JsonNode root = objectMapper.readTree(jsonText);
            JsonNode characters = root.path("characters");
            if (!characters.isArray()) return profiles;
            for (JsonNode node : characters) {
                String name = normalizeName(node.path("name").asText(null));
                String type = normalizeType(node.path("type").asText("角色"));
                String want = sanitizeProfileContent(node.path("want").asText(""));
                String fear = sanitizeProfileContent(node.path("fear").asText(""));
                String knowledge = sanitizeProfileContent(node.path("knowledge").asText(""));
                String summary = sanitizeProfileContent(node.path("summary").asText(""));
                if (!isLikelyCharacterName(name)) continue;
                if (summary.isBlank() && want.isBlank() && fear.isBlank() && knowledge.isBlank()) continue;

                CharacterProfile profile = new CharacterProfile();
                profile.setNovelId(novelId);
                profile.setCharacterName(name);
                profile.setCharacterType(type);
                profile.setProfileContent(buildStructuredProfileContent(summary, want, fear, knowledge, node));
                profiles.add(profile);
            }
        } catch (Exception e) {
            log.warn("strict json parse failed: {}", e.getMessage());
        }
        return profiles;
    }

    private boolean looksLikeJson(String profileText) {
        if (profileText == null) return false;
        String trimmed = profileText.trim();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String extractCharacterType(String header) {
        if (header == null || header.isBlank()) return "角色";
        String[] parts = header.split("[-—:：]", 2);
        return normalizeType(parts[0]);
    }

    private String extractCharacterNameFromHeader(String header) {
        if (header == null || header.isBlank()) return null;
        String[] parts = header.split("[-—:：]", 2);
        if (parts.length < 2) return null;
        String candidate = normalizeName(parts[1]);
        return isLikelyCharacterName(candidate) ? candidate : null;
    }

    private void addValidName(Set<String> names, String candidate) {
        String normalized = normalizeName(candidate);
        if (normalized == null) return;
        names.add(normalized);
    }

    private String normalizeName(String candidate) {
        if (candidate == null) return null;
        String normalized = candidate.trim().replaceAll("[^\\p{IsHan}A-Za-z0-9·_]", "");
        if (normalized.isEmpty() || UNKNOWN_NAME.equals(normalized) || "完整设定".equals(normalized)) return null;
        if (normalized.length() > 30) normalized = normalized.substring(0, 30);
        return normalized;
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) return "角色";
        return type.trim();
    }

    private String firstLine(String content) {
        if (content == null || content.isBlank()) return "无";
        String oneLine = content.strip().replace("\r", "").split("\n")[0].trim();
        return oneLine.length() > 80 ? oneLine.substring(0, 80) + "..." : oneLine;
    }

    private String extractJsonBody(String text) {
        String content = text == null ? "" : text.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?\\s*", "");
            content = content.replaceFirst("\\s*```\\s*$", "");
        }
        int startObj = content.indexOf('{');
        int endObj = content.lastIndexOf('}');
        if (startObj >= 0 && endObj > startObj) {
            return content.substring(startObj, endObj + 1);
        }
        return content;
    }

    private String buildStructuredProfileContent(String summary, String want, String fear, String knowledge, JsonNode rawNode) {
        StringBuilder builder = new StringBuilder();
        if (!summary.isBlank()) builder.append("简介: ").append(summary).append("\n");
        if (!want.isBlank()) builder.append("目标: ").append(want).append("\n");
        if (!fear.isBlank()) builder.append("恐惧: ").append(fear).append("\n");
        if (!knowledge.isBlank()) builder.append("信息差: ").append(knowledge).append("\n");
        if (builder.isEmpty()) {
            builder.append(sanitizeProfileContent(rawNode.toPrettyString()));
        }
        return builder.toString().trim();
    }

    private List<CharacterProfile> parsePlainTextProfiles(String profileText, Long novelId) {
        List<CharacterProfile> profiles = new ArrayList<>();
        String[] blocks = profileText.split("\\n\\s*\\n");
        for (String block : blocks) {
            String clean = sanitizeProfileContent(block);
            if (clean.isBlank()) continue;
            String name = extractCharacterName(clean);
            if (UNKNOWN_NAME.equals(name) || !isLikelyCharacterName(name)) continue;
            CharacterProfile profile = new CharacterProfile();
            profile.setNovelId(novelId);
            profile.setCharacterName(name);
            profile.setCharacterType("角色");
            profile.setProfileContent(clean);
            profiles.add(profile);
        }
        return profiles;
    }

    private String sanitizeProfileContent(String content) {
        if (content == null) return "";
        String cleaned = content.replace("```json", "")
                .replace("```", "")
                .replace("\uFEFF", "")
                .replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]", "")
                .replaceAll("[◆◇■□★☆※¤§¶]+", " ")
                .replaceAll("[ \\t]+", " ")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
        return cleaned;
    }

    @Transactional
    public void repairProfilesIfNeeded(List<CharacterProfile> profiles) {
        for (CharacterProfile profile : profiles) {
            if (profile == null || RAW_TYPE.equalsIgnoreCase(profile.getCharacterType())) continue;
            String fixedName = normalizeName(profile.getCharacterName());
            String fixedContent = sanitizeProfileContent(profile.getProfileContent());
            if (fixedName == null) fixedName = normalizeName(extractCharacterName(fixedContent));
            if (fixedName == null) fixedName = UNKNOWN_NAME;
            boolean changed = !fixedName.equals(profile.getCharacterName()) || !fixedContent.equals(profile.getProfileContent());
            if (changed) {
                profile.setCharacterName(fixedName);
                profile.setProfileContent(fixedContent);
                characterProfileRepository.save(profile);
            }
        }
    }

    private boolean isLikelyCharacterName(String candidate) {
        if (candidate == null || candidate.isBlank()) return false;
        if (candidate.length() < 2 || candidate.length() > 6) return false;
        if (List.of("角色设定", "主要角色", "对立角色", "世界观", "剧情规划", "写作风格").contains(candidate)) return false;
        if (NAME_BLACKLIST.contains(candidate)) return false;
        for (String keyword : NAME_BLACKLIST) {
            if (candidate.contains(keyword) && candidate.length() <= keyword.length() + 1) return false;
        }
        return true;
    }
}
