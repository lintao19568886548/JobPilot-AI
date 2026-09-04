package com.jobpilot.offer.service;

import static org.junit.jupiter.api.Assertions.*;
import com.jobpilot.common.exception.ValidationException;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OfferComparisonServiceTest {
    @Test void acceptsExactCompleteWeightSet(){assertDoesNotThrow(()->OfferComparisonService.validateWeights(weights()));}
    @Test void rejectsWeightsThatDoNotTotalOneHundred(){Map<String,BigDecimal> values=weights();values.put("CASH",new BigDecimal("49"));assertThrows(ValidationException.class,()->OfferComparisonService.validateWeights(values));}
    @Test void rejectsMissingDimension(){Map<String,BigDecimal> values=weights();values.remove("LOCATION");assertThrows(ValidationException.class,()->OfferComparisonService.validateWeights(values));}
    private static Map<String,BigDecimal> weights(){Map<String,BigDecimal> result=new LinkedHashMap<>();result.put("CASH",new BigDecimal("50"));result.put("VARIABLE_BONUS",new BigDecimal("10"));result.put("BENEFITS",new BigDecimal("10"));result.put("GROWTH",new BigDecimal("10"));result.put("WORK_LIFE",new BigDecimal("5"));result.put("STABILITY",new BigDecimal("5"));result.put("LOCATION",new BigDecimal("5"));result.put("PREFERENCE",new BigDecimal("5"));return result;}
}
