package com.start.agent.service;

import com.start.agent.model.CharacterProfile;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从角色档案等构造「不可违背约束」与姓名锁定规则，减少生成时吃设定。
 */
@Service
public class EntityConsistencyService {
    private static final Pattern CHINESE_NAME = Pattern.compile("(?<![\\p{IsHan}])([\\p{IsHan}]{2,4})(?![\\p{IsHan}])");

    public String buildImmutableConstraints(List<LockRule> lockRules) {
        if (lockRules == null || lockRules.isEmpty()) {
            return "无";
        }
        StringBuilder builder = new StringBuilder("以下角色名称是固定锚点，必须保持完全一致，不得改名或使用近似替代名。\n");
        for (LockRule rule : lockRules) {
            builder.append("- ").append(rule.getName()).append(" | 规则: ").append(rule.getMode()).append("\n");
        }
        return builder.toString().trim();
    }

    public List<LockRule> buildStrongLockRules(List<CharacterProfile> profiles) {
        Map<String, String> rules = new LinkedHashMap<>();
        if (profiles == null) return List.of();
        for (CharacterProfile profile : profiles) {
            if (profile == null || profile.getCharacterName() == null || profile.getCharacterName().isBlank()) continue;
            String name = profile.getCharacterName().trim();
            if ("未知角色".equals(name) || "完整设定".equals(name)) continue;
            String type = profile.getCharacterType() == null ? "" : profile.getCharacterType().toLowerCase();
            String mode;
            if (type.contains("主角") || type.contains("女主")) mode = "MUST_APPEAR_OR_EXPLAIN";
            else if (type.contains("反派")) mode = "NO_RENAME";
            else mode = "ALLOW_ABSENT_NO_RENAME";
            rules.putIfAbsent(name, mode);
        }
        List<LockRule> result = new ArrayList<>();
        for (Map.Entry<String, String> entry : rules.entrySet()) {
            result.add(new LockRule(entry.getKey(), entry.getValue()));
        }
        return result;
    }

    public String detectNameConsistencyIssue(String previousContent, String chapterContent, List<LockRule> lockRules) {
        if (chapterContent == null || chapterContent.isBlank() || lockRules == null || lockRules.isEmpty()) {
            return null;
        }
        List<String> lockedNames = lockRules.stream().map(LockRule::getName).toList();

        Set<String> previousNames = extractNames(previousContent);
        Set<String> currentNames = extractNames(chapterContent);

        List<String> missingLockedNames = new ArrayList<>();
        for (String lockedName : lockedNames) {
            if (lockedName == null || lockedName.isBlank()) continue;
            boolean shouldAppear = previousNames.contains(lockedName) || lockRules.stream().anyMatch(rule -> rule.getName().equals(lockedName) && "MUST_APPEAR_OR_EXPLAIN".equals(rule.getMode()));
            boolean appearsNow = currentNames.contains(lockedName);
            if (shouldAppear && !appearsNow) {
                missingLockedNames.add(lockedName);
            }
        }

        List<String> suspiciousAliases = new ArrayList<>();
        for (String currentName : currentNames) {
            if (lockedNames.contains(currentName)) continue;
            for (String lockedName : lockedNames) {
                if (isLikelyAlias(currentName, lockedName)) {
                    suspiciousAliases.add(currentName + " -> " + lockedName);
                    break;
                }
            }
        }

        if (missingLockedNames.isEmpty() && suspiciousAliases.isEmpty()) {
            return null;
        }

        StringBuilder issue = new StringBuilder("检测到角色名一致性风险。");
        if (!missingLockedNames.isEmpty()) {
            issue.append(" 缺失已锁定角色名: ").append(String.join("、", missingLockedNames)).append("。");
        }
        if (!suspiciousAliases.isEmpty()) {
            issue.append(" 疑似别名漂移: ").append(String.join("；", suspiciousAliases)).append("。");
        }
        issue.append(" 请在不改变剧情的前提下修复名称一致性。");
        return issue.toString();
    }

    private Set<String> extractNames(String content) {
        Set<String> names = new LinkedHashSet<>();
        if (content == null || content.isBlank()) {
            return names;
        }
        Matcher matcher = CHINESE_NAME.matcher(content);
        while (matcher.find()) {
            String candidate = matcher.group(1);
            if (isLikelyName(candidate)) {
                names.add(candidate);
            }
        }
        return names;
    }

    private boolean isLikelyName(String token) {
        return token.length() >= 2 && token.length() <= 4
                && !token.startsWith("第")
                && !token.endsWith("章节")
                && !token.equals("本章完")
                && !token.equals("未完待续");
    }

    private boolean isLikelyAlias(String currentName, String lockedName) {
        if (currentName.length() != lockedName.length()) return false;
        if (currentName.equals(lockedName)) return false;
        if (currentName.charAt(0) != lockedName.charAt(0)) return false;
        return levenshtein(currentName, lockedName) <= 1;
    }

    private int levenshtein(String left, String right) {
        int[][] dp = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) dp[i][0] = i;
        for (int j = 0; j <= right.length(); j++) dp[0][j] = j;
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1), dp[i - 1][j - 1] + cost);
            }
        }
        return dp[left.length()][right.length()];
    }

    public static class LockRule {
        private final String name;
        private final String mode;

        public LockRule(String name, String mode) {
            this.name = name;
            this.mode = mode;
        }

        public String getName() {
            return name;
        }

        public String getMode() {
            return mode;
        }
    }
}
