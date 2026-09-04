package com.jobpilot.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.matching.domain.HardFilterRuleEntity;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterRuleRequest;
import com.jobpilot.matching.mapper.HardFilterRuleMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HardFilterServiceTest {
    private HardFilterRuleMapper mapper;
    private HardFilterService service;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        mapper = mock(HardFilterRuleMapper.class);
        service = new HardFilterService(mapper, objectMapper, new JsonCodec(objectMapper), mock(AuditService.class));
    }

    @Test
    void returnsPassWhenNoActiveRuleIsViolated() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("experience", "EXPERIENCE_YEARS", "GTE", "5", "DOWNGRADE", "10")));
        assertThat(service.evaluate(1L, profile("6"), job("3"), company()).result()).isEqualTo("PASS");
    }

    @Test
    void returnsDowngradeAndAccumulatesPenalty() {
        when(mapper.selectList(any())).thenReturn(List.of(rule("experience", "EXPERIENCE_YEARS", "GTE", "5", "DOWNGRADE", "12.5")));
        var result = service.evaluate(1L, profile("2"), job("5"), company());
        assertThat(result.result()).isEqualTo("DOWNGRADE");
        assertThat(result.penalty()).isEqualByComparingTo("12.5");
        assertThat(result.evidence()).hasSize(1);
    }

    @Test
    void rejectDominatesDowngrade() {
        when(mapper.selectList(any())).thenReturn(List.of(
                rule("experience", "EXPERIENCE_YEARS", "GTE", "5", "DOWNGRADE", "10"),
                rule("blacklist", "COMPANY_BLACKLIST", "CONTAINS", "{\"values\":[\"unsafe\"]}", "REJECT", "0")));
        assertThat(service.evaluate(1L, profile("2"), job("5"), company()).result()).isEqualTo("REJECT");
    }

    @Test
    void evaluatesNumericRangeOperandsInsteadOfSilentlyPassing() {
        when(mapper.selectList(any())).thenReturn(List.of(
                rule("grad_range", "GRADUATION_YEAR", "RANGE", "{\"values\":[2024,2026]}", "REJECT", "0")));
        CandidateProfileEntity profile = profile("2"); profile.setGraduationYear(2023);
        assertThat(service.evaluate(1L, profile, job("5"), company()).result()).isEqualTo("REJECT");
    }

    @Test
    void rejectsOperatorOutsideTheTypeWhitelist() {
        var request = new HardFilterRuleRequest("bad", "Bad", "CITY", "GTE", objectMapper.valueToTree("Hangzhou"),
                "REJECT", BigDecimal.ZERO, 1, true);
        assertThatThrownBy(() -> service.create(1L, request)).isInstanceOf(ValidationException.class)
                .hasMessageContaining("operator is not allowed");
    }

    private HardFilterRuleEntity rule(String key, String type, String operator, String operand, String action, String penalty) {
        HardFilterRuleEntity rule = new HardFilterRuleEntity();
        rule.setRuleKey(key); rule.setRuleType(type); rule.setOperator(operator);
        rule.setOperandJson(operand.startsWith("{") ? operand : "{\"value\":" + operand + "}");
        rule.setResultAction(action); rule.setPenalty(new BigDecimal(penalty)); rule.setPriority(1); rule.setActive(true); rule.setVersionNo(1);
        return rule;
    }

    private CandidateProfileEntity profile(String years) {
        CandidateProfileEntity profile = new CandidateProfileEntity();
        profile.setYearsOfExperience(new BigDecimal(years));
        return profile;
    }

    private JobEntity job(String years) {
        JobEntity job = new JobEntity(); job.setTitle("Java Engineer"); job.setDescriptionClean("Spring Boot");
        job.setExperienceMinYears(new BigDecimal(years)); return job;
    }

    private CompanyEntity company() {
        CompanyEntity company = new CompanyEntity(); company.setDisplayName("Unsafe Labs"); return company;
    }
}
