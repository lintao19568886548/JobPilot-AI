package com.jobpilot.job.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.candidate.domain.SkillEntity;
import com.jobpilot.candidate.mapper.SkillMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.job.domain.CompanyEntity;
import com.jobpilot.job.domain.JobDedupLogEntity;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.domain.JobParseRunEntity;
import com.jobpilot.job.domain.JobRevisionLogEntity;
import com.jobpilot.job.domain.JobSkillEntity;
import com.jobpilot.job.domain.JobSourceEntity;
import com.jobpilot.job.dto.JobDtos.CreateResult;
import com.jobpilot.job.dto.JobDtos.JobCreateRequest;
import com.jobpilot.job.dto.JobDtos.JobDetailView;
import com.jobpilot.job.dto.JobDtos.JobPageView;
import com.jobpilot.job.dto.JobDtos.JobQuery;
import com.jobpilot.job.dto.JobDtos.JobSkillView;
import com.jobpilot.job.dto.JobDtos.JobSummaryView;
import com.jobpilot.job.dto.JobDtos.JobUpdateRequest;
import com.jobpilot.job.dto.JobDtos.ParseRunView;
import com.jobpilot.job.dto.JobDtos.SourceRequest;
import com.jobpilot.job.dto.JobDtos.SourceView;
import com.jobpilot.job.mapper.CompanyMapper;
import com.jobpilot.job.mapper.JobDedupLogMapper;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.job.mapper.JobParseRunMapper;
import com.jobpilot.job.mapper.JobRevisionLogMapper;
import com.jobpilot.job.mapper.JobSkillMapper;
import com.jobpilot.job.mapper.JobSourceMapper;
import com.jobpilot.job.normalization.JobNormalizationService;
import com.jobpilot.job.parser.JobParserClient;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class JobService {
    private static final String DEDUP_VERSION = "rules-v1";
    private final JobMapper jobMapper;
    private final CompanyMapper companyMapper;
    private final JobSourceMapper sourceMapper;
    private final JobSkillMapper skillMapper;
    private final SkillMapper catalogMapper;
    private final JobParseRunMapper parseRunMapper;
    private final JobDedupLogMapper dedupMapper;
    private final JobRevisionLogMapper revisionMapper;
    private final CompanyService companyService;
    private final SkillAliasService aliasService;
    private final JobNormalizationService normalization;
    private final JobParserClient parserClient;
    private final JsonCodec json;
    private final ObjectMapper objectMapper;
    private final AuditService audit;

    public JobService(JobMapper jobMapper, CompanyMapper companyMapper, JobSourceMapper sourceMapper,
                      JobSkillMapper skillMapper, SkillMapper catalogMapper, JobParseRunMapper parseRunMapper,
                      JobDedupLogMapper dedupMapper, JobRevisionLogMapper revisionMapper,
                      CompanyService companyService, SkillAliasService aliasService,
                      JobNormalizationService normalization, JobParserClient parserClient,
                      JsonCodec json, ObjectMapper objectMapper, AuditService audit) {
        this.jobMapper = jobMapper; this.companyMapper = companyMapper; this.sourceMapper = sourceMapper;
        this.skillMapper = skillMapper; this.catalogMapper = catalogMapper; this.parseRunMapper = parseRunMapper;
        this.dedupMapper = dedupMapper; this.revisionMapper = revisionMapper; this.companyService = companyService;
        this.aliasService = aliasService; this.normalization = normalization; this.parserClient = parserClient;
        this.json = json; this.objectMapper = objectMapper; this.audit = audit;
    }

    @Transactional
    public CreateResult create(Long userId, JobCreateRequest request) {
        validateRanges(request.salaryMin(), request.salaryMax(), request.experienceMinYears(), request.experienceMaxYears());
        String platform = upper(defaultValue(request.platform(), "MANUAL"));
        String sourceType = upper(defaultValue(request.sourceType(), "MANUAL"));
        JobSourceEntity exact = findExactSource(platform, request.platformJobId(), request.jobUrl());
        if (exact != null) {
            JobEntity existing = ownedByInternalId(userId, exact.getJobId());
            exact.setLastSeenAt(LocalDateTime.now());
            sourceMapper.updateById(exact);
            recordDedup(userId, exact.getId(), existing.getId(), "EXACT_DUPLICATE", BigDecimal.valueOf(100));
            audit.record(userId, "JOB_SOURCE_MERGE", "JOB", existing.getPublicId());
            return new CreateResult(detail(existing), "EXACT_DUPLICATE", false);
        }

        CompanyEntity company = companyService.findOrCreate(request.companyName(), request.companyWebsite(), request.industry(),
                request.companySize(), request.financingStage(), request.headquartersCity(), platform);
        String normalizedTitle = normalization.title(request.title());
        JobEntity ruleMatch = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>()
                .eq(JobEntity::getUserId, userId).eq(JobEntity::getCompanyId, company.getId())
                .eq(JobEntity::getNormalizedTitle, normalizedTitle)
                .eq(request.city() != null, JobEntity::getCity, trim(request.city())).last("LIMIT 1"));
        if (ruleMatch != null) {
            JobSourceEntity source = addSourceEntity(userId, ruleMatch, request, platform, sourceType);
            recordDedup(userId, source.getId(), ruleMatch.getId(), "RULE_MERGED", BigDecimal.valueOf(95));
            audit.record(userId, "JOB_SOURCE_MERGE", "JOB", ruleMatch.getPublicId());
            return new CreateResult(detail(ruleMatch), "RULE_MERGED", false);
        }

        LocalDateTime now = LocalDateTime.now();
        JobEntity job = new JobEntity();
        job.setUserId(userId); job.setCompanyId(company.getId()); job.setTitle(request.title().trim());
        job.setNormalizedTitle(normalizedTitle); job.setCanonicalJobKey(normalization.canonicalKey(request.companyName(), request.title(), request.city()));
        job.setFingerprintHash(normalization.fingerprint(request.companyName(), request.title(), request.city(), request.description()));
        applyCreate(job, request); job.setDescriptionRaw(request.description().trim()); job.setDescriptionClean(request.description().trim());
        job.setResponsibilitiesJson("[]"); job.setRequirementsJson("[]"); job.setFirstCollectedAt(now); job.setLastCollectedAt(now);
        job.setStatus("DISCOVERED"); job.setParseStatus("PENDING"); job.setRawContentHash(normalization.contentHash(request.description()));
        job.setParseResultJson("{}"); job.setManualFieldsJson(json.write(manualFields(request)));
        jobMapper.insert(job);
        JobSourceEntity source = addSourceEntity(userId, job, request, platform, sourceType);
        recordDedup(userId, source.getId(), job.getId(), "NEW_JOB", BigDecimal.ZERO);
        audit.record(userId, "JOB_CREATE", "JOB", job.getPublicId());
        if (!Boolean.FALSE.equals(request.parseAfterCreate())) parseInternal(userId, job);
        return new CreateResult(detail(job), "NEW_JOB", true);
    }

    public JobPageView list(Long userId, JobQuery filters) {
        int limit = filters.limit() == null ? 20 : Math.max(1, Math.min(filters.limit(), 100));
        LambdaQueryWrapper<JobEntity> query = new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId, userId);
        if (has(filters.title())) query.like(JobEntity::getTitle, filters.title().trim());
        if (has(filters.city())) query.eq(JobEntity::getCity, filters.city().trim());
        if (filters.salaryMin() != null) query.ge(JobEntity::getSalaryMax, filters.salaryMin());
        if (filters.salaryMax() != null) query.le(JobEntity::getSalaryMin, filters.salaryMax());
        if (has(filters.education())) query.eq(JobEntity::getEducation, upper(filters.education()));
        if (filters.experienceMin() != null) query.ge(JobEntity::getExperienceMaxYears, filters.experienceMin());
        if (filters.experienceMax() != null) query.le(JobEntity::getExperienceMinYears, filters.experienceMax());
        if (has(filters.status())) query.eq(JobEntity::getStatus, upper(filters.status()));
        if (has(filters.parseStatus())) query.eq(JobEntity::getParseStatus, upper(filters.parseStatus()));
        if (filters.publishFrom() != null) query.ge(JobEntity::getPublishAt, filters.publishFrom());
        if (filters.publishTo() != null) query.le(JobEntity::getPublishAt, filters.publishTo());
        applyRelationFilters(query, userId, filters);
        String sort = has(filters.sort()) ? filters.sort().trim().toLowerCase() : "publish_desc";
        if (!Set.of("publish_desc", "updated_desc").contains(sort)) throw new ValidationException("sort must be publish_desc or updated_desc");
        Cursor cursor = decodeCursor(filters.cursor());
        String orderColumn = "updated_desc".equals(sort) ? "updated_at" : "COALESCE(publish_at, first_collected_at)";
        if (cursor != null) query.apply("(" + orderColumn + " < {0} OR (" + orderColumn + " = {0} AND id < {1}))", cursor.time(), cursor.id());
        query.last("ORDER BY " + orderColumn + " DESC, id DESC LIMIT " + (limit + 1));
        List<JobEntity> rows = jobMapper.selectList(query);
        boolean more = rows.size() > limit;
        List<JobEntity> page = more ? rows.subList(0, limit) : rows;
        String next = more && !page.isEmpty() ? encodeCursor(page.get(page.size() - 1), sort) : null;
        return new JobPageView(page.stream().map(this::summary).toList(), next, more, limit);
    }

    public JobDetailView get(Long userId, String publicId) { return detail(owned(userId, publicId)); }

    @Transactional
    public JobDetailView update(Long userId, String publicId, JobUpdateRequest request) {
        validateRanges(request.salaryMin(), request.salaryMax(), request.experienceMinYears(), request.experienceMaxYears());
        JobEntity job = owned(userId, publicId);
        if (job.getVersion() != request.version()) throw new ValidationException("Job was modified concurrently; reload and retry");
        JsonNode before = objectMapper.valueToTree(job);
        CompanyEntity company = companyService.findOrCreate(request.companyName(), null, null, null, null, null, "MANUAL");
        job.setCompanyId(company.getId()); job.setTitle(request.title().trim()); job.setNormalizedTitle(normalization.title(request.title()));
        applyUpdate(job, request); job.setDescriptionRaw(request.description().trim()); job.setDescriptionClean(request.description().trim());
        job.setRawContentHash(normalization.contentHash(request.description()));
        job.setCanonicalJobKey(normalization.canonicalKey(request.companyName(), request.title(), request.city()));
        job.setFingerprintHash(normalization.fingerprint(request.companyName(), request.title(), request.city(), request.description()));
        Set<String> fields = new LinkedHashSet<>(json.readStringList(job.getManualFieldsJson()));
        fields.addAll(List.of("title","companyName","city","district","workplaceText","remoteType","salary","education","experience","graduateYear","jobType","description","businessDomain","teamName","publishAt"));
        job.setManualFieldsJson(json.write(fields));
        if (jobMapper.updateById(job) != 1) throw new ValidationException("Job was modified concurrently; reload and retry");
        JobRevisionLogEntity revision = new JobRevisionLogEntity(); revision.setUserId(userId); revision.setJobId(job.getId());
        revision.setBeforeJson(before.toString()); revision.setAfterJson(objectMapper.valueToTree(job).toString());
        revision.setChangedFieldsJson(json.write(fields)); revision.setReason(request.reason()); revision.setTraceId(TraceContext.getTraceId());
        revisionMapper.insert(revision); audit.record(userId, "JOB_UPDATE", "JOB", publicId);
        return detail(job);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        JobEntity job = owned(userId, publicId); jobMapper.deleteById(job.getId());
        audit.record(userId, "JOB_DELETE", "JOB", publicId);
    }

    @Transactional
    public JobDetailView ignore(Long userId, String publicId) {
        JobEntity job = owned(userId, publicId); job.setStatus("IGNORED"); jobMapper.updateById(job);
        audit.record(userId, "JOB_IGNORE", "JOB", publicId); return detail(job);
    }

    @Transactional
    public JobDetailView restore(Long userId, String publicId) {
        JobEntity job = owned(userId, publicId); job.setStatus(Set.of("SUCCESS", "PARTIAL").contains(job.getParseStatus()) ? "ACTIVE" : "DISCOVERED");
        jobMapper.updateById(job); audit.record(userId, "JOB_RESTORE", "JOB", publicId); return detail(job);
    }

    @Transactional
    public JobDetailView parse(Long userId, String publicId) { JobEntity job = owned(userId, publicId); parseInternal(userId, job); return detail(job); }

    public List<SourceView> sources(Long userId, String publicId) { return sourceViews(owned(userId, publicId)); }

    @Transactional
    public SourceView addSource(Long userId, String publicId, SourceRequest request) {
        JobEntity job = owned(userId, publicId);
        JobSourceEntity exact = findExactSource(upper(request.platform()), request.platformJobId(), request.jobUrl());
        if (exact != null) return sourceView(exact);
        JobSourceEntity source = new JobSourceEntity(); source.setUserId(userId); source.setJobId(job.getId());
        source.setPlatform(upper(request.platform())); source.setPlatformJobId(trim(request.platformJobId())); source.setSourceType(upper(request.sourceType()));
        source.setJobUrl(trim(request.jobUrl())); source.setNormalizedUrlHash(normalization.urlHash(request.jobUrl())); source.setSourceTitle(trim(request.sourceTitle()));
        source.setSourceCompanyName(trim(request.sourceCompanyName())); source.setRawSnapshotJson(request.rawSnapshot() == null ? "{}" : request.rawSnapshot().toString());
        source.setPublishAt(request.publishAt()); source.setCollectedAt(LocalDateTime.now()); source.setLastSeenAt(LocalDateTime.now());
        source.setAvailabilityStatus("AVAILABLE"); source.setCollectorVersion(trim(request.collectorVersion())); source.setUserInitiated(!Boolean.FALSE.equals(request.userInitiated()));
        sourceMapper.insert(source); audit.record(userId, "JOB_SOURCE_CREATE", "JOB_SOURCE", source.getPublicId()); return sourceView(source);
    }

    private void parseInternal(Long userId, JobEntity job) {
        CompanyEntity company = companyMapper.selectById(job.getCompanyId());
        JobParseRunEntity run = new JobParseRunEntity(); run.setUserId(userId); run.setJobId(job.getId()); run.setStatus("PROCESSING");
        run.setParserMode("RULES_ONLY"); run.setParserVersion("pending"); run.setSchemaVersion("job-parser-v1");
        run.setInputHash(job.getRawContentHash()); run.setResultJson("{}"); parseRunMapper.insert(run);
        job.setParseStatus("PROCESSING"); jobMapper.updateById(job); audit.record(userId, "JOB_PARSE_START", "JOB", job.getPublicId());
        long started = System.nanoTime();
        try {
            JsonNode result = parserClient.parse(job, company.getDisplayName());
            applyParseResult(job, result);
            run.setStatus(result.path("warnings").isArray() && !result.path("warnings").isEmpty() ? "PARTIAL" : "SUCCESS");
            run.setParserMode(result.path("parserMode").asText("RULES_ONLY")); run.setParserVersion(result.path("parserVersion").asText("rules-v1"));
            run.setSchemaVersion(result.path("schemaVersion").asText("job-parser-v1")); run.setPromptVersion(textOrNull(result, "promptVersion"));
            run.setModelName(textOrNull(result, "modelName")); run.setOutputHash(normalization.contentHash(result.toString())); run.setResultJson(result.toString());
            run.setElapsedMs((int)((System.nanoTime() - started) / 1_000_000)); parseRunMapper.updateById(run);
            job.setParseStatus("PARTIAL".equals(run.getStatus()) ? "PARTIAL" : "SUCCESS"); job.setStatus("ACTIVE");
            job.setParserVersion(run.getParserVersion()); job.setParserMode(run.getParserMode()); job.setParseResultJson(result.toString()); jobMapper.updateById(job);
            replaceSkills(job, result.path("skills")); audit.record(userId, "JOB_PARSE_SUCCESS", "JOB", job.getPublicId());
        } catch (BusinessException exception) {
            run.setStatus("FAILED"); run.setErrorCode("AI_SERVICE_UNAVAILABLE"); run.setErrorMessage(exception.getMessage());
            run.setElapsedMs((int)((System.nanoTime() - started) / 1_000_000)); parseRunMapper.updateById(run);
            job.setParseStatus("FAILED"); jobMapper.updateById(job); audit.record(userId, "JOB_PARSE_FAILED", "JOB", job.getPublicId());
        }
    }

    private void applyParseResult(JobEntity job, JsonNode r) {
        Set<String> manual = new HashSet<>(json.readStringList(job.getManualFieldsJson()));
        if (!manual.contains("salary") && r.has("salary")) {
            JsonNode salary = r.path("salary"); job.setSalaryMin(decimal(salary.get("min"))); job.setSalaryMax(decimal(salary.get("max")));
            if (salary.hasNonNull("months")) job.setSalaryMonths(salary.get("months").asInt());
            if (salary.hasNonNull("currency")) job.setCurrency(salary.get("currency").asText());
            if (salary.hasNonNull("originalText")) job.setSalaryText(salary.get("originalText").asText());
        }
        if (!manual.contains("education") && r.hasNonNull("education")) job.setEducation(r.get("education").asText());
        if (!manual.contains("experience")) { job.setExperienceMinYears(decimal(r.get("experienceMinYears"))); job.setExperienceMaxYears(decimal(r.get("experienceMaxYears"))); }
        if (!manual.contains("graduateYear") && r.path("graduateYears").isArray() && !r.path("graduateYears").isEmpty()) job.setGraduateYear(r.path("graduateYears").get(0).asInt());
        if (!manual.contains("jobType") && r.hasNonNull("jobType")) job.setJobType(r.get("jobType").asText());
        if (!manual.contains("businessDomain") && r.hasNonNull("businessDomain")) job.setBusinessDomain(r.get("businessDomain").asText());
        if (!manual.contains("teamName") && r.hasNonNull("teamName")) job.setTeamName(r.get("teamName").asText());
        job.setResponsibilitiesJson(r.path("responsibilities").isArray() ? r.path("responsibilities").toString() : "[]");
        job.setRequirementsJson(r.path("requirements").isArray() ? r.path("requirements").toString() : "[]");
    }

    private void replaceSkills(JobEntity job, JsonNode skills) {
        skillMapper.delete(new LambdaQueryWrapper<JobSkillEntity>().eq(JobSkillEntity::getJobId, job.getId()));
        if (!skills.isArray()) return;
        for (JsonNode item : skills) {
            String name = item.path("canonicalName").asText(item.path("displayName").asText());
            aliasService.resolve(name).ifPresent(skill -> insertSkill(job, skill, item));
        }
    }

    private void insertSkill(JobEntity job, SkillEntity skill, JsonNode item) {
        JobSkillEntity entity = new JobSkillEntity(); entity.setJobId(job.getId()); entity.setSkillId(skill.getId());
        entity.setRequirementType(item.path("requirementType").asText("RELATED"));
        entity.setImportance("MUST_HAVE".equals(entity.getRequirementType()) ? 90 : "NICE_TO_HAVE".equals(entity.getRequirementType()) ? 60 : 40);
        entity.setMinYears(decimal(item.get("minYears"))); entity.setEvidenceText(item.path("evidenceText").asText(nameFallback(skill)));
        entity.setSource("PARSER"); entity.setConfidence(decimalOrDefault(item.get("confidence"), BigDecimal.valueOf(0.8))); skillMapper.insert(entity);
    }

    private String nameFallback(SkillEntity skill) { return "Mentioned skill: " + skill.getDisplayName(); }

    private JobDetailView detail(JobEntity job) {
        return new JobDetailView(summary(job), job.getDescriptionRaw(), job.getDescriptionClean(),
                json.readStringList(job.getResponsibilitiesJson()), json.readStringList(job.getRequirementsJson()),
                job.getBusinessDomain(), job.getTeamName(), job.getRawContentHash(), json.readNode(job.getParseResultJson()),
                json.readStringList(job.getManualFieldsJson()), sourceViews(job), skillViews(job), parseRuns(job));
    }

    private JobSummaryView summary(JobEntity job) {
        long sources = sourceMapper.selectCount(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getJobId, job.getId()));
        long skills = skillMapper.selectCount(new LambdaQueryWrapper<JobSkillEntity>().eq(JobSkillEntity::getJobId, job.getId()));
        return new JobSummaryView(job.getPublicId(), job.getTitle(), job.getNormalizedTitle(), companyService.view(job.getCompanyId()),
                job.getCity(), job.getDistrict(), job.getWorkplaceText(), job.getRemoteType(), job.getSalaryMin(), job.getSalaryMax(),
                job.getSalaryMonths(), job.getCurrency(), job.getSalaryText(), job.getEducation(), job.getExperienceMinYears(),
                job.getExperienceMaxYears(), job.getGraduateYear(), job.getJobType(), job.getStatus(), job.getParseStatus(),
                job.getParserMode(), job.getParserVersion(), job.getPublishAt(), job.getUpdatedAt(), (int)sources, (int)skills, job.getVersion());
    }

    private List<SourceView> sourceViews(JobEntity job) { return sourceMapper.selectList(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getJobId, job.getId()).orderByDesc(JobSourceEntity::getCollectedAt)).stream().map(this::sourceView).toList(); }
    private SourceView sourceView(JobSourceEntity s) { return new SourceView(s.getPublicId(), s.getPlatform(), s.getPlatformJobId(), s.getSourceType(), s.getJobUrl(), s.getSourceTitle(), s.getSourceCompanyName(), json.readNode(s.getRawSnapshotJson()), s.getPublishAt(), s.getCollectedAt(), s.getLastSeenAt(), s.getAvailabilityStatus(), s.getCollectorVersion(), Boolean.TRUE.equals(s.getUserInitiated())); }
    private List<JobSkillView> skillViews(JobEntity job) { return skillMapper.selectList(new LambdaQueryWrapper<JobSkillEntity>().eq(JobSkillEntity::getJobId, job.getId()).orderByDesc(JobSkillEntity::getImportance)).stream().map(s -> { SkillEntity c = catalogMapper.selectById(s.getSkillId()); return new JobSkillView(s.getPublicId(), c.getPublicId(), c.getCanonicalName(), c.getDisplayName(), s.getRequirementType(), s.getImportance(), s.getMinYears(), s.getEvidenceText(), s.getSource(), s.getConfidence()); }).toList(); }
    private List<ParseRunView> parseRuns(JobEntity job) { return parseRunMapper.selectList(new LambdaQueryWrapper<JobParseRunEntity>().eq(JobParseRunEntity::getJobId, job.getId()).orderByDesc(JobParseRunEntity::getCreatedAt)).stream().map(r -> new ParseRunView(r.getPublicId(), r.getStatus(), r.getParserMode(), r.getParserVersion(), r.getSchemaVersion(), r.getPromptVersion(), r.getModelName(), r.getInputHash(), r.getOutputHash(), json.readNode(r.getResultJson()), r.getErrorCode(), r.getErrorMessage(), r.getElapsedMs(), r.getCreatedAt())).toList(); }

    private JobSourceEntity addSourceEntity(Long userId, JobEntity job, JobCreateRequest r, String platform, String sourceType) {
        JobSourceEntity s = new JobSourceEntity(); s.setUserId(userId); s.setJobId(job.getId()); s.setPlatform(platform); s.setPlatformJobId(trim(r.platformJobId()));
        s.setSourceType(sourceType); s.setJobUrl(trim(r.jobUrl())); s.setNormalizedUrlHash(normalization.urlHash(r.jobUrl())); s.setSourceTitle(r.title().trim());
        s.setSourceCompanyName(r.companyName().trim()); s.setRawSnapshotJson(json.write(objectMapper.valueToTree(r))); s.setPublishAt(r.publishAt());
        s.setCollectedAt(LocalDateTime.now()); s.setLastSeenAt(LocalDateTime.now()); s.setAvailabilityStatus("AVAILABLE"); s.setCollectorVersion("jobpilot-phase2-v1");
        s.setUserInitiated(!Boolean.FALSE.equals(r.userInitiated())); sourceMapper.insert(s); audit.record(userId, "JOB_SOURCE_CREATE", "JOB_SOURCE", s.getPublicId()); return s;
    }

    private JobSourceEntity findExactSource(String platform, String platformJobId, String url) {
        if (has(platformJobId)) { JobSourceEntity s = sourceMapper.selectOne(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getPlatform, platform).eq(JobSourceEntity::getPlatformJobId, platformJobId.trim()).last("LIMIT 1")); if (s != null) return s; }
        String hash = normalization.urlHash(url); if (hash != null) return sourceMapper.selectOne(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getPlatform, platform).eq(JobSourceEntity::getNormalizedUrlHash, hash).last("LIMIT 1"));
        return null;
    }

    private void recordDedup(Long userId, Long sourceId, Long jobId, String decision, BigDecimal score) { JobDedupLogEntity log = new JobDedupLogEntity(); log.setUserId(userId); log.setIncomingSourceId(sourceId); log.setCandidateJobId(jobId); log.setRuleScore(score); log.setDecision(decision); log.setAlgorithmVersion(DEDUP_VERSION); log.setDecidedBy("SYSTEM"); log.setDetailJson("{}"); dedupMapper.insert(log); audit.record(userId, "JOB_DEDUP_DECISION", "JOB", jobMapper.selectById(jobId).getPublicId()); }

    private JobEntity owned(Long userId, String publicId) { JobEntity e = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId, userId).eq(JobEntity::getPublicId, publicId).last("LIMIT 1")); if (e == null) throw new ResourceNotFoundException("Job"); return e; }
    private JobEntity ownedByInternalId(Long userId, Long id) { JobEntity e = jobMapper.selectOne(new LambdaQueryWrapper<JobEntity>().eq(JobEntity::getUserId, userId).eq(JobEntity::getId, id).last("LIMIT 1")); if (e == null) throw new ResourceNotFoundException("Job"); return e; }

    private void applyCreate(JobEntity j, JobCreateRequest r) { j.setCity(trim(r.city())); j.setDistrict(trim(r.district())); j.setWorkplaceText(trim(r.workplaceText())); j.setRemoteType(upper(defaultValue(r.remoteType(), "UNKNOWN"))); j.setSalaryMin(r.salaryMin()); j.setSalaryMax(r.salaryMax()); j.setSalaryMonths(r.salaryMonths()); j.setCurrency(upper(defaultValue(r.currency(), "CNY"))); j.setSalaryText(trim(r.salaryText())); j.setEducation(upper(trim(r.education()))); j.setExperienceMinYears(r.experienceMinYears()); j.setExperienceMaxYears(r.experienceMaxYears()); j.setGraduateYear(r.graduateYear()); j.setJobType(upper(defaultValue(r.jobType(), "FULL_TIME"))); j.setBusinessDomain(trim(r.businessDomain())); j.setTeamName(trim(r.teamName())); j.setPublishAt(r.publishAt()); }
    private void applyUpdate(JobEntity j, JobUpdateRequest r) { j.setCity(trim(r.city())); j.setDistrict(trim(r.district())); j.setWorkplaceText(trim(r.workplaceText())); j.setRemoteType(upper(defaultValue(r.remoteType(), "UNKNOWN"))); j.setSalaryMin(r.salaryMin()); j.setSalaryMax(r.salaryMax()); j.setSalaryMonths(r.salaryMonths()); j.setCurrency(upper(defaultValue(r.currency(), "CNY"))); j.setSalaryText(trim(r.salaryText())); j.setEducation(upper(trim(r.education()))); j.setExperienceMinYears(r.experienceMinYears()); j.setExperienceMaxYears(r.experienceMaxYears()); j.setGraduateYear(r.graduateYear()); j.setJobType(upper(defaultValue(r.jobType(), "FULL_TIME"))); j.setBusinessDomain(trim(r.businessDomain())); j.setTeamName(trim(r.teamName())); j.setPublishAt(r.publishAt()); }
    private List<String> manualFields(JobCreateRequest r) { List<String> f = new ArrayList<>(List.of("title","companyName","description")); if (r.city()!=null)f.add("city"); if(r.salaryMin()!=null||r.salaryText()!=null)f.add("salary"); if(r.education()!=null)f.add("education"); if(r.experienceMinYears()!=null)f.add("experience"); if(r.graduateYear()!=null)f.add("graduateYear"); if(r.jobType()!=null)f.add("jobType"); if(r.businessDomain()!=null)f.add("businessDomain"); if(r.teamName()!=null)f.add("teamName"); return f; }
    private void validateRanges(BigDecimal salaryMin, BigDecimal salaryMax, BigDecimal expMin, BigDecimal expMax) { if(salaryMin!=null&&salaryMax!=null&&salaryMax.compareTo(salaryMin)<0)throw new ValidationException("salaryMax must be greater than or equal to salaryMin"); if(expMin!=null&&expMax!=null&&expMax.compareTo(expMin)<0)throw new ValidationException("experienceMaxYears must be greater than or equal to experienceMinYears"); }

    private void applyRelationFilters(LambdaQueryWrapper<JobEntity> q, Long userId, JobQuery f) {
        if(has(f.company())||has(f.industry())||has(f.companySize())) { LambdaQueryWrapper<CompanyEntity> cq=new LambdaQueryWrapper<>(); if(has(f.company()))cq.like(CompanyEntity::getDisplayName,f.company().trim()); if(has(f.industry()))cq.eq(CompanyEntity::getIndustry,f.industry().trim()); if(has(f.companySize()))cq.eq(CompanyEntity::getCompanySize,f.companySize().trim()); List<Long> ids=companyMapper.selectList(cq).stream().map(CompanyEntity::getId).toList(); if(ids.isEmpty())q.eq(JobEntity::getId,-1L);else q.in(JobEntity::getCompanyId,ids); }
        if(has(f.platform())) { List<Long> ids=sourceMapper.selectList(new LambdaQueryWrapper<JobSourceEntity>().eq(JobSourceEntity::getUserId,userId).eq(JobSourceEntity::getPlatform,upper(f.platform()))).stream().map(JobSourceEntity::getJobId).distinct().toList(); if(ids.isEmpty())q.eq(JobEntity::getId,-1L);else q.in(JobEntity::getId,ids); }
        if(has(f.skill())) { List<Long> catalog=catalogMapper.selectList(new LambdaQueryWrapper<SkillEntity>().like(SkillEntity::getDisplayName,f.skill().trim())).stream().map(SkillEntity::getId).toList(); List<Long> ids=catalog.isEmpty()?List.of():skillMapper.selectList(new LambdaQueryWrapper<JobSkillEntity>().in(JobSkillEntity::getSkillId,catalog)).stream().map(JobSkillEntity::getJobId).distinct().toList(); if(ids.isEmpty())q.eq(JobEntity::getId,-1L);else q.in(JobEntity::getId,ids); }
    }

    private String encodeCursor(JobEntity j, String sort) { LocalDateTime t="updated_desc".equals(sort)?j.getUpdatedAt():(j.getPublishAt()!=null?j.getPublishAt():j.getFirstCollectedAt()); return Base64.getUrlEncoder().withoutPadding().encodeToString((t+"|"+j.getId()).getBytes(StandardCharsets.UTF_8)); }
    private Cursor decodeCursor(String value) { if(!has(value))return null; try { String decoded=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8); int i=decoded.lastIndexOf('|'); return new Cursor(LocalDateTime.parse(decoded.substring(0,i)),Long.parseLong(decoded.substring(i+1))); } catch(Exception e){ throw new ValidationException("Invalid cursor"); } }
    private record Cursor(LocalDateTime time, Long id) { }
    private BigDecimal decimal(JsonNode n) { return n==null||n.isNull()?null:n.decimalValue(); }
    private BigDecimal decimalOrDefault(JsonNode n, BigDecimal d) { BigDecimal v=decimal(n);return v==null?d:v; }
    private String textOrNull(JsonNode n,String field){return n.hasNonNull(field)&&!n.get(field).asText().isBlank()?n.get(field).asText():null;}
    private String trim(String v){return v==null||v.isBlank()?null:v.trim();}
    private String upper(String v){return v==null?null:v.trim().toUpperCase();}
    private String defaultValue(String v,String d){return v==null||v.isBlank()?d:v;}
    private boolean has(String v){return v!=null&&!v.isBlank();}
}
