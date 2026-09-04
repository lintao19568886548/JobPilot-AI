package com.jobpilot.tailoring.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.RecruiterEntity;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.application.mapper.RecruiterMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import com.jobpilot.tailoring.domain.PromptTemplateEntity;
import com.jobpilot.tailoring.mapper.PromptTemplateMapper;
import org.springframework.stereotype.Service;

@Service
public class TailoringReferenceService {
    private final JobMapper jobs;
    private final CompanyMapper companies;
    private final ResumeMapper resumes;
    private final ResumeVersionMapper versions;
    private final ApplicationMapper applications;
    private final RecruiterMapper recruiters;
    private final PromptTemplateMapper prompts;

    public TailoringReferenceService(JobMapper jobs, CompanyMapper companies, ResumeMapper resumes,
                                     ResumeVersionMapper versions, ApplicationMapper applications,
                                     RecruiterMapper recruiters, PromptTemplateMapper prompts) {
        this.jobs = jobs; this.companies = companies; this.resumes = resumes; this.versions = versions;
        this.applications = applications; this.recruiters = recruiters;
        this.prompts = prompts;
    }

    public JobEntity job(Long userId, String publicId) {
        JobEntity entity = jobs.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Job");
        return entity;
    }

    public CompanyEntity company(Long id) { return id == null ? null : companies.selectById(id); }

    public JobEntity jobByDatabaseId(Long id) { return id == null ? null : jobs.selectById(id); }

    public ResumeVersionEntity version(Long userId, String publicId) {
        ResumeVersionEntity version = versions.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getPublicId, publicId).last("LIMIT 1"));
        if (version == null || resumeById(userId, version.getResumeId()) == null) {
            throw new ResourceNotFoundException("Resume version");
        }
        return version;
    }

    public ResumeVersionEntity versionById(Long userId, Long id) {
        ResumeVersionEntity version = id == null ? null : versions.selectById(id);
        if (version == null || resumeById(userId, version.getResumeId()) == null) {
            throw new ResourceNotFoundException("Resume version");
        }
        return version;
    }

    public ResumeEntity resume(Long userId, String publicId) {
        ResumeEntity resume = resumes.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId).eq(ResumeEntity::getPublicId, publicId).last("LIMIT 1"));
        if (resume == null) throw new ResourceNotFoundException("Resume");
        return resume;
    }

    public ResumeEntity resumeById(Long userId, Long id) {
        return id == null ? null : resumes.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId).eq(ResumeEntity::getId, id).last("LIMIT 1"));
    }

    public ResumeEntity master(Long userId) {
        ResumeEntity resume = resumes.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId).eq(ResumeEntity::getMaster, true)
                .eq(ResumeEntity::getStatus, "ACTIVE").last("LIMIT 1"));
        if (resume == null || resume.getCurrentVersionId() == null) throw new ResourceNotFoundException("Active Master Resume");
        return resume;
    }

    public ApplicationEntity application(Long userId, String publicId, boolean nullable) {
        if (publicId == null || publicId.isBlank()) return null;
        ApplicationEntity entity = applications.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null && !nullable) throw new ResourceNotFoundException("Application");
        return entity;
    }

    public RecruiterEntity recruiter(Long userId, String publicId, boolean nullable) {
        if (publicId == null || publicId.isBlank()) return null;
        RecruiterEntity entity = recruiters.selectOne(new LambdaQueryWrapper<RecruiterEntity>()
                .eq(RecruiterEntity::getUserId, userId).eq(RecruiterEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null && !nullable) throw new ResourceNotFoundException("Recruiter");
        return entity;
    }

    public ApplicationEntity applicationById(Long userId, Long id) {
        ApplicationEntity entity = id == null ? null : applications.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getId, id).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Application");
        return entity;
    }

    public RecruiterEntity recruiterById(Long userId, Long id) {
        RecruiterEntity entity = id == null ? null : recruiters.selectOne(new LambdaQueryWrapper<RecruiterEntity>()
                .eq(RecruiterEntity::getUserId, userId).eq(RecruiterEntity::getId, id).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Recruiter");
        return entity;
    }

    public PromptTemplateEntity promptById(Long id) { return id == null ? null : prompts.selectById(id); }
}
