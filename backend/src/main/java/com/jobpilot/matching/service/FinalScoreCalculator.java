package com.jobpilot.matching.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FinalScoreCalculator {
    private static final BigDecimal HUNDRED = new BigDecimal("100");

    public Map<String, BigDecimal> effectiveWeights(Map<String, BigDecimal> configured, boolean llmAvailable) {
        if (llmAvailable) return new LinkedHashMap<>(configured);
        BigDecimal total = configured.entrySet().stream()
                .filter(entry -> !"llm".equals(entry.getKey()))
                .map(Map.Entry::getValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        if (total.signum() <= 0) throw new IllegalArgumentException("Non-LLM match weights must total more than zero");
        Map<String, BigDecimal> effective = new LinkedHashMap<>();
        configured.forEach((key, weight) -> effective.put(key, "llm".equals(key)
                ? BigDecimal.ZERO
                : weight.multiply(HUNDRED).divide(total, 6, RoundingMode.HALF_UP)));
        return effective;
    }

    public BigDecimal weighted(Map<String, BigDecimal> weights, Map<String, BigDecimal> scores) {
        BigDecimal total = weights.entrySet().stream()
                .map(entry -> scores.getOrDefault(entry.getKey(), BigDecimal.ZERO)
                        .multiply(entry.getValue()).divide(HUNDRED, 6, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        return total.max(BigDecimal.ZERO).min(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }

    public String level(BigDecimal score, Map<String, BigDecimal> thresholds) {
        return thresholds.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue(Comparator.reverseOrder()))
                .filter(entry -> score.compareTo(entry.getValue()) >= 0)
                .map(Map.Entry::getKey)
                .findFirst()
                .orElse("D");
    }
}
