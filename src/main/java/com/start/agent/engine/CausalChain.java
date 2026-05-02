package com.start.agent.engine;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

/** 由多条 {@link Event} 组成的情节因果链容器。 */
@Data
public class CausalChain {
    private List<Event> events = new ArrayList<>();

    public void addEvent(Event event) {
        events.add(event);
    }

    public boolean isValid() {
        return !events.isEmpty();
    }
}
