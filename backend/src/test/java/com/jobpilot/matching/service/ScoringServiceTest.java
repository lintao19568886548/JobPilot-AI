package com.jobpilot.matching.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.JobSkillMapper;
import com.jobpilot.matching.domain.SkillRelationEntity;
import com.jobpilot.matching.mapper.SkillRelationMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ScoringServiceTest {
    @Test
    void scoresOntologyProjectPreferencesAndCompanyFromPersistedFacts() {
        CandidateSkillMapper candidateMapper=mock(CandidateSkillMapper.class); ProjectMapper projectMapper=mock(ProjectMapper.class);
        SkillMapper skillMapper=mock(SkillMapper.class); JobSkillMapper jobSkillMapper=mock(JobSkillMapper.class);
        SkillRelationMapper relationMapper=mock(SkillRelationMapper.class);
        CandidateSkillEntity docker=candidateSkill(1L,"01SKILLDOCKER000000000001",80);
        CandidateSkillEntity kafka=candidateSkill(3L,"01SKILLKAFKA0000000000001",100);
        JobSkillEntity required=new JobSkillEntity();required.setJobId(10L);required.setSkillId(2L);required.setRequirementType("MUST_HAVE");required.setImportance(100);required.setEvidenceText("Kubernetes required");
        ProjectEntity project=new ProjectEntity();project.setPublicId("01PROJECT00000000000000001");project.setName("Platform");project.setTechnologiesJson("[\"Kubernetes\"]");
        when(candidateMapper.selectList(any())).thenReturn(List.of(docker,kafka));when(projectMapper.selectList(any())).thenReturn(List.of(project));
        when(jobSkillMapper.selectList(any())).thenReturn(List.of(required));
        when(skillMapper.selectBatchIds(any())).thenReturn(List.of(skill(1L,"docker","Docker"),skill(2L,"kubernetes","Kubernetes"),skill(3L,"kafka","Kafka")));
        when(relationMapper.selectList(any())).thenReturn(List.of(relation(1L,2L,"0.500"),relation(3L,2L,"0.800")));
        ScoringService service=new ScoringService(candidateMapper,projectMapper,skillMapper,jobSkillMapper,relationMapper,new JsonCodec(new ObjectMapper()));

        CandidateProfileEntity profile=new CandidateProfileEntity();profile.setPublicId("01PROFILE000000000000001");profile.setTargetCitiesJson("[\"Shanghai\"]");
        profile.setTargetRolesJson("[\"Java\"]");profile.setTargetSalaryMin(new BigDecimal("20"));profile.setTargetIndustriesJson("[\"Software\"]");profile.setTargetCompanyTypesJson("[\"SERIES_B\"]");
        JobEntity job=new JobEntity();job.setId(10L);job.setTitle("Senior Java Engineer");job.setCity("Shanghai");job.setSalaryMax(new BigDecimal("30"));
        CompanyEntity company=new CompanyEntity();company.setDisplayName("Example");company.setIndustry("Software");company.setCompanySize("100-499");company.setFinancingStage("SERIES_B");company.setRiskFlagsJson("[]");

        var result=service.score(1L,profile,job,company);
        assertThat(result.skillScore()).isEqualByComparingTo("80.00");
        assertThat(result.projectScore()).isEqualByComparingTo("100.00");
        assertThat(result.preferenceScore()).isEqualByComparingTo("100.00");
        assertThat(result.companyScore()).isEqualByComparingTo("93.33");
        assertThat(result.details()).extracting(ScoringService.DetailDraft::decision).contains("ONTOLOGY_RELATED","EVIDENCED","MATCH","CLEAR");
    }

    private CandidateSkillEntity candidateSkill(Long id,String publicId,int proficiency){CandidateSkillEntity value=new CandidateSkillEntity();value.setSkillId(id);value.setPublicId(publicId);value.setProficiency(proficiency);return value;}
    private SkillEntity skill(Long id,String canonical,String display){SkillEntity value=new SkillEntity();value.setId(id);value.setCanonicalName(canonical);value.setDisplayName(display);return value;}
    private SkillRelationEntity relation(Long from,Long to,String weight){SkillRelationEntity value=new SkillRelationEntity();value.setFromSkillId(from);value.setToSkillId(to);value.setRelationType("RELATED");value.setWeight(new BigDecimal(weight));return value;}
}
