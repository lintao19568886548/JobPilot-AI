package com.jobpilot.matching.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateProfileEntity;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.ProjectEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.ProjectMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.JobSkillMapper;
import com.jobpilot.matching.domain.SkillRelationEntity;
import com.jobpilot.matching.dto.MatchingDtos.EvidenceItemView;
import com.jobpilot.matching.mapper.SkillRelationMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class ScoringService {
    private static final BigDecimal HUNDRED = new BigDecimal("100.00");
    private final CandidateSkillMapper candidateSkillMapper;
    private final ProjectMapper projectMapper;
    private final SkillMapper skillMapper;
    private final JobSkillMapper jobSkillMapper;
    private final SkillRelationMapper relationMapper;
    private final JsonCodec json;

    public ScoringService(CandidateSkillMapper candidateSkillMapper, ProjectMapper projectMapper, SkillMapper skillMapper,
                          JobSkillMapper jobSkillMapper, SkillRelationMapper relationMapper, JsonCodec json) {
        this.candidateSkillMapper = candidateSkillMapper; this.projectMapper = projectMapper; this.skillMapper = skillMapper;
        this.jobSkillMapper = jobSkillMapper; this.relationMapper = relationMapper; this.json = json;
    }

    public DeterministicScores score(Long userId, CandidateProfileEntity profile, JobEntity job, CompanyEntity company) {
        List<CandidateSkillEntity> candidateSkills = candidateSkillMapper.selectList(new LambdaQueryWrapper<CandidateSkillEntity>()
                .eq(CandidateSkillEntity::getUserId, userId));
        List<ProjectEntity> projects = projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId).orderByDesc(ProjectEntity::getFeatured));
        List<JobSkillEntity> jobSkills = jobSkillMapper.selectList(new LambdaQueryWrapper<JobSkillEntity>().eq(JobSkillEntity::getJobId, job.getId()));
        Set<Long> skillIds = new java.util.HashSet<>();
        candidateSkills.forEach(skill -> skillIds.add(skill.getSkillId())); jobSkills.forEach(skill -> skillIds.add(skill.getSkillId()));
        Map<Long, SkillEntity> catalog = skillIds.isEmpty() ? Map.of() : skillMapper.selectBatchIds(skillIds).stream().collect(Collectors.toMap(SkillEntity::getId, value -> value));
        List<SkillRelationEntity> relations = relationMapper.selectList(new LambdaQueryWrapper<SkillRelationEntity>());
        DimensionResult skill = skillScore(candidateSkills, jobSkills, catalog, relations);
        DimensionResult project = projectScore(projects, jobSkills, catalog);
        DimensionResult preference = preferenceScore(profile, job);
        DimensionResult companyScore = companyScore(profile, company);
        List<EvidenceItemView> advantages = new ArrayList<>();
        List<EvidenceItemView> gaps = new ArrayList<>();
        advantages.addAll(skill.advantages()); advantages.addAll(project.advantages());
        gaps.addAll(skill.gaps()); gaps.addAll(project.gaps());
        List<DetailDraft> details = new ArrayList<>();
        details.addAll(skill.details()); details.addAll(project.details()); details.addAll(preference.details()); details.addAll(companyScore.details());
        return new DeterministicScores(skill.score(), project.score(), preference.score(), companyScore.score(),
                advantages, gaps, new ArrayList<>(), details, candidateSkills, projects, jobSkills, catalog);
    }

    private DimensionResult skillScore(List<CandidateSkillEntity> candidateSkills, List<JobSkillEntity> jobSkills,
                                       Map<Long, SkillEntity> catalog, List<SkillRelationEntity> relations) {
        if (jobSkills.isEmpty()) return neutral("SKILL", "no_job_skills", "The job has no structured skill requirements");
        Map<Long, CandidateSkillEntity> exact = candidateSkills.stream().collect(Collectors.toMap(CandidateSkillEntity::getSkillId, value -> value, (a,b)->a));
        Map<String, SkillRelationEntity> relationMap = new HashMap<>();
        for (SkillRelationEntity relation : relations) relationMap.put(relation.getFromSkillId() + ":" + relation.getToSkillId(), relation);
        BigDecimal numerator = BigDecimal.ZERO, denominator = BigDecimal.ZERO;
        List<EvidenceItemView> advantages = new ArrayList<>(), gaps = new ArrayList<>();
        List<DetailDraft> details = new ArrayList<>();
        for (JobSkillEntity required : jobSkills) {
            BigDecimal importance = BigDecimal.valueOf(required.getImportance() == null ? 50 : required.getImportance());
            BigDecimal multiplier = switch (required.getRequirementType()) { case "MUST_HAVE" -> new BigDecimal("1.50"); case "NICE_TO_HAVE" -> BigDecimal.ONE; default -> new BigDecimal("0.50"); };
            BigDecimal weight = importance.multiply(multiplier);
            denominator = denominator.add(weight);
            CandidateSkillEntity matched = exact.get(required.getSkillId());
            BigDecimal relationWeight = BigDecimal.ONE;
            String matchKind = "EXACT";
            if (matched == null) {
                relationWeight = BigDecimal.ZERO;
                for (CandidateSkillEntity candidate : candidateSkills) {
                    SkillRelationEntity relation = relationMap.get(candidate.getSkillId() + ":" + required.getSkillId());
                    if (relation != null && (matched == null || relation.getWeight().compareTo(relationWeight) > 0)) {
                        matched = candidate; relationWeight = relation.getWeight(); matchKind = "ONTOLOGY_" + relation.getRelationType();
                    }
                }
            }
            SkillEntity requiredSkill = catalog.get(required.getSkillId());
            String display = requiredSkill == null ? String.valueOf(required.getSkillId()) : requiredSkill.getDisplayName();
            if (matched != null) {
                BigDecimal ratio = BigDecimal.valueOf(matched.getProficiency()).divide(HUNDRED, 6, RoundingMode.HALF_UP).multiply(relationWeight);
                numerator = numerator.add(weight.multiply(ratio));
                String reference = "skill:" + matched.getPublicId();
                advantages.add(new EvidenceItemView(display + " matches the job requirement", List.of(reference), required.getEvidenceText(), "LOW"));
                details.add(new DetailDraft("SKILL", requiredSkill == null ? display : requiredSkill.getCanonicalName(), reference, required.getEvidenceText(),
                        ratio.multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP), matchKind, display + " matched using " + matchKind.toLowerCase(Locale.ROOT)));
            } else {
                String severity = "MUST_HAVE".equals(required.getRequirementType()) ? "HIGH" : "MEDIUM";
                gaps.add(new EvidenceItemView("Missing " + display, List.of(), required.getEvidenceText(), severity));
                details.add(new DetailDraft("SKILL", requiredSkill == null ? display : requiredSkill.getCanonicalName(), null, required.getEvidenceText(), BigDecimal.ZERO,
                        "MISSING", "No confirmed candidate skill evidence was found"));
            }
        }
        BigDecimal score = denominator.signum() == 0 ? new BigDecimal("50.00") : numerator.divide(denominator, 6, RoundingMode.HALF_UP).multiply(HUNDRED);
        return new DimensionResult(bound(score), advantages, gaps, details);
    }

    private DimensionResult projectScore(List<ProjectEntity> projects, List<JobSkillEntity> jobSkills, Map<Long, SkillEntity> catalog) {
        if (jobSkills.isEmpty()) return neutral("PROJECT", "no_job_skills", "Project score is neutral because the job has no structured skills");
        int matchedCount = 0;
        List<EvidenceItemView> advantages = new ArrayList<>(), gaps = new ArrayList<>();
        List<DetailDraft> details = new ArrayList<>();
        for (JobSkillEntity required : jobSkills) {
            SkillEntity skill = catalog.get(required.getSkillId());
            if (skill == null) continue;
            ProjectEntity matched = projects.stream().filter(project -> projectText(project).contains(skill.getDisplayName().toLowerCase(Locale.ROOT))
                    || projectText(project).contains(skill.getCanonicalName().replace('_',' ').toLowerCase(Locale.ROOT))).findFirst().orElse(null);
            if (matched != null) {
                matchedCount++;
                String reference = "project:" + matched.getPublicId();
                advantages.add(new EvidenceItemView(matched.getName() + " provides project evidence for " + skill.getDisplayName(), List.of(reference), required.getEvidenceText(), "LOW"));
                details.add(new DetailDraft("PROJECT", skill.getCanonicalName(), reference, required.getEvidenceText(), HUNDRED, "EVIDENCED", "Project technology or description contains the required skill"));
            } else if ("MUST_HAVE".equals(required.getRequirementType())) {
                gaps.add(new EvidenceItemView("No project evidence for " + skill.getDisplayName(), List.of(), required.getEvidenceText(), "MEDIUM"));
                details.add(new DetailDraft("PROJECT", skill.getCanonicalName(), null, required.getEvidenceText(), BigDecimal.ZERO, "NO_EVIDENCE", "No project fact supports this required skill"));
            }
        }
        return new DimensionResult(bound(BigDecimal.valueOf(matchedCount).multiply(HUNDRED).divide(BigDecimal.valueOf(jobSkills.size()), 2, RoundingMode.HALF_UP)), advantages, gaps, details);
    }

    private DimensionResult preferenceScore(CandidateProfileEntity profile, JobEntity job) {
        List<BigDecimal> parts = new ArrayList<>(); List<DetailDraft> details = new ArrayList<>(); String ref = "profile:" + profile.getPublicId();
        List<String> cities = json.readStringList(profile.getTargetCitiesJson());
        if (!cities.isEmpty() && job.getCity() != null) addPreference(parts, details, "city", cities.stream().anyMatch(city -> city.equalsIgnoreCase(job.getCity())), ref, job.getCity());
        List<String> roles = json.readStringList(profile.getTargetRolesJson());
        if (!roles.isEmpty()) addPreference(parts, details, "target_role", roles.stream().anyMatch(role -> job.getTitle().toLowerCase(Locale.ROOT).contains(role.toLowerCase(Locale.ROOT))), ref, job.getTitle());
        if (profile.getTargetSalaryMin() != null && job.getSalaryMax() != null) addPreference(parts, details, "salary", job.getSalaryMax().compareTo(profile.getTargetSalaryMin()) >= 0, ref, job.getSalaryText());
        if ("REMOTE".equals(job.getRemoteType())) addPreference(parts, details, "remote", Boolean.TRUE.equals(profile.getAcceptRemote()), ref, job.getRemoteType());
        BigDecimal score = parts.isEmpty() ? new BigDecimal("50.00") : parts.stream().reduce(BigDecimal.ZERO, BigDecimal::add).divide(BigDecimal.valueOf(parts.size()), 2, RoundingMode.HALF_UP);
        return new DimensionResult(score, List.of(), List.of(), details);
    }

    private DimensionResult companyScore(CandidateProfileEntity profile, CompanyEntity company) {
        if (company == null) return neutral("COMPANY", "company_missing", "Company metadata is unavailable");
        List<BigDecimal> parts = new ArrayList<>(); List<DetailDraft> details = new ArrayList<>(); String ref="profile:"+profile.getPublicId();
        List<String> industries = json.readStringList(profile.getTargetIndustriesJson());
        if (!industries.isEmpty() && company.getIndustry()!=null) addPreference(parts, details, "industry", industries.stream().anyMatch(value->value.equalsIgnoreCase(company.getIndustry())), ref, company.getIndustry());
        List<String> types = json.readStringList(profile.getTargetCompanyTypesJson());
        if (!types.isEmpty() && (company.getCompanySize()!=null || company.getFinancingStage()!=null)) {
            boolean matched=types.stream().anyMatch(value->value.equalsIgnoreCase(company.getCompanySize())||value.equalsIgnoreCase(company.getFinancingStage()));
            addPreference(parts, details, "company_type", matched, ref, String.join(" / ",safe(company.getFinancingStage()),safe(company.getCompanySize())).trim());
        }
        boolean hasRisk = company.getRiskFlagsJson()!=null && !company.getRiskFlagsJson().equals("[]") && !company.getRiskFlagsJson().equals("{}");
        parts.add(hasRisk ? new BigDecimal("20.00") : new BigDecimal("80.00"));
        details.add(new DetailDraft("COMPANY", "risk_flags", null, company.getDisplayName(), hasRisk?new BigDecimal("20.00"):new BigDecimal("80.00"), hasRisk?"RISK":"CLEAR", hasRisk?"Company has recorded risk flags":"No company risk flags are recorded"));
        BigDecimal score=parts.stream().reduce(BigDecimal.ZERO,BigDecimal::add).divide(BigDecimal.valueOf(parts.size()),2,RoundingMode.HALF_UP);
        return new DimensionResult(score,List.of(),List.of(),details);
    }

    private void addPreference(List<BigDecimal> parts,List<DetailDraft> details,String key,boolean matched,String ref,String evidence){BigDecimal score=matched?HUNDRED:BigDecimal.ZERO;parts.add(score);details.add(new DetailDraft("PREFERENCE",key,ref,evidence,score,matched?"MATCH":"MISMATCH",matched?"Candidate preference matches":"Candidate preference does not match"));}
    private String projectText(ProjectEntity project) { return String.join(" ", safe(project.getName()), safe(project.getDescription()), safe(project.getResponsibilities()), safe(project.getAchievements()), safe(project.getTechnologiesJson())).toLowerCase(Locale.ROOT); }
    private String safe(String value){return value==null?"":value;}
    private BigDecimal bound(BigDecimal value){return value.max(BigDecimal.ZERO).min(HUNDRED).setScale(2,RoundingMode.HALF_UP);}
    private DimensionResult neutral(String dimension,String key,String explanation){return new DimensionResult(new BigDecimal("50.00"),List.of(),List.of(),List.of(new DetailDraft(dimension,key,null,null,new BigDecimal("50.00"),"NEUTRAL",explanation)));}

    public record DeterministicScores(BigDecimal skillScore, BigDecimal projectScore, BigDecimal preferenceScore, BigDecimal companyScore,
            List<EvidenceItemView> advantages, List<EvidenceItemView> gaps, List<EvidenceItemView> risks, List<DetailDraft> details,
            List<CandidateSkillEntity> candidateSkills, List<ProjectEntity> projects, List<JobSkillEntity> jobSkills, Map<Long, SkillEntity> catalog) { }
    private record DimensionResult(BigDecimal score, List<EvidenceItemView> advantages, List<EvidenceItemView> gaps, List<DetailDraft> details) { }
    public record DetailDraft(String dimension, String itemKey, String candidateEvidenceRef, String jobEvidence, BigDecimal score, String decision, String explanation) { }
}
