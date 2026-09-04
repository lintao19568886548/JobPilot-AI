package com.jobpilot.candidate.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.dto.CandidateDtos.CompletenessView;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileRequest;
import com.jobpilot.candidate.dto.CandidateDtos.ProfileView;
import com.jobpilot.candidate.mapper.CandidateProfileMapper;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateProfileService {

    private final CandidateProfileMapper profileMapper;
    private final CandidateProfileCompletenessService completenessService;
    private final JsonCodec jsonCodec;
    private final AuditService auditService;

    public CandidateProfileService(
            CandidateProfileMapper profileMapper,
            CandidateProfileCompletenessService completenessService,
            JsonCodec jsonCodec,
            AuditService auditService) {
        this.profileMapper = profileMapper;
        this.completenessService = completenessService;
        this.jsonCodec = jsonCodec;
        this.auditService = auditService;
    }

    @Transactional
    public ProfileView get(Long userId) {
        CandidateProfileEntity profile = getOrCreate(userId);
        refreshCompleteness(profile);
        return toView(profile);
    }

    @Transactional
    public ProfileView replace(Long userId, ProfileRequest request) {
        validateSalary(request.targetSalaryMin(), request.targetSalaryMax());
        CandidateProfileEntity profile = getOrCreate(userId);
        apply(profile, request, true);
        refreshCompleteness(profile);
        requireUpdated(profileMapper.updateById(profile));
        auditService.record(userId, "PROFILE_UPDATE", "CANDIDATE_PROFILE", profile.getPublicId());
        return toView(profile);
    }

    @Transactional
    public ProfileView patch(Long userId, ProfileRequest request) {
        CandidateProfileEntity profile = getOrCreate(userId);
        BigDecimal min = request.targetSalaryMin() == null ? profile.getTargetSalaryMin() : request.targetSalaryMin();
        BigDecimal max = request.targetSalaryMax() == null ? profile.getTargetSalaryMax() : request.targetSalaryMax();
        validateSalary(min, max);
        apply(profile, request, false);
        refreshCompleteness(profile);
        requireUpdated(profileMapper.updateById(profile));
        auditService.record(userId, "PROFILE_UPDATE", "CANDIDATE_PROFILE", profile.getPublicId());
        return toView(profile);
    }

    @Transactional
    public CompletenessView completeness(Long userId) {
        CandidateProfileEntity profile = getOrCreate(userId);
        Map<String, Integer> breakdown = completenessService.breakdown(profile);
        int score = breakdown.values().stream().mapToInt(Integer::intValue).sum();
        if (!Integer.valueOf(score).equals(profile.getProfileCompleteness())) {
            profile.setProfileCompleteness(score);
            requireUpdated(profileMapper.updateById(profile));
        }
        return new CompletenessView(score, breakdown);
    }

    @Transactional
    public CandidateProfileEntity getOrCreate(Long userId) {
        CandidateProfileEntity existing = profileMapper.selectOne(new LambdaQueryWrapper<CandidateProfileEntity>()
                .eq(CandidateProfileEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }
        CandidateProfileEntity profile = new CandidateProfileEntity();
        profile.setUserId(userId);
        profile.setTargetCitiesJson("[]");
        profile.setTargetRolesJson("[]");
        profile.setTargetIndustriesJson("[]");
        profile.setTargetCompanyTypesJson("[]");
        profile.setSalaryCurrency("CNY");
        profile.setAcceptRemote(false);
        profile.setAcceptRelocation(false);
        profile.setProfileCompleteness(0);
        profileMapper.insert(profile);
        return profile;
    }

    @Transactional
    public void refreshCompleteness(Long userId) {
        CandidateProfileEntity profile = getOrCreate(userId);
        int before = profile.getProfileCompleteness() == null ? 0 : profile.getProfileCompleteness();
        refreshCompleteness(profile);
        if (before != profile.getProfileCompleteness()) {
            requireUpdated(profileMapper.updateById(profile));
        }
    }

    private void refreshCompleteness(CandidateProfileEntity profile) {
        profile.setProfileCompleteness(completenessService.score(profile));
    }

    private void apply(CandidateProfileEntity profile, ProfileRequest request, boolean replace) {
        set(profile::setFullName, request.fullName(), replace);
        set(profile::setHeadline, request.headline(), replace);
        set(profile::setPhone, request.phone(), replace);
        set(profile::setEmail, request.email(), replace);
        set(profile::setCurrentCity, request.currentCity(), replace);
        set(profile::setGraduationYear, request.graduationYear(), replace);
        set(profile::setHighestEducation, request.highestEducation(), replace);
        set(profile::setSchool, request.school(), replace);
        set(profile::setMajor, request.major(), replace);
        set(profile::setYearsOfExperience, request.yearsOfExperience(), replace);
        set(profile::setJobStatus, request.jobStatus(), replace);
        set(profile::setGithubUrl, request.githubUrl(), replace);
        set(profile::setPersonalWebsite, request.personalWebsite(), replace);
        set(profile::setSummary, request.summary(), replace);
        set(profile::setTargetSalaryMin, request.targetSalaryMin(), replace);
        set(profile::setTargetSalaryMax, request.targetSalaryMax(), replace);
        set(profile::setSalaryCurrency, request.salaryCurrency(), replace);
        set(profile::setAcceptRemote, request.acceptRemote(), replace);
        set(profile::setAcceptRelocation, request.acceptRelocation(), replace);
        if (replace || request.targetCities() != null) {
            profile.setTargetCitiesJson(jsonCodec.write(cleanList(request.targetCities())));
        }
        if (replace || request.targetRoles() != null) {
            profile.setTargetRolesJson(jsonCodec.write(cleanList(request.targetRoles())));
        }
        if (replace || request.targetIndustries() != null) {
            profile.setTargetIndustriesJson(jsonCodec.write(cleanList(request.targetIndustries())));
        }
        if (replace || request.targetCompanyTypes() != null) {
            profile.setTargetCompanyTypesJson(jsonCodec.write(cleanList(request.targetCompanyTypes())));
        }
        if (replace && profile.getSalaryCurrency() == null) {
            profile.setSalaryCurrency("CNY");
        }
        if (replace && profile.getAcceptRemote() == null) {
            profile.setAcceptRemote(false);
        }
        if (replace && profile.getAcceptRelocation() == null) {
            profile.setAcceptRelocation(false);
        }
    }

    private List<String> cleanList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream().map(String::trim).filter(value -> !value.isBlank()).distinct().toList();
    }

    private <T> void set(java.util.function.Consumer<T> setter, T value, boolean replace) {
        if (replace || value != null) {
            setter.accept(value);
        }
    }

    private void validateSalary(BigDecimal min, BigDecimal max) {
        if (min != null && max != null && min.compareTo(max) > 0) {
            throw new ValidationException("targetSalaryMin must not exceed targetSalaryMax");
        }
    }

    private void requireUpdated(int rows) {
        if (rows != 1) {
            throw new ValidationException("Profile was modified concurrently; reload and retry");
        }
    }

    private ProfileView toView(CandidateProfileEntity profile) {
        return new ProfileView(
                profile.getPublicId(), profile.getFullName(), profile.getHeadline(), profile.getPhone(), profile.getEmail(),
                profile.getCurrentCity(), jsonCodec.readStringList(profile.getTargetCitiesJson()), profile.getGraduationYear(),
                profile.getHighestEducation(), profile.getSchool(), profile.getMajor(), profile.getYearsOfExperience(),
                profile.getJobStatus(), profile.getGithubUrl(), profile.getPersonalWebsite(), profile.getSummary(),
                jsonCodec.readStringList(profile.getTargetRolesJson()),
                jsonCodec.readStringList(profile.getTargetIndustriesJson()),
                jsonCodec.readStringList(profile.getTargetCompanyTypesJson()),
                profile.getTargetSalaryMin(), profile.getTargetSalaryMax(), profile.getSalaryCurrency(),
                profile.getAcceptRemote(), profile.getAcceptRelocation(),
                profile.getProfileCompleteness() == null ? 0 : profile.getProfileCompleteness(),
                profile.getVersion() == null ? 0 : profile.getVersion(), profile.getCreatedAt(), profile.getUpdatedAt());
    }
}

