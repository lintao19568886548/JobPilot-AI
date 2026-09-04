package com.jobpilot.learning.service;

import static com.jobpilot.learning.dto.LearningDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.mapper.InterviewMapper;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.learning.domain.FeedbackEntity;
import com.jobpilot.learning.domain.FeedbackRebuildRunEntity;
import com.jobpilot.learning.mapper.FeedbackMapper;
import com.jobpilot.learning.mapper.FeedbackRebuildRunMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.offer.domain.OfferEntity;
import com.jobpilot.offer.mapper.OfferMapper;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.domain.RecommendationEventEntity;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.mapper.RecommendationEventMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
public class FeedbackService {
    public static final String FEATURE_SCHEMA = "ltr-features-v1";
    private static final Map<String,BigDecimal> LABELS=Map.of("VIEWED",bd("0.10"),"APPLIED",bd("0.25"),"REPLIED",bd("0.50"),"INTERVIEW",bd("0.75"),"OFFER",BigDecimal.ONE);
    private static final Set<String> REPLIED=Set.of("REPLIED","WRITTEN_TEST","INTERVIEW_1","INTERVIEW_2","INTERVIEW_3","HR_INTERVIEW","OFFER");
    private static final Set<String> INTERVIEW=Set.of("INTERVIEW_1","INTERVIEW_2","INTERVIEW_3","HR_INTERVIEW");
    private final FeedbackMapper feedback; private final FeedbackRebuildRunMapper runs;
    private final ApplicationMapper applications; private final ApplicationLogMapper logs;
    private final RecommendationEventMapper recommendationEvents; private final JobRecommendationMapper recommendations;
    private final InterviewMapper interviews; private final OfferMapper offers; private final JobMatchMapper matches;
    private final JobMapper jobs; private final CompanyMapper companies; private final JsonCodec json; private final AuditService audit;

    public FeedbackService(FeedbackMapper feedback, FeedbackRebuildRunMapper runs, ApplicationMapper applications,
                           ApplicationLogMapper logs, RecommendationEventMapper recommendationEvents,
                           JobRecommendationMapper recommendations, InterviewMapper interviews, OfferMapper offers,
                           JobMatchMapper matches, JobMapper jobs, CompanyMapper companies, JsonCodec json, AuditService audit) {
        this.feedback=feedback;this.runs=runs;this.applications=applications;this.logs=logs;this.recommendationEvents=recommendationEvents;
        this.recommendations=recommendations;this.interviews=interviews;this.offers=offers;this.matches=matches;this.jobs=jobs;
        this.companies=companies;this.json=json;this.audit=audit;
    }

    record Fact(Long jobId, Long applicationId, String sourceType, String sourcePublicId,
                String eventType, LocalDateTime occurredAt, String source) { }

