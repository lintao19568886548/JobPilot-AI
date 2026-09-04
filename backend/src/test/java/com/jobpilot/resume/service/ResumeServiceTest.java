package com.jobpilot.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeSectionEntity;
import com.jobpilot.resume.domain.ResumeSectionType;
import com.jobpilot.resume.domain.ResumeSourceType;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.dto.ResumeDtos.ResumeCreateRequest;
import com.jobpilot.resume.dto.ResumeDtos.SectionRequest;
import com.jobpilot.resume.dto.ResumeDtos.VersionCreateRequest;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeSectionMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ResumeServiceTest {

    private ResumeMapper resumeMapper;
    private ResumeVersionMapper versionMapper;
    private ResumeSectionMapper sectionMapper;
    private AuditService audit;
    private ObjectMapper objectMapper;
    private ResumeService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "resume-test"), ResumeEntity.class);
        resumeMapper = mock(ResumeMapper.class);
        versionMapper = mock(ResumeVersionMapper.class);
        sectionMapper = mock(ResumeSectionMapper.class);
        audit = mock(AuditService.class);
        objectMapper = new ObjectMapper();
        service = new ResumeService(resumeMapper, versionMapper, sectionMapper, new JsonCodec(objectMapper), audit,
                mock(com.jobpilot.job.mapper.JobMapper.class), mock(com.jobpilot.tailoring.mapper.PromptTemplateMapper.class));
    }

    @Test
    void firstResumeAutomaticallyBecomesDefault() {
        when(resumeMapper.selectCount(any())).thenReturn(0L);
        doAnswer(invocation -> assign(invocation.getArgument(0), 1L, "resume-1")).when(resumeMapper)
                .insert(any(ResumeEntity.class));
        when(versionMapper.selectCount(any())).thenReturn(0L);
        var result = service.create(7L,
                new ResumeCreateRequest("Java Backend", "Java Engineer", true, false, "Master facts", "ACTIVE"));
        assertThat(result.resume().defaultResume()).isTrue();
        assertThat(result.resume().master()).isTrue();
    }

    @Test
    void createVersionIncrementsVersionAndPersistsStructuredSections() {
        ResumeEntity resume = activeResume();
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume);
        when(versionMapper.selectMaxVersionNumber(1L)).thenReturn(1);
        doAnswer(invocation -> assign(invocation.getArgument(0), 2L, "version-2"))
                .when(versionMapper).insert(any(ResumeVersionEntity.class));
        doAnswer(invocation -> assign(invocation.getArgument(0), 3L, "section-1"))
                .when(sectionMapper).insert(any(ResumeSectionEntity.class));
        when(resumeMapper.updateById(resume)).thenReturn(1);
        when(sectionMapper.selectList(any())).thenAnswer(invocation -> List.of());
        var request = new VersionCreateRequest("Java Backend v2", Map.of("name", "Lin"),
                "Lin - Java Engineer", ResumeSourceType.MANUAL, "USER",
                List.of(new SectionRequest(ResumeSectionType.BASIC_INFO,
                        Map.of("fullName", "Lin"), 0)));
        var result = service.createVersion(7L, "resume-1", request);
        assertThat(result.versionNumber()).isEqualTo(2);
        assertThat(result.contentHash()).hasSize(64);
        assertThat(resume.getCurrentVersionId()).isEqualTo(2L);
    }

    @Test
    void versionRejectsDuplicateSectionTypes() {
        ResumeEntity resume = activeResume();
        when(resumeMapper.selectOne(any())).thenReturn(resume);
        when(resumeMapper.selectByIdForUpdate(1L)).thenReturn(resume);
        var section = new SectionRequest(ResumeSectionType.SKILLS, objectMapper.createArrayNode().add("Java"), 0);
        var request = new VersionCreateRequest("v2", objectMapper.createObjectNode(), null,
                ResumeSourceType.MANUAL, "USER", List.of(section, section));
        assertThatThrownBy(() -> service.createVersion(7L, "resume-1", request))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void lookupDoesNotRevealAnotherUsersResume() {
        assertThatThrownBy(() -> service.get(999L, "resume-1")).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createRejectsUnknownStatus() {
        assertThatThrownBy(() -> service.create(7L,
                new ResumeCreateRequest("Resume", null, false, false, null, "REMOVED")))
                .isInstanceOf(ValidationException.class);
    }

    private ResumeEntity activeResume() {
        ResumeEntity resume = new ResumeEntity();
        resume.setId(1L);
        resume.setPublicId("resume-1");
        resume.setUserId(7L);
        resume.setName("Java Backend");
        resume.setTargetRole("Java Engineer");
        resume.setMaster(true);
        resume.setDefaultResume(true);
        resume.setStatus("ACTIVE");
        resume.setVersion(0);
        return resume;
    }

    private int assign(Object value, long id, String publicId) {
        com.jobpilot.common.persistence.BaseEntity entity = (com.jobpilot.common.persistence.BaseEntity) value;
        entity.setId(id);
        entity.setPublicId(publicId);
        entity.setVersion(0);
        return 1;
    }
}
