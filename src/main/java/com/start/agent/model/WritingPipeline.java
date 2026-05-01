package com.start.agent.model;

public enum WritingPipeline {
    POWER_FANTASY,
    LIGHT_NOVEL,
    SLICE_OF_LIFE;

    public static WritingPipeline fromPath(String style) {
        if (style == null) return POWER_FANTASY;
        return switch (style.trim().toLowerCase()) {
            case "light", "light-novel", "ln" -> LIGHT_NOVEL;
            case "slice", "slice-of-life", "daily" -> SLICE_OF_LIFE;
            default -> POWER_FANTASY;
        };
    }
}
