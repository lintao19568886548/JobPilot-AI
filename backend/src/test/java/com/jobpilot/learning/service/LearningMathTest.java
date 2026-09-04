package com.jobpilot.learning.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LearningMathTest {
    @Test
    void chronologicalSplitKeepsFutureOutOfTrainingSet() {
        List<LearningMath.Point> points = new ArrayList<>();
        for (int index = 0; index < 30; index++) points.add(point(index, index % 2));

        LearningMath.Split split = LearningMath.timeSplit(points);

        assertThat(split.train()).hasSize(24);
        assertThat(split.validation()).hasSize(6);
        assertThat(split.leakageSafe()).isTrue();
        assertThat(split.train().getLast().occurredAt()).isBeforeOrEqualTo(split.cutoffAt());
    }

    @Test
    void evaluationProducesVersionableNormalizedWeights() {
        List<LearningMath.Point> points = new ArrayList<>();
        for (int index = 0; index < 40; index++) points.add(point(index, index > 19 ? 1 : 0));

        LearningMath.Evaluation evaluation = LearningMath.evaluate(LearningMath.timeSplit(points));

        assertThat(evaluation.weights()).containsOnlyKeys(LearningMath.FEATURES);
        assertThat(evaluation.weights().values()).containsOnlyElementsOf(evaluation.weights().values());
        assertThat(evaluation.weights().values().stream().reduce(BigDecimal.ZERO, BigDecimal::add))
                .isEqualByComparingTo("100.00");
        assertThat(evaluation.candidateNdcg()).isBetween(BigDecimal.ZERO, BigDecimal.ONE);
    }

    @Test
    void scoreIgnoresMissingFeaturesWithoutInventingValues() {
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        features.put("skill", new BigDecimal("90"));
        Map<String, BigDecimal> weights = new LinkedHashMap<>();
        weights.put("skill", new BigDecimal("30"));
        weights.put("embedding", new BigDecimal("20"));

        assertThat(LearningMath.score(features, weights)).isEqualByComparingTo("90.00");
    }

    private static LearningMath.Point point(int index, int label) {
        BigDecimal score = BigDecimal.valueOf(index + 30L);
        Map<String, BigDecimal> features = new LinkedHashMap<>();
        for (String name : LearningMath.FEATURES) features.put(name, score);
        return new LearningMath.Point(features, BigDecimal.ZERO, score, BigDecimal.valueOf(label),
                LocalDateTime.of(2026, 1, 1, 0, 0).plusDays(index));
    }
}
