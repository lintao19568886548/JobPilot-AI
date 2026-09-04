package com.jobpilot.learning.service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class LearningMath {
    public static final List<String> FEATURES = List.of("skill", "embedding", "llm", "project", "preference", "company");
    private static final Map<String, BigDecimal> BASELINE = Map.of(
            "skill", bd("30"), "embedding", bd("20"), "llm", bd("25"),
            "project", bd("10"), "preference", bd("10"), "company", bd("5"));
    private LearningMath() { }

    public record Point(Map<String, BigDecimal> features, BigDecimal penalty,
                        BigDecimal baselineScore, BigDecimal label, LocalDateTime occurredAt) { }
    public record Split(List<Point> train, List<Point> validation, LocalDateTime cutoffAt, boolean leakageSafe) { }
    public record Evaluation(Map<String, BigDecimal> weights, BigDecimal baselineNdcg,
                             BigDecimal candidateNdcg, BigDecimal positiveRate, boolean eligible) { }

    public static Split timeSplit(List<Point> values) {
        List<Point> sorted = values.stream().sorted(Comparator.comparing(Point::occurredAt)).toList();
        if (sorted.size() < 2) return new Split(sorted, List.of(), null, true);
        int validation = Math.max(1, (int)Math.ceil(sorted.size() * 0.2));
        int cut = sorted.size() - validation;
        List<Point> train = new ArrayList<>(sorted.subList(0, cut));
        List<Point> holdout = new ArrayList<>(sorted.subList(cut, sorted.size()));
        LocalDateTime cutoff = holdout.getFirst().occurredAt();
        boolean safe = train.stream().allMatch(point -> !point.occurredAt().isAfter(cutoff));
        return new Split(train, holdout, cutoff, safe);
    }

    public static Evaluation evaluate(Split split) {
        Map<String, BigDecimal> learned = learn(split.train());
        BigDecimal baseline = ndcg(split.validation(), point -> point.baselineScore());
        BigDecimal candidate = ndcg(split.validation(), point -> adjustedScore(point.features(), learned, point.penalty()));
        BigDecimal positive = split.validation().isEmpty() ? BigDecimal.ZERO : split.validation().stream()
                .map(Point::label).reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(split.validation().size()), 4, RoundingMode.HALF_UP);
        return new Evaluation(learned, baseline, candidate, positive,
                split.leakageSafe() && !split.validation().isEmpty() && candidate.compareTo(baseline) >= 0);
    }

    public static BigDecimal score(Map<String, BigDecimal> features, Map<String, BigDecimal> weights) {
        BigDecimal numerator = BigDecimal.ZERO, denominator = BigDecimal.ZERO;
        for (String key : FEATURES) {
            BigDecimal value = features.get(key), weight = weights.get(key);
            if (value != null && weight != null && weight.signum() > 0) {
                numerator = numerator.add(value.multiply(weight)); denominator = denominator.add(weight);
            }
        }
        return denominator.signum() == 0 ? null : numerator.divide(denominator, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal adjustedScore(Map<String, BigDecimal> features, Map<String, BigDecimal> weights,
                                           BigDecimal penalty) {
        BigDecimal score = score(features, weights);
        if (score == null) return null;
        return score.subtract(penalty == null ? BigDecimal.ZERO : penalty)
                .max(BigDecimal.ZERO).min(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
    }

    static Map<String, BigDecimal> learn(List<Point> train) {
        Map<String, BigDecimal> signal = new LinkedHashMap<>(); BigDecimal total = BigDecimal.ZERO;
        for (String key : FEATURES) {
            BigDecimal value = train.stream().filter(point -> point.features().get(key) != null)
                    .map(point -> point.features().get(key).multiply(point.label()))
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            value = value.max(new BigDecimal("0.0001")); signal.put(key, value); total = total.add(value);
        }
        Map<String, BigDecimal> result = new LinkedHashMap<>(); BigDecimal assigned = BigDecimal.ZERO;
        for (int index=0; index<FEATURES.size(); index++) {
            String key=FEATURES.get(index);
            BigDecimal normalized=signal.get(key).multiply(new BigDecimal("100")).divide(total,8,RoundingMode.HALF_UP);
            BigDecimal blended=BASELINE.get(key).multiply(new BigDecimal("0.80")).add(normalized.multiply(new BigDecimal("0.20")));
            BigDecimal rounded=index==FEATURES.size()-1?new BigDecimal("100.00").subtract(assigned):blended.setScale(2,RoundingMode.HALF_UP);
            result.put(key,rounded);assigned=assigned.add(rounded);
        }
        return result;
    }

    static BigDecimal ndcg(List<Point> points, java.util.function.Function<Point, BigDecimal> scorer) {
        if (points.isEmpty()) return BigDecimal.ZERO.setScale(4);
        List<Point> predicted=points.stream().sorted(Comparator.comparing(scorer,Comparator.nullsLast(Comparator.reverseOrder()))).limit(10).toList();
        List<Point> ideal=points.stream().sorted(Comparator.comparing(Point::label).reversed()).limit(10).toList();
        double dcg=dcg(predicted), idcg=dcg(ideal); return BigDecimal.valueOf(idcg==0?0:dcg/idcg).setScale(4,RoundingMode.HALF_UP);
    }
    private static double dcg(List<Point> points){double result=0;for(int i=0;i<points.size();i++)result+=(Math.pow(2,points.get(i).label().doubleValue())-1)/(Math.log(i+2)/Math.log(2));return result;}
    private static BigDecimal bd(String value){return new BigDecimal(value);}
}
