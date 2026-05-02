package com.start.agent.engine;

import lombok.Data;
import java.util.*;

/** 模拟器中的角色模型：动机、恐惧、知情范围与关系边（非数据库实体）。 */
@Data
public class CharacterAgent {
    private String id;
    private String name;
    private String role;
    
    private String want;
    private String fear;
    private Set<String> knowledge = new HashSet<>();
    private Map<String, Integer> relationships = new HashMap<>();
    
    private EmotionalState emotionalState = new EmotionalState();
    private List<BehaviorPattern> behaviorPatterns = new ArrayList<>();
    
    public Decision makeDecision(WorldState world, Situation context) {
        List<Action> possibleActions = generatePossibleActions(world, context);
        
        if (possibleActions.isEmpty()) {
            return new Decision(null, "当前情境无合适行动");
        }
        
        Action bestAction = possibleActions.stream()
            .map(action -> evaluateAction(action, world, context))
            .max(Comparator.comparingDouble(ActionScore::getScore))
            .map(ActionScore::getAction)
            .orElse(possibleActions.get(0));
        
        return new Decision(bestAction, calculateReasoning(bestAction, context));
    }
    
    private List<Action> generatePossibleActions(WorldState world, Situation context) {
        List<Action> actions = new ArrayList<>();
        
        if (context != null && context.isThreatDetected()) {
            actions.add(new Action("INVESTIGATE", "调查异常源"));
            actions.add(new Action("REPORT", "向宗门报告"));
            actions.add(new Action("ELIMINATE", "直接清除威胁"));
        }
        
        if (context != null && context.isSocialInteraction()) {
            actions.add(new Action("PROBE", "试探对方底细"));
            actions.add(new Action("INTIMIDATE", "威慑对方"));
            actions.add(new Action("COOPERATE", "寻求合作"));
        }
        
        if (actions.isEmpty()) {
            actions.add(new Action("OBSERVE", "观察周围情况"));
            actions.add(new Action("WAIT", "等待时机"));
        }
        
        return actions;
    }
    
    private ActionScore evaluateAction(Action action, WorldState world, Situation context) {
        double score = 0.0;
        
        score += calculateGoalAlignment(action, context);
        score -= calculateFearTrigger(action, context);
        score += calculateKnowledgeAdvantage(action);
        score += calculateRelationshipImpact(action, world);
        
        return new ActionScore(action, score);
    }
    
    private double calculateGoalAlignment(Action action, Situation context) {
        if (action != null && action.getType() != null) {
            if (action.getType().equals("ELIMINATE") && context != null && context.isThreatDetected()) {
                return 0.8;
            }
        }
        return 0.3;
    }
    
    private double calculateFearTrigger(Action action, Situation context) {
        if (fear != null && fear.contains("被看不起") && action != null && action.getType() != null && action.getType().equals("REPORT")) {
            return 0.6;
        }
        return 0.1;
    }
    
    private double calculateKnowledgeAdvantage(Action action) {
        return knowledge.size() * 0.05;
    }
    
    private double calculateRelationshipImpact(Action action, WorldState world) {
        return 0.0;
    }
    
    private String calculateReasoning(Action action, Situation context) {
        if (action == null) return "无合适行动";
        return String.format("基于目标(%s)和当前情境做出的决策", want != null ? want : "未知");
    }
    
    public void updateKnowledge(String newInfo) {
        if (newInfo != null && !newInfo.isEmpty()) {
            knowledge.add(newInfo);
        }
    }
    
    public void adjustEmotionalState(double impact) {
        if (emotionalState != null) {
            emotionalState.adjust(impact);
        }
    }
    
    @Data
    public static class EmotionalState {
        private double stress = 0.0;
        private double confidence = 0.5;
        private double aggression = 0.2;
        
        public void adjust(double impact) {
            stress = Math.min(1.0, Math.max(0.0, stress + impact * 0.3));
            confidence = Math.max(0.0, Math.min(1.0, confidence - impact * 0.1));
            aggression = Math.min(1.0, Math.max(0.0, aggression + impact * 0.2));
        }
    }
    
    @Data
    public static class Decision {
        private Action action;
        private String reasoning;
        
        public Decision(Action action, String reasoning) {
            this.action = action;
            this.reasoning = reasoning;
        }
    }
    
    @Data
    public static class Action {
        private String type;
        private String description;
        
        public Action(String type, String description) {
            this.type = type;
            this.description = description;
        }
    }
    
    @Data
    public static class ActionScore {
        private Action action;
        private double score;
        
        public ActionScore(Action action, double score) {
            this.action = action;
            this.score = score;
        }
    }
    
    @Data
    public static class BehaviorPattern {
        private String pattern;
        private double weight;
        
        public BehaviorPattern() {
            this.weight = 1.0;
        }
        
        public BehaviorPattern(String pattern, double weight) {
            this.pattern = pattern;
            this.weight = weight;
        }
    }
}