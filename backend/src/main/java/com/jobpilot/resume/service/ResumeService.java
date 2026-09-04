package com.jobpilot.resume.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.resume.domain.ResumeEntity;
import com.jobpilot.resume.domain.ResumeSectionEntity;
import com.jobpilot.resume.domain.ResumeSectionType;
import com.jobpilot.resume.domain.ResumeSourceType;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.dto.ResumeDtos.ResumeCreateRequest;
import com.jobpilot.resume.dto.ResumeDtos.ResumeDetailView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSectionView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeUpdateRequest;
import com.jobpilot.resume.dto.ResumeDtos.ResumeVersionSummaryView;
import com.jobpilot.resume.dto.ResumeDtos.ResumeVersionView;
import com.jobpilot.resume.dto.ResumeDtos.SectionRequest;
import com.jobpilot.resume.dto.ResumeDtos.VersionCreateRequest;
import com.jobpilot.resume.dto.ResumeDtos.TailoredVersionCommand;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.resume.mapper.ResumeMapper;
import com.jobpilot.resume.mapper.ResumeSectionMapper;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import com.jobpilot.tailoring.mapper.PromptTemplateMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeService {

    private static final Set<String> VALID_STATUSES = Set.of("DRAFT", "ACTIVE", "ARCHIVED");
    private final ResumeMapper resumeMapper;
    private final ResumeVersionMapper versionMapper;
    private final ResumeSectionMapper sectionMapper;
    private final JsonCodec jsonCodec;
    private final AuditService auditService;
    private final JobMapper jobMapper;
    private final PromptTemplateMapper promptTemplateMapper;

    public ResumeService(
            ResumeMapper resumeMapper,
            ResumeVersionMapper versionMapper,
            ResumeSectionMapper sectionMapper,
            JsonCodec jsonCodec,
            AuditService auditService,
            JobMapper jobMapper,
            PromptTemplateMapper promptTemplateMapper) {
        this.resumeMapper = resumeMapper;
        this.versionMapper = versionMapper;
        this.sectionMapper = sectionMapper;
        this.jsonCodec = jsonCodec;
        this.auditService = auditService;
        this.jobMapper = jobMapper;
        this.promptTemplateMapper = promptTemplateMapper;
    }

    public List<ResumeSummaryView> list(Long userId) {
        return resumeMapper.selectList(new LambdaQueryWrapper<ResumeEntity>()
                        .eq(ResumeEntity::getUserId, userId)
                        .orderByDesc(ResumeEntity::getMaster)
                        .orderByDesc(ResumeEntity::getDefaultResume)
                        .orderByDesc(ResumeEntity::getUpdatedAt))
                .stream().map(this::summary).toList();
    }

    public ResumeDetailView get(Long userId, String publicId) {
        ResumeEntity resume = owned(userId, publicId);
        return new ResumeDetailView(summary(resume), versionsFor(resume));
    }

    @Transactional
    public ResumeDetailView create(Long userId, ResumeCreateRequest request) {
        String status = normalizedStatus(request.status());
        long existingCount = resumeMapper.selectCount(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId));
        boolean makeDefault = Boolean.TRUE.equals(request.defaultResume()) || existingCount == 0;
        boolean makeMaster = Boolean.TRUE.equals(request.master());
        if (makeMaster) {
            clearMaster(userId);
        }
        if (makeDefault) {
            clearDefault(userId);
        }
        ResumeEntity resume = new ResumeEntity();
        resume.setUserId(userId);
        resume.setName(request.name().trim());
        resume.setTargetRole(request.targetRole());
        resume.setMaster(makeMaster);
        resume.setDefaultResume(makeDefault);
        resume.setDescription(request.description());
        resume.setStatus(status);
        resumeMapper.insert(resume);
        auditService.record(userId, "RESUME_CREATE", "RESUME", resume.getPublicId());
        if (makeMaster) {
            auditService.record(userId, "SET_MASTER_RESUME", "RESUME", resume.getPublicId());
        }
        if (makeDefault) {
            auditService.record(userId, "SET_DEFAULT_RESUME", "RESUME", resume.getPublicId());
        }
        return new ResumeDetailView(summary(resume), List.of());
    }

    @Transactional
    public ResumeDetailView update(Long userId, String publicId, ResumeUpdateRequest request) {
        ResumeEntity resume = owned(userId, publicId);
        resume.setName(request.name().trim());
        resume.setTargetRole(request.targetRole());
        resume.setDescription(request.description());
        resume.setStatus(normalizedStatus(request.status()));
        if (resumeMapper.updateById(resume) != 1) {
            throw new ValidationException("Resume was modified concurrently; reload and retry");
        }
        auditService.record(userId, "RESUME_UPDATE", "RESUME", publicId);
        return new ResumeDetailView(summary(resume), versionsFor(resume));
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        ResumeEntity resume = owned(userId, publicId);
        if (Boolean.TRUE.equals(resume.getMaster())) {
            throw new BusinessException(4093001, "Master Resume cannot be deleted before another Resume is set as Master", HttpStatus.CONFLICT);
        }
        resumeMapper.deleteById(resume.getId());
        auditService.record(userId, "RESUME_DELETE", "RESUME", publicId);
    }

    @Transactional
    public ResumeDetailView setDefault(Long userId, String publicId) {
        ResumeEntity resume = owned(userId, publicId);
        requireActive(resume);
        clearDefault(userId);
        resume.setDefaultResume(true);
        if (resumeMapper.updateById(resume) != 1) {
            throw new ValidationException("Resume was modified concurrently; reload and retry");
        }
        auditService.record(userId, "SET_DEFAULT_RESUME", "RESUME", publicId);
        return new ResumeDetailView(summary(resume), versionsFor(resume));
    }

    @Transactional
    public ResumeDetailView setMaster(Long userId, String publicId) {
        ResumeEntity resume = owned(userId, publicId);
        requireActive(resume);
        clearMaster(userId);
        resume.setMaster(true);
        if (resumeMapper.updateById(resume) != 1) {
            throw new ValidationException("Resume was modified concurrently; reload and retry");
        }
        auditService.record(userId, "SET_MASTER_RESUME", "RESUME", publicId);
        return new ResumeDetailView(summary(resume), versionsFor(resume));
    }

    @Transactional
    public ResumeVersionView createVersion(Long userId, String resumePublicId, VersionCreateRequest request) {
        ResumeEntity ownedResume = owned(userId, resumePublicId);
        ResumeEntity resume = resumeMapper.selectByIdForUpdate(ownedResume.getId());
        if (resume == null) {
            throw new ResourceNotFoundException("Resume");
        }
        JsonNode contentNode = jsonCodec.toNode(request.content());
        validateContent(contentNode, request.sections());
        int nextVersion = versionMapper.selectMaxVersionNumber(resume.getId()) + 1;
        String content = jsonCodec.writeNode(contentNode);
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setResumeId(resume.getId());
        version.setVersionNumber(nextVersion);
        version.setVersionName(request.versionName() == null || request.versionName().isBlank()
                ? resume.getName() + " v" + nextVersion
                : request.versionName().trim());
        version.setContentJson(content);
        version.setRenderedText(request.renderedText());
        version.setSourceType(request.sourceType().name());
        version.setCreatedBy(request.createdBy() == null || request.createdBy().isBlank() ? "USER" : request.createdBy().trim());
        version.setActive(true);
        version.setContentHash(contentHash(content, request.sections()));
        versionMapper.insert(version);
        List<SectionRequest> sections = request.sections() == null ? List.of() : request.sections();
        for (int index = 0; index < sections.size(); index++) {
            SectionRequest item = sections.get(index);
            ResumeSectionEntity section = new ResumeSectionEntity();
            section.setResumeVersionId(version.getId());
            section.setSectionType(item.sectionType().name());
            section.setContentJson(jsonCodec.writeNode(jsonCodec.toNode(item.content())));
            section.setSortOrder(item.sortOrder() == null ? index : item.sortOrder());
            sectionMapper.insert(section);
        }
        resume.setCurrentVersionId(version.getId());
        if (resumeMapper.updateById(resume) != 1) {
            throw new ValidationException("Resume was modified concurrently; reload and retry");
        }
        auditService.record(userId, "RESUME_VERSION_CREATE", "RESUME_VERSION", version.getPublicId());
        return versionView(resume, version);
    }

    @Transactional
    public ResumeVersionView createTailoredVersion(Long userId, String resumePublicId, TailoredVersionCommand command) {
        ResumeEntity ownedResume = owned(userId, resumePublicId);
        if (Boolean.TRUE.equals(ownedResume.getMaster())) {
            throw new BusinessException(4096001, "Tailored versions cannot replace the Master Resume fact source", HttpStatus.CONFLICT);
        }
        ResumeEntity resume = resumeMapper.selectByIdForUpdate(ownedResume.getId());
        ResumeVersionEntity parent = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getPublicId, command.baseVersionId()).last("LIMIT 1"));
        ResumeEntity parentResume = parent == null ? null : resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, parent.getResumeId()).eq(ResumeEntity::getUserId, userId)
                .eq(ResumeEntity::getMaster, true).last("LIMIT 1"));
        if (parent == null || parentResume == null) throw new ValidationException("Tailored version must use the current user's Master Resume");
        com.jobpilot.job.domain.JobEntity job = jobMapper.selectOne(new LambdaQueryWrapper<com.jobpilot.job.domain.JobEntity>()
                .eq(com.jobpilot.job.domain.JobEntity::getUserId, userId)
                .eq(com.jobpilot.job.domain.JobEntity::getPublicId, command.jobId()).last("LIMIT 1"));
        if (job == null) throw new ResourceNotFoundException("Job");
        validateContent(command.content(), command.sections());
        int next = versionMapper.selectMaxVersionNumber(resume.getId()) + 1;
        String content = jsonCodec.writeNode(command.content());
        ResumeVersionEntity version = new ResumeVersionEntity();
        version.setResumeId(resume.getId()); version.setParentVersionId(parent.getId());
        version.setVersionNumber(next); version.setVersionName(command.versionName() == null || command.versionName().isBlank()
                ? resume.getName() + " v" + next : command.versionName().trim());
        version.setContentJson(content); version.setRenderedText(command.renderedText());
        version.setSourceType(ResumeSourceType.AI.name()); version.setCreatedBy("RESUME_AGENT"); version.setActive(true);
        version.setContentHash(contentHash(content, command.sections())); version.setTailoredForJobId(job.getId());
        version.setPromptTemplateId(command.promptTemplateId()); version.setAiCallId(command.aiCallId());
        version.setModelName(command.modelName()); version.setTruthCheckStatus("VERIFIED");
        versionMapper.insert(version);
        List<SectionRequest> sections = command.sections() == null ? List.of() : command.sections();
        for (int index = 0; index < sections.size(); index++) {
            SectionRequest item = sections.get(index);
            ResumeSectionEntity section = new ResumeSectionEntity();
            section.setResumeVersionId(version.getId()); section.setSectionType(item.sectionType().name());
            section.setContentJson(jsonCodec.writeNode(jsonCodec.toNode(item.content()))); section.setSortOrder(item.sortOrder() == null ? index : item.sortOrder());
            sectionMapper.insert(section);
        }
        resume.setCurrentVersionId(version.getId());
        if (resumeMapper.updateById(resume) != 1) throw new ValidationException("Resume was modified concurrently; reload and retry");
        auditService.record(userId, "TAILORED_RESUME_VERSION_CREATE", "RESUME_VERSION", version.getPublicId());
        return versionView(resume, version);
    }

    public List<ResumeVersionSummaryView> versions(Long userId, String resumePublicId) {
        return versionsFor(owned(userId, resumePublicId));
    }

    public ResumeVersionView version(Long userId, String versionPublicId) {
        ResumeVersionEntity version = versionMapper.selectOne(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getPublicId, versionPublicId)
                .last("LIMIT 1"));
        if (version == null) {
            throw new ResourceNotFoundException("Resume version");
        }
        ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getId, version.getResumeId())
                .eq(ResumeEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (resume == null) {
            throw new ResourceNotFoundException("Resume version");
        }
        return versionView(resume, version);
    }

    private ResumeEntity owned(Long userId, String publicId) {
        ResumeEntity resume = resumeMapper.selectOne(new LambdaQueryWrapper<ResumeEntity>()
                .eq(ResumeEntity::getPublicId, publicId)
                .eq(ResumeEntity::getUserId, userId)
                .last("LIMIT 1"));
        if (resume == null) {
            throw new ResourceNotFoundException("Resume");
        }
        return resume;
    }

    private void clearMaster(Long userId) {
        resumeMapper.update(null, new LambdaUpdateWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .eq(ResumeEntity::getMaster, true)
                .set(ResumeEntity::getMaster, false));
    }

    private void clearDefault(Long userId) {
        resumeMapper.update(null, new LambdaUpdateWrapper<ResumeEntity>()
                .eq(ResumeEntity::getUserId, userId)
                .eq(ResumeEntity::getDefaultResume, true)
                .set(ResumeEntity::getDefaultResume, false));
    }

    private ResumeSummaryView summary(ResumeEntity resume) {
        long count = versionMapper.selectCount(new LambdaQueryWrapper<ResumeVersionEntity>()
                .eq(ResumeVersionEntity::getResumeId, resume.getId()));
        ResumeVersionEntity current = resume.getCurrentVersionId() == null ? null : versionMapper.selectById(resume.getCurrentVersionId());
        return new ResumeSummaryView(resume.getPublicId(), resume.getName(), resume.getTargetRole(), resume.getMaster(),
                resume.getDefaultResume(), resume.getDescription(), resume.getStatus(),
                current == null ? null : current.getPublicId(), current == null ? null : current.getVersionNumber(),
                count, resume.getVersion(), resume.getCreatedAt(), resume.getUpdatedAt());
    }

    private List<ResumeVersionSummaryView> versionsFor(ResumeEntity resume) {
        return versionMapper.selectList(new LambdaQueryWrapper<ResumeVersionEntity>()
                        .eq(ResumeVersionEntity::getResumeId, resume.getId())
                        .orderByDesc(ResumeVersionEntity::getVersionNumber))
                .stream().map(version -> new ResumeVersionSummaryView(
                        version.getPublicId(), version.getVersionNumber(), version.getVersionName(),
                        ResumeSourceType.valueOf(version.getSourceType()), version.getCreatedBy(), version.getActive(),
                        version.getId().equals(resume.getCurrentVersionId()), version.getCreatedAt(),
                        publicVersionId(version.getParentVersionId()), publicJobId(version.getTailoredForJobId()),
                        version.getTruthCheckStatus()))
                .toList();
    }

    private ResumeVersionView versionView(ResumeEntity resume, ResumeVersionEntity version) {
        List<ResumeSectionView> sections = sectionMapper.selectList(new LambdaQueryWrapper<ResumeSectionEntity>()
                        .eq(ResumeSectionEntity::getResumeVersionId, version.getId())
                        .orderByAsc(ResumeSectionEntity::getSortOrder))
                .stream().map(section -> new ResumeSectionView(section.getPublicId(),
                        ResumeSectionType.valueOf(section.getSectionType()), jsonCodec.readNode(section.getContentJson()),
                        section.getSortOrder())).toList();
        return new ResumeVersionView(version.getPublicId(), resume.getPublicId(), version.getVersionNumber(),
                version.getVersionName(), jsonCodec.readNode(version.getContentJson()), version.getRenderedText(),
                ResumeSourceType.valueOf(version.getSourceType()), version.getCreatedBy(), version.getActive(),
                version.getId().equals(resume.getCurrentVersionId()), version.getContentHash(), version.getCreatedAt(), sections,
                publicVersionId(version.getParentVersionId()), publicJobId(version.getTailoredForJobId()),
                promptVersion(version.getPromptTemplateId()), version.getModelName(), version.getTruthCheckStatus());
    }

    private String publicVersionId(Long id) {
        ResumeVersionEntity entity = id == null ? null : versionMapper.selectById(id);
        return entity == null ? null : entity.getPublicId();
    }

    private String publicJobId(Long id) {
        com.jobpilot.job.domain.JobEntity entity = id == null ? null : jobMapper.selectById(id);
        return entity == null ? null : entity.getPublicId();
    }

    private String promptVersion(Long id) {
        com.jobpilot.tailoring.domain.PromptTemplateEntity entity = id == null ? null : promptTemplateMapper.selectById(id);
        return entity == null ? null : entity.getTemplateKey().toLowerCase() + "/v" + entity.getVersionNo();
    }

    private void validateContent(JsonNode content, List<SectionRequest> sections) {
        if (!content.isObject()) {
            throw new ValidationException("Resume content must be a JSON object");
        }
        if (sections == null) {
            return;
        }
        long distinct = sections.stream().map(SectionRequest::sectionType).distinct().count();
        if (distinct != sections.size()) {
            throw new ValidationException("Resume section types must be unique within a version");
        }
        if (sections.stream().map(SectionRequest::content).map(jsonCodec::toNode)
                .anyMatch(section -> !section.isObject() && !section.isArray())) {
            throw new ValidationException("Resume section content must be a JSON object or array");
        }
    }

    private String contentHash(String content, List<SectionRequest> sections) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(content.getBytes(StandardCharsets.UTF_8));
            if (sections != null) {
                sections.forEach(section -> digest.update((section.sectionType().name()
                        + jsonCodec.writeNode(jsonCodec.toNode(section.content())))
                        .getBytes(StandardCharsets.UTF_8)));
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String normalizedStatus(String value) {
        String status = value == null || value.isBlank() ? "ACTIVE" : value.trim().toUpperCase();
        if (!VALID_STATUSES.contains(status)) {
            throw new ValidationException("Unknown resume status");
        }
        return status;
    }

    private void requireActive(ResumeEntity resume) {
        if (!"ACTIVE".equals(resume.getStatus())) {
            throw new ValidationException("Only an ACTIVE Resume can be selected");
        }
    }
}
