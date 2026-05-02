package com.start.agent.engine;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

/** 单书世界观运行时快照：角色、地点、历史事件与全局规则。 */
@Slf4j
@Data
public class WorldState {
    private Long novelId;
    
    private Map<String, Object> globalRules = new HashMap<>();
    private Map<String, CharacterAgent> characters = new HashMap<>();
    private Map<String, Object> locations = new HashMap<>();
    private List<Event> eventHistory = new ArrayList<>();
    private Map<String, String> activeConflicts = new HashMap<>();
    
    private int currentChapter = 0;
    private Map<String, Integer> informationLevels = new HashMap<>();
    
    public void initialize(String outline, String characterProfiles) {
        parseGlobalRules(outline);
        log.debug("【世界状态】初始化完成，规则数: {}", globalRules.size());
    }
    
    private void parseGlobalRules(String outline) {
        globalRules.put("cultivation_detection", true);
        globalRules.put("sect_authority", "absolute");
        globalRules.put("qi_pollution_spread", true);
    }
    
    public void updateState(Event event) {
        if (event == null) return;
        
        eventHistory.add(event);
        applyEventEffects(event);
    }
    
    private void applyEventEffects(Event event) {
        if (event.getAffectedCharacters() != null) {
            for (String charId : event.getAffectedCharacters()) {
                CharacterAgent agent = characters.get(charId);
                if (agent != null) {
                    if (event.getRevealedInformation() != null) {
                        event.getRevealedInformation().forEach((key, value) -> agent.updateKnowledge(value));
                    }
                    agent.adjustEmotionalState(event.getEmotionalImpact());
                }
            }
        }
    }
    
    public List<CausalChainValidator.CausalLink> validateCausality(Event proposedEvent) {
        List<CausalChainValidator.CausalLink> missingLinks = new ArrayList<>();
        
        if (proposedEvent == null || !proposedEvent.hasValidCause()) {
            missingLinks.add(new CausalChainValidator.CausalLink(
                "MISSING_CAUSE",
                "事件缺少因果来源",
                proposedEvent != null ? proposedEvent.getDescription() : "null事件"
            ));
        }
        
        if (proposedEvent != null && violatesGlobalRules(proposedEvent)) {
            missingLinks.add(new CausalChainValidator.CausalLink(
                "RULE_VIOLATION",
                "事件违反世界规则",
                getViolatedRule(proposedEvent)
            ));
        }
        
        return missingLinks;
    }
    
    private boolean violatesGlobalRules(Event event) {
        return false;
    }
    
    private String getViolatedRule(Event event) {
        return "";
    }
}
