package com.jobpilot.matching.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class FinalScoreCalculatorTest {
    private final FinalScoreCalculator calculator = new FinalScoreCalculator();

    @Test
    void renormalizesWeightsWhenLlmIsNotConfigured() {
        Map<String, BigDecimal> configured = weights();
        Map<String, BigDecimal> effective = calculator.effectiveWeights(configured, false);

        assertThat(effective.get("llm")).isEqualByComparingTo("0");
        assertThat(effective.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.000000");
        assertThat(effective.get("skill")).isEqualByComparingTo("40.000000");
    }

    @Test
    void computesBoundedWeightedScore() {
        Map<String, BigDecimal> scores = new LinkedHashMap<>();
        weights().keySet().forEach(key -> scores.put(key, new BigDecimal("80")));
        assertThat(calculator.weighted(weights(), scores)).isEqualByComparingTo("80.00");
    }

    @ParameterizedTest
    @MethodSource("levelBoundaries")
    void assignsStableLevelBoundaries(String score, String expected) {
        assertThat(calculator.level(new BigDecimal(score), thresholds())).isEqualTo(expected);
    }

    static Stream<Arguments> levelBoundaries() {
        return Stream.of(
                Arguments.of("100", "S"), Arguments.of("90", "S"), Arguments.of("89.99", "A"),
                Arguments.of("80", "A"), Arguments.of("79.99", "B"), Arguments.of("70", "B"),
                Arguments.of("69.99", "C"), Arguments.of("60", "C"), Arguments.of("59.99", "D"),
                Arguments.of("0", "D"));
    }

    private Map<String, BigDecimal> weights() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("skill", new BigDecimal("30")); values.put("embedding", new BigDecimal("20"));
        values.put("llm", new BigDecimal("25")); values.put("project", new BigDecimal("10"));
        values.put("preference", new BigDecimal("10")); values.put("company", new BigDecimal("5"));
        return values;
    }

    private Map<String, BigDecimal> thresholds() {
        return Map.of("S", new BigDecimal("90"), "A", new BigDecimal("80"), "B", new BigDecimal("70"),
                "C", new BigDecimal("60"), "D", BigDecimal.ZERO);
    }
}
