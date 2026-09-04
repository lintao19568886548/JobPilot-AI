package com.jobpilot.learning.service;

import static com.jobpilot.learning.dto.LearningDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.learning.domain.FeedbackEntity;
import com.jobpilot.learning.domain.LtrModelVersionEntity;
import com.jobpilot.learning.domain.LtrShadowResultEntity;
import com.jobpilot.learning.domain.LtrTrainingRunEntity;
import com.jobpilot.learning.mapper.LtrModelVersionMapper;
import com.jobpilot.learning.mapper.LtrShadowResultMapper;
import com.jobpilot.learning.mapper.LtrTrainingRunMapper;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.recommendation.domain.JobRecommendationEntity;
import com.jobpilot.recommendation.mapper.JobRecommendationMapper;
import com.jobpilot.recommendation.service.RecommendationProjectionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningService {
    private final FeedbackService feedback; private final LtrTrainingRunMapper runs; private final LtrModelVersionMapper models;
    private final LtrShadowResultMapper shadows; private final JobRecommendationMapper recommendations; private final JobMatchMapper matches;
    private final JobMapper jobs; private final RecommendationProjectionService projection; private final LearningRankingService ranking;
    private final ObjectMapper objectMapper; private final JsonCodec json; private final AuditService audit; private final int minimumSample;

    public LearningService(FeedbackService feedback,LtrTrainingRunMapper runs,LtrModelVersionMapper models,LtrShadowResultMapper shadows,
                           JobRecommendationMapper recommendations,JobMatchMapper matches,JobMapper jobs,
                           RecommendationProjectionService projection,LearningRankingService ranking,ObjectMapper objectMapper,
                           JsonCodec json,AuditService audit,@Value("${jobpilot.learning.minimum-sample:30}") int minimumSample){
        this.feedback=feedback;this.runs=runs;this.models=models;this.shadows=shadows;this.recommendations=recommendations;this.matches=matches;
        this.jobs=jobs;this.projection=projection;this.ranking=ranking;this.objectMapper=objectMapper;this.json=json;this.audit=audit;this.minimumSample=Math.max(30,minimumSample);
    }

    @Transactional
    public TrainResult train(Long userId,String key,TrainRequest request){FeedbackService.validateKey(key);range(request.from(),request.to());String hash=LearningHash.sha256(json.write(Map.of("from",request.from(),"to",request.to(),"schema",FeedbackService.FEATURE_SCHEMA,"minimum",minimumSample)));
        LtrTrainingRunEntity prior=runByKey(userId,key);if(prior!=null){if(!prior.getRequestHash().equals(hash))throw conflict("Training idempotency key was used with different input");return new TrainResult(runView(prior),modelByRun(prior.getId()));}
        List<FeedbackEntity> samples=feedback.range(userId,request.from(),request.to());List<LearningMath.Point> points=samples.stream().map(this::point).toList();LearningMath.Split split=LearningMath.timeSplit(points);boolean enough=points.size()>=minimumSample&&split.validation().size()>=5;
        LearningMath.Evaluation evaluation=LearningMath.evaluate(split);boolean leakage=samples.stream().allMatch(this::leakageSafe)&&split.leakageSafe();String status=!enough?"INSUFFICIENT_DATA":leakage&&evaluation.eligible()?"EVALUATED":"REJECTED";
        Map<String,Object> metrics=new LinkedHashMap<>();metrics.put("baselineNdcgAt10",evaluation.baselineNdcg());metrics.put("candidateNdcgAt10",evaluation.candidateNdcg());metrics.put("validationPositiveRate",evaluation.positiveRate());metrics.put("minimumSample",minimumSample);metrics.put("eligible",enough&&leakage&&evaluation.eligible());metrics.put("timeSplit","80/20_CHRONOLOGICAL");metrics.put("leakageSafe",leakage);
        Map<String,Object> parameters=Map.of("schemaVersion",FeedbackService.FEATURE_SCHEMA,"weights",evaluation.weights(),"method","CONSERVATIVE_80_BASELINE_20_SIGNAL");
        LtrTrainingRunEntity run=new LtrTrainingRunEntity();run.setUserId(userId);run.setIdempotencyKey(key.trim());run.setRequestHash(hash);run.setDateFrom(request.from());run.setDateTo(request.to());run.setCutoffAt(split.cutoffAt());run.setMinimumSample(minimumSample);run.setSampleCount(points.size());run.setTrainCount(split.train().size());run.setValidationCount(split.validation().size());run.setStatus(status);run.setFeatureSchemaVersion(FeedbackService.FEATURE_SCHEMA);run.setLeakageSafe(leakage);run.setMetricsJson(json.write(metrics));run.setParametersJson(json.write(parameters));run.setErrorMessageSafe(enough?null:"At least "+minimumSample+" immutable feedback samples and 5 validation samples are required");run.setStartedAt(LocalDateTime.now());run.setFinishedAt(LocalDateTime.now());runs.insert(run);
        LtrModelVersionEntity model=null;if(enough){model=new LtrModelVersionEntity();model.setUserId(userId);model.setTrainingRunId(run.getId());model.setVersionNo(nextVersion(userId));model.setName(request.name()==null||request.name().isBlank()?"LTR v"+model.getVersionNo():request.name().trim());model.setModelType("CONSERVATIVE_LINEAR");model.setFeatureSchemaJson(json.write(Map.of("version",FeedbackService.FEATURE_SCHEMA,"features",LearningMath.FEATURES)));model.setParametersJson(json.write(parameters));Map<String,Object> window=new LinkedHashMap<>();window.put("from",request.from());window.put("to",request.to());window.put("cutoffAt",split.cutoffAt());model.setTrainingWindowJson(json.write(window));model.setSampleCount(points.size());model.setEvaluationJson(json.write(metrics));model.setActivationEligible(leakage&&evaluation.eligible());model.setStatus(Boolean.TRUE.equals(model.getActivationEligible())?"DRAFT":"REJECTED");models.insert(model);}
        audit.record(userId,"LTR_TRAIN","LTR_TRAINING_RUN",run.getPublicId());return new TrainResult(runView(run),model==null?null:modelView(model));}

    @Transactional
    public ModelView shadow(Long userId,String publicId,String key){FeedbackService.validateKey(key);LtrModelVersionEntity model=ownedModel(userId,publicId);if(!Boolean.TRUE.equals(model.getActivationEligible())||!List.of("DRAFT","SHADOW").contains(model.getStatus()))throw conflict("Only an eligible draft model can run in shadow mode");List<LtrShadowResultEntity> existing=shadowEntities(model.getId());if(existing.isEmpty()){
            List<JobRecommendationEntity> current=projection.synchronize(userId);Map<Long,JobMatchEntity> latest=latestMatches(userId);Map<Long,JobEntity> jobById=jobs.selectList(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId,userId)).stream().collect(Collectors.toMap(JobEntity::getId,Function.identity()));Map<String,BigDecimal> weights=ranking.weights(model);
            List<JobRecommendationEntity> currentOrder=current.stream().filter(v->v.getRankScore()!=null).sorted(Comparator.comparing(JobRecommendationEntity::getRankScore).reversed().thenComparing(JobRecommendationEntity::getId)).toList();Map<Long,Integer> currentRanks=positions(currentOrder,JobRecommendationEntity::getRankScore);
            Map<Long,BigDecimal> shadowScores=new LinkedHashMap<>();for(JobRecommendationEntity rec:current){JobMatchEntity match=latest.get(rec.getJobId());shadowScores.put(rec.getId(),match==null?null:LearningMath.adjustedScore(LearningRankingService.features(match),weights,match.getPenaltyScore()));}
            List<JobRecommendationEntity> shadowOrder=current.stream().filter(v->shadowScores.get(v.getId())!=null).sorted(Comparator.comparing((JobRecommendationEntity v)->shadowScores.get(v.getId())).reversed().thenComparing(JobRecommendationEntity::getId)).toList();Map<Long,Integer> shadowRanks=positions(shadowOrder,v->shadowScores.get(v.getId()));
            for(JobRecommendationEntity rec:currentOrder){BigDecimal next=shadowScores.get(rec.getId());if(next==null)continue;LtrShadowResultEntity item=new LtrShadowResultEntity();item.setUserId(userId);item.setModelVersionId(model.getId());item.setRecommendationId(rec.getId());item.setJobId(rec.getJobId());item.setCurrentScore(rec.getRankScore());item.setShadowScore(next);item.setScoreDelta(next.subtract(rec.getRankScore()).setScale(2,java.math.RoundingMode.HALF_UP));item.setCurrentRankPosition(currentRanks.get(rec.getId()));item.setShadowRankPosition(shadowRanks.get(rec.getId()));JobEntity job=jobById.get(rec.getJobId());item.setFeatureSnapshotJson(json.write(Map.of("matchId",latest.get(rec.getJobId()).getPublicId(),"jobId",job==null?"UNKNOWN":job.getPublicId(),"weights",weights)));shadows.insert(item);}
        }
        if("DRAFT".equals(model.getStatus())){model.setStatus("SHADOW");model.setShadowedAt(LocalDateTime.now());models.updateById(model);}audit.record(userId,"LTR_SHADOW","LTR_MODEL_VERSION",publicId);return modelView(ownedModel(userId,publicId));}

    @Transactional
    public ModelView activate(Long userId,String publicId,int expectedVersion){LtrModelVersionEntity model=ownedModel(userId,publicId);version(model,expectedVersion);if(!Boolean.TRUE.equals(model.getActivationEligible())||!"SHADOW".equals(model.getStatus())||shadowEntities(model.getId()).isEmpty())throw conflict("Model must pass evaluation and shadow ranking before activation");LtrModelVersionEntity active=ranking.active(userId);if(active!=null&&!active.getId().equals(model.getId())){active.setStatus("RETIRED");active.setRetiredAt(LocalDateTime.now());models.updateById(active);}model.setStatus("ACTIVE");model.setActivatedAt(LocalDateTime.now());model.setRetiredAt(null);models.updateById(model);projection.synchronize(userId);audit.record(userId,"LTR_MODEL_ACTIVATE","LTR_MODEL_VERSION",publicId);return modelView(ownedModel(userId,publicId));}

    @Transactional
    public RollbackView rollback(Long userId,String publicId,int expectedVersion){LtrModelVersionEntity current=ownedModel(userId,publicId);version(current,expectedVersion);if(!"ACTIVE".equals(current.getStatus()))throw conflict("Only the active model can be rolled back");LtrModelVersionEntity previous=models.selectOne(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getUserId,userId).eq(LtrModelVersionEntity::getStatus,"RETIRED").lt(LtrModelVersionEntity::getVersionNo,current.getVersionNo()).orderByDesc(LtrModelVersionEntity::getVersionNo).last("LIMIT 1"));current.setStatus("RETIRED");current.setRetiredAt(LocalDateTime.now());models.updateById(current);if(previous!=null){previous.setStatus("ACTIVE");previous.setActivatedAt(LocalDateTime.now());previous.setRetiredAt(null);models.updateById(previous);}projection.synchronize(userId);audit.record(userId,"LTR_MODEL_ROLLBACK","LTR_MODEL_VERSION",publicId);return new RollbackView(modelView(current),previous==null?null:modelView(previous),previous==null?LearningRankingService.BASELINE_VERSION:"ltr-v"+previous.getVersionNo());}

    public List<ModelView> models(Long userId){return models.selectList(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getUserId,userId).orderByDesc(LtrModelVersionEntity::getVersionNo)).stream().map(this::modelView).toList();}
    public ModelView model(Long userId,String id){return modelView(ownedModel(userId,id));}
    public LearningDashboard dashboard(Long userId){List<LtrTrainingRunEntity> history=runs.selectList(new LambdaQueryWrapper<LtrTrainingRunEntity>().eq(LtrTrainingRunEntity::getUserId,userId).orderByDesc(LtrTrainingRunEntity::getCreatedAt));LtrModelVersionEntity active=ranking.active(userId);long shadowCount=shadows.selectCount(new LambdaQueryWrapper<LtrShadowResultEntity>().eq(LtrShadowResultEntity::getUserId,userId));return new LearningDashboard(feedback.summary(userId,minimumSample),active==null?null:modelView(active),history.isEmpty()?null:runView(history.getFirst()),models.selectCount(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getUserId,userId)).intValue(),(int)shadowCount,active==null?LearningRankingService.BASELINE_VERSION:"ltr-v"+active.getVersionNo(),List.of("仅按时间顺序留出验证集","必须由用户明确启用","影子运行不会改变线上排序","支持一键回滚","不执行任何外部操作"));}

    private LearningMath.Point point(FeedbackEntity value){try{Map<String,Object> root=objectMapper.readValue(value.getFeatureSnapshotJson(),new TypeReference<>(){});Map<String,BigDecimal> features=objectMapper.convertValue(root.get("features"),new TypeReference<LinkedHashMap<String,BigDecimal>>(){});BigDecimal penalty=objectMapper.convertValue(root.get("penalty"),BigDecimal.class);BigDecimal baseline=objectMapper.convertValue(root.get("baselineOverall"),BigDecimal.class);return new LearningMath.Point(features,penalty,baseline,value.getLabelValue(),value.getOccurredAt());}catch(Exception e){throw new IllegalStateException("Stored feedback feature snapshot is invalid",e);}}
    private boolean leakageSafe(FeedbackEntity value){try{var node=objectMapper.readTree(value.getFeatureSnapshotJson());return node.path("leakageSafe").asBoolean(false)&&!LocalDateTime.parse(node.path("matchEvaluatedAt").asText()).isAfter(value.getOccurredAt());}catch(Exception e){return false;}}
    private Map<Long,JobMatchEntity> latestMatches(Long userId){Map<Long,JobMatchEntity> result=new LinkedHashMap<>();matches.selectList(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId,userId).orderByDesc(JobMatchEntity::getEvaluatedAt).orderByDesc(JobMatchEntity::getId)).forEach(v->result.putIfAbsent(v.getJobId(),v));return result;}
    private Map<Long,Integer> positions(List<JobRecommendationEntity> values,Function<JobRecommendationEntity,BigDecimal> score){Map<Long,Integer> result=new LinkedHashMap<>();for(int i=0;i<values.size();i++)result.put(values.get(i).getId(),i+1);return result;}
    private ModelView modelByRun(Long runId){LtrModelVersionEntity model=models.selectOne(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getTrainingRunId,runId).last("LIMIT 1"));return model==null?null:modelView(model);}
    private LtrTrainingRunEntity runByKey(Long userId,String key){return runs.selectOne(new LambdaQueryWrapper<LtrTrainingRunEntity>().eq(LtrTrainingRunEntity::getUserId,userId).eq(LtrTrainingRunEntity::getIdempotencyKey,key.trim()).last("LIMIT 1"));}
    private int nextVersion(Long userId){return models.selectList(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getUserId,userId)).stream().mapToInt(LtrModelVersionEntity::getVersionNo).max().orElse(0)+1;}
    private LtrModelVersionEntity ownedModel(Long userId,String id){LtrModelVersionEntity value=models.selectOne(new LambdaQueryWrapper<LtrModelVersionEntity>().eq(LtrModelVersionEntity::getUserId,userId).eq(LtrModelVersionEntity::getPublicId,id).last("LIMIT 1"));if(value==null)throw new ResourceNotFoundException("LTR model");return value;}
    private List<LtrShadowResultEntity> shadowEntities(Long modelId){return shadows.selectList(new LambdaQueryWrapper<LtrShadowResultEntity>().eq(LtrShadowResultEntity::getModelVersionId,modelId).orderByAsc(LtrShadowResultEntity::getShadowRankPosition));}
    private ModelView modelView(LtrModelVersionEntity value){Map<Long,JobRecommendationEntity> recs=shadowEntities(value.getId()).isEmpty()?Map.of():recommendations.selectBatchIds(shadowEntities(value.getId()).stream().map(LtrShadowResultEntity::getRecommendationId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(JobRecommendationEntity::getId,Function.identity()));Map<Long,JobEntity> jobById=shadowEntities(value.getId()).isEmpty()?Map.of():jobs.selectBatchIds(shadowEntities(value.getId()).stream().map(LtrShadowResultEntity::getJobId).collect(Collectors.toSet())).stream().collect(Collectors.toMap(JobEntity::getId,Function.identity()));List<ShadowResultView> result=shadowEntities(value.getId()).stream().map(v->new ShadowResultView(v.getPublicId(),recs.get(v.getRecommendationId())==null?null:recs.get(v.getRecommendationId()).getPublicId(),jobById.get(v.getJobId())==null?null:jobById.get(v.getJobId()).getPublicId(),v.getCurrentScore(),v.getShadowScore(),v.getScoreDelta(),v.getCurrentRankPosition(),v.getShadowRankPosition())).toList();return new ModelView(value.getPublicId(),value.getVersionNo(),value.getVersion(),value.getName(),value.getModelType(),value.getSampleCount(),value.getStatus(),Boolean.TRUE.equals(value.getActivationEligible()),json.readNode(value.getParametersJson()),json.readNode(value.getEvaluationJson()),value.getShadowedAt(),value.getActivatedAt(),value.getRetiredAt(),value.getCreatedAt(),result);}
    private TrainingRunView runView(LtrTrainingRunEntity value){return new TrainingRunView(value.getPublicId(),value.getDateFrom(),value.getDateTo(),value.getCutoffAt(),value.getMinimumSample(),value.getSampleCount(),value.getTrainCount(),value.getValidationCount(),value.getStatus(),Boolean.TRUE.equals(value.getLeakageSafe()),json.readNode(value.getMetricsJson()),json.readNode(value.getParametersJson()),value.getErrorMessageSafe(),value.getFinishedAt());}
    private static void version(LtrModelVersionEntity value,int expected){if(value.getVersion()!=expected)throw conflict("LTR model version conflict");}
    private static BusinessException conflict(String message){return new BusinessException(4091003,message,HttpStatus.CONFLICT);}
    private static void range(LocalDate from,LocalDate to){if(from==null||to==null||from.isAfter(to)||ChronoUnit.DAYS.between(from,to)>730)throw new ValidationException("Training range is invalid or exceeds 731 days");}
}
