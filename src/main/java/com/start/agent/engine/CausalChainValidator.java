package com.start.agent.engine;

import lombok.Data;
import java.util.*;

@Data
public class CausalChainValidator {

    public ValidationResult validate(Event proposedEvent, WorldState currentState) {
        ValidationResult result = new ValidationResult();

        List<CausalLink> missingLinks = currentState.validateCausality(proposedEvent);
        if (!missingLinks.isEmpty()) {
            result.setValid(false);
            result.setRejectedReasons(missingLinks);
            return result;
        }

        if (!checkInformationDisclosureRules(proposedEvent, currentState)) {
            result.setValid(false);
            result.setRejectedReasons(Arrays.asList(
                new CausalLink("INFO_LEAK", "信息揭露过快", "违反了延迟释放机制")
            ));
            return result;
        }

        if (!checkCharacterConsistency(proposedEvent, currentState)) {
            result.setValid(false);
            result.setRejectedReasons(Arrays.asList(
                new CausalLink("CHARACTER_BREAK", "人物行为不一致", "角色行为不符合其目标/恐惧/知识")
            ));
            return result;
        }

        result.setValid(true);
        result.setSuggestedEnhancements(generateCausalEnhancements(proposedEvent, currentState));

        return result;
    }

    private boolean checkInformationDisclosureRules(Event event, WorldState state) {
        int currentLevel = event.getInformationLevel();
        int maxAllowedLevel = calculateMaxAllowedLevel(state, event);

        return currentLevel <= maxAllowedLevel;
    }

    private int calculateMaxAllowedLevel(WorldState state, Event event) {
        String key = event.getId() + "_level";
        int currentRevealed = state.getInformationLevels().getOrDefault(key, 0);

        if (currentRevealed == 0) return 1;
        if (currentRevealed == 1) return 2;
        return 3;
    }

    private boolean checkCharacterConsistency(Event event, WorldState state) {
        for (String charId : event.getAffectedCharacters()) {
            CharacterAgent agent = state.getCharacters().get(charId);
            if (agent != null && !isActionConsistent(agent, event)) {
                return false;
            }
        }
        return true;
    }

    private boolean isActionConsistent(CharacterAgent agent, Event event) {
        return true;
    }

    private List<CausalEnhancement> generateCausalEnhancements(Event event, WorldState state) {
        List<CausalEnhancement> enhancements = new ArrayList<>();

        if (event.getCause() == null || event.getCause().isEmpty()) {
            enhancements.add(new CausalEnhancement(
                "ADD_CAUSE",
                "为事件添加因果来源",
                suggestCauses(event, state)
            ));
        }

        return enhancements;
    }

    private List<String> suggestCauses(Event event, WorldState state) {
        List<String> suggestions = new ArrayList<>();

        if (event.getEventType().equals("ANTAGONIST_ARRIVAL")) {
            suggestions.add("宗门检测到异常气息，派遣弟子前来调查");
            suggestions.add("反派接到任务，必须清除潜在威胁");
            suggestions.add("世界规则触发：异常点必须被清除");
        }

        return suggestions;
    }

    @Data
    static class ValidationResult {
        private boolean valid;
        private List<CausalLink> rejectedReasons = new ArrayList<>();
        private List<CausalEnhancement> suggestedEnhancements = new ArrayList<>();
    }

    @Data
    static class CausalLink {
        private String type;
        private String message;
        private String detail;

        public CausalLink(String type, String message, String detail) {
            this.type = type;
            this.message = message;
            this.detail = detail;
        }
    }

    @Data
    static class CausalEnhancement {
        private String type;
        private String description;
        private List<String> suggestions;

        public CausalEnhancement(String type, String description, List<String> suggestions) {
            this.type = type;
            this.description = description;
            this.suggestions = suggestions;
        }
    }
}