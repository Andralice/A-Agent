package com.start.agent.engine;

import lombok.Data;
import java.util.HashMap;
import java.util.Map;

/** 地点及其环境因子（用于事件对环境的影响推演）。 */
@Data
public class LocationState {
    private String id;
    private String name;
    private Map<String, Double> environmentalFactors = new HashMap<>();

    public void applyEnvironmentalChange(Map<String, Double> changes) {
        if (changes != null) {
            for (Map.Entry<String, Double> entry : changes.entrySet()) {
                environmentalFactors.merge(entry.getKey(), entry.getValue(), Double::sum);
            }
        }
    }
}
