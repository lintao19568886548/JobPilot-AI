package com.jobpilot.analytics.service;

import static org.junit.jupiter.api.Assertions.*;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CompleteAnalyticsServiceTest {
    @Test void returnsNullRateForZeroDenominator(){var metric=CompleteAnalyticsService.metric("X","X",0,0);assertNull(metric.rate());assertEquals(0,metric.sampleSize());}
    @Test void calculatesPercentageWithExplicitCounts(){var metric=CompleteAnalyticsService.metric("X","X",1,4);assertEquals(new BigDecimal("25.00"),metric.rate());assertEquals(1,metric.numerator());assertEquals(4,metric.denominator());}
}
