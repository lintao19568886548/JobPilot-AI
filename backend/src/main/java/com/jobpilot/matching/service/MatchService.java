package com.jobpilot.matching.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
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
import com.jobpilot.common.config.MatchingProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.matching.client.MatchingAiClient;
import com.jobpilot.matching.client.MatchingAiClient.AiCallResult;
import com.jobpilot.matching.domain.AiCallLogEntity;
import com.jobpilot.matching.domain.EntityEmbeddingEntity;
import com.jobpilot.matching.domain.JobMatchDetailEntity;
import com.jobpilot.matching.domain.JobMatchEntity;
import com.jobpilot.matching.domain.MatchConfigEntity;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.domain.OutboxEventEntity;
import com.jobpilot.matching.dto.MatchingDtos.AiAnalysisItem;
import com.jobpilot.matching.dto.MatchingDtos.AiMatchResponse;
import com.jobpilot.matching.dto.MatchingDtos.EmbeddingRecord;
import com.jobpilot.matching.dto.MatchingDtos.EvidenceItemView;
import com.jobpilot.matching.dto.MatchingDtos.HardFilterEvidenceView;
import com.jobpilot.matching.dto.MatchingDtos.JobMatchView;
import com.jobpilot.matching.dto.MatchingDtos.MatchDetailItemView;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunRequest;
import com.jobpilot.matching.dto.MatchingDtos.MatchRunView;
import com.jobpilot.matching.dto.MatchingDtos.ScoreView;
import com.jobpilot.matching.mapper.EntityEmbeddingMapper;
import com.jobpilot.matching.mapper.JobMatchDetailMapper;
import com.jobpilot.matching.mapper.JobMatchMapper;
import com.jobpilot.matching.mapper.MatchConfigMapper;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.matching.mapper.OutboxEventMapper;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MatchService {
    private final JobMapper jobMapper; private final CompanyMapper companyMapper; private final CandidateProfileMapper profileMapper;
    private final CandidateSkillMapper candidateSkillMapper; private final ProjectMapper projectMapper; private final EducationMapper educationMapper; private final ExperienceMapper experienceMapper;
    private final ResumeMapper resumeMapper; private final ResumeVersionMapper resumeVersionMapper; private final MatchConfigMapper configMapper;
    private final MatchRunMapper runMapper; private final JobMatchMapper matchMapper; private final JobMatchDetailMapper detailMapper;
    private final EntityEmbeddingMapper embeddingMapper; private final OutboxEventMapper outboxMapper;
    private final MatchConfigService configService; private final HardFilterService hardFilterService; private final ScoringService scoringService;
    private final FinalScoreCalculator scoreCalculator;
    private final MatchingAiClient aiClient; private final AiCallLogService aiCallLogService; private final MatchingProperties properties;
    private final ObjectMapper objectMapper; private final JsonCodec json; private final AuditService audit;

    public MatchService(JobMapper jobMapper, CompanyMapper companyMapper, CandidateProfileMapper profileMapper,
                        CandidateSkillMapper candidateSkillMapper, ProjectMapper projectMapper, EducationMapper educationMapper, ExperienceMapper experienceMapper,
                        ResumeMapper resumeMapper, ResumeVersionMapper resumeVersionMapper, MatchConfigMapper configMapper,
                        MatchRunMapper runMapper, JobMatchMapper matchMapper, JobMatchDetailMapper detailMapper,
                        EntityEmbeddingMapper embeddingMapper, OutboxEventMapper outboxMapper,
                        MatchConfigService configService, HardFilterService hardFilterService, ScoringService scoringService, FinalScoreCalculator scoreCalculator,
                        MatchingAiClient aiClient, AiCallLogService aiCallLogService, MatchingProperties properties,
                        ObjectMapper objectMapper, JsonCodec json, AuditService audit) {
        this.jobMapper=jobMapper;this.companyMapper=companyMapper;this.profileMapper=profileMapper;this.candidateSkillMapper=candidateSkillMapper;this.projectMapper=projectMapper;this.educationMapper=educationMapper;this.experienceMapper=experienceMapper;this.resumeMapper=resumeMapper;
        this.resumeVersionMapper=resumeVersionMapper;this.configMapper=configMapper;this.runMapper=runMapper;this.matchMapper=matchMapper;
        this.detailMapper=detailMapper;this.embeddingMapper=embeddingMapper;this.outboxMapper=outboxMapper;this.configService=configService;
        this.hardFilterService=hardFilterService;this.scoringService=scoringService;this.aiClient=aiClient;this.aiCallLogService=aiCallLogService;
        this.scoreCalculator=scoreCalculator;this.properties=properties;this.objectMapper=objectMapper;this.json=json;this.audit=audit;
    }

    @Transactional
    public MatchRunView start(Long userId, String jobPublicId, MatchRunRequest request, String headerKey) {
        JobEntity job=ownedJob(userId,jobPublicId); CandidateProfileEntity profile=profile(userId);
        ResumeVersionEntity resume=resume(userId,request.resumeVersionId()); MatchConfigEntity config=configService.activeEntity(userId);
        String inputHash=inputHash(userId,job,profile,resume,config); boolean force=Boolean.TRUE.equals(request.force());
        String key=firstNonBlank(headerKey,request.idempotencyKey(),"match-"+inputHash);
        MatchRunEntity sameKey=runMapper.selectOne(new LambdaQueryWrapper<MatchRunEntity>().eq(MatchRunEntity::getUserId,userId).eq(MatchRunEntity::getIdempotencyKey,key).last("LIMIT 1"));
        if(sameKey!=null){if(!sameKey.getInputHash().equals(inputHash)||Boolean.TRUE.equals(sameKey.getForceRun())!=force)throw new BusinessException(4092001,"Idempotency key was already used with different match input",HttpStatus.CONFLICT);return runView(sameKey);}
        if(!force){MatchRunEntity reusable=runMapper.selectOne(new LambdaQueryWrapper<MatchRunEntity>().eq(MatchRunEntity::getUserId,userId).eq(MatchRunEntity::getInputHash,inputHash).eq(MatchRunEntity::getStatus,"SUCCEEDED").orderByDesc(MatchRunEntity::getCreatedAt).last("LIMIT 1"));if(reusable!=null)return runView(reusable);}
        MatchRunEntity run=new MatchRunEntity();run.setUserId(userId);run.setJobId(job.getId());run.setCandidateProfileId(profile.getId());run.setResumeVersionId(resume==null?null:resume.getId());
        run.setMatchConfigId(config.getId());run.setIdempotencyKey(key);run.setInputHash(inputHash);run.setForceRun(force);run.setStatus("PENDING");run.setAttemptCount(0);run.setMaxAttempts(properties.getMaxAttempts());run.setAvailableAt(LocalDateTime.now());run.setTraceId(TraceContext.getTraceId());runMapper.insert(run);
        OutboxEventEntity event=new OutboxEventEntity();event.setAggregateType("MATCH_RUN");event.setAggregateId(run.getId());event.setEventType("MATCH_REQUESTED");event.setPayloadJson(json.write(Map.of("runId",run.getPublicId(),"inputHash",inputHash)));event.setIdempotencyKey("match-run:"+run.getPublicId());event.setStatus("PENDING");event.setAvailableAt(LocalDateTime.now());event.setAttemptCount(0);event.setMaxAttempts(properties.getMaxAttempts());outboxMapper.insert(event);
        audit.record(userId,"MATCH_RUN_CREATE","MATCH_RUN",run.getPublicId());return runView(run);
    }

    public MatchRunView getRun(Long userId,String publicId){return runView(ownedRun(userId,publicId));}

    @Transactional
    public MatchRunView retry(Long userId,String publicId){MatchRunEntity run=ownedRun(userId,publicId);if(!List.of("FAILED","DEAD").contains(run.getStatus()))throw new ValidationException("Only FAILED or DEAD match runs can be retried");boolean wasDead="DEAD".equals(run.getStatus());run.setStatus("PENDING");run.setErrorCode(null);run.setErrorMessageSafe(null);run.setAvailableAt(LocalDateTime.now());if(wasDead)run.setAttemptCount(0);runMapper.updateById(run);OutboxEventEntity event=outboxFor(run.getId());event.setStatus("PENDING");event.setAvailableAt(LocalDateTime.now());event.setLastErrorSafe(null);if(event.getAttemptCount()>=event.getMaxAttempts())event.setAttemptCount(0);outboxMapper.updateById(event);audit.record(userId,"MATCH_RUN_RETRY","MATCH_RUN",run.getPublicId());return runView(run);}

    public List<JobMatchView> listMatches(Long userId,String jobPublicId){JobEntity job=ownedJob(userId,jobPublicId);return matchMapper.selectList(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId,userId).eq(JobMatchEntity::getJobId,job.getId()).orderByDesc(JobMatchEntity::getEvaluatedAt)).stream().map(this::matchView).toList();}
    public JobMatchView getMatch(Long userId,String publicId){JobMatchEntity match=matchMapper.selectOne(new LambdaQueryWrapper<JobMatchEntity>().eq(JobMatchEntity::getUserId,userId).eq(JobMatchEntity::getPublicId,publicId).last("LIMIT 1"));if(match==null)throw new ResourceNotFoundException("Job match");return matchView(match);}

    @Transactional
    public void executeRun(Long runId){MatchRunEntity run=runMapper.selectById(runId);if(run==null||!"RUNNING".equals(run.getStatus()))return;JobEntity job=jobMapper.selectById(run.getJobId());CandidateProfileEntity profile=profileMapper.selectById(run.getCandidateProfileId());MatchConfigEntity config=configMapper.selectById(run.getMatchConfigId());ResumeVersionEntity resume=run.getResumeVersionId()==null?null:resumeVersionMapper.selectById(run.getResumeVersionId());CompanyEntity company=companyMapper.selectById(job.getCompanyId());
        HardFilterService.HardFilterOutcome hard=hardFilterService.evaluate(run.getUserId(),profile,job,company);
        if("REJECT".equals(hard.result())){JobMatchEntity match=baseMatch(run,config,hard);match.setRecommendation("REJECTED");match.setReasonText("硬性筛选未通过；请查看规则证据。");match.setLlmStatus("SKIPPED_NOT_CONFIGURED");match.setStatus("REJECTED");match.setEvaluatedAt(LocalDateTime.now());matchMapper.insert(match);persistHardDetails(match,hard.evidence());complete(run,match);return;}
        ScoringService.DeterministicScores deterministic=scoringService.score(run.getUserId(),profile,job,company);Map<String,Object> payload=payload(run,job,company,profile,resume,deterministic);String requestHash=sha256(write(payload));AiCallLogEntity aiLog=aiCallLogService.start(run.getUserId(),requestHash);AiCallResult aiResult;
        try{aiResult=aiClient.evaluate(payload);AiMatchResponse response=aiResult.response();aiCallLogService.finish(aiLog.getId(),"SKIPPED_NOT_CONFIGURED".equals(response.llmStatus())?"NONE":"OPENAI_COMPATIBLE",response.modelName(),aiResult.responseHash(),response.inputTokens(),response.outputTokens(),response.elapsedMs(),response.llmStatus(),null);}
        catch(BusinessException exception){aiCallLogService.finish(aiLog.getId(),"OPENAI_COMPATIBLE",null,null,null,null,null,"FAILED","AI_PROVIDER_ERROR");throw exception;}
        AiMatchResponse ai=aiResult.response();persistEmbeddings(run.getUserId(),aiResult.embeddings());Map<String,BigDecimal> weights=configService.weights(config);Map<String,BigDecimal> effective=scoreCalculator.effectiveWeights(weights,ai.llmScore()!=null);Map<String,BigDecimal> dimensionScores=new LinkedHashMap<>();dimensionScores.put("skill",deterministic.skillScore());dimensionScores.put("embedding",ai.embeddingScore());dimensionScores.put("llm",ai.llmScore()==null?BigDecimal.ZERO:ai.llmScore());dimensionScores.put("project",deterministic.projectScore());dimensionScores.put("preference",deterministic.preferenceScore());dimensionScores.put("company",deterministic.companyScore());BigDecimal overall=scoreCalculator.weighted(effective,dimensionScores).subtract(hard.penalty()).max(BigDecimal.ZERO).setScale(2,RoundingMode.HALF_UP);Map<String,BigDecimal> thresholds=configService.thresholds(config);String level=scoreCalculator.level(overall,thresholds);
        List<EvidenceItemView> advantages=new ArrayList<>(deterministic.advantages()),gaps=new ArrayList<>(deterministic.gaps()),risks=new ArrayList<>(deterministic.risks());advantages.addAll(items(ai.advantages()));gaps.addAll(items(ai.gaps()));risks.addAll(items(ai.risks()));hard.evidence().forEach(e->risks.add(new EvidenceItemView(e.explanation(),List.of(),e.expected().toString(),"HIGH")));
        JobMatchEntity match=baseMatch(run,config,hard);match.setAiCallId(aiLog.getId());match.setSkillScore(deterministic.skillScore());match.setEmbeddingScore(ai.embeddingScore());match.setLlmScore(ai.llmScore());match.setProjectScore(deterministic.projectScore());match.setPreferenceScore(deterministic.preferenceScore());match.setCompanyScore(deterministic.companyScore());match.setPenaltyScore(hard.penalty());match.setOverallScore(overall);match.setLevel(level);match.setAdvantagesJson(json.write(advantages));match.setGapsJson(json.write(gaps));match.setRisksJson(json.write(risks));match.setRecommendation(overall.compareTo(new BigDecimal("80"))>=0?"RECOMMEND":overall.compareTo(new BigDecimal("60"))>=0?"CONSIDER":"NOT_RECOMMENDED");match.setReasonText(reason(overall,hard,ai));match.setRecommendedResumeVersionId(resume==null?null:resume.getId());match.setEffectiveWeightsJson(json.write(effective));
        JsonNode embedding=ai.embedding();match.setEmbeddingProvider(embedding.path("provider").asText());match.setEmbeddingModel(embedding.path("model").asText());match.setEmbeddingVersion(embedding.path("embeddingVersion").asText());match.setLlmStatus(ai.llmStatus());match.setPromptVersion(ai.promptVersion());match.setModelName(ai.modelName());match.setStatus("SUCCEEDED");match.setEvaluatedAt(LocalDateTime.now());matchMapper.insert(match);persistHardDetails(match,hard.evidence());for(ScoringService.DetailDraft detail:deterministic.details())persistDetail(match,detail);complete(run,match);
    }

    private JobMatchEntity baseMatch(MatchRunEntity run,MatchConfigEntity config,HardFilterService.HardFilterOutcome hard){JobMatchEntity match=new JobMatchEntity();match.setUserId(run.getUserId());match.setJobId(run.getJobId());match.setCandidateProfileId(run.getCandidateProfileId());match.setResumeVersionId(run.getResumeVersionId());match.setMatchConfigId(config.getId());match.setMatchRunId(run.getId());match.setInputHash(run.getInputHash());match.setHardFilterResult(hard.result());match.setHardFilterJson(json.write(hard.evidence()));match.setPenaltyScore(hard.penalty());match.setAdvantagesJson("[]");match.setGapsJson("[]");match.setRisksJson("[]");match.setAlgorithmVersion(config.getAlgorithmVersion());match.setConfigVersion(config.getVersionNo());match.setWeightsSnapshotJson(config.getWeightsJson());match.setThresholdsSnapshotJson(config.getLevelThresholdsJson());match.setEffectiveWeightsJson(json.write(scoreCalculator.effectiveWeights(configService.weights(config),false)));return match;}
    private void complete(MatchRunEntity run,JobMatchEntity match){run.setResultMatchId(match.getId());run.setStatus("SUCCEEDED");run.setFinishedAt(LocalDateTime.now());run.setErrorCode(null);run.setErrorMessageSafe(null);runMapper.updateById(run);audit.record(run.getUserId(),"JOB_MATCH_CREATE","JOB_MATCH",match.getPublicId());}
    private void persistHardDetails(JobMatchEntity match,List<HardFilterEvidenceView> evidence){for(HardFilterEvidenceView item:evidence){persistDetail(match,new ScoringService.DetailDraft("HARD_FILTER",item.ruleKey(),null,item.expected().toString(),null,item.action(),item.explanation()));}}
    private void persistDetail(JobMatchEntity match,ScoringService.DetailDraft draft){JobMatchDetailEntity entity=new JobMatchDetailEntity();entity.setJobMatchId(match.getId());entity.setDimensionName(draft.dimension());entity.setItemKey(draft.itemKey());entity.setCandidateEvidenceRef(draft.candidateEvidenceRef());entity.setJobEvidenceText(draft.jobEvidence());entity.setScore(draft.score());entity.setDecision(draft.decision());entity.setExplanation(draft.explanation());detailMapper.insert(entity);}

    private Map<String,Object> payload(MatchRunEntity run,JobEntity job,CompanyEntity company,CandidateProfileEntity profile,ResumeVersionEntity resume,ScoringService.DeterministicScores scores){List<Map<String,Object>> jobSkills=new ArrayList<>(),candidateSkills=new ArrayList<>(),evidence=new ArrayList<>(),documents=new ArrayList<>(),skillDocuments=new ArrayList<>();for(JobSkillEntity item:scores.jobSkills()){SkillEntity skill=scores.catalog().get(item.getSkillId());if(skill!=null)jobSkills.add(mapSkill(skill,item.getRequirementType(),item.getEvidenceText()));}for(CandidateSkillEntity item:scores.candidateSkills()){SkillEntity skill=scores.catalog().get(item.getSkillId());if(skill!=null){String text=skill.getDisplayName()+" proficiency "+item.getProficiency()+" years "+String.valueOf(item.getYears());candidateSkills.add(mapSkill(skill,null,null));evidence.add(Map.of("evidenceRef","skill:"+item.getPublicId(),"evidenceType","SKILL","text",text));skillDocuments.add(document("SKILL_EVIDENCE",item.getId(),item.getPublicId(),text));}}
        List<EducationEntity> educations=educationMapper.selectList(new LambdaQueryWrapper<EducationEntity>().eq(EducationEntity::getUserId,run.getUserId()).orderByAsc(EducationEntity::getId));List<ExperienceEntity> experiences=experienceMapper.selectList(new LambdaQueryWrapper<ExperienceEntity>().eq(ExperienceEntity::getUserId,run.getUserId()).orderByAsc(ExperienceEntity::getId));StringBuilder factText=new StringBuilder();for(EducationEntity item:educations){String text=String.join(" ",safe(item.getSchool()),safe(item.getDegree()),safe(item.getMajor()),safe(item.getDescription()));factText.append(' ').append(text);evidence.add(Map.of("evidenceRef","education:"+item.getPublicId(),"evidenceType","EDUCATION","text",limit(text,5000)));}for(ExperienceEntity item:experiences){String text=String.join(" ",safe(item.getCompanyName()),safe(item.getRole()),safe(item.getDescription()),safe(item.getResponsibilities()),safe(item.getAchievements()),safe(item.getTechnologiesJson()));factText.append(' ').append(text);evidence.add(Map.of("evidenceRef","experience:"+item.getPublicId(),"evidenceType","EXPERIENCE","text",limit(text,5000)));}String profileText=String.join(" ",safe(profile.getHeadline()),safe(profile.getSummary()),safe(profile.getTargetRolesJson()),safe(profile.getTargetIndustriesJson()),safe(profile.getCurrentCity()),safe(profile.getHighestEducation()),safe(profile.getSchool()),safe(profile.getMajor()),profile.getYearsOfExperience()==null?"":profile.getYearsOfExperience().toPlainString()+" years experience",factText.toString());evidence.add(Map.of("evidenceRef","profile:"+profile.getPublicId(),"evidenceType","PROFILE","text",profileText.isBlank()?"Candidate profile":profileText));documents.add(document("JOB",job.getId(),job.getPublicId(),jobText(job)));documents.add(document("PROFILE",profile.getId(),profile.getPublicId(),profileText.isBlank()?"Candidate profile":profileText));
        if(resume!=null){String text=firstNonBlank(resume.getRenderedText(),resume.getContentJson(),"Structured resume");evidence.add(Map.of("evidenceRef","resume:"+resume.getPublicId(),"evidenceType","RESUME","text",limit(text,5000)));documents.add(document("RESUME_VERSION",resume.getId(),resume.getPublicId(),text));}
        for(Map<String,Object> skillDocument:skillDocuments){if(documents.size()<50)documents.add(skillDocument);}
        for(ProjectEntity project:scores.projects()){String text=String.join(" ",safe(project.getName()),safe(project.getDescription()),safe(project.getResponsibilities()),safe(project.getAchievements()),safe(project.getTechnologiesJson()));evidence.add(Map.of("evidenceRef","project:"+project.getPublicId(),"evidenceType","PROJECT","text",limit(text,5000)));if(documents.size()<50)documents.add(document("PROJECT",project.getId(),project.getPublicId(),text));}
        Map<String,Object> payload=new LinkedHashMap<>();payload.put("schemaVersion","match-evaluate-request-v1");payload.put("taskId",run.getPublicId());payload.put("traceId",run.getTraceId());payload.put("userId",run.getUserId());payload.put("promptVersion","matching/v1");payload.put("jobTitle",job.getTitle());payload.put("companyName",company==null?"Unknown company":company.getDisplayName());payload.put("jobCity",job.getCity());payload.put("jobDescription",firstNonBlank(job.getDescriptionClean(),job.getDescriptionRaw(),"Job description unavailable"));payload.put("jobSkills",jobSkills);payload.put("candidateSkills",candidateSkills);payload.put("candidateEvidence",evidence);payload.put("documents",documents);return payload;}
    private Map<String,Object> mapSkill(SkillEntity skill,String type,String evidence){Map<String,Object> map=new LinkedHashMap<>();map.put("canonicalName",skill.getCanonicalName());map.put("displayName",skill.getDisplayName());if(type!=null)map.put("requirementType",type);if(evidence!=null)map.put("evidenceText",limit(evidence,1000));return map;}
    private Map<String,Object> document(String type,Long id,String publicId,String text){String content=limit(text,100000);return Map.of("entityType",type,"entityId",id,"entityPublicId",publicId,"text",content,"contentHash",sha256(content));}
    private String jobText(JobEntity job){return String.join(" ",safe(job.getTitle()),safe(job.getCity()),safe(job.getDescriptionClean()),safe(job.getRequirementsJson()),safe(job.getResponsibilitiesJson()));}

    private void persistEmbeddings(Long userId,List<EmbeddingRecord> records){for(EmbeddingRecord record:records){EntityEmbeddingEntity existing=embeddingMapper.selectOne(new LambdaQueryWrapper<EntityEmbeddingEntity>().eq(EntityEmbeddingEntity::getUserId,userId).eq(EntityEmbeddingEntity::getEntityType,record.entityType()).eq(EntityEmbeddingEntity::getEntityId,record.entityId()).eq(EntityEmbeddingEntity::getEmbeddingModel,record.model()).eq(EntityEmbeddingEntity::getContentHash,record.contentHash()).last("LIMIT 1"));if(existing!=null)continue;EntityEmbeddingEntity entity=new EntityEmbeddingEntity();entity.setUserId(userId);entity.setEntityType(record.entityType());entity.setEntityId(record.entityId());entity.setEntityPublicId(record.entityPublicId());entity.setVectorStore("MILVUS");entity.setCollectionName(record.collectionName());entity.setVectorId(record.vectorId());entity.setEmbeddingProvider(record.provider());entity.setEmbeddingModel(record.model());entity.setDimension(record.dimension());entity.setContentHash(record.contentHash());entity.setEmbeddingVersion(record.embeddingVersion());entity.setStatus("READY");entity.setEmbeddedAt(LocalDateTime.now());embeddingMapper.insert(entity);}}
    private String reason(BigDecimal overall,HardFilterService.HardFilterOutcome hard,AiMatchResponse ai){String base="综合分 "+overall.setScale(2,RoundingMode.HALF_UP)+"，硬性筛选结果为 "+hard.result()+"。";return "SKIPPED_NOT_CONFIGURED".equals(ai.llmStatus())?base+" 未配置 LLM 服务商，已跳过分析并重新归一化有效权重。":firstNonBlank(ai.reason(),base);}
    private List<EvidenceItemView> items(List<AiAnalysisItem> items){if(items==null)return List.of();return items.stream().map(item->new EvidenceItemView(item.text(),item.candidateEvidenceRefs()==null?List.of():item.candidateEvidenceRefs(),item.jobEvidence(),item.severity())).toList();}

    private MatchRunView runView(MatchRunEntity run){return new MatchRunView(run.getPublicId(),jobMapper.selectById(run.getJobId()).getPublicId(),run.getResumeVersionId()==null?null:resumeVersionMapper.selectById(run.getResumeVersionId()).getPublicId(),configMapper.selectById(run.getMatchConfigId()).getPublicId(),run.getStatus(),run.getAttemptCount(),run.getMaxAttempts(),run.getResultMatchId()==null?null:matchMapper.selectById(run.getResultMatchId()).getPublicId(),run.getErrorCode(),run.getErrorMessageSafe(),run.getAvailableAt(),run.getStartedAt(),run.getFinishedAt(),run.getCreatedAt());}
    private JobMatchView matchView(JobMatchEntity match){JobEntity job=jobMapper.selectById(match.getJobId());CandidateProfileEntity profile=profileMapper.selectById(match.getCandidateProfileId());MatchConfigEntity config=configMapper.selectById(match.getMatchConfigId());ResumeVersionEntity resume=match.getResumeVersionId()==null?null:resumeVersionMapper.selectById(match.getResumeVersionId());List<JobMatchDetailEntity> details=detailMapper.selectList(new LambdaQueryWrapper<JobMatchDetailEntity>().eq(JobMatchDetailEntity::getJobMatchId,match.getId()).orderByAsc(JobMatchDetailEntity::getId));return new JobMatchView(match.getPublicId(),job.getPublicId(),profile.getPublicId(),resume==null?null:resume.getPublicId(),config.getPublicId(),runMapper.selectById(match.getMatchRunId()).getPublicId(),match.getHardFilterResult(),readList(match.getHardFilterJson(),new TypeReference<List<HardFilterEvidenceView>>(){}),new ScoreView(match.getSkillScore(),match.getEmbeddingScore(),match.getLlmScore(),match.getProjectScore(),match.getPreferenceScore(),match.getCompanyScore(),match.getPenaltyScore(),match.getOverallScore()),match.getLevel(),readList(match.getAdvantagesJson(),new TypeReference<List<EvidenceItemView>>(){}),readList(match.getGapsJson(),new TypeReference<List<EvidenceItemView>>(){}),readList(match.getRisksJson(),new TypeReference<List<EvidenceItemView>>(){}),match.getRecommendation(),match.getReasonText(),match.getRecommendedResumeVersionId()==null?null:resumeVersionMapper.selectById(match.getRecommendedResumeVersionId()).getPublicId(),match.getAlgorithmVersion(),match.getConfigVersion(),readMap(match.getWeightsSnapshotJson()),readMap(match.getEffectiveWeightsJson()),readMap(match.getThresholdsSnapshotJson()),match.getEmbeddingProvider(),match.getEmbeddingModel(),match.getEmbeddingVersion(),match.getLlmStatus(),match.getPromptVersion(),match.getModelName(),match.getStatus(),details.stream().map(detail->new MatchDetailItemView(detail.getPublicId(),detail.getDimensionName(),detail.getItemKey(),detail.getCandidateEvidenceRef(),detail.getJobEvidenceText(),detail.getScore(),detail.getDecision(),detail.getExplanation())).toList(),match.getEvaluatedAt());}
    private <T> T readList(String value,TypeReference<T> type){try{return objectMapper.readValue(value,type);}catch(Exception exception){throw new IllegalStateException("Stored match JSON is invalid",exception);}}
    private Map<String,BigDecimal> readMap(String value){try{return objectMapper.readValue(value,new TypeReference<LinkedHashMap<String,BigDecimal>>(){});}catch(Exception exception){throw new IllegalStateException("Stored match JSON is invalid",exception);}}

    private JobEntity ownedJob(Long userId,String publicId){JobEntity entity=jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId,userId).eq(JobEntity::getPublicId,publicId).last("LIMIT 1"));if(entity==null)throw new ResourceNotFoundException("Job");return entity;}
    private MatchRunEntity ownedRun(Long userId,String publicId){MatchRunEntity entity=runMapper.selectOne(new LambdaQueryWrapper<MatchRunEntity>().eq(MatchRunEntity::getUserId,userId).eq(MatchRunEntity::getPublicId,publicId).last("LIMIT 1"));if(entity==null)throw new ResourceNotFoundException("Match run");return entity;}
    private CandidateProfileEntity profile(Long userId){CandidateProfileEntity entity=profileMapper.selectOne(new LambdaQueryWrapper<CandidateProfileEntity>().eq(CandidateProfileEntity::getUserId,userId).last("LIMIT 1"));if(entity==null)throw new ValidationException("Candidate profile is required before matching");return entity;}
    private ResumeVersionEntity resume(Long userId,String publicId){if(publicId!=null&&!publicId.isBlank()){ResumeVersionEntity version=resumeVersionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>().eq(ResumeVersionEntity::getPublicId,publicId).last("LIMIT 1"));if(version==null)throw new ResourceNotFoundException("Resume version");ResumeEntity owner=resumeMapper.selectById(version.getResumeId());if(owner==null||!owner.getUserId().equals(userId))throw new ResourceNotFoundException("Resume version");return version;}ResumeEntity selected=resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>().eq(ResumeEntity::getUserId,userId).eq(ResumeEntity::getDefaultResume,true).last("LIMIT 1"));if(selected==null)selected=resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>().eq(ResumeEntity::getUserId,userId).eq(ResumeEntity::getMaster,true).last("LIMIT 1"));return selected==null||selected.getCurrentVersionId()==null?null:resumeVersionMapper.selectById(selected.getCurrentVersionId());}
    private OutboxEventEntity outboxFor(Long runId){OutboxEventEntity event=outboxMapper.selectOne(new LambdaQueryWrapper<OutboxEventEntity>().eq(OutboxEventEntity::getAggregateType,"MATCH_RUN").eq(OutboxEventEntity::getAggregateId,runId).last("LIMIT 1"));if(event==null)throw new ResourceNotFoundException("Match outbox event");return event;}
    private String inputHash(Long userId,JobEntity job,CandidateProfileEntity profile,ResumeVersionEntity resume,MatchConfigEntity config){return sha256(String.join("|",safe(job.getRawContentHash()),String.valueOf(job.getVersion()),String.valueOf(profile.getVersion()),candidateFactsHash(userId),resume==null?"none":safe(resume.getContentHash()),config.getWeightsJson(),config.getLevelThresholdsJson(),String.valueOf(config.getVersionNo()),config.getAlgorithmVersion(),hardFilterService.activeRulesHash(userId)));}
    private String candidateFactsHash(Long userId){List<String> facts=new ArrayList<>();candidateSkillMapper.selectList(new LambdaQueryWrapper<CandidateSkillEntity>().eq(CandidateSkillEntity::getUserId,userId).orderByAsc(CandidateSkillEntity::getId)).forEach(item->facts.add(String.join("|","skill",safe(item.getPublicId()),String.valueOf(item.getVersion()),String.valueOf(item.getSkillId()),String.valueOf(item.getProficiency()),String.valueOf(item.getYears()),String.valueOf(item.getLastUsedAt()),safe(item.getSource()),String.valueOf(item.getPrimarySkill()))));projectMapper.selectList(new LambdaQueryWrapper<ProjectEntity>().eq(ProjectEntity::getUserId,userId).orderByAsc(ProjectEntity::getId)).forEach(item->facts.add(String.join("|","project",safe(item.getPublicId()),String.valueOf(item.getVersion()),safe(item.getName()),safe(item.getRole()),String.valueOf(item.getStartDate()),String.valueOf(item.getEndDate()),safe(item.getDescription()),safe(item.getResponsibilities()),safe(item.getAchievements()),safe(item.getTechnologiesJson()),String.valueOf(item.getFeatured()))));educationMapper.selectList(new LambdaQueryWrapper<EducationEntity>().eq(EducationEntity::getUserId,userId).orderByAsc(EducationEntity::getId)).forEach(item->facts.add(String.join("|","education",safe(item.getPublicId()),String.valueOf(item.getVersion()),safe(item.getSchool()),safe(item.getDegree()),safe(item.getMajor()),String.valueOf(item.getStartDate()),String.valueOf(item.getEndDate()),String.valueOf(item.getGraduationYear()),safe(item.getDescription()))));experienceMapper.selectList(new LambdaQueryWrapper<ExperienceEntity>().eq(ExperienceEntity::getUserId,userId).orderByAsc(ExperienceEntity::getId)).forEach(item->facts.add(String.join("|","experience",safe(item.getPublicId()),String.valueOf(item.getVersion()),safe(item.getCompanyName()),safe(item.getRole()),safe(item.getEmploymentType()),safe(item.getLocation()),String.valueOf(item.getStartDate()),String.valueOf(item.getEndDate()),String.valueOf(item.getCurrentlyWorking()),safe(item.getDescription()),safe(item.getResponsibilities()),safe(item.getAchievements()),safe(item.getTechnologiesJson()))));return sha256(String.join("\n",facts));}
    private String write(Object value){try{return objectMapper.writeValueAsString(value);}catch(Exception exception){throw new ValidationException("Unable to serialize match input");}}
    private String sha256(String value){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(Exception exception){throw new IllegalStateException("SHA-256 is unavailable",exception);}}
    private String firstNonBlank(String...values){for(String value:values)if(value!=null&&!value.isBlank())return value;return UUID.randomUUID().toString();}
    private String safe(String value){return value==null?"":value;}
    private String limit(String value,int max){String safe=safe(value).trim();if(safe.isEmpty())safe="No text provided";return safe.length()<=max?safe:safe.substring(0,max);}
}
