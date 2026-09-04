package com.jobpilot.analytics.service;

import static com.jobpilot.analytics.dto.CompleteAnalyticsDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.analytics.domain.AnalyticsSnapshotEntity;
import com.jobpilot.analytics.mapper.AnalyticsSnapshotMapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.RecommendationProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.mapper.InterviewMapper;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CompleteAnalyticsService {
    private static final Set<String> REPLY = Set.of("REPLIED", "WRITTEN_TEST", "INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3", "HR_INTERVIEW", "OFFER");
    private static final Set<String> INTERVIEW = Set.of("INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3", "HR_INTERVIEW", "OFFER");
    private final JobMapper jobs; private final JobSourceMapper sources; private final CompanyMapper companies;
    private final ApplicationMapper applications; private final ApplicationLogMapper logs; private final InterviewMapper interviews;
    private final OfferMapper offers; private final JobMatchMapper matches; private final ResumeVersionMapper versions; private final ResumeMapper resumes;
    private final AnalyticsSnapshotMapper snapshots; private final AnalyticsService legacy; private final RecommendationProperties properties;
    private final JsonCodec json; private final ObjectMapper objectMapper; private final AuditService audit;

    public CompleteAnalyticsService(JobMapper jobs, JobSourceMapper sources, CompanyMapper companies,
                                    ApplicationMapper applications, ApplicationLogMapper logs, InterviewMapper interviews,
                                    OfferMapper offers, JobMatchMapper matches, ResumeVersionMapper versions, ResumeMapper resumes,
                                    AnalyticsSnapshotMapper snapshots, AnalyticsService legacy, RecommendationProperties properties,
                                    JsonCodec json, ObjectMapper objectMapper, AuditService audit) {
        this.jobs = jobs; this.sources = sources; this.companies = companies; this.applications = applications; this.logs = logs;
        this.interviews = interviews; this.offers = offers; this.matches = matches; this.versions = versions; this.resumes = resumes;
        this.snapshots = snapshots; this.legacy = legacy; this.properties = properties; this.json = json; this.objectMapper = objectMapper; this.audit = audit;
    }

    public CompleteAnalyticsView calculate(Long userId, LocalDate from, LocalDate to) {
        range(from, to); ZoneId zone = ZoneId.of(properties.getAnalyticsTimezone());
        List<JobEntity> allJobs = jobs.selectList(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId, userId));
        List<JobEntity> cohort = allJobs.stream().filter(j -> within(j.getFirstCollectedAt(), from, to, zone)).toList();
        Set<Long> cohortJobIds = cohort.stream().map(JobEntity::getId).collect(Collectors.toSet());
        List<ApplicationEntity> apps = applications.selectList(new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getUserId, userId)).stream()
                .filter(a -> cohortJobIds.contains(a.getJobId())).toList();
        Set<Long> appIds = apps.stream().map(ApplicationEntity::getId).collect(Collectors.toSet());
        List<ApplicationLogEntity> appLogs = logs.selectList(new LambdaQueryWrapper<ApplicationLogEntity>().eq(ApplicationLogEntity::getUserId, userId)).stream()
                .filter(l -> appIds.contains(l.getApplicationId())).toList();
        List<InterviewEntity> interviewFacts = interviews.selectList(new LambdaQueryWrapper<InterviewEntity>().eq(InterviewEntity::getUserId, userId)).stream()
                .filter(i -> i.getApplicationId() != null && appIds.contains(i.getApplicationId())).toList();
        List<OfferEntity> offerFacts = offers.selectList(new LambdaQueryWrapper<OfferEntity>().eq(OfferEntity::getUserId, userId)).stream()
                .filter(o -> appIds.contains(o.getApplicationId())).toList();
        List<JobMatchEntity> latestMatches = latest(matches.selectList(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId, userId)), cohortJobIds);

        long denominator = cohortJobIds.size();
        Set<Long> appliedJobs = apps.stream().map(ApplicationEntity::getJobId).collect(Collectors.toSet());
        Set<Long> repliedApps = reached(appLogs, REPLY); Set<Long> interviewApps = reached(appLogs, INTERVIEW);
        interviewFacts.forEach(i -> interviewApps.add(i.getApplicationId()));
        Set<Long> offerApps = offerFacts.stream().map(OfferEntity::getApplicationId).collect(Collectors.toSet());
        Set<Long> acceptedApps = offerFacts.stream().filter(o -> "ACCEPTED".equals(o.getStatus())).map(OfferEntity::getApplicationId).collect(Collectors.toSet());
        List<ConversionMetric> funnel = List.of(metric("DISCOVERED", "已发现", denominator, denominator),
                metric("EVALUATED", "已评估", latestMatches.size(), denominator), metric("APPLIED", "已投递", appliedJobs.size(), denominator),
                metric("REPLIED", "已回复", repliedApps.size(), denominator), metric("INTERVIEW", "进入面试", interviewApps.size(), denominator),
                metric("OFFER", "获得 Offer", offerApps.size(), denominator), metric("ACCEPTED", "已接受", acceptedApps.size(), denominator));

        Map<Long, JobEntity> jobById = allJobs.stream().collect(Collectors.toMap(JobEntity::getId, Function.identity()));
        Map<Long, JobSourceEntity> sourceById = sources.selectList(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getUserId, userId)).stream().collect(Collectors.toMap(JobSourceEntity::getId, Function.identity(), (a,b)->a));
        Set<Long> companyIds=allJobs.stream().map(JobEntity::getCompanyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, CompanyEntity> companyById = companyIds.isEmpty()?Map.of():companies.selectBatchIds(companyIds).stream().collect(Collectors.toMap(CompanyEntity::getId, Function.identity()));
        Set<Long> resumeVersionIds=apps.stream().map(ApplicationEntity::getResumeVersionId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long, ResumeVersionEntity> versionById = resumeVersionIds.isEmpty()?Map.of():versions.selectBatchIds(resumeVersionIds).stream().collect(Collectors.toMap(ResumeVersionEntity::getId, Function.identity()));
        Map<Long, ResumeEntity> resumeById = resumes.selectList(new LambdaQueryWrapper<ResumeEntity>().eq(ResumeEntity::getUserId, userId)).stream().collect(Collectors.toMap(ResumeEntity::getId, Function.identity()));
        Map<Long, JobMatchEntity> matchByJob = latestMatches.stream().collect(Collectors.toMap(JobMatchEntity::getJobId, Function.identity()));
        Map<String, List<ConversionMetric>> dimensions = new LinkedHashMap<>();
        dimensions.put("PLATFORM", groups(apps, a -> value(sourceById.get(a.getJobSourceId()), JobSourceEntity::getPlatform), repliedApps, interviewApps, offerApps));
        dimensions.put("JOB_DIRECTION", groups(apps, a -> value(jobById.get(a.getJobId()), j -> blank(j.getNormalizedTitle(), j.getTitle())), repliedApps, interviewApps, offerApps));
        dimensions.put("COMPANY", groups(apps, a -> { JobEntity j=jobById.get(a.getJobId()); return j == null ? "UNKNOWN" : value(companyById.get(j.getCompanyId()), CompanyEntity::getDisplayName); }, repliedApps, interviewApps, offerApps));
        dimensions.put("CITY", groups(apps, a -> value(jobById.get(a.getJobId()), JobEntity::getCity), repliedApps, interviewApps, offerApps));
        dimensions.put("SALARY_BAND", groups(apps, a -> salaryBand(jobById.get(a.getJobId())), repliedApps, interviewApps, offerApps));
        dimensions.put("RESUME_VERSION", groups(apps, a -> { ResumeVersionEntity v=versionById.get(a.getResumeVersionId()); ResumeEntity r=v==null?null:resumeById.get(v.getResumeId()); return v==null?"UNKNOWN":(r==null?"Resume":r.getName())+" · v"+v.getVersionNumber(); }, repliedApps, interviewApps, offerApps));
        dimensions.put("MATCH_SCORE_BUCKET", groups(apps, a -> matchBucket(matchByJob.get(a.getJobId())), repliedApps, interviewApps, offerApps));
        return new CompleteAnalyticsView(funnel, dimensions, java.time.OffsetDateTime.now(ZoneOffset.UTC), properties.getAnalyticsTimezone());
    }

    @Transactional
    public SnapshotView rebuild(Long userId, String idempotencyKey, SnapshotRequest request) {
        validateKey(idempotencyKey); range(request.from(), request.to());
        CompleteAnalyticsView result = calculate(userId, request.from(), request.to());
        String hash = sha256(json.write(Map.of("from", request.from(), "to", request.to(), "funnel", result.funnel(), "dimensions", result.dimensions())));
        AnalyticsSnapshotEntity existing = byKey(userId, idempotencyKey);
        if (existing != null) return sameHash(existing, hash);
        var rebuilt = legacy.rebuild(userId, request.from(), request.to());
        AnalyticsSnapshotEntity entity = new AnalyticsSnapshotEntity(); entity.setUserId(userId); entity.setSchemaVersion("PHASE9_V1");
        entity.setDateFrom(request.from()); entity.setDateTo(request.to()); entity.setInputHash(hash); entity.setIdempotencyKey(idempotencyKey.trim());
        entity.setResultJson(json.write(result)); entity.setRowCount(rebuilt.rowCount()); entity.setGeneratedAt(LocalDateTime.now(ZoneOffset.UTC));
        try { snapshots.insert(entity); }
        catch (DataIntegrityViolationException exception) { AnalyticsSnapshotEntity concurrent=byKey(userId,idempotencyKey); if(concurrent!=null)return sameHash(concurrent,hash); throw exception; }
        audit.record(userId, "ANALYTICS_SNAPSHOT_CREATE", "ANALYTICS_SNAPSHOT", entity.getPublicId());
        return view(entity);
    }

    public List<SnapshotView> snapshots(Long userId) { return snapshots.selectList(new LambdaQueryWrapper<AnalyticsSnapshotEntity>().eq(AnalyticsSnapshotEntity::getUserId,userId).orderByDesc(AnalyticsSnapshotEntity::getGeneratedAt)).stream().map(this::view).toList(); }
    public SnapshotView snapshot(Long userId, String publicId) { AnalyticsSnapshotEntity value=snapshots.selectOne(new LambdaQueryWrapper<AnalyticsSnapshotEntity>().eq(AnalyticsSnapshotEntity::getUserId,userId).eq(AnalyticsSnapshotEntity::getPublicId,publicId).last("LIMIT 1")); if(value==null)throw new ResourceNotFoundException("Analytics Snapshot"); return view(value); }

    private SnapshotView view(AnalyticsSnapshotEntity value) { try { return new SnapshotView(value.getPublicId(), value.getSchemaVersion(), value.getDateFrom(), value.getDateTo(), value.getInputHash(), value.getRowCount(), objectMapper.readValue(value.getResultJson(), CompleteAnalyticsView.class), value.getGeneratedAt().atOffset(ZoneOffset.UTC)); } catch(JsonProcessingException e){throw new IllegalStateException("Stored analytics snapshot JSON is invalid",e);} }
    private SnapshotView sameHash(AnalyticsSnapshotEntity value,String hash){if(!value.getInputHash().equals(hash))throw new BusinessException(4099101,"Idempotency key was already used with different analytics input",HttpStatus.CONFLICT);return view(value);}
    private AnalyticsSnapshotEntity byKey(Long userId,String key){return snapshots.selectOne(new LambdaQueryWrapper<AnalyticsSnapshotEntity>().eq(AnalyticsSnapshotEntity::getUserId,userId).eq(AnalyticsSnapshotEntity::getIdempotencyKey,key.trim()).last("LIMIT 1"));}

    static ConversionMetric metric(String key,String label,long numerator,long denominator){return new ConversionMetric(key,label,numerator,denominator,denominator,denominator==0?null:BigDecimal.valueOf(numerator).multiply(new BigDecimal("100")).divide(BigDecimal.valueOf(denominator),2,RoundingMode.HALF_UP),denominator<5);}
    private static List<ConversionMetric> groups(List<ApplicationEntity> apps, Function<ApplicationEntity,String> classifier,
                                                  Set<Long> replied, Set<Long> interviewed, Set<Long> offered) {
        Map<String,List<ApplicationEntity>> grouped=apps.stream().collect(Collectors.groupingBy(
                a->blank(classifier.apply(a),"UNKNOWN"),LinkedHashMap::new,Collectors.toList()));
        List<ConversionMetric> result = new ArrayList<>();
        grouped.forEach((group, values) -> {
            result.add(stageMetric(group, "REPLIED", "Replied", values, replied));
            result.add(stageMetric(group, "INTERVIEW", "Interview", values, interviewed));
            result.add(stageMetric(group, "OFFER", "Offer", values, offered));
        });
        return result.stream().sorted(Comparator.comparing(ConversionMetric::denominator).reversed()
                .thenComparing(ConversionMetric::key)).toList();
    }
    private static ConversionMetric stageMetric(String group, String stage, String stageLabel,
                                                  List<ApplicationEntity> values, Set<Long> reached) {
        return metric(group + "::" + stage, group + " · " + stageLabel,
                values.stream().filter(a -> reached.contains(a.getId())).count(), values.size());
    }
    private static Set<Long> reached(List<ApplicationLogEntity> logs,Set<String> statuses){return logs.stream().filter(l->statuses.contains(l.getToStatus())).map(ApplicationLogEntity::getApplicationId).collect(Collectors.toCollection(LinkedHashSet::new));}
    private static List<JobMatchEntity> latest(List<JobMatchEntity> values,Set<Long> jobIds){Map<Long,JobMatchEntity> result=new LinkedHashMap<>();values.stream().filter(v->jobIds.contains(v.getJobId())).sorted(Comparator.comparing(JobMatchEntity::getEvaluatedAt,Comparator.nullsLast(Comparator.reverseOrder())).thenComparing(JobMatchEntity::getId,Comparator.reverseOrder())).forEach(v->result.putIfAbsent(v.getJobId(),v));return new ArrayList<>(result.values());}
    private static String matchBucket(JobMatchEntity value){if(value==null||value.getOverallScore()==null)return "UNKNOWN";int score=value.getOverallScore().intValue();return score>=90?"90-100":score>=80?"80-89":score>=70?"70-79":score>=60?"60-69":"0-59";}
    private static String salaryBand(JobEntity job){if(job==null||job.getSalaryMax()==null)return "UNKNOWN";BigDecimal max=job.getSalaryMax();return max.compareTo(new BigDecimal("15000"))<0?"<15K":max.compareTo(new BigDecimal("30000"))<0?"15K-30K":max.compareTo(new BigDecimal("50000"))<0?"30K-50K":"50K+";}
    private static <T> String value(T value,Function<T,String> reader){return value==null?"UNKNOWN":blank(reader.apply(value),"UNKNOWN");}
    private static String blank(String value,String fallback){return value==null||value.isBlank()?fallback:value.trim();}
    private static boolean within(LocalDateTime value,LocalDate from,LocalDate to,ZoneId zone){if(value==null)return false;LocalDate day=value.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate();return !day.isBefore(from)&&!day.isAfter(to);}
    private static void range(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to))throw new ValidationException("Analytics from must be on or before to");if(ChronoUnit.DAYS.between(from,to)+1>366)throw new ValidationException("Analytics range cannot exceed 366 days");}
    private static void validateKey(String key){if(key==null||key.isBlank()||key.length()>120)throw new ValidationException("Idempotency-Key header is required and must be at most 120 characters");}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
