package com.jobpilot.interview.service;

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
public class InterviewReferenceService {
    private final ApplicationMapper applications;
    private final JobMapper jobs;
    private final CompanyMapper companies;
    private final ResumeVersionMapper versions;
    private final ResumeMapper resumes;

    public InterviewReferenceService(ApplicationMapper applications, JobMapper jobs, CompanyMapper companies,
                                     ResumeVersionMapper versions, ResumeMapper resumes) {
        this.applications = applications; this.jobs = jobs; this.companies = companies;
        this.versions = versions; this.resumes = resumes;
    }

    public References resolve(Long userId, String applicationPublicId, String jobPublicId,
                              String resumeVersionPublicId, String companyName, String role) {
        ApplicationEntity application = optionalApplication(userId, applicationPublicId);
        JobEntity job = application == null ? optionalJob(userId, jobPublicId) : jobById(userId, application.getJobId());
        if (application != null && jobPublicId != null && !jobPublicId.isBlank() && !job.getPublicId().equals(jobPublicId)) {
            throw new ValidationException("applicationId and jobId refer to different jobs");
        }
        ResumeVersionEntity version = application == null
                ? optionalVersion(userId, resumeVersionPublicId)
                : versionById(userId, application.getResumeVersionId());
        if (application != null && resumeVersionPublicId != null && !resumeVersionPublicId.isBlank()
                && !version.getPublicId().equals(resumeVersionPublicId)) {
            throw new ValidationException("applicationId and resumeVersionId refer to different resume versions");
        }
        CompanyEntity company = job == null ? null : companies.selectById(job.getCompanyId());
        String companySnapshot = nonBlank(companyName, company == null ? null : company.getDisplayName());
        String roleSnapshot = nonBlank(role, job == null ? null : job.getTitle());
        if (companySnapshot == null || roleSnapshot == null) {
            throw new ValidationException("Interview requires a Job/Application or manual companyName and role");
        }
        return new References(application, job, company, version, companySnapshot, roleSnapshot);
    }

    public ApplicationEntity optionalApplication(Long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        ApplicationEntity value = applications.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Application");
        return value;
    }

    public JobEntity optionalJob(Long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        JobEntity value = jobs.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Job");
        return value;
    }

    public ResumeVersionEntity optionalVersion(Long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) return null;
        ResumeVersionEntity value = versions.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null || resumes.selectCount(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, value.getResumeId()).eq(ResumeEntity::getUserId, userId)) == 0) {
            throw new ResourceNotFoundException("Resume Version");
        }
        return value;
    }

    public JobEntity jobById(Long userId, Long id) {
        JobEntity value = jobs.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getId, id).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Job");
        return value;
    }

    public ResumeVersionEntity versionById(Long userId, Long id) {
        ResumeVersionEntity value = versions.selectById(id);
        if (value == null || resumes.selectCount(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, value.getResumeId()).eq(ResumeEntity::getUserId, userId)) == 0) {
            throw new ResourceNotFoundException("Resume Version");
        }
        return value;
    }

    private static String nonBlank(String preferred, String fallback) {
        if (preferred != null && !preferred.isBlank()) return preferred.trim();
        return fallback == null || fallback.isBlank() ? null : fallback.trim();
    }

    public record References(ApplicationEntity application, JobEntity job, CompanyEntity company,
                             ResumeVersionEntity resumeVersion, String companyName, String role) { }
}
