package com.start.agent.engine;

import lombok.Data;
import java.util.ArrayList;
import java.util.List;

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