    @Transactional
    public FeedbackRebuildView rebuild(Long userId,String key,FeedbackRebuildRequest request){
        validateKey(key);range(request.from(),request.to());String requestHash=LearningHash.sha256(json.write(Map.of("from",request.from(),"to",request.to(),"schema",FEATURE_SCHEMA)));
        FeedbackRebuildRunEntity existing=byKey(userId,key);if(existing!=null){if(!existing.getRequestHash().equals(requestHash))throw new BusinessException(4091001,"Idempotency key was used with different feedback input",HttpStatus.CONFLICT);return view(existing);}
        LocalDateTime started=LocalDateTime.now();List<Fact> facts=facts(userId,request.from(),request.to());
        Map<Long,List<JobMatchEntity>> byJob=matches.selectList(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId,userId))
                .stream().collect(Collectors.groupingBy(JobMatchEntity::getJobId));
        Map<Long,JobEntity> jobById=jobs.selectList(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId,userId)).stream().collect(Collectors.toMap(JobEntity::getId,Function.identity()));
        Set<Long> companyIds=jobById.values().stream().map(JobEntity::getCompanyId).filter(java.util.Objects::nonNull).collect(Collectors.toSet());
        Map<Long,CompanyEntity> companyById=companyIds.isEmpty()?Map.of():companies.selectBatchIds(companyIds).stream().collect(Collectors.toMap(CompanyEntity::getId,Function.identity()));
        int created=0,reused=0,skipped=0;
        for(Fact fact:facts){JobMatchEntity match=historical(byJob.getOrDefault(fact.jobId(),List.of()),fact.occurredAt());if(match==null){skipped++;continue;}
            JobEntity job=jobById.get(fact.jobId());Map<String,Object> snapshot=snapshot(match,job,job==null?null:companyById.get(job.getCompanyId()),fact);
            String snapshotJson=json.write(snapshot),hash=LearningHash.sha256(snapshotJson);
            FeedbackEntity entity=new FeedbackEntity();entity.setUserId(userId);entity.setJobId(fact.jobId());entity.setJobMatchId(match.getId());entity.setApplicationId(fact.applicationId());entity.setSourceType(fact.sourceType());entity.setSourcePublicId(fact.sourcePublicId());entity.setEventType(fact.eventType());entity.setLabelValue(LABELS.get(fact.eventType()));entity.setOccurredAt(fact.occurredAt());entity.setFeatureSchemaVersion(FEATURE_SCHEMA);entity.setFeatureSnapshotJson(snapshotJson);entity.setFeatureHash(hash);entity.setSource(fact.source());
            try{feedback.insert(entity);created++;}catch(DataIntegrityViolationException duplicate){FeedbackEntity same=feedback.selectOne(new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getUserId,userId).eq(FeedbackEntity::getSourceType,fact.sourceType()).eq(FeedbackEntity::getSourcePublicId,fact.sourcePublicId()).eq(FeedbackEntity::getEventType,fact.eventType()).last("LIMIT 1"));if(same==null||!same.getFeatureHash().equals(hash))throw new BusinessException(4091002,"Feedback source already exists with different immutable features",HttpStatus.CONFLICT);reused++;}
        }
        FeedbackRebuildRunEntity run=new FeedbackRebuildRunEntity();run.setUserId(userId);run.setIdempotencyKey(key.trim());run.setRequestHash(requestHash);run.setDateFrom(request.from());run.setDateTo(request.to());run.setCreatedCount(created);run.setReusedCount(reused);run.setSkippedNoHistoricalMatch(skipped);run.setLeakageSafe(true);run.setStatus("SUCCEEDED");run.setStartedAt(started);run.setFinishedAt(LocalDateTime.now());runs.insert(run);audit.record(userId,"FEEDBACK_REBUILD","FEEDBACK_REBUILD_RUN",run.getPublicId());return view(run);
    }

    public FeedbackSummary summary(Long userId,int minimum){List<FeedbackEntity> values=feedback.selectList(new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getUserId,userId));Map<String,Long> counts=values.stream().collect(Collectors.groupingBy(FeedbackEntity::getEventType,LinkedHashMap::new,Collectors.counting()));LABELS.keySet().forEach(key->counts.putIfAbsent(key,0L));LocalDateTime latest=values.stream().map(FeedbackEntity::getOccurredAt).max(Comparator.naturalOrder()).orElse(null);return new FeedbackSummary(values.size(),counts,FEATURE_SCHEMA,minimum,values.size()>=minimum,latest);}
    public List<FeedbackEntity> range(Long userId,LocalDate from,LocalDate to){return feedback.selectList(new LambdaQueryWrapper<FeedbackEntity>().eq(FeedbackEntity::getUserId,userId).ge(FeedbackEntity::getOccurredAt,from.atStartOfDay()).lt(FeedbackEntity::getOccurredAt,to.plusDays(1).atStartOfDay()).orderByAsc(FeedbackEntity::getOccurredAt).orderByAsc(FeedbackEntity::getId));}

    private List<Fact> facts(Long userId,LocalDate from,LocalDate to){List<Fact> result=new ArrayList<>();
        Map<Long,JobRecommendationEntity> recs=recommendations.selectList(new LambdaQueryWrapper<JobRecommendationEntity>().eq(JobRecommendationEntity::getUserId,userId)).stream().collect(Collectors.toMap(JobRecommendationEntity::getId,Function.identity()));
        recommendationEvents.selectList(new LambdaQueryWrapper<RecommendationEventEntity>().eq(RecommendationEventEntity::getUserId,userId).eq(RecommendationEventEntity::getEventType,"VIEWED")).forEach(event->{JobRecommendationEntity rec=recs.get(event.getRecommendationId());if(rec!=null&&within(event.getOccurredAt(),from,to))result.add(new Fact(rec.getJobId(),null,"RECOMMENDATION_EVENT",event.getPublicId(),"VIEWED",event.getOccurredAt(),"USER"));});
        List<ApplicationEntity> appValues=applications.selectList(new LambdaQueryWrapper<ApplicationEntity>().eq(ApplicationEntity::getUserId,userId));Map<Long,ApplicationEntity> appById=appValues.stream().collect(Collectors.toMap(ApplicationEntity::getId,Function.identity()));
        appValues.forEach(app->{if(app.getAppliedAt()!=null&&within(app.getAppliedAt(),from,to))result.add(new Fact(app.getJobId(),app.getId(),"APPLICATION",app.getPublicId(),"APPLIED",app.getAppliedAt(),app.getApplicationMode()));});
        logs.selectList(new LambdaQueryWrapper<ApplicationLogEntity>().eq(ApplicationLogEntity::getUserId,userId)).forEach(log->{ApplicationEntity app=appById.get(log.getApplicationId());String type=event(log.getToStatus());if(app!=null&&type!=null&&within(log.getOccurredAt(),from,to))result.add(new Fact(app.getJobId(),app.getId(),"APPLICATION_LOG",log.getPublicId(),type,log.getOccurredAt(),log.getSource()));});
        interviews.selectList(new LambdaQueryWrapper<InterviewEntity>().eq(InterviewEntity::getUserId,userId)).forEach(value->{if(value.getApplicationId()!=null&&value.getJobId()!=null&&within(value.getCreatedAt(),from,to))result.add(new Fact(value.getJobId(),value.getApplicationId(),"INTERVIEW",value.getPublicId(),"INTERVIEW",value.getCreatedAt(),"USER"));});
        offers.selectList(new LambdaQueryWrapper<OfferEntity>().eq(OfferEntity::getUserId,userId)).forEach(value->{if(within(value.getCreatedAt(),from,to))result.add(new Fact(value.getJobId(),value.getApplicationId(),"OFFER",value.getPublicId(),"OFFER",value.getCreatedAt(),"USER"));});return result;}
    private String event(String status){if(status==null)return null;if("OFFER".equals(status))return"OFFER";if(INTERVIEW.contains(status))return"INTERVIEW";if(REPLIED.contains(status))return"REPLIED";return null;}
    private JobMatchEntity historical(List<JobMatchEntity> values,LocalDateTime occurred){return values.stream().filter(match->match.getEvaluatedAt()!=null&&!match.getEvaluatedAt().isAfter(occurred)).max(Comparator.comparing(JobMatchEntity::getEvaluatedAt).thenComparing(JobMatchEntity::getId)).orElse(null);}
    private Map<String,Object> snapshot(JobMatchEntity match,JobEntity job,CompanyEntity company,Fact fact){Map<String,Object> value=new LinkedHashMap<>();value.put("schemaVersion",FEATURE_SCHEMA);value.put("features",LearningRankingService.features(match));value.put("penalty",match.getPenaltyScore());value.put("baselineOverall",match.getOverallScore());value.put("matchId",match.getPublicId());value.put("matchEvaluatedAt",match.getEvaluatedAt());value.put("outcomeOccurredAt",fact.occurredAt());value.put("leakageSafe",!match.getEvaluatedAt().isAfter(fact.occurredAt()));value.put("jobDirection",job==null?"UNKNOWN":job.getNormalizedTitle());value.put("company",company==null?"UNKNOWN":company.getDisplayName());value.put("city",job==null?"UNKNOWN":job.getCity());value.put("resumeVersionId",match.getResumeVersionId());value.put("embeddingModel",match.getEmbeddingModel());return value;}
    private FeedbackRebuildRunEntity byKey(Long userId,String key){return runs.selectOne(new LambdaQueryWrapper<FeedbackRebuildRunEntity>().eq(FeedbackRebuildRunEntity::getUserId,userId).eq(FeedbackRebuildRunEntity::getIdempotencyKey,key.trim()).last("LIMIT 1"));}
    private FeedbackRebuildView view(FeedbackRebuildRunEntity value){return new FeedbackRebuildView(value.getPublicId(),value.getDateFrom(),value.getDateTo(),value.getCreatedCount(),value.getReusedCount(),value.getSkippedNoHistoricalMatch(),Boolean.TRUE.equals(value.getLeakageSafe()),value.getStatus(),value.getFinishedAt());}
    private static void range(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to))throw new ValidationException("Feedback from must be on or before to");if(ChronoUnit.DAYS.between(from,to)>730)throw new ValidationException("Feedback range cannot exceed 731 days");}
    static void validateKey(String key){if(key==null||key.isBlank()||key.length()>120)throw new ValidationException("Idempotency-Key is required and must be at most 120 characters");}
    private static boolean within(LocalDateTime value,LocalDate from,LocalDate to){return value!=null&&!value.toLocalDate().isBefore(from)&&!value.toLocalDate().isAfter(to);}
    private static BigDecimal bd(String value){return new BigDecimal(value);}
}
