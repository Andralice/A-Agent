package com.start.agent.engine;

import lombok.Data;

/** 场景紧迫度与交互类型的简单状态描述（供模拟器决策）。 */
@Data
public class Situation {
    private boolean threatDetected;
    private boolean socialInteraction;
    private double urgency;
    
    public Situation() {
        this.threatDetected = false;
        this.socialInteraction = false;
        this.urgency = 0.5;
    }
    
    public Situation(boolean threatDetected, boolean socialInteraction, double urgency) {
        this.threatDetected = threatDetected;
        this.socialInteraction = socialInteraction;
        this.urgency = urgency;
    }
    
    public boolean isThreatDetected() {
        return threatDetected;
    }
    
    public boolean isSocialInteraction() {
        return socialInteraction;
    }
    
    public double getUrgency() {
        return urgency;
    }
    
    public void setThreatDetected(boolean threatDetected) {
        this.threatDetected = threatDetected;
    }
    
    public void setSocialInteraction(boolean socialInteraction) {
        this.socialInteraction = socialInteraction;
    }
    
    public void setUrgency(double urgency) {
        this.urgency = urgency;
    }
}
