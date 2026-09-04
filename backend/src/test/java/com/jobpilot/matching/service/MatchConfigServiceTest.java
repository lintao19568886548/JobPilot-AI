package com.jobpilot.matching.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.matching.dto.MatchingDtos.MatchConfigRequest;
import com.jobpilot.matching.mapper.MatchConfigMapper;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MatchConfigServiceTest {
    private MatchConfigService service;

    @BeforeEach
    void setUp() {
        MatchConfigMapper mapper = mock(MatchConfigMapper.class);
        when(mapper.selectList(any())).thenReturn(List.of());
        service = new MatchConfigService(mapper, new ObjectMapper(), new JsonCodec(new ObjectMapper()), mock(AuditService.class));
    }

    @Test
    void rejectsWeightsThatDoNotTotalOneHundred() {
        Map<String, BigDecimal> weights = weights(); weights.put("skill", new BigDecimal("29"));
        assertThatThrownBy(() -> service.create(1L, request(weights, thresholds())))
                .isInstanceOf(ValidationException.class).hasMessageContaining("total 100");
    }

    @Test
    void rejectsIncompleteDimensionKeys() {
        Map<String, BigDecimal> weights = weights(); weights.remove("company");
        assertThatThrownBy(() -> service.create(1L, request(weights, thresholds())))
                .isInstanceOf(ValidationException.class).hasMessageContaining("exactly");
    }

    @Test
    void rejectsOverlappingLevelThresholds() {
        Map<String, BigDecimal> thresholds = thresholds(); thresholds.put("A", new BigDecimal("90"));
        assertThatThrownBy(() -> service.create(1L, request(weights(), thresholds)))
                .isInstanceOf(ValidationException.class).hasMessageContaining("descend");
    }

    private MatchConfigRequest request(Map<String, BigDecimal> weights, Map<String, BigDecimal> thresholds) {
        return new MatchConfigRequest("test", weights, thresholds, Map.of(), false);
    }

    private Map<String, BigDecimal> weights() {
        Map<String, BigDecimal> values = new LinkedHashMap<>();
        values.put("skill", new BigDecimal("30")); values.put("embedding", new BigDecimal("20"));
        values.put("llm", new BigDecimal("25")); values.put("project", new BigDecimal("10"));
        values.put("preference", new BigDecimal("10")); values.put("company", new BigDecimal("5")); return values;
    }

    private Map<String, BigDecimal> thresholds() {
        return new LinkedHashMap<>(Map.of("S", new BigDecimal("90"), "A", new BigDecimal("80"), "B", new BigDecimal("70"), "C", new BigDecimal("60"), "D", BigDecimal.ZERO));
    }
}
