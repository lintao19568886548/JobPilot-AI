package com.jobpilot.search.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSkillMapper;
import java.util.List;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class GlobalSearchServiceTest {
    private JobMapper jobMapper;
    private CompanyMapper companyMapper;
    private SkillMapper skillMapper;
    private CandidateSkillMapper candidateSkillMapper;
    private GlobalSearchService service;

    @BeforeEach
    void setUp() {
        var assistant = new MapperBuilderAssistant(new MybatisConfiguration(), "global-search-test");
        TableInfoHelper.initTableInfo(assistant, JobEntity.class);
        TableInfoHelper.initTableInfo(assistant, JobSkillEntity.class);
        jobMapper = mock(JobMapper.class);
        companyMapper = mock(CompanyMapper.class);
        skillMapper = mock(SkillMapper.class);
        candidateSkillMapper = mock(CandidateSkillMapper.class);
        when(jobMapper.selectList(any())).thenReturn(List.of());
        when(candidateSkillMapper.selectList(any())).thenReturn(List.of());
        RecommendationProperties properties = new RecommendationProperties();
        properties.setGlobalSearchMaxResults(20);
        service = new GlobalSearchService(jobMapper, companyMapper, mock(JobSkillMapper.class), skillMapper,
                candidateSkillMapper, properties);
    }

    @Test
    void rejectsUnknownSearchType() {
        assertThatThrownBy(() -> service.search(1L, "java", "USER", 10))
                .isInstanceOf(ValidationException.class).hasMessageContaining("Search types");
    }

    @Test
    void rejectsTooShortAndTooLongQueries() {
        assertThatThrownBy(() -> service.search(1L, "x", null, 10)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.search(1L, "x".repeat(101), null, 10)).isInstanceOf(ValidationException.class);
    }

    @Test
    void treatsSqlWildcardCharactersAsLiteralText() {
        assertThat(service.search(1L, "%_\\", "JOB", 10).total()).isZero();
    }

    @Test
    void returnsOnlyJobsOwnedByTheCurrentUserQuery() {
        JobEntity job = new JobEntity();
        job.setId(7L); job.setPublicId("job_owned"); job.setTitle("Java Platform Engineer");
        job.setCity("Hangzhou"); job.setStatus("ACTIVE");
        when(jobMapper.selectList(any())).thenReturn(List.of(job));

        var result = service.search(42L, "java", "JOB", 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.groups().getFirst().items().getFirst().id()).isEqualTo("job_owned");
        assertThat(result.groups().getFirst().items().getFirst().targetUrl()).contains("job_owned");
    }

    @Test
    void searchesOnlyCandidateVisibleSkills() {
        CandidateSkillEntity link = new CandidateSkillEntity(); link.setSkillId(11L);
        SkillEntity skill = new SkillEntity();
        skill.setId(11L); skill.setPublicId("skill_java"); skill.setDisplayName("Java");
        skill.setCanonicalName("java"); skill.setCategory("LANGUAGE"); skill.setStatus("ACTIVE");
        when(candidateSkillMapper.selectList(any())).thenReturn(List.of(link));
        when(skillMapper.selectBatchIds(any())).thenReturn(List.of(skill));

        var result = service.search(42L, "java", "SKILL", 10);

        assertThat(result.total()).isEqualTo(1);
        assertThat(result.groups().getFirst().items().getFirst().type()).isEqualTo("SKILL");
    }
}
