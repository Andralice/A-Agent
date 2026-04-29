package com.start.agent.engine;

import lombok.Data;
import java.util.*;

@Data
public class Event {
    private String id;
    private String description;
    private String eventType;
    
    private String cause;
    private List<String> effects = new ArrayList<>();
    
    private List<String> affectedCharacters = new ArrayList<>();
    private List<String> affectedLocations = new ArrayList<>();
    
    private Map<String, String> revealedInformation = new HashMap<>();
    private double emotionalImpact = 0.0;
    private Map<String, Double> environmentalImpact = new HashMap<>();
    
    private int informationLevel = 1;
    private boolean isNaturalConsequence = false;
    
    public boolean hasValidCause() {
        return cause != null && !cause.isEmpty();
    }
    
    public CausalChain buildCausalChain() {
        CausalChain chain = new CausalChain();
        chain.addEvent(this);
        return chain;
    }
}