package com.jobpilot.dashboard.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.mapper.CandidateProfileMapper;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.service.CandidateProfileService;
import com.jobpilot.dashboard.dto.DashboardView;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import com.jobpilot.resume.service.ResumeService;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobImportTaskEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobImportTaskMapper;
import com.jobpilot.job.mapper.JobMapper;
import java.time.LocalDateTime;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final CandidateProfileService profileService;
    private final CandidateProfileMapper profileMapper;
    private final CandidateSkillMapper skillMapper;
    private final ProjectMapper projectMapper;
    private final ExperienceMapper experienceMapper;
    private final EducationMapper educationMapper;
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ResumeService resumeService;
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final JobImportTaskMapper importTaskMapper;

    public DashboardService(
            CandidateProfileService profileService,
            CandidateProfileMapper profileMapper,
            CandidateSkillMapper skillMapper,
            ProjectMapper projectMapper,
            ExperienceMapper experienceMapper,
            EducationMapper educationMapper,
            ResumeMapper resumeMapper,
            ResumeVersionMapper versionMapper,
            ResumeService resumeService,
            JobMapper jobMapper,
            CompanyMapper companyMapper,
            JobImportTaskMapper importTaskMapper) {
        this.profileService = profileService;
        this.profileMapper = profileMapper;
        this.skillMapper = skillMapper;
        this.projectMapper = projectMapper;
        this.experienceMapper = experienceMapper;
        this.educationMapper = educationMapper;
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.resumeService = resumeService;
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.importTaskMapper = importTaskMapper;
    }

    public DashboardView get(Long userId) {
        int completeness = profileService.completeness(userId).score();
        CandidateProfileEntity profile = profileMapper.selectOne(new LambdaQueryWrapper<CandidateProfileEntity>()
                .eq(CandidateProfileEntity::getUserId, userId).last("LIMIT 1"));
        long skills = skillMapper.selectCount(new LambdaQueryWrapper<CandidateSkillEntity>().eq(CandidateSkillEntity::getUserId, userId));
        long projects = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getUserId, userId));
        long experiences = experienceMapper.selectCount(new LambdaQueryWrapper<ExperienceEntity>().eq(ExperienceEntity::getUserId, userId));
        long educations = educationMapper.selectCount(new LambdaQueryWrapper<EducationEntity>().eq(EducationEntity::getUserId, userId));
        long resumes = resumeMapper.selectCount(new LambdaQueryWrapper<ResumeEntity>().eq(ResumeEntity::getUserId, userId));
        long versions = versionMapper.selectCount(new LambdaQueryWrapper<ResumeVersionEntity>()
                .inSql(ResumeVersionEntity::getResumeId, "SELECT id FROM resumes WHERE user_id = " + userId + " AND deleted_at IS NULL"));
        ResumeSummaryView defaultResume = resumeService.list(userId).stream()
                .filter(item -> Boolean.TRUE.equals(item.defaultResume()))
                .findFirst().orElse(null);
        LocalDateTime latestResume = resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .orderByDesc(ResumeEntity::getUpdatedAt)
                        .last("LIMIT 1"))
                .stream().findFirst().map(ResumeEntity::getUpdatedAt).orElse(null);
        long totalJobs = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId, userId));
        long activeJobs = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getStatus, "ACTIVE"));
        long parsedJobs = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).in(JobEntity::getParseStatus, "SUCCESS", "PARTIAL"));
        long parseFailedJobs = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getParseStatus, "FAILED"));
        long ignoredJobs = jobMapper.selectCount(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getStatus, "IGNORED"));
        long companies = companyMapper.selectCount(new LambdaQueryWrapper<CompanyEntity>()
                .inSql(CompanyEntity::getId, "SELECT DISTINCT company_id FROM jobs WHERE user_id = " + userId + " AND deleted_at IS NULL"));
        long imports = importTaskMapper.selectCount(new LambdaQueryWrapper<JobImportTaskEntity>().eq(JobImportTaskEntity::getUserId, userId));
        JobImportTaskEntity latestImport = importTaskMapper.selectOne(new LambdaQueryWrapper<JobImportTaskEntity>()
                .eq(JobImportTaskEntity::getUserId, userId).orderByDesc(JobImportTaskEntity::getCreatedAt).last("LIMIT 1"));
        JobEntity latestJob = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).orderByDesc(JobEntity::getUpdatedAt).last("LIMIT 1"));
        LocalDateTime lastUpdated = latest(latest(profile == null ? null : profile.getUpdatedAt(), latestResume),
                latestJob == null ? null : latestJob.getUpdatedAt());
        return new DashboardView(completeness, skills, projects, experiences, educations, resumes, versions,
                totalJobs, activeJobs, parsedJobs, parseFailedJobs, ignoredJobs, companies, imports,
                latestImport == null ? null : latestImport.getCreatedAt(), defaultResume, lastUpdated,
                "岗位匹配与推荐将在 Phase 3 上线");
    }

    private LocalDateTime latest(LocalDateTime first, LocalDateTime second) {
        if (first == null) {
            return second;
        }
        if (second == null) {
            return first;
        }
        return first.isAfter(second) ? first : second;
    }
}
