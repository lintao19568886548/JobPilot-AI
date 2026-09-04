package com.jobpilot.operations.service;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class BudgetEvaluator {
    private BudgetEvaluator() { }

    public static BigDecimal percent(BigDecimal used, BigDecimal limit) {
        if (limit == null || limit.signum() <= 0) return null;
        return used.multiply(BigDecimal.valueOf(100)).divide(limit, 2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(long used, Long limit) {
        return limit == null ? null : percent(BigDecimal.valueOf(used), BigDecimal.valueOf(limit));
    }

    public static String status(boolean enabled, BigDecimal warningThreshold, BigDecimal... percentages) {
        if (!enabled) return "DISABLED";
        BigDecimal maximum = BigDecimal.ZERO;
        boolean configured = false;
        for (BigDecimal value : percentages) {
            if (value != null) {
                configured = true;
                if (value.compareTo(maximum) > 0) maximum = value;
            }
        }
        if (!configured) return "DISABLED";
        if (maximum.compareTo(BigDecimal.valueOf(100)) >= 0) return "EXCEEDED";
        if (maximum.compareTo(warningThreshold) >= 0) return "WARNING";
        return "OK";
    }
}
