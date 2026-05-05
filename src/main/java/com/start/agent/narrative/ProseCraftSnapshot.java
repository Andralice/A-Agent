package com.start.agent.narrative;

/**
 * 文笔四层旋钮（句子节奏 / 感知权重 / 词粒度 / 信息揭示）；字段均可缺省，由 {@link ProseCraftResolver} 填入。
 */
public record ProseCraftSnapshot(
        Rhythm rhythm,
        Perception perception,
        Language language,
        InformationFlow informationFlow
) {
    public static ProseCraftSnapshot disabled() {
        return new ProseCraftSnapshot(null, null, null, null);
    }

    public boolean enabled() {
        return (rhythm != null && rhythm.present())
                || (perception != null && perception.present())
                || (language != null && language.present())
                || (informationFlow != null && informationFlow.present());
    }

    public record Rhythm(Double sentenceLengthVariance, Double pauseDensity, Double fragmentation) {
        public boolean present() {
            return sentenceLengthVariance != null || pauseDensity != null || fragmentation != null;
        }
    }

    public record Perception(Double sensoryWeight, Double conceptualWeight,
                             Double externalActionWeight, Double internalThoughtWeight) {
        public boolean present() {
            return sensoryWeight != null || conceptualWeight != null
                    || externalActionWeight != null || internalThoughtWeight != null;
        }
    }

    public record Language(Double abstractionLevel, Double wordPrecision,
                           Double adjectiveControl, Double technicalDensity) {
        public boolean present() {
            return abstractionLevel != null || wordPrecision != null
                    || adjectiveControl != null || technicalDensity != null;
        }
    }

    /**
     * {@code revealType}：immediate / layered / withheld（大小写不敏感）。
     */
    public record InformationFlow(String revealType, Double uncertaintyMaintenance, Double clarityDelay) {
        public boolean present() {
            return (revealType != null && !revealType.isBlank())
                    || uncertaintyMaintenance != null || clarityDelay != null;
        }
    }
}
