package com.jobpilot.operations.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class BudgetEvaluatorTest {
    @Test void calculatesUsageAndStatesWithoutInventingMissingLimits(){
        assertThat(BudgetEvaluator.percent(80,100L)).isEqualByComparingTo("80.00");
        assertThat(BudgetEvaluator.percent(1,null)).isNull();
        assertThat(BudgetEvaluator.status(false,BigDecimal.valueOf(80),BigDecimal.valueOf(999))).isEqualTo("DISABLED");
        assertThat(BudgetEvaluator.status(true,BigDecimal.valueOf(80),(BigDecimal)null)).isEqualTo("DISABLED");
        assertThat(BudgetEvaluator.status(true,BigDecimal.valueOf(80),BigDecimal.valueOf(79.99))).isEqualTo("OK");
        assertThat(BudgetEvaluator.status(true,BigDecimal.valueOf(80),BigDecimal.valueOf(80))).isEqualTo("WARNING");
        assertThat(BudgetEvaluator.status(true,BigDecimal.valueOf(80),BigDecimal.valueOf(100))).isEqualTo("EXCEEDED");
    }
}
