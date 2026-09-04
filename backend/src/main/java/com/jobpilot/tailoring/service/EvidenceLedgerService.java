package com.jobpilot.tailoring.service;

import static com.jobpilot.tailoring.dto.TailoringDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.EducationEntity;
import com.jobpilot.candidate.domain.ExperienceEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.CandidateProfileMapper;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.EducationMapper;
import com.jobpilot.candidate.mapper.ExperienceMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeSectionEntity;
import com.jobpilot.resume.mapper.ResumeSectionMapper;
import com.jobpilot.tailoring.domain.CandidateEvidenceItemEntity;
import com.jobpilot.tailoring.mapper.CandidateEvidenceItemMapper;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EvidenceLedgerService {
    private final CandidateEvidenceItemMapper mapper;
    private final CandidateProfileMapper profiles;
    private final EducationMapper educations;
    private final ExperienceMapper experiences;
    private final ProjectMapper projects;
    private final CandidateSkillMapper candidateSkills;
    private final SkillMapper skills;
    private final ResumeSectionMapper sections;
    private final TailoringReferenceService references;
    private final JsonCodec json;

    public EvidenceLedgerService(CandidateEvidenceItemMapper mapper, CandidateProfileMapper profiles,
                                 EducationMapper educations, ExperienceMapper experiences, ProjectMapper projects,
                                 CandidateSkillMapper candidateSkills, SkillMapper skills,
                                 ResumeSectionMapper sections, TailoringReferenceService references, JsonCodec json) {
        this.mapper = mapper; this.profiles = profiles; this.educations = educations; this.experiences = experiences;
        this.projects = projects; this.candidateSkills = candidateSkills; this.skills = skills;
        this.sections = sections; this.references = references; this.json = json;
    }

    @Transactional
    public EvidenceLedgerView refresh(Long userId) {
        CandidateProfileEntity profile = profiles.selectOne(new LambdaQueryWrapper<CandidateProfileEntity>()
                .eq(CandidateProfileEntity::getUserId, userId).last("LIMIT 1"));
        if (profile == null) throw new ResourceNotFoundException("Candidate profile");
        add(userId, "PROFILE", "CANDIDATE_PROFILE", profile.getPublicId(), "fullName", profile.getFullName());
        add(userId, "PROFILE", "CANDIDATE_PROFILE", profile.getPublicId(), "headline", profile.getHeadline());
        add(userId, "PROFILE", "CANDIDATE_PROFILE", profile.getPublicId(), "summary", profile.getSummary());
        add(userId, "PROFILE", "CANDIDATE_PROFILE", profile.getPublicId(), "education",
                compact(profile.getHighestEducation(), profile.getSchool(), profile.getMajor()));
        add(userId, "PROFILE", "CANDIDATE_PROFILE", profile.getPublicId(), "experienceYears",
                profile.getYearsOfExperience() == null ? null : profile.getYearsOfExperience().toPlainString() + " 年经验");

        for (EducationEntity item : educations.selectList(new LambdaQueryWrapper<EducationEntity>()
                .eq(EducationEntity::getUserId, userId).orderByAsc(EducationEntity::getSortOrder))) {
            add(userId, "EDUCATION", "EDUCATION", item.getPublicId(), "record",
                    compact(item.getSchool(), item.getDegree(), item.getMajor(), item.getGraduationYear(), item.getDescription()));
        }
        for (ExperienceEntity item : experiences.selectList(new LambdaQueryWrapper<ExperienceEntity>()
                .eq(ExperienceEntity::getUserId, userId).orderByAsc(ExperienceEntity::getSortOrder))) {
            add(userId, "EXPERIENCE", "EXPERIENCE", item.getPublicId(), "record",
                    compact(item.getCompanyName(), item.getRole(), item.getDescription(), item.getResponsibilities(),
                            item.getAchievements(), readableJson(item.getTechnologiesJson())));
        }
        for (ProjectEntity item : projects.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId).orderByDesc(ProjectEntity::getFeatured)
                .orderByAsc(ProjectEntity::getSortOrder))) {
            add(userId, "PROJECT", "PROJECT", item.getPublicId(), "record",
                    compact(item.getName(), item.getRole(), item.getDescription(), item.getResponsibilities(),
                            item.getAchievements(), readableJson(item.getTechnologiesJson())));
        }
        for (CandidateSkillEntity item : candidateSkills.selectList(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId).orderByDesc(CandidateSkillEntity::getPrimarySkill)
                .orderByDesc(CandidateSkillEntity::getProficiency))) {
            SkillEntity skill = skills.selectById(item.getSkillId());
            if (skill != null) add(userId, "SKILL", "CANDIDATE_SKILL", item.getPublicId(), "skill",
                    compact(skill.getDisplayName(), "熟练度 " + item.getProficiency(),
                            item.getYears() == null ? null : item.getYears().toPlainString() + " 年"));
        }
        ResumeEntity master = references.master(userId);
        for (ResumeSectionEntity section : sections.selectList(new LambdaQueryWrapper<ResumeSectionEntity>()
                .eq(ResumeSectionEntity::getResumeVersionId, master.getCurrentVersionId())
                .orderByAsc(ResumeSectionEntity::getSortOrder))) {
            add(userId, "RESUME_SECTION", "MASTER_RESUME_SECTION", section.getPublicId(),
                    section.getSectionType(), readableJson(section.getContentJson()));
        }
        return current(userId);
    }

    public EvidenceLedgerView current(Long userId) {
        List<CandidateEvidenceItemEntity> all = mapper.selectList(new LambdaQueryWrapper<CandidateEvidenceItemEntity>()
                .eq(CandidateEvidenceItemEntity::getUserId, userId).orderByDesc(CandidateEvidenceItemEntity::getVersionNo));
        Map<String, CandidateEvidenceItemEntity> latest = new LinkedHashMap<>();
        all.forEach(item -> latest.putIfAbsent(item.getEvidenceKey(), item));
        List<EvidenceView> views = latest.values().stream().map(this::view)
                .sorted(Comparator.comparing(EvidenceView::evidenceType).thenComparing(EvidenceView::evidenceKey)).toList();
        String snapshot = TailoringHash.sha256(views.stream().map(v -> v.evidenceRef() + ":" + v.contentHash())
                .sorted().collect(Collectors.joining("|")));
        LocalDateTime refreshed = views.stream().map(EvidenceView::createdAt).filter(Objects::nonNull)
                .max(LocalDateTime::compareTo).orElse(null);
        return new EvidenceLedgerView(views, views.size(), snapshot, refreshed);
    }

    private void add(Long userId, String type, String sourceType, String sourceId, String field, Object raw) {
        if (raw == null || raw.toString().isBlank()) return;
        String text = raw.toString().replaceAll("\\s+", " ").trim();
        String key = sourceType + ":" + sourceId + ":" + field;
        String valueJson = json.write(Map.of("text", text));
        String hash = TailoringHash.sha256(type + "|" + key + "|" + valueJson);
        CandidateEvidenceItemEntity same = mapper.selectOne(new LambdaQueryWrapper<CandidateEvidenceItemEntity>()
                .eq(CandidateEvidenceItemEntity::getUserId, userId)
                .eq(CandidateEvidenceItemEntity::getEvidenceKey, key)
                .eq(CandidateEvidenceItemEntity::getContentHash, hash).last("LIMIT 1"));
        if (same != null) return;
        CandidateEvidenceItemEntity latest = mapper.selectOne(new LambdaQueryWrapper<CandidateEvidenceItemEntity>()
                .eq(CandidateEvidenceItemEntity::getUserId, userId)
                .eq(CandidateEvidenceItemEntity::getEvidenceKey, key)
                .orderByDesc(CandidateEvidenceItemEntity::getVersionNo).last("LIMIT 1"));
        CandidateEvidenceItemEntity item = new CandidateEvidenceItemEntity();
        item.setUserId(userId); item.setEvidenceKey(key); item.setEvidenceType(type); item.setSourceType(sourceType);
        item.setSourcePublicId(sourceId); item.setFieldPath(field); item.setValueJson(valueJson);
        item.setNormalizedText(text); item.setContentHash(hash);
        item.setVersionNo(latest == null ? 1 : latest.getVersionNo() + 1);
        mapper.insert(item);
    }

    private EvidenceView view(CandidateEvidenceItemEntity item) {
        return new EvidenceView(item.getPublicId(), item.getEvidenceKey(), item.getEvidenceType(), item.getSourceType(),
                item.getSourcePublicId(), item.getFieldPath(), json.readNode(item.getValueJson()),
                item.getNormalizedText(), item.getContentHash(), item.getVersionNo(), item.getCreatedAt());
    }

    private String readableJson(String value) {
        if (value == null || value.isBlank()) return null;
        try { return String.join("、", json.readStringList(value)); }
        catch (Exception ignored) { return value; }
    }

    private static String compact(Object... values) {
        List<String> items = new ArrayList<>();
        for (Object value : values) if (value != null && !value.toString().isBlank()) items.add(value.toString().trim());
        return String.join("；", items);
    }
}
