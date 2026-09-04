package com.jobpilot.search.service;

import static com.jobpilot.search.dto.SearchDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.candidate.domain.CandidateSkillEntity;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.CandidateSkillMapper;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSkillMapper;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class GlobalSearchService {
    private static final Set<String> ALLOWED_TYPES = Set.of("JOB", "COMPANY", "SKILL", "STATUS");
    private static final Map<String, String> STATUS_LABELS = Map.ofEntries(
            Map.entry("ACTIVE", "有效岗位"), Map.entry("IGNORED", "已忽略"),
            Map.entry("UNEVALUATED", "未评估"), Map.entry("READY", "已评估"),
            Map.entry("MATCH_FAILED", "匹配失败"), Map.entry("FAVORITE", "已收藏"),
            Map.entry("S", "S 级匹配"), Map.entry("A", "A 级匹配"),
            Map.entry("B", "B 级匹配"), Map.entry("C", "C 级匹配"),
            Map.entry("D", "D 级匹配"));

    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final JobSkillMapper jobSkillMapper;
    private final SkillMapper skillMapper;
    private final CandidateSkillMapper candidateSkillMapper;
    private final RecommendationProperties properties;

    public GlobalSearchService(JobMapper jobMapper, CompanyMapper companyMapper,
                               JobSkillMapper jobSkillMapper, SkillMapper skillMapper,
                               CandidateSkillMapper candidateSkillMapper,
                               RecommendationProperties properties) {
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.jobSkillMapper = jobSkillMapper;
        this.skillMapper = skillMapper;
        this.candidateSkillMapper = candidateSkillMapper;
        this.properties = properties;
    }

    public GlobalSearchResult search(Long userId, String query, String types, Integer requestedLimit) {
        String q = query == null ? "" : query.trim();
        if (q.length() < 2 || q.length() > 100) {
            throw new ValidationException("Search query length must be between 2 and 100 characters");
        }
        int limit = requestedLimit == null ? 10
                : Math.max(1, Math.min(properties.getGlobalSearchMaxResults(), requestedLimit));
        List<String> requestedTypes = parseTypes(types);
        List<JobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).orderByDesc(JobEntity::getUpdatedAt));
        Set<Long> jobIds = jobs.stream().map(JobEntity::getId).collect(Collectors.toSet());
        Set<Long> companyIds = jobs.stream().map(JobEntity::getCompanyId).collect(Collectors.toSet());
        Map<Long, CompanyEntity> companies = companyIds.isEmpty() ? Map.of()
                : companyMapper.selectBatchIds(companyIds).stream()
                .collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        Set<Long> visibleSkillIds = new LinkedHashSet<>();
        if (!jobIds.isEmpty()) {
            jobSkillMapper.selectList(new LambdaQueryWrapper<JobSkillEntity>().in(JobSkillEntity::getJobId, jobIds))
                    .forEach(link -> visibleSkillIds.add(link.getSkillId()));
        }
        candidateSkillMapper.selectList(new LambdaQueryWrapper<CandidateSkillEntity>()
                        .eq(CandidateSkillEntity::getUserId, userId))
                .forEach(link -> visibleSkillIds.add(link.getSkillId()));
        List<SkillEntity> visibleSkills = visibleSkillIds.isEmpty() ? List.of() : skillMapper.selectBatchIds(visibleSkillIds);

        List<SearchGroup> groups = new ArrayList<>();
        for (String type : requestedTypes) {
            List<SearchResultItem> items = switch (type) {
                case "JOB" -> jobs.stream()
                        .filter(job -> matches(job.getTitle(), q) || matches(job.getCity(), q)
                                || matches(job.getBusinessDomain(), q))
                        .limit(limit)
                        .map(job -> {
                            CompanyEntity company = companies.get(job.getCompanyId());
                            return new SearchResultItem(job.getPublicId(), "JOB", job.getTitle(),
                                    (company == null ? "Unknown company" : company.getDisplayName())
                                            + " · " + safe(job.getCity(), "Location not set"),
                                    job.getStatus(), "/jobs?jobId=" + encode(job.getPublicId()));
                        }).toList();
                case "COMPANY" -> companies.values().stream()
                        .filter(company -> matches(company.getDisplayName(), q)
                                || matches(company.getIndustry(), q))
                        .sorted(java.util.Comparator.comparing(CompanyEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .limit(limit)
                        .map(company -> new SearchResultItem(company.getPublicId(), "COMPANY",
                                company.getDisplayName(), safe(company.getIndustry(), "Industry not set"),
                                "COMPANY", "/jobs?company=" + encode(company.getDisplayName()))).toList();
                case "SKILL" -> visibleSkills.stream()
                        .filter(skill -> matches(skill.getDisplayName(), q) || matches(skill.getCanonicalName(), q)
                                || matches(skill.getCategory(), q))
                        .sorted(java.util.Comparator.comparing(SkillEntity::getDisplayName, String.CASE_INSENSITIVE_ORDER))
                        .limit(limit)
                        .map(skill -> new SearchResultItem(skill.getPublicId(), "SKILL", skill.getDisplayName(),
                                safe(skill.getCategory(), "OTHER"), skill.getStatus(),
                                "/recommendations?skill=" + encode(skill.getDisplayName()))).toList();
                case "STATUS" -> STATUS_LABELS.entrySet().stream()
                        .filter(entry -> matches(entry.getKey(), q) || matches(entry.getValue(), q))
                        .sorted(Map.Entry.comparingByKey())
                        .limit(limit)
                        .map(entry -> new SearchResultItem(entry.getKey(), "STATUS", entry.getValue(),
                                entry.getKey(), "FILTER", "/recommendations?status=" + encode(entry.getKey()))).toList();
                default -> List.of();
            };
            groups.add(new SearchGroup(type, items));
        }
        int total = groups.stream().mapToInt(group -> group.items().size()).sum();
        return new GlobalSearchResult(q, requestedTypes, groups, total, limit);
    }

    private List<String> parseTypes(String value) {
        if (value == null || value.isBlank()) return List.of("JOB", "COMPANY", "SKILL", "STATUS");
        List<String> result = java.util.Arrays.stream(value.split(","))
                .map(String::trim).filter(part -> !part.isEmpty())
                .map(part -> part.toUpperCase(Locale.ROOT)).distinct().toList();
        if (result.isEmpty() || result.stream().anyMatch(type -> !ALLOWED_TYPES.contains(type))) {
            throw new ValidationException("Search types must use JOB, COMPANY, SKILL, or STATUS");
        }
        return result;
    }

    private static boolean matches(String value, String query) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(query.toLowerCase(Locale.ROOT));
    }
    private static String safe(String value, String fallback) { return value == null || value.isBlank() ? fallback : value; }
    private static String encode(String value) { return URLEncoder.encode(value, StandardCharsets.UTF_8); }
}
