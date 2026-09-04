package com.jobpilot.application.service;

import static com.jobpilot.application.dto.ApplicationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.RecruiterEntity;
import com.jobpilot.application.mapper.RecruiterMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import org.springframework.stereotype.Service;

@Service
public class ApplicationReferenceService {
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final JobSourceMapper sourceMapper;
    private final JobMatchMapper matchMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final RecruiterMapper recruiterMapper;

    public ApplicationReferenceService(JobMapper jobMapper, CompanyMapper companyMapper,
                                       JobSourceMapper sourceMapper, JobMatchMapper matchMapper,
                                       ResumeMapper resumeMapper, ResumeVersionMapper versionMapper,
                                       RecruiterMapper recruiterMapper) {
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.sourceMapper = sourceMapper;
        this.matchMapper = matchMapper;
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.recruiterMapper = recruiterMapper;
    }

    public JobEntity job(Long userId, String publicId) {
        JobEntity result = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getPublicId, publicId).last("LIMIT 1"));
        if (result == null || !"ACTIVE".equals(result.getStatus())) throw new ResourceNotFoundException("Active job");
        return result;
    }

    public JobEntity jobById(Long userId, Long id) {
        JobEntity result = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getId, id).last("LIMIT 1"));
        if (result == null) throw new ResourceNotFoundException("Job");
        return result;
    }

    public JobSourceEntity source(Long userId, Long jobId) {
        return sourceMapper.selectOne(new LambdaQueryWrapper<JobSourceEntity>()
                .eq(JobSourceEntity::getUserId, userId).eq(JobSourceEntity::getJobId, jobId)
                .orderByAsc(JobSourceEntity::getId).last("LIMIT 1"));
    }

    public JobMatchEntity match(Long userId, Long jobId, String publicId) {
        LambdaQueryWrapper<JobMatchEntity> query = new LambdaQueryWrapper<JobMatchEntity>()
                .eq(JobMatchEntity::getUserId, userId).eq(JobMatchEntity::getJobId, jobId)
                .eq(JobMatchEntity::getStatus, "SUCCEEDED");
        if (publicId != null && !publicId.isBlank()) query.eq(JobMatchEntity::getPublicId, publicId);
        else query.orderByDesc(JobMatchEntity::getEvaluatedAt).orderByDesc(JobMatchEntity::getId).last("LIMIT 1");
        JobMatchEntity result = matchMapper.selectOne(query);
        if (publicId != null && !publicId.isBlank() && result == null) throw new ResourceNotFoundException("Job match");
        return result;
    }

    public ResumeVersionEntity resumeVersion(Long userId, String publicId) {
        if (publicId == null || publicId.isBlank()) {
            ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                    .eq(ResumeEntity::getUserId, userId).eq(ResumeEntity::getDefaultResume, true)
                    .eq(ResumeEntity::getStatus, "ACTIVE").isNotNull(ResumeEntity::getCurrentVersionId).last("LIMIT 1"));
            if (resume == null) throw new ResourceNotFoundException("Default resume version");
            ResumeVersionEntity result = versionMapper.selectById(resume.getCurrentVersionId());
            if (result == null) throw new ResourceNotFoundException("Default resume version");
            return result;
        }
        ResumeVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getPublicId, publicId).eq(ResumeVersionEntity::getActive, true).last("LIMIT 1"));
        if (version == null) throw new ResourceNotFoundException("Resume version");
        ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, version.getResumeId()).eq(ResumeEntity::getUserId, userId).last("LIMIT 1"));
        if (resume == null) throw new ResourceNotFoundException("Resume version");
        return version;
    }

    public ResumeVersionEntity resumeVersionById(Long userId, Long id) {
        ResumeVersionEntity version = versionMapper.selectById(id);
        if (version == null) throw new ResourceNotFoundException("Resume version");
        ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, version.getResumeId()).eq(ResumeEntity::getUserId, userId).last("LIMIT 1"));
        if (resume == null) throw new ResourceNotFoundException("Resume version");
        return version;
    }

    public String matchPublicId(Long userId, Long id) {
        if (id == null) return null;
        JobMatchEntity match = matchMapper.selectOne(new LambdaQueryWrapper<JobMatchEntity>()
                .eq(JobMatchEntity::getId, id).eq(JobMatchEntity::getUserId, userId).last("LIMIT 1"));
        return match == null ? null : match.getPublicId();
    }

    public RecruiterEntity recruiter(Long userId, String publicId, boolean optional) {
        if (publicId == null || publicId.isBlank()) return null;
        RecruiterEntity result = recruiterMapper.selectOne(new LambdaQueryWrapper<RecruiterEntity>()
                .eq(RecruiterEntity::getUserId, userId).eq(RecruiterEntity::getPublicId, publicId).last("LIMIT 1"));
        if (result == null && !optional) throw new ResourceNotFoundException("Recruiter");
        if (result == null) throw new ResourceNotFoundException("Recruiter");
        return result;
    }

    public RecruiterEntity recruiterById(Long userId, Long id) {
        if (id == null) return null;
        RecruiterEntity result = recruiterMapper.selectOne(new LambdaQueryWrapper<RecruiterEntity>()
                .eq(RecruiterEntity::getUserId, userId).eq(RecruiterEntity::getId, id).last("LIMIT 1"));
        if (result == null) throw new ResourceNotFoundException("Recruiter");
        return result;
    }

    public QueueJobView jobView(JobEntity job, JobSourceEntity source) {
        CompanyEntity company = job.getCompanyId() == null ? null : companyMapper.selectById(job.getCompanyId());
        return new QueueJobView(job.getPublicId(), job.getTitle(),
                company == null ? null : company.getDisplayName(), job.getCity(),
                source == null ? "UNKNOWN" : source.getPlatform(),
                source == null ? null : source.getJobUrl(), job.getStatus());
    }

    public QueueResumeView resumeView(ResumeVersionEntity version) {
        ResumeEntity resume = resumeMapper.selectById(version.getResumeId());
        return new QueueResumeView(version.getPublicId(), resume == null ? null : resume.getPublicId(),
                resume == null ? null : resume.getName(), version.getVersionNumber(), version.getVersionName());
    }
}
