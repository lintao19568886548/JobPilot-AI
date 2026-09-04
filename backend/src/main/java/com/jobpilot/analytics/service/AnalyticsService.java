package com.jobpilot.analytics.service;

import static com.jobpilot.analytics.dto.AnalyticsDtos.*;

import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.application.service.ApplicationService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.analytics.domain.AnalyticsDailyEntity;
import com.jobpilot.analytics.mapper.AnalyticsDailyMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.dashboard.dto.DashboardView;
import com.jobpilot.dashboard.service.DashboardService;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.dto.RecommendationDtos.RecommendationListItem;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.service.RecommendationProjectionService;
import com.jobpilot.recommendation.service.RecommendationService;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AnalyticsService {
    private final AnalyticsDailyMapper dailyMapper;
    private final JobMapper jobMapper;
    private final JobSourceMapper sourceMapper;
    private final JobRecommendationMapper recommendationMapper;
    private final RecommendationProjectionService projection;
    private final RecommendationService recommendationService;
    private final DashboardService dashboardService;
    private final ApplicationService applicationService;
    private final ApplicationQueueItemMapper queueMapper;
    private final ApplicationLogMapper applicationLogMapper;
    private final RecommendationProperties properties;
    private final AuditService audit;

    public AnalyticsService(AnalyticsDailyMapper dailyMapper, JobMapper jobMapper,
                            JobSourceMapper sourceMapper,
                            JobRecommendationMapper recommendationMapper,
                            RecommendationProjectionService projection,
                            RecommendationService recommendationService,
                            DashboardService dashboardService, ApplicationService applicationService,
                            ApplicationQueueItemMapper queueMapper, ApplicationLogMapper applicationLogMapper,
                            RecommendationProperties properties, AuditService audit) {
        this.dailyMapper = dailyMapper;
        this.jobMapper = jobMapper;
        this.sourceMapper = sourceMapper;
        this.recommendationMapper = recommendationMapper;
        this.projection = projection;
        this.recommendationService = recommendationService;
        this.dashboardService = dashboardService;
        this.applicationService = applicationService;
        this.queueMapper = queueMapper;
        this.applicationLogMapper = applicationLogMapper;
        this.properties = properties;
        this.audit = audit;
        ZoneId.of(properties.getAnalyticsTimezone());
    }

    public AnalyticsDashboardView dashboard(Long userId) {
        List<RecommendationListItem> items = recommendationService.snapshot(userId);
        DashboardView foundation = dashboardService.get(userId);
        LocalDate today = LocalDate.now(ZoneId.of(properties.getAnalyticsTimezone()));
        long evaluated = items.stream().filter(item -> item.match() != null).count();
        long high = items.stream().filter(item -> item.match() != null && item.match().level() != null
                && Set.of("S", "A").contains(item.match().level())).count();
        long bLevel = items.stream().filter(item -> item.match() != null && "B".equals(item.match().level())).count();
        long todayNew = items.stream().filter(item -> date(item.job().collectedAt()) != null
                && date(item.job().collectedAt()).equals(today)).count();
        long todayEvaluated = items.stream().filter(item -> item.match() != null
                && date(item.match().evaluatedAt()) != null
                && date(item.match().evaluatedAt()).equals(today)).count();
        MatchKpis kpis = new MatchKpis(items.size(),
                items.stream().filter(item -> "ACTIVE".equals(item.job().jobStatus())).count(), evaluated,
                items.stream().filter(item -> "UNEVALUATED".equals(item.status())).count(), high, bLevel,
                items.stream().filter(RecommendationListItem::favorite).count(),
                items.stream().filter(item -> "IGNORED".equals(item.status())).count(),
                items.stream().filter(item -> "MATCH_FAILED".equals(item.status())).count(),
                todayNew, todayEvaluated);
        List<RecommendationListItem> recommendations = items.stream()
                .filter(item -> !"IGNORED".equals(item.status()) && item.match() != null)
                .sorted(Comparator.comparing(RecommendationListItem::rankScore,
                                Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(RecommendationListItem::id))
                .limit(6).toList();
        var applicationKpis = applicationService.kpis(userId);
        return new AnalyticsDashboardView(foundation, kpis, applicationKpis, recommendations,
                levelDistribution(items), sourceDistribution(items), statusDistribution(items),
                funnel(items, applicationKpis), unavailable(), properties.getAnalyticsTimezone(), today, LocalDateTime.now());
    }

    public List<MetricPoint> levels(Long userId) { return dashboard(userId).levels(); }
    public List<MetricPoint> sources(Long userId) { return dashboard(userId).sources(); }
    public List<FunnelStage> funnel(Long userId) { return dashboard(userId).funnel(); }

    @Transactional
    public RebuildResult rebuild(Long userId, LocalDate from, LocalDate to) {
        if (from.isAfter(to)) throw new ValidationException("Analytics from must be on or before to");
        long days = ChronoUnit.DAYS.between(from, to) + 1;
        if (days > 366) throw new ValidationException("Analytics rebuild range cannot exceed 366 days");
        List<JobEntity> jobs = jobMapper.selectList(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId));
        List<JobRecommendationEntity> recommendations = projection.synchronize(userId);
        Map<Long, JobMatchEntity> matches = projection.latestMatches(userId);
        Map<Long, JobSourceEntity> primarySources = primarySources(userId);
        List<ApplicationQueueItemEntity> queueItems = queueMapper.selectList(
                new LambdaQueryWrapper<ApplicationQueueItemEntity>().eq(ApplicationQueueItemEntity::getUserId, userId));
        List<ApplicationLogEntity> applicationLogs = applicationLogMapper.selectList(
                new LambdaQueryWrapper<ApplicationLogEntity>().eq(ApplicationLogEntity::getUserId, userId));
        int rows = 0;
        for (LocalDate day = from; !day.isAfter(to); day = day.plusDays(1)) {
            LocalDate currentDay = day;
            dailyMapper.delete(new LambdaQueryWrapper<AnalyticsDailyEntity>()
                    .eq(AnalyticsDailyEntity::getUserId, userId)
                    .eq(AnalyticsDailyEntity::getMetricDate, currentDay));
            List<JobEntity> dayJobs = jobs.stream().filter(job -> date(jobDate(job)) != null
                    && date(jobDate(job)).equals(currentDay)).toList();
            List<JobMatchEntity> dayMatches = matches.values().stream()
                    .filter(match -> date(match.getEvaluatedAt()).equals(currentDay)).toList();
            long dayFavorites = recommendations.stream().filter(rec -> Boolean.TRUE.equals(rec.getFavorite())
                    && !rec.getCreatedAt().toLocalDate().isAfter(currentDay)).count();
            long dayIgnored = recommendations.stream().filter(rec -> "IGNORED".equals(rec.getRecommendationStatus())
                    && !rec.getCreatedAt().toLocalDate().isAfter(currentDay)).count();
            long queued = queueItems.stream().filter(item -> date(item.getCreatedAt()).equals(currentDay)).count();
            List<ApplicationLogEntity> dayApplicationLogs = applicationLogs.stream()
                    .filter(log -> date(log.getOccurredAt()).equals(currentDay)).toList();
            insert(userId, currentDay, "OVERALL", "ALL", dayJobs.size(), dayMatches.size(),
                    high(dayMatches), dayFavorites, dayIgnored, queued,
                    statusEvents(dayApplicationLogs, "APPLIED"), statusEvents(dayApplicationLogs, "VIEWED"),
                    statusEvents(dayApplicationLogs, "REPLIED"), statusEvents(dayApplicationLogs, "WRITTEN_TEST"),
                    dayApplicationLogs.stream().filter(log -> log.getToStatus().startsWith("INTERVIEW_")
                            || "HR_INTERVIEW".equals(log.getToStatus())).count(),
                    statusEvents(dayApplicationLogs, "OFFER"),
                    dayApplicationLogs.stream().filter(log -> Set.of("REJECTED", "WITHDRAWN", "CLOSED")
                            .contains(log.getToStatus())).count());
            rows++;

            Map<String, List<JobEntity>> sourceGroups = dayJobs.stream().collect(Collectors.groupingBy(job -> {
                JobSourceEntity source = primarySources.get(job.getId());
                return source == null || source.getPlatform() == null ? "UNKNOWN" : source.getPlatform();
            }, LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<JobEntity>> entry : sourceGroups.entrySet()) {
                Set<Long> ids = entry.getValue().stream().map(JobEntity::getId).collect(Collectors.toSet());
                List<JobMatchEntity> groupMatches = dayMatches.stream().filter(match -> ids.contains(match.getJobId())).toList();
                insert(userId, currentDay, "SOURCE", entry.getKey(), entry.getValue().size(), groupMatches.size(),
                        high(groupMatches), 0, 0);
                rows++;
            }
            Map<String, List<JobEntity>> cityGroups = dayJobs.stream().collect(Collectors.groupingBy(
                    job -> job.getCity() == null || job.getCity().isBlank() ? "UNKNOWN" : job.getCity(),
                    LinkedHashMap::new, Collectors.toList()));
            for (Map.Entry<String, List<JobEntity>> entry : cityGroups.entrySet()) {
                insert(userId, currentDay, "CITY", entry.getKey(), entry.getValue().size(), 0, 0, 0, 0);
                rows++;
            }
            Map<String, Long> levelGroups = dayMatches.stream().collect(Collectors.groupingBy(
                    match -> match.getLevel() == null ? "REJECTED" : match.getLevel(),
                    LinkedHashMap::new, Collectors.counting()));
            for (Map.Entry<String, Long> entry : levelGroups.entrySet()) {
                insert(userId, currentDay, "LEVEL", entry.getKey(), entry.getValue(), entry.getValue(),
                        Set.of("S", "A").contains(entry.getKey()) ? entry.getValue() : 0, 0, 0);
                rows++;
            }
            Map<String, Long> statusGroups = recommendations.stream().collect(Collectors.groupingBy(
                    JobRecommendationEntity::getRecommendationStatus, LinkedHashMap::new, Collectors.counting()));
            for (Map.Entry<String, Long> entry : statusGroups.entrySet()) {
                insert(userId, currentDay, "RECOMMENDATION_STATUS", entry.getKey(), entry.getValue(), 0, 0,
                        "READY".equals(entry.getKey()) ? dayFavorites : 0,
                        "IGNORED".equals(entry.getKey()) ? entry.getValue() : 0);
                rows++;
            }
        }
        audit.record(userId, "ANALYTICS_REBUILD", "ANALYTICS_DAILY", from + ".." + to);
        return new RebuildResult(from, to, (int) days, rows, properties.getAnalyticsTimezone(), LocalDateTime.now());
    }

    private List<MetricPoint> levelDistribution(List<RecommendationListItem> items) {
        long denominator = items.stream().filter(item -> item.match() != null).count();
        List<MetricPoint> result = new ArrayList<>();
        for (String level : List.of("S", "A", "B", "C", "D", "REJECTED")) {
            long count = items.stream().filter(item -> item.match() != null)
                    .filter(item -> "REJECTED".equals(level) ? item.match().level() == null
                            : level.equals(item.match().level())).count();
            result.add(metric(level, level, count, denominator));
        }
        return result;
    }

    private List<MetricPoint> sourceDistribution(List<RecommendationListItem> items) {
        Map<String, Long> groups = items.stream().collect(Collectors.groupingBy(
                item -> item.job().sourcePlatform() == null ? "UNKNOWN" : item.job().sourcePlatform(),
                LinkedHashMap::new, Collectors.counting()));
        long denominator = items.size();
        return groups.entrySet().stream().map(entry -> metric(entry.getKey(), entry.getKey(),
                entry.getValue(), denominator)).toList();
    }

    private List<MetricPoint> statusDistribution(List<RecommendationListItem> items) {
        Map<String, Long> groups = items.stream().collect(Collectors.groupingBy(
                RecommendationListItem::status, LinkedHashMap::new, Collectors.counting()));
        long denominator = items.size();
        return groups.entrySet().stream().map(entry -> metric(entry.getKey(), entry.getKey(),
                entry.getValue(), denominator)).toList();
    }

    private List<FunnelStage> funnel(List<RecommendationListItem> items,
                                     com.jobpilot.application.dto.ApplicationDtos.ApplicationKpis applications) {
        long total = items.size();
        long evaluated = items.stream().filter(item -> item.match() != null).count();
        long recommended = items.stream().filter(item -> item.match() != null && item.match().level() != null
                && Set.of("S", "A", "B").contains(item.match().level())).count();
        long favorite = items.stream().filter(RecommendationListItem::favorite).count();
        long ignored = items.stream().filter(item -> "IGNORED".equals(item.status())).count();
        return List.of(stage("DISCOVERED", "已发现", total, total),
                stage("EVALUATED", "已评估", evaluated, total),
                stage("RECOMMENDED", "S/A/B 推荐", recommended, total),
                stage("FAVORITE", "已收藏", favorite, total),
                stage("QUEUED", "已入队", applications.queueTotal(), total),
                stage("APPLIED", "已投递", applications.applications(), total),
                stage("REPLIED", "已回复", applications.replied(), total),
                stage("IGNORED", "已忽略", ignored, total));
    }

    private List<CapabilityAvailability> unavailable() {
        return List.of(new CapabilityAvailability("OFFER", true, "PHASE_9", "Offer 中心已开放"),
                new CapabilityAvailability("EXTERNAL_MEETING_ACTIONS", false, "MANUAL_ONLY", "JobPilot 不会加入会议或发送邀请"),
                new CapabilityAvailability("AUTOMATIC_SUBMISSION", false, "MANUAL_ONLY", "外部自动投递保持禁用"));
    }

    private Map<Long, JobSourceEntity> primarySources(Long userId) {
        Map<Long, JobSourceEntity> result = new LinkedHashMap<>();
        sourceMapper.selectList(new LambdaQueryWrapper<JobSourceEntity>()
                        .eq(JobSourceEntity::getUserId, userId).orderByAsc(JobSourceEntity::getId))
                .forEach(source -> result.putIfAbsent(source.getJobId(), source));
        return result;
    }

    private void insert(Long userId, LocalDate day, String type, String key,
                        long jobs, long evaluated, long highMatch, long favorites, long ignored) {
        insert(userId, day, type, key, jobs, evaluated, highMatch, favorites, ignored,
                0, 0, 0, 0, 0, 0, 0, 0);
    }

    private void insert(Long userId, LocalDate day, String type, String key,
                        long jobs, long evaluated, long highMatch, long favorites, long ignored,
                        long queued, long applied, long viewed, long replied, long writtenTest,
                        long interview, long offer, long terminal) {
        AnalyticsDailyEntity row = new AnalyticsDailyEntity();
        row.setUserId(userId);
        row.setMetricDate(day);
        row.setDimensionType(type);
        row.setDimensionKey(key);
        row.setJobCount(Math.toIntExact(jobs));
        row.setEvaluatedCount(Math.toIntExact(evaluated));
        row.setHighMatchCount(Math.toIntExact(highMatch));
        row.setFavoriteCount(Math.toIntExact(favorites));
        row.setIgnoredCount(Math.toIntExact(ignored));
        row.setQueuedCount(Math.toIntExact(queued));
        row.setAppliedCount(Math.toIntExact(applied));
        row.setViewedCount(Math.toIntExact(viewed));
        row.setRepliedCount(Math.toIntExact(replied));
        row.setWrittenTestCount(Math.toIntExact(writtenTest));
        row.setInterviewStageCount(Math.toIntExact(interview));
        row.setOfferStageCount(Math.toIntExact(offer));
        row.setTerminalCount(Math.toIntExact(terminal));
        dailyMapper.insert(row);
    }

    private static long statusEvents(List<ApplicationLogEntity> logs, String status) {
        return logs.stream().filter(log -> status.equals(log.getToStatus())).count();
    }

    private static long high(List<JobMatchEntity> matches) {
        return matches.stream().filter(match -> match.getLevel() != null
                && Set.of("S", "A").contains(match.getLevel())).count();
    }
    private static MetricPoint metric(String key, String label, long value, long denominator) {
        return new MetricPoint(key, label, value, value, denominator, denominator, denominator < 5);
    }
    private static FunnelStage stage(String key, String label, long value, long denominator) {
        return new FunnelStage(key, label, value, value, denominator, denominator, true);
    }
    private static LocalDateTime jobDate(JobEntity job) {
        return job.getFirstCollectedAt();
    }
    private static LocalDate date(LocalDateTime value) { return value == null ? null : value.toLocalDate(); }
}
