package com.start.agent.engine;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import java.util.*;

/**
 * 叙事因果模拟入口（实验性）：驱动 {@link WorldState} 与 {@link CausalChainValidator} 推演事件链。
 */
@Slf4j
public class WorldSimulator {
    
    private final CausalChainValidator validator;
    
    public WorldSimulator(CausalChainValidator validator) {
        this.validator = validator;
    }
    
    public SimulationResult simulate(WorldState initialState, int steps) {
        log.info("【世界模拟】开始 {} 步模拟", steps);
        
        WorldState currentState = initialState;
        List<Event> simulatedEvents = new ArrayList<>();
        
        for (int i = 0; i < steps; i++) {
            log.debug("【世界模拟】第 {} 步", i + 1);
            
            List<Event> possibleEvents = generatePossibleEvents(currentState);
            Event selectedEvent = selectMostCausalEvent(possibleEvents, currentState);
            
            if (selectedEvent != null) {
                CausalChainValidator.ValidationResult validation = validator.validate(selectedEvent, currentState);
                
                if (validation.isValid()) {
                    currentState.updateState(selectedEvent);
                    simulatedEvents.add(selectedEvent);
                    log.info("【世界模拟】 事件已接受: {}", selectedEvent.getDescription());
                } else {
                    log.warn("【世界模拟】 事件被拒绝: {}, 原因: {}", 
                        selectedEvent.getDescription(), 
                        validation.getRejectedReasons());
                    
                    Event enhancedEvent = applyEnhancements(selectedEvent, validation);
                    if (enhancedEvent != null) {
                        currentState.updateState(enhancedEvent);
                        simulatedEvents.add(enhancedEvent);
                    }
                }
            }
        }
        
        return new SimulationResult(currentState, simulatedEvents);
    }
    
    private List<Event> generatePossibleEvents(WorldState state) {
        List<Event> events = new ArrayList<>();
        
        if (state.getCharacters() != null && !state.getCharacters().isEmpty()) {
            for (CharacterAgent agent : state.getCharacters().values()) {
                Situation context = analyzeSituation(agent, state);
                CharacterAgent.Decision decision = agent.makeDecision(state, context);
                
                if (decision != null && decision.getAction() != null) {
                    Event event = convertActionToEvent(agent, decision, state);
                    events.add(event);
                }
            }
        }
        
        events.addAll(generateEnvironmentalEvents(state));
        
        return events;
    }
    
    private Situation analyzeSituation(CharacterAgent agent, WorldState state) {
        Situation situation = new Situation();
        
        situation.setThreatDetected(checkForThreats(agent, state));
        situation.setSocialInteraction(hasSocialContext(agent, state));
        situation.setUrgency(calculateUrgency(agent, state));
        
        return situation;
    }
    
    private Event selectMostCausalEvent(List<Event> events, WorldState state) {
        if (events.isEmpty()) return null;
        
        return events.stream()
            .filter(e -> e != null && e.hasValidCause())
            .max(Comparator.comparingDouble(e -> calculateCausalStrength(e, state)))
            .orElse(events.get(0));
    }
    
    private double calculateCausalStrength(Event event, WorldState state) {
        if (event == null) return 0.0;
        
        double strength = 0.0;
        
        if (event.hasValidCause()) {
            strength += 0.5;
        }
        
        if (event.getAffectedCharacters() != null) {
            strength += event.getAffectedCharacters().size() * 0.2;
        }
        
        if (event.getEffects() != null) {
            strength += event.getEffects().size() * 0.15;
        }
        
        return strength;
    }
    
    private Event applyEnhancements(Event event, CausalChainValidator.ValidationResult validation) {
        if (event == null || validation == null) return null;
        
        for (CausalChainValidator.CausalEnhancement enhancement : validation.getSuggestedEnhancements()) {
            if ("ADD_CAUSE".equals(enhancement.getType()) && enhancement.getSuggestions() != null && !enhancement.getSuggestions().isEmpty()) {
                event.setCause(enhancement.getSuggestions().get(0));
                event.setNaturalConsequence(true);
                return event;
            }
        }
        return null;
    }
    
    private List<Event> generateEnvironmentalEvents(WorldState state) {
        List<Event> events = new ArrayList<>();
        
        if (state.getGlobalRules() != null && (Boolean) state.getGlobalRules().getOrDefault("qi_pollution_spread", false)) {
            Event pollutionEvent = new Event();
            pollutionEvent.setDescription("邪修气息扩散，周围环境开始异变");
            pollutionEvent.setEventType("ENVIRONMENTAL_CHANGE");
            pollutionEvent.setCause("主角修炼导致气息泄露");
            events.add(pollutionEvent);
        }
        
        return events;
    }
    
    private boolean checkForThreats(CharacterAgent agent, WorldState state) {
        return false;
    }
    
    private boolean hasSocialContext(CharacterAgent agent, WorldState state) {
        return false;
    }
    
    private double calculateUrgency(CharacterAgent agent, WorldState state) {
        return 0.5;
    }
    
    private Event convertActionToEvent(CharacterAgent agent, CharacterAgent.Decision decision, WorldState state) {
        Event event = new Event();
        String description = (agent.getName() != null ? agent.getName() : "未知角色") + "决定：" + 
                           (decision.getAction().getDescription() != null ? decision.getAction().getDescription() : "执行行动");
        event.setDescription(description);
        event.setEventType(decision.getAction().getType());
        event.setCause(decision.getReasoning());
        if (agent.getId() != null) {
            event.getAffectedCharacters().add(agent.getId());
        }
        return event;
    }
    
    @Data
    public static class SimulationResult {
        private WorldState finalState;
        private List<Event> events;
        
        public SimulationResult(WorldState finalState, List<Event> events) {
            this.finalState = finalState;
            this.events = events != null ? events : new ArrayList<>();
        }
    }
}