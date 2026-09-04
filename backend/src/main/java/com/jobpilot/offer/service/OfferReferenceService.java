package com.jobpilot.offer.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import org.springframework.stereotype.Service;

@Service
public class OfferReferenceService {
    private final ApplicationMapper applications;
    private final JobMapper jobs;
    private final CompanyMapper companies;
    private final ResumeVersionMapper versions;
    private final ResumeMapper resumes;
    public OfferReferenceService(ApplicationMapper applications, JobMapper jobs, CompanyMapper companies,
                                 ResumeVersionMapper versions, ResumeMapper resumes) {
        this.applications = applications; this.jobs = jobs; this.companies = companies; this.versions = versions; this.resumes = resumes;
    }

    public References application(Long userId, String publicId) {
        ApplicationEntity application = applications.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getPublicId, publicId).last("LIMIT 1"));
        if (application == null) throw new ResourceNotFoundException("Application");
        if (!"OFFER".equals(application.getStatus())) throw new ValidationException("Application must be in OFFER status");
        JobEntity job = jobs.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getId, application.getJobId()).last("LIMIT 1"));
        if (job == null) throw new ResourceNotFoundException("Job");
        CompanyEntity company = companies.selectById(job.getCompanyId());
        if (company == null) throw new ResourceNotFoundException("Company");
        ResumeVersionEntity version = versions.selectById(application.getResumeVersionId());
        ResumeEntity resume = version == null ? null : resumes.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, version.getResumeId()).eq(ResumeEntity::getUserId, userId).last("LIMIT 1"));
        if (version == null || resume == null) throw new ResourceNotFoundException("Resume Version");
        return new References(application, job, company, version);
    }

    public record References(ApplicationEntity application, JobEntity job, CompanyEntity company, ResumeVersionEntity resumeVersion) { }
}
