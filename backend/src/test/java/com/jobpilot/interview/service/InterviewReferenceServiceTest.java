package com.jobpilot.interview.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import org.junit.jupiter.api.Test;

class InterviewReferenceServiceTest {
    @Test
    void rejectsResumeVersionOwnedByAnotherUser() {
        ResumeVersionMapper versions = mock(ResumeVersionMapper.class);
        ResumeMapper resumes = mock(ResumeMapper.class);
        ResumeVersionEntity version = new ResumeVersionEntity(); version.setResumeId(8L);
        when(versions.selectOne(any(Wrapper.class))).thenReturn(version);
        when(resumes.selectCount(any(Wrapper.class))).thenReturn(0L);
        InterviewReferenceService service = new InterviewReferenceService(mock(ApplicationMapper.class), mock(JobMapper.class),
                mock(CompanyMapper.class), versions, resumes);

        assertThatThrownBy(() -> service.optionalVersion(1L, "foreign-version"))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void rejectsApplicationAndJobThatPointAtDifferentRecords() {
        ApplicationMapper applications = mock(ApplicationMapper.class);
        JobMapper jobs = mock(JobMapper.class);
        ApplicationEntity application = new ApplicationEntity(); application.setJobId(7L);
        JobEntity linkedJob = new JobEntity(); linkedJob.setId(7L); linkedJob.setPublicId("job-linked");
        when(applications.selectOne(any(Wrapper.class))).thenReturn(application);
        when(jobs.selectOne(any(Wrapper.class))).thenReturn(linkedJob);
        InterviewReferenceService service = new InterviewReferenceService(applications, jobs, mock(CompanyMapper.class),
                mock(ResumeVersionMapper.class), mock(ResumeMapper.class));

        assertThatThrownBy(() -> service.resolve(1L, "application-1", "job-other", null, null, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("different jobs");
    }
}
