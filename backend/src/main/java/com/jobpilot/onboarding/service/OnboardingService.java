package com.jobpilot.onboarding.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileView;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.service.CandidateProfileService;
import com.jobpilot.onboarding.dto.OnboardingDtos.DataQualityView;
import com.jobpilot.onboarding.dto.OnboardingDtos.OnboardingOverviewView;
import com.jobpilot.onboarding.dto.OnboardingDtos.QualityIssueView;
import com.jobpilot.onboarding.dto.OnboardingDtos.QualitySummaryView;
import com.jobpilot.onboarding.dto.OnboardingDtos.ReadinessStepView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.service.ResumeService;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OnboardingService {

    public static final int READY_SCORE = 80;
    private static final int TARGET_SKILLS = 5;
    private static final int TARGET_PRIMARY_SKILLS = 2;
    private static final int TARGET_PROJECTS = 2;

    private final CandidateProfileService profileService;
    private final CandidateSkillMapper skillMapper;
    private final EducationMapper educationMapper;
    private final ExperienceMapper experienceMapper;
    private final ProjectMapper projectMapper;
    private final ResumeService resumeService;

    public OnboardingService(
            CandidateProfileService profileService,
            CandidateSkillMapper skillMapper,
            EducationMapper educationMapper,
            ExperienceMapper experienceMapper,
            ProjectMapper projectMapper,
            ResumeService resumeService) {
        this.profileService = profileService;
        this.skillMapper = skillMapper;
        this.educationMapper = educationMapper;
        this.experienceMapper = experienceMapper;
        this.projectMapper = projectMapper;
        this.resumeService = resumeService;
    }

    @Transactional
    public OnboardingOverviewView overview(Long userId) {
        Facts facts = facts(userId);
        List<ReadinessStepView> steps = steps(facts);
        List<QualityIssueView> issues = issues(facts);
        int score = steps.stream().mapToInt(ReadinessStepView::earnedScore).sum();
        QualitySummaryView summary = summary(issues);
        boolean ready = score >= READY_SCORE && summary.blockers() == 0;
        return new OnboardingOverviewView(
                score,
                ready ? "READY" : "NEEDS_WORK",
                ready,
                steps.stream().filter(step -> "COMPLETE".equals(step.status())).count(),
                steps.size(),
                steps,
                summary,
                Instant.now());
    }

    @Transactional
    public DataQualityView dataQuality(Long userId) {
        List<QualityIssueView> issues = issues(facts(userId));
        return new DataQualityView(summary(issues), issues, Instant.now());
    }

    private Facts facts(Long userId) {
        ProfileView profile = profileService.get(userId);
        long skills = skillMapper.selectCount(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId));
        long primarySkills = skillMapper.selectCount(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId)
                .eq(CandidateSkillEntity::getPrimarySkill, true));
        long educations = educationMapper.selectCount(new LambdaQueryWrapper<EducationEntity>()
                .eq(EducationEntity::getUserId, userId));
        long experiences = experienceMapper.selectCount(new LambdaQueryWrapper<ExperienceEntity>()
                .eq(ExperienceEntity::getUserId, userId));
        long projects = projectMapper.selectCount(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId));
        List<ResumeSummaryView> resumes = resumeService.list(userId);
        long activeMasters = resumes.stream()
                .filter(item -> Boolean.TRUE.equals(item.master()) && "ACTIVE".equals(item.status()))
                .count();
        long activeDefaults = resumes.stream()
                .filter(item -> Boolean.TRUE.equals(item.defaultResume()) && "ACTIVE".equals(item.status()))
                .count();
        long currentVersions = resumes.stream().filter(item -> item.currentVersionId() != null).count();
        return new Facts(profile, skills, primarySkills, educations, experiences, projects,
                activeMasters, activeDefaults, currentVersions);
    }

    private List<ReadinessStepView> steps(Facts facts) {
        ProfileView profile = facts.profile();
        long identity = countPresent(profile.fullName(), profile.email(), profile.headline(), profile.currentCity());
        long preferences = (profile.targetRoles().isEmpty() ? 0 : 1)
                + (profile.targetCities().isEmpty() ? 0 : 1)
                + (profile.targetSalaryMin() == null || profile.targetSalaryMax() == null ? 0 : 1);
        int skillScore = proportional(facts.skills(), TARGET_SKILLS, 15)
                + proportional(facts.primarySkills(), TARGET_PRIMARY_SKILLS, 5);
        List<ReadinessStepView> result = new ArrayList<>();
        result.add(step("IDENTITY", identity, 4, 15, proportional(identity, 4, 15),
                "基本身份", "姓名、邮箱、职业标题和当前城市", "/candidate", true));
        result.add(step("CAREER_TARGET", preferences, 3, 15, proportional(preferences, 3, 15),
                "求职目标", "目标岗位、目标城市和薪资范围", "/candidate", false));
        result.add(step("SKILLS", facts.skills(), TARGET_SKILLS, 20, skillScore,
                "技能证据", "至少 5 项技能，其中至少 2 项标记为核心技能；当前核心技能 " + facts.primarySkills(),
                "/candidate", false));
        result.add(step("EDUCATION", facts.educations(), 1, 10, proportional(facts.educations(), 1, 10),
                "教育经历", "至少保存一条真实教育经历", "/candidate", false));
        result.add(step("EXPERIENCE", facts.experiences(), 1, 10, proportional(facts.experiences(), 1, 10),
                "工作或实习", "至少保存一条真实工作、实习或兼职经历", "/candidate", false));
        result.add(step("PROJECTS", facts.projects(), TARGET_PROJECTS, 10,
                proportional(facts.projects(), TARGET_PROJECTS, 10), "项目证据", "至少保存两个可用于匹配的真实项目",
                "/candidate", false));
        result.add(step("MASTER_RESUME", facts.activeMasters(), 1, 10,
                proportional(facts.activeMasters(), 1, 10), "主简历", "需要一份已启用的主简历作为事实来源",
                "/resumes", true));
        result.add(step("DEFAULT_RESUME", facts.activeDefaults(), 1, 5,
                proportional(facts.activeDefaults(), 1, 5), "默认简历", "需要一份已启用的默认简历",
                "/resumes", false));
        result.add(step("RESUME_VERSION", facts.currentVersions(), 1, 5,
                proportional(facts.currentVersions(), 1, 5), "简历版本", "至少一份简历需要当前不可变版本",
                "/resumes", true));
        return List.copyOf(result);
    }

    private List<QualityIssueView> issues(Facts facts) {
        ProfileView profile = facts.profile();
        List<QualityIssueView> result = new ArrayList<>();
        if (!present(profile.fullName())) {
            result.add(issue("PROFILE_NAME_MISSING", "BLOCKER", "缺少姓名", "姓名是简历事实与申请材料的必要字段。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (!present(profile.email())) {
            result.add(issue("PROFILE_EMAIL_MISSING", "BLOCKER", "缺少邮箱", "邮箱是候选人基本联系方式。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (facts.activeMasters() == 0) {
            result.add(issue("MASTER_RESUME_MISSING", "BLOCKER", "缺少已启用的主简历", "AI 只能从主简历选择已确认事实。", "RESUME", "/resumes"));
        }
        if (facts.currentVersions() == 0) {
            result.add(issue("RESUME_VERSION_MISSING", "BLOCKER", "缺少当前简历版本", "创建不可变简历版本后才能追踪投递材料。", "RESUME_VERSION", "/resumes"));
        }
        if (!present(profile.headline())) {
            result.add(issue("PROFILE_HEADLINE_MISSING", "WARNING", "缺少职业标题", "职业标题可帮助岗位方向和匹配解释保持一致。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (profile.targetRoles().isEmpty()) {
            result.add(issue("TARGET_ROLE_MISSING", "WARNING", "缺少目标岗位", "至少设置一个目标岗位。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (profile.targetCities().isEmpty()) {
            result.add(issue("TARGET_CITY_MISSING", "WARNING", "缺少目标城市", "至少设置一个目标城市。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (profile.targetSalaryMin() == null || profile.targetSalaryMax() == null) {
            result.add(issue("TARGET_SALARY_MISSING", "WARNING", "薪资范围不完整", "同时填写最低和最高期望薪资。", "CANDIDATE_PROFILE", "/candidate"));
        }
        if (facts.skills() < TARGET_SKILLS) {
            result.add(issue("SKILLS_BELOW_MINIMUM", "WARNING", "技能证据不足", "至少维护 5 项结构化技能；当前 " + facts.skills() + " 项。", "CANDIDATE_SKILL", "/candidate"));
        }
        if (facts.primarySkills() < TARGET_PRIMARY_SKILLS) {
            result.add(issue("PRIMARY_SKILLS_BELOW_MINIMUM", "WARNING", "核心技能不足", "至少将 2 项真实优势标记为核心技能；当前 " + facts.primarySkills() + " 项。", "CANDIDATE_SKILL", "/candidate"));
        }
        if (facts.activeDefaults() == 0) {
            result.add(issue("DEFAULT_RESUME_MISSING", "WARNING", "缺少已启用的默认简历", "默认简历用于未显式选择版本时的安全回退。", "RESUME", "/resumes"));
        }
        if (facts.educations() == 0) {
            result.add(issue("EDUCATION_MISSING", "INFO", "尚无教育经历", "补充真实教育经历可完善候选人证据。", "EDUCATION", "/candidate"));
        }
        if (facts.experiences() == 0) {
            result.add(issue("EXPERIENCE_MISSING", "INFO", "尚无工作或实习经历", "如有经历，请按事实补充；系统不会自动生成。", "EXPERIENCE", "/candidate"));
        }
        if (facts.projects() == 0) {
            result.add(issue("PROJECT_MISSING", "INFO", "尚无项目经历", "项目技术栈和成果可作为匹配证据。", "PROJECT", "/candidate"));
        }
        return result.stream().sorted(Comparator
                .comparingInt((QualityIssueView item) -> severityOrder(item.severity()))
                .thenComparing(QualityIssueView::code)).toList();
    }

    private ReadinessStepView step(String key, long current, long target, int weight, int earned,
                                   String title, String description, String actionPath, boolean blocking) {
        String status = current >= target && earned == weight ? "COMPLETE" : current > 0 ? "IN_PROGRESS" : "MISSING";
        return new ReadinessStepView(key, status, current, target, weight, earned, title, description, actionPath, blocking);
    }

    private QualityIssueView issue(String code, String severity, String title, String description,
                                   String resourceType, String actionPath) {
        return new QualityIssueView(code, severity, title, description, resourceType, actionPath);
    }

    private QualitySummaryView summary(List<QualityIssueView> issues) {
        long blockers = issues.stream().filter(item -> "BLOCKER".equals(item.severity())).count();
        long warnings = issues.stream().filter(item -> "WARNING".equals(item.severity())).count();
        long info = issues.stream().filter(item -> "INFO".equals(item.severity())).count();
        return new QualitySummaryView(blockers, warnings, info, issues.size());
    }

    private int proportional(long current, long target, int weight) {
        return (int) Math.round(weight * Math.min(current, target) / (double) target);
    }

    private long countPresent(String... values) {
        long count = 0;
        for (String value : values) {
            if (present(value)) {
                count++;
            }
        }
        return count;
    }

    private boolean present(String value) {
        return value != null && !value.isBlank();
    }

    private int severityOrder(String severity) {
        return switch (severity) {
            case "BLOCKER" -> 0;
            case "WARNING" -> 1;
            default -> 2;
        };
    }

    private record Facts(
            ProfileView profile,
            long skills,
            long primarySkills,
            long educations,
            long experiences,
            long projects,
            long activeMasters,
            long activeDefaults,
            long currentVersions) {
    }
}
