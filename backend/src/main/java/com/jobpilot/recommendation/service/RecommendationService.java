package com.jobpilot.recommendation.service;

import static com.jobpilot.recommendation.dto.RecommendationDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSkillMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.service.MatchService;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.domain.RecommendationEventEntity;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.mapper.RecommendationEventMapper;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {
    private static final List<String> VIEWS = List.of("ALL", "TOP", "UNEVALUATED", "IGNORED", "FAVORITE");
    private static final List<String> SORTS = List.of("AI_RECOMMENDED", "MATCH_DESC", "PUBLISH_DESC", "SALARY_DESC", "COMPANY_ASC", "CITY_ASC");

    private final RecommendationProjectionService projection;
    private final JobRecommendationMapper recommendationMapper;
    private final RecommendationEventMapper eventMapper;
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final JobSourceMapper sourceMapper;
    private final JobSkillMapper jobSkillMapper;
    private final SkillMapper skillMapper;
    private final MatchService matchService;
    private final JsonCodec json;
    private final AuditService audit;
    private final StringRedisTemplate redis;

    public RecommendationService(RecommendationProjectionService projection,
                                 JobRecommendationMapper recommendationMapper,
                                 RecommendationEventMapper eventMapper,
                                 JobMapper jobMapper, CompanyMapper companyMapper,
                                 JobSourceMapper sourceMapper, JobSkillMapper jobSkillMapper,
                                 SkillMapper skillMapper, MatchService matchService,
                                 JsonCodec json, AuditService audit, StringRedisTemplate redis) {
        this.projection = projection;
        this.recommendationMapper = recommendationMapper;
        this.eventMapper = eventMapper;
        this.jobMapper = jobMapper;
        this.companyMapper = companyMapper;
        this.sourceMapper = sourceMapper;
        this.jobSkillMapper = jobSkillMapper;
        this.skillMapper = skillMapper;
        this.matchService = matchService;
        this.json = json;
        this.audit = audit;
        this.redis = redis;
    }

    public RecommendationCapabilities capabilities() {
        return new RecommendationCapabilities(true, "PHASE_5",
                "Application Queue and human-confirmed CRM tracking are available", VIEWS, SORTS);
    }

    public RecommendationPage list(Long userId, RecommendationQuery query) {
        validateQuery(query);
        List<RecommendationListItem> all = snapshot(userId);
        String view = upperOr(query.view(), "ALL");
        String sort = upperOr(query.sort(), "AI_RECOMMENDED");
        List<RecommendationListItem> filtered = all.stream()
                .filter(item -> matchesView(item, view))
                .filter(item -> query.level() == null || item.match() != null && query.level().equalsIgnoreCase(item.match().level()))
                .filter(item -> query.hardFilter() == null || item.match() != null && query.hardFilter().equalsIgnoreCase(item.match().hardFilterResult()))
                .filter(item -> query.recommendation() == null || item.match() != null && query.recommendation().equalsIgnoreCase(item.match().recommendation()))
                .filter(item -> !has(query.city()) || contains(item.job().city(), query.city()))
                .filter(item -> !has(query.companyId()) || query.companyId().equals(item.job().companyId()))
                .filter(item -> !has(query.companyName()) || contains(item.job().companyName(), query.companyName()))
                .filter(item -> query.salaryMin() == null || item.job().salaryMax() != null && item.job().salaryMax().compareTo(query.salaryMin()) >= 0)
                .filter(item -> query.salaryMax() == null || item.job().salaryMin() != null && item.job().salaryMin().compareTo(query.salaryMax()) <= 0)
                .filter(item -> query.publishFrom() == null || item.job().publishAt() != null && !item.job().publishAt().isBefore(query.publishFrom()))
                .filter(item -> query.publishTo() == null || item.job().publishAt() != null && !item.job().publishAt().isAfter(query.publishTo()))
                .filter(item -> !has(query.skill()) || item.job().skills().stream().anyMatch(skill -> contains(skill.name(), query.skill())))
                .filter(item -> !has(query.sourcePlatform()) || equalsIgnoreCase(item.job().sourcePlatform(), query.sourcePlatform()))
                .filter(item -> query.favorite() == null || item.favorite() == query.favorite())
                .filter(item -> !has(query.keyword()) || keyword(item, query.keyword()))
                .sorted(comparator(sort))
                .toList();
        int limit = query.limit() == null ? 20 : Math.max(1, Math.min(100, query.limit()));
        int start = cursorStart(filtered, query.cursor());
        int end = Math.min(filtered.size(), start + limit);
        List<RecommendationListItem> page = start >= filtered.size() ? List.of() : filtered.subList(start, end);
        boolean more = end < filtered.size();
        String next = more && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1).id()) : null;
        return new RecommendationPage(page, next, more, limit, filtered.size());
    }

    public List<RecommendationListItem> snapshot(Long userId) {
        List<JobRecommendationEntity> recommendations = projection.synchronize(userId);
        Map<Long, JobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
                        .eq(JobEntity::getUserId, userId))
                .stream().collect(Collectors.toMap(JobEntity::getId, Function.identity()));
        Set<Long> companyIds = jobs.values().stream().map(JobEntity::getCompanyId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CompanyEntity> companies = companyIds.isEmpty()
                ? Map.of() : byId(companyMapper.selectBatchIds(companyIds));
        Map<Long, JobMatchEntity> matches = projection.latestMatches(userId);
        Map<Long, JobSourceEntity> sources = new LinkedHashMap<>();
        sourceMapper.selectList(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getUserId, userId)
                        .orderByAsc(JobSourceEntity::getId))
                .forEach(source -> sources.putIfAbsent(source.getJobId(), source));
        Map<Long, List<SkillChip>> skills = skills(jobs.keySet());
        return recommendations.stream()
                .filter(rec -> jobs.containsKey(rec.getJobId()))
                .map(rec -> item(rec, jobs.get(rec.getJobId()), jobs.get(rec.getJobId()).getCompanyId() == null
                                ? null : companies.get(jobs.get(rec.getJobId()).getCompanyId()),
                        matches.get(rec.getJobId()), sources.get(rec.getJobId()), skills.getOrDefault(rec.getJobId(), List.of())))
                .toList();
    }

    public RecommendationDetail get(Long userId, String publicId) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        RecommendationListItem item = snapshot(userId).stream()
                .filter(candidate -> candidate.id().equals(publicId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation"));
        return new RecommendationDetail(item,
                recommendation.getLatestMatchId() == null || item.match() == null ? null
                        : matchService.getMatch(userId, item.match().id()),
                events(userId, publicId));
    }

    public List<RecommendationEventView> events(Long userId, String publicId) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        return eventMapper.selectList(new LambdaQueryWrapper<RecommendationEventEntity>()
                        .eq(RecommendationEventEntity::getUserId, userId)
                        .eq(RecommendationEventEntity::getRecommendationId, recommendation.getId())
                        .orderByDesc(RecommendationEventEntity::getOccurredAt)
                        .orderByDesc(RecommendationEventEntity::getId))
                .stream().map(this::eventView).toList();
    }

    @Transactional
    public RecommendationEventView viewed(Long userId, String publicId) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        RecommendationEventEntity existing = eventMapper.selectOne(new LambdaQueryWrapper<RecommendationEventEntity>()
                .eq(RecommendationEventEntity::getUserId, userId)
                .eq(RecommendationEventEntity::getRecommendationId, recommendation.getId())
                .eq(RecommendationEventEntity::getEventType, "VIEWED").last("LIMIT 1"));
        if (existing != null) return eventView(existing);
        RecommendationEventEntity event = new RecommendationEventEntity();
        event.setUserId(userId); event.setRecommendationId(recommendation.getId()); event.setJobId(recommendation.getJobId());
        event.setJobMatchId(recommendation.getLatestMatchId()); event.setEventType("VIEWED");
        event.setPreviousStateJson(json.write(Map.of())); event.setCurrentStateJson(json.write(state(recommendation)));
        event.setFeatureSnapshotJson(json.write(feature(recommendation))); event.setTraceId(TraceContext.getTraceId());
        event.setOccurredAt(LocalDateTime.now()); eventMapper.insert(event);
        audit.record(userId, "RECOMMENDATION_VIEWED", "RECOMMENDATION", publicId);
        return eventView(event);
    }

    @Transactional
    public RecommendationListItem favorite(Long userId, String publicId, int expectedVersion) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        if (Boolean.TRUE.equals(recommendation.getFavorite())) return currentItem(userId, publicId);
        return change(userId, recommendation, expectedVersion, "FAVORITE", null, () -> recommendation.setFavorite(true));
    }

    @Transactional
    public RecommendationListItem unfavorite(Long userId, String publicId, int expectedVersion) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        if (!Boolean.TRUE.equals(recommendation.getFavorite())) return currentItem(userId, publicId);
        return change(userId, recommendation, expectedVersion, "UNFAVORITE", null, () -> recommendation.setFavorite(false));
    }

    @Transactional
    public RecommendationListItem ignore(Long userId, String publicId, int expectedVersion, String reason) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        if ("IGNORED".equals(recommendation.getRecommendationStatus())) return currentItem(userId, publicId);
        String normalizedReason = has(reason) ? reason.trim() : "Not relevant for current search";
        return change(userId, recommendation, expectedVersion, "IGNORE", normalizedReason, () -> {
            recommendation.setRecommendationStatus("IGNORED");
            recommendation.setIgnoredReason(normalizedReason);
            recommendation.setIgnoredAt(LocalDateTime.now());
        });
    }

    @Transactional
    public RecommendationListItem restore(Long userId, String publicId, int expectedVersion) {
        JobRecommendationEntity recommendation = owned(userId, publicId);
        if (!"IGNORED".equals(recommendation.getRecommendationStatus())) return currentItem(userId, publicId);
        JobMatchEntity match = recommendation.getLatestMatchId() == null ? null
                : projection.latestMatches(userId).get(recommendation.getJobId());
        MatchRunEntity run = projection.latestRuns(userId).get(recommendation.getJobId());
        return change(userId, recommendation, expectedVersion, "RESTORE", null, () -> {
            recommendation.setRecommendationStatus(projection.derivedStatus(match, run));
            recommendation.setIgnoredReason(null);
            recommendation.setIgnoredAt(null);
        });
    }

    public JobRecommendationEntity owned(Long userId, String publicId) {
        projection.synchronize(userId);
        JobRecommendationEntity entity = recommendationMapper.selectOne(new LambdaQueryWrapper<JobRecommendationEntity>()
                .eq(JobRecommendationEntity::getUserId, userId)
                .eq(JobRecommendationEntity::getPublicId, publicId).last("LIMIT 1"));
        if (entity == null) throw new ResourceNotFoundException("Recommendation");
        return entity;
    }

    private RecommendationListItem change(Long userId, JobRecommendationEntity recommendation,
                                          int expectedVersion, String eventType, String reason,
                                          Runnable mutation) {
        if (recommendation.getVersion() != expectedVersion) {
            throw new BusinessException(4094001, "Recommendation was modified concurrently; reload and retry", HttpStatus.CONFLICT);
        }
        Map<String, Object> before = state(recommendation);
        mutation.run();
        if (recommendationMapper.updateById(recommendation) != 1) {
            throw new BusinessException(4094001, "Recommendation was modified concurrently; reload and retry", HttpStatus.CONFLICT);
        }
        RecommendationEventEntity event = new RecommendationEventEntity();
        event.setUserId(userId);
        event.setRecommendationId(recommendation.getId());
        event.setJobId(recommendation.getJobId());
        event.setJobMatchId(recommendation.getLatestMatchId());
        event.setEventType(eventType);
        event.setReason(reason);
        event.setPreviousStateJson(json.write(before));
        event.setCurrentStateJson(json.write(state(recommendation)));
        event.setFeatureSnapshotJson(json.write(feature(recommendation)));
        event.setTraceId(TraceContext.getTraceId());
        event.setOccurredAt(LocalDateTime.now());
        eventMapper.insert(event);
        audit.record(userId, "RECOMMENDATION_" + eventType, "RECOMMENDATION", recommendation.getPublicId());
        invalidate(userId);
        return currentItem(userId, recommendation.getPublicId());
    }

    private RecommendationListItem currentItem(Long userId, String publicId) {
        return snapshot(userId).stream().filter(item -> item.id().equals(publicId)).findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Recommendation"));
    }

    private Map<String, Object> state(JobRecommendationEntity recommendation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("status", recommendation.getRecommendationStatus());
        value.put("favorite", Boolean.TRUE.equals(recommendation.getFavorite()));
        value.put("ignoredReason", recommendation.getIgnoredReason());
        value.put("ignoredAt", recommendation.getIgnoredAt());
        value.put("version", recommendation.getVersion());
        return value;
    }

    private Map<String, Object> feature(JobRecommendationEntity recommendation) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("rankVersion", recommendation.getRankVersion());
        value.put("rankScore", recommendation.getRankScore());
        value.put("latestMatchId", recommendation.getLatestMatchId());
        value.put("capturedAt", LocalDateTime.now());
        return value;
    }

    private RecommendationEventView eventView(RecommendationEventEntity event) {
        return new RecommendationEventView(event.getPublicId(), event.getEventType(), event.getReason(),
                json.readNode(event.getPreviousStateJson()), json.readNode(event.getCurrentStateJson()),
                json.readNode(event.getFeatureSnapshotJson()), event.getTraceId(), event.getOccurredAt());
    }

    private RecommendationListItem item(JobRecommendationEntity recommendation, JobEntity job,
                                        CompanyEntity company, JobMatchEntity match,
                                        JobSourceEntity source, List<SkillChip> skills) {
        RecommendationJobView jobView = new RecommendationJobView(job.getPublicId(), job.getTitle(),
                company == null ? null : company.getPublicId(), company == null ? "Unknown company" : company.getDisplayName(),
                company == null ? null : company.getIndustry(), job.getCity(), job.getSalaryMin(), job.getSalaryMax(),
                job.getCurrency(), job.getSalaryText(), job.getStatus(), job.getParseStatus(),
                source == null ? null : source.getPlatform(), skills,
                job.getPublishAt(), job.getFirstCollectedAt(), job.getUpdatedAt());
        RecommendationMatchView matchView = match == null ? null : new RecommendationMatchView(
                match.getPublicId(), match.getStatus(), match.getHardFilterResult(), match.getOverallScore(),
                match.getLevel(), match.getRecommendation(), match.getReasonText(), match.getLlmStatus(),
                match.getAlgorithmVersion(), match.getConfigVersion(), match.getEmbeddingModel(), match.getEvaluatedAt());
        return new RecommendationListItem(recommendation.getPublicId(), recommendation.getVersion(),
                recommendation.getRecommendationStatus(), Boolean.TRUE.equals(recommendation.getFavorite()),
                recommendation.getIgnoredReason(), recommendation.getRankScore(),
                json.readNode(recommendation.getRankBasisJson()), jobView, matchView, recommendation.getUpdatedAt());
    }

    private Map<Long, List<SkillChip>> skills(Set<Long> jobIds) {
        if (jobIds.isEmpty()) return Map.of();
        List<JobSkillEntity> links = jobSkillMapper.selectList(new LambdaQueryWrapper<JobSkillEntity>()
                .in(JobSkillEntity::getJobId, jobIds).orderByDesc(JobSkillEntity::getImportance));
        Set<Long> skillIds = links.stream().map(JobSkillEntity::getSkillId).collect(Collectors.toSet());
        Map<Long, SkillEntity> catalog = skillIds.isEmpty() ? Map.of() : byId(skillMapper.selectBatchIds(skillIds));
        Map<Long, List<SkillChip>> result = new HashMap<>();
        for (JobSkillEntity link : links) {
            SkillEntity skill = catalog.get(link.getSkillId());
            if (skill != null) result.computeIfAbsent(link.getJobId(), ignored -> new ArrayList<>())
                    .add(new SkillChip(skill.getPublicId(), skill.getDisplayName(), link.getRequirementType(), link.getImportance()));
        }
        return result;
    }

    private boolean matchesView(RecommendationListItem item, String view) {
        return switch (view) {
            case "TOP" -> !"IGNORED".equals(item.status()) && item.match() != null
                    && item.match().level() != null
                    && Set.of("S", "A", "B").contains(item.match().level());
            case "UNEVALUATED" -> "UNEVALUATED".equals(item.status());
            case "IGNORED" -> "IGNORED".equals(item.status());
            case "FAVORITE" -> item.favorite();
            default -> !"IGNORED".equals(item.status());
        };
    }

    private Comparator<RecommendationListItem> comparator(String sort) {
        Comparator<RecommendationListItem> stable = Comparator.comparing(RecommendationListItem::id).reversed();
        Comparator<RecommendationListItem> score = Comparator.comparing(
                RecommendationListItem::rankScore, Comparator.nullsLast(Comparator.reverseOrder()));
        Comparator<RecommendationListItem> published = Comparator.comparing(
                item -> item.job().publishAt(), Comparator.nullsLast(Comparator.reverseOrder()));
        return switch (sort) {
            case "MATCH_DESC" -> score.thenComparing(published).thenComparing(stable);
            case "PUBLISH_DESC" -> published.thenComparing(score).thenComparing(stable);
            case "SALARY_DESC" -> Comparator.comparing((RecommendationListItem item) -> item.job().salaryMax(),
                            Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(score).thenComparing(stable);
            case "COMPANY_ASC" -> Comparator.comparing((RecommendationListItem item) -> safe(item.job().companyName()),
                            String.CASE_INSENSITIVE_ORDER).thenComparing(score).thenComparing(stable);
            case "CITY_ASC" -> Comparator.comparing((RecommendationListItem item) -> safe(item.job().city()),
                            String.CASE_INSENSITIVE_ORDER).thenComparing(score).thenComparing(stable);
            default -> Comparator.comparingInt(this::recommendationOrder).thenComparing(score)
                    .thenComparing(published).thenComparing(stable);
        };
    }

    private int recommendationOrder(RecommendationListItem item) {
        if (item.match() == null) return 4;
        return switch (safe(item.match().recommendation())) {
            case "RECOMMEND" -> 0;
            case "CONSIDER" -> 1;
            case "NOT_RECOMMENDED" -> 2;
            case "REJECTED" -> 3;
            default -> 4;
        };
    }

    private boolean keyword(RecommendationListItem item, String keyword) {
        return contains(item.job().title(), keyword) || contains(item.job().companyName(), keyword)
                || contains(item.job().city(), keyword)
                || item.job().skills().stream().anyMatch(skill -> contains(skill.name(), keyword));
    }

    private int cursorStart(List<RecommendationListItem> items, String cursor) {
        if (!has(cursor)) return 0;
        String id;
        try {
            id = new String(Base64.getUrlDecoder().decode(cursor), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            throw new com.jobpilot.common.exception.ValidationException("Invalid recommendation cursor");
        }
        for (int index = 0; index < items.size(); index++) {
            if (items.get(index).id().equals(id)) return index + 1;
        }
        throw new com.jobpilot.common.exception.ValidationException("Recommendation cursor is stale or invalid");
    }

    private String encodeCursor(String id) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(id.getBytes(StandardCharsets.UTF_8));
    }

    private void invalidate(Long userId) {
        redis.delete("jobpilot:phase4:dashboard:" + userId);
    }

    private void validateQuery(RecommendationQuery query) {
        String view = upperOr(query.view(), "ALL");
        String sort = upperOr(query.sort(), "AI_RECOMMENDED");
        if (!VIEWS.contains(view)) throw new com.jobpilot.common.exception.ValidationException("Invalid recommendation view");
        if (!SORTS.contains(sort)) throw new com.jobpilot.common.exception.ValidationException("Invalid recommendation sort");
        if (has(query.level()) && !Set.of("S", "A", "B", "C", "D").contains(query.level().toUpperCase(Locale.ROOT)))
            throw new com.jobpilot.common.exception.ValidationException("Invalid match level");
        if (has(query.hardFilter()) && !Set.of("PASS", "DOWNGRADE", "REJECT").contains(query.hardFilter().toUpperCase(Locale.ROOT)))
            throw new com.jobpilot.common.exception.ValidationException("Invalid hard filter result");
        if (has(query.recommendation()) && !Set.of("RECOMMEND", "CONSIDER", "NOT_RECOMMENDED", "REJECTED").contains(query.recommendation().toUpperCase(Locale.ROOT)))
            throw new com.jobpilot.common.exception.ValidationException("Invalid recommendation result");
        if (query.limit() != null && (query.limit() < 1 || query.limit() > 100))
            throw new com.jobpilot.common.exception.ValidationException("Recommendation limit must be between 1 and 100");
        if (has(query.city()) && query.city().length() > 120
                || has(query.companyId()) && query.companyId().length() > 26
                || has(query.companyName()) && query.companyName().length() > 200
                || has(query.skill()) && query.skill().length() > 120
                || has(query.sourcePlatform()) && query.sourcePlatform().length() > 40
                || has(query.keyword()) && query.keyword().length() > 200
                || has(query.cursor()) && query.cursor().length() > 200)
            throw new com.jobpilot.common.exception.ValidationException("Recommendation filter is too long");
        if (query.salaryMin() != null && query.salaryMin().signum() < 0
                || query.salaryMax() != null && query.salaryMax().signum() < 0)
            throw new com.jobpilot.common.exception.ValidationException("Salary filters must be non-negative");
        if (query.salaryMin() != null && query.salaryMax() != null && query.salaryMin().compareTo(query.salaryMax()) > 0)
            throw new com.jobpilot.common.exception.ValidationException("salaryMin cannot exceed salaryMax");
    }

    private static <T extends com.jobpilot.common.persistence.BaseEntity> Map<Long, T> byId(Collection<T> values) {
        return values.stream().collect(Collectors.toMap(T::getId, Function.identity()));
    }

    private static boolean has(String value) { return value != null && !value.isBlank(); }
    private static boolean contains(String value, String fragment) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(fragment.trim().toLowerCase(Locale.ROOT));
    }
    private static boolean equalsIgnoreCase(String value, String other) { return value != null && value.equalsIgnoreCase(other); }
    private static String upperOr(String value, String fallback) { return has(value) ? value.trim().toUpperCase(Locale.ROOT) : fallback; }
    private static String safe(String value) { return value == null ? "" : value; }
}
