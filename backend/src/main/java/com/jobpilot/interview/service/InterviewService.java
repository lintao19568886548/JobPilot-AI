package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.domain.InterviewReminderEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.mapper.InterviewMapper;
import com.jobpilot.interview.mapper.InterviewReminderMapper;
import com.jobpilot.interview.mapper.InterviewRoundMapper;
import java.net.URI;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewService {
    private final InterviewMapper interviews;
    private final InterviewRoundMapper rounds;
    private final InterviewReminderMapper reminders;
    private final InterviewReferenceService references;
    private final InterviewViewAssembler views;
    private final AuditService audit;

    public InterviewService(InterviewMapper interviews, InterviewRoundMapper rounds, InterviewReminderMapper reminders,
                            InterviewReferenceService references, InterviewViewAssembler views, AuditService audit) {
        this.interviews = interviews; this.rounds = rounds; this.reminders = reminders;
        this.references = references; this.views = views; this.audit = audit;
    }

    public InterviewPage list(Long userId, int page, int size, String status, LocalDateTime from, LocalDateTime to,
                              String company, String role, String jobId, String applicationId) {
        if (page < 1 || size < 1 || size > 100) throw new ValidationException("page must be >= 1 and size must be 1..100");
        LambdaQueryWrapper<InterviewEntity> query = new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getUserId, userId).orderByDesc(InterviewEntity::getUpdatedAt).orderByDesc(InterviewEntity::getId);
        if (status != null && !status.isBlank()) query.eq(InterviewEntity::getStatus, status);
        if (company != null && !company.isBlank()) query.like(InterviewEntity::getCompanyNameSnapshot, company.trim());
        if (role != null && !role.isBlank()) query.like(InterviewEntity::getRoleSnapshot, role.trim());
        if (jobId != null && !jobId.isBlank()) query.eq(InterviewEntity::getJobId, references.optionalJob(userId, jobId).getId());
        if (applicationId != null && !applicationId.isBlank()) query.eq(InterviewEntity::getApplicationId, references.optionalApplication(userId, applicationId).getId());
        if (from != null) query.apply("EXISTS (SELECT 1 FROM interview_rounds r WHERE r.interview_id = interviews.id AND r.deleted_at IS NULL AND r.scheduled_start_at >= {0})", from);
        if (to != null) query.apply("EXISTS (SELECT 1 FROM interview_rounds r WHERE r.interview_id = interviews.id AND r.deleted_at IS NULL AND r.scheduled_start_at <= {0})", to);
        Page<InterviewEntity> result = interviews.selectPage(Page.of(page, size), query);
        return new InterviewPage(result.getRecords().stream().map(item -> views.interview(item, true)).toList(), result.getTotal(), page, size);
    }

    @Transactional
    public InterviewView create(Long userId, InterviewCreateRequest request) {
        InterviewTime.validateZone(request.timezone());
        InterviewReferenceService.References linked = references.resolve(userId, request.applicationId(), request.jobId(),
                request.resumeVersionId(), request.companyName(), request.role());
        InterviewEntity entity = new InterviewEntity();
        entity.setUserId(userId);
        entity.setApplicationId(linked.application() == null ? null : linked.application().getId());
        entity.setJobId(linked.job() == null ? null : linked.job().getId());
        entity.setCompanyId(linked.company() == null ? null : linked.company().getId());
        entity.setResumeVersionId(linked.resumeVersion() == null ? null : linked.resumeVersion().getId());
        entity.setCompanyNameSnapshot(linked.companyName()); entity.setRoleSnapshot(linked.role());
        entity.setStatus("SCHEDULED"); entity.setResult("PENDING"); entity.setTimezone(request.timezone()); entity.setNotes(request.notes());
        interviews.insert(entity);
        audit.record(userId, "INTERVIEW_CREATE", "INTERVIEW", entity.getPublicId());
        return views.interview(entity, true);
    }

    public InterviewView get(Long userId, String publicId) { return views.interview(ownedInterview(userId, publicId), true); }

    @Transactional
    public InterviewView update(Long userId, String publicId, InterviewUpdateRequest request) {
        InterviewEntity entity = ownedInterview(userId, publicId);
        version(entity.getVersion(), request.version());
        if (request.status() != null) InterviewPolicy.interviewTransition(entity.getStatus(), request.status());
        if (request.timezone() != null) InterviewTime.validateZone(request.timezone());
        if (request.companyName() != null && !request.companyName().isBlank()) entity.setCompanyNameSnapshot(request.companyName().trim());
        if (request.role() != null && !request.role().isBlank()) entity.setRoleSnapshot(request.role().trim());
        if (request.status() != null) entity.setStatus(request.status());
        if (request.result() != null) entity.setResult(request.result());
        if (request.timezone() != null) entity.setTimezone(request.timezone());
        entity.setNotes(request.notes());
        if (interviews.updateById(entity) != 1) conflict();
        if ("CANCELLED".equals(entity.getStatus()) || "NO_SHOW".equals(entity.getStatus())) cancelPendingReminders(userId, entity.getId(), null);
        audit.record(userId, "INTERVIEW_UPDATE", "INTERVIEW", publicId);
        return views.interview(ownedInterview(userId, publicId), true);
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        InterviewEntity entity = ownedInterview(userId, publicId);
        cancelPendingReminders(userId, entity.getId(), null);
        interviews.deleteById(entity);
        audit.record(userId, "INTERVIEW_DELETE", "INTERVIEW", publicId);
    }

    @Transactional
    public RoundView createRound(Long userId, String interviewPublicId, RoundCreateRequest request) {
        InterviewEntity interview = ownedInterview(userId, interviewPublicId);
        if ("CANCELLED".equals(interview.getStatus())) throw new BusinessException(4098002, "Cannot add a round to a cancelled interview", HttpStatus.CONFLICT);
        LocalDateTime start = InterviewTime.toUtc(request.scheduledStartAt(), request.timezone());
        LocalDateTime end = InterviewTime.toUtc(request.scheduledEndAt(), request.timezone());
        InterviewTime.validateRange(start, end); validateLink(request.meetingLink());
        InterviewRoundEntity entity = new InterviewRoundEntity();
        entity.setUserId(userId); entity.setInterviewId(interview.getId()); entity.setRoundNo(request.roundNo());
        entity.setRoundType(request.roundType()); entity.setTitle(request.title().trim()); entity.setScheduledStartAt(start);
        entity.setScheduledEndAt(end); entity.setTimezone(request.timezone()); entity.setFormat(request.format());
        entity.setMeetingLink(blankToNull(request.meetingLink())); entity.setLocation(blankToNull(request.location()));
        entity.setInterviewerName(blankToNull(request.interviewerName())); entity.setStatus("PLANNED");
        entity.setResult("PENDING"); entity.setNotes(request.notes());
        try { rounds.insert(entity); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException(4098003, "Round number already exists", HttpStatus.CONFLICT); }
        audit.record(userId, "ROUND_CREATE", "INTERVIEW_ROUND", entity.getPublicId());
        return views.round(entity);
    }

    @Transactional
    public RoundView updateRound(Long userId, String publicId, RoundUpdateRequest request) {
        InterviewRoundEntity entity = ownedRound(userId, publicId);
        version(entity.getVersion(), request.version()); InterviewPolicy.roundTransition(entity.getStatus(), request.status());
        LocalDateTime start = InterviewTime.toUtc(request.scheduledStartAt(), request.timezone());
        LocalDateTime end = InterviewTime.toUtc(request.scheduledEndAt(), request.timezone());
        InterviewTime.validateRange(start, end); validateLink(request.meetingLink());
        entity.setRoundNo(request.roundNo()); entity.setRoundType(request.roundType()); entity.setTitle(request.title().trim());
        entity.setScheduledStartAt(start); entity.setScheduledEndAt(end); entity.setTimezone(request.timezone());
        entity.setFormat(request.format()); entity.setMeetingLink(blankToNull(request.meetingLink()));
        entity.setLocation(blankToNull(request.location())); entity.setInterviewerName(blankToNull(request.interviewerName()));
        entity.setStatus(request.status()); entity.setResult(request.result()); entity.setNotes(request.notes());
        entity.setCompletedAt("COMPLETED".equals(request.status()) ? LocalDateTime.now(ZoneOffset.UTC) : null);
        try { if (rounds.updateById(entity) != 1) conflict(); }
        catch (DataIntegrityViolationException exception) { throw new BusinessException(4098003, "Round number already exists", HttpStatus.CONFLICT); }
        if ("CANCELLED".equals(entity.getStatus())) cancelPendingReminders(userId, entity.getInterviewId(), entity.getId());
        audit.record(userId, "ROUND_UPDATE", "INTERVIEW_ROUND", publicId);
        return views.round(ownedRound(userId, publicId));
    }

    @Transactional
    public void deleteRound(Long userId, String publicId) {
        InterviewRoundEntity entity = ownedRound(userId, publicId);
        cancelPendingReminders(userId, entity.getInterviewId(), entity.getId());
        rounds.deleteById(entity); audit.record(userId, "ROUND_DELETE", "INTERVIEW_ROUND", publicId);
    }

    public InterviewEntity ownedInterview(Long userId, String publicId) {
        InterviewEntity value = interviews.selectOne(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getUserId, userId).eq(InterviewEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview");
        return value;
    }

    public InterviewRoundEntity ownedRound(Long userId, String publicId) {
        InterviewRoundEntity value = rounds.selectOne(new LambdaQueryWrapper<InterviewRoundEntity>()
                .eq(InterviewRoundEntity::getUserId, userId).eq(InterviewRoundEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview Round");
        ownedInterviewById(userId, value.getInterviewId());
        return value;
    }

    public InterviewEntity ownedInterviewById(Long userId, Long id) {
        InterviewEntity value = interviews.selectOne(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getUserId, userId).eq(InterviewEntity::getId, id).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview");
        return value;
    }

    private void cancelPendingReminders(Long userId, Long interviewId, Long roundId) {
        LambdaQueryWrapper<InterviewReminderEntity> query = new LambdaQueryWrapper<InterviewReminderEntity>()
                .eq(InterviewReminderEntity::getUserId, userId).eq(InterviewReminderEntity::getInterviewId, interviewId)
                .eq(InterviewReminderEntity::getStatus, "PENDING");
        if (roundId != null) query.eq(InterviewReminderEntity::getRoundId, roundId);
        List<InterviewReminderEntity> values = reminders.selectList(query);
        for (InterviewReminderEntity reminder : values) {
            reminder.setStatus("CANCELLED"); reminder.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC)); reminders.updateById(reminder);
        }
    }

    private static void validateLink(String value) {
        if (value == null || value.isBlank()) return;
        try {
            URI uri = URI.create(value.trim());
            if (!("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme())) || uri.getHost() == null) throw new IllegalArgumentException();
        } catch (Exception exception) { throw new ValidationException("meetingLink must be an HTTP(S) URL"); }
    }

    static void version(Integer current, Integer requested) {
        if (!current.equals(requested)) conflict();
    }

    private static void conflict() { throw new BusinessException(4098004, "Resource version conflict", HttpStatus.CONFLICT); }
    private static String blankToNull(String value) { return value == null || value.isBlank() ? null : value.trim(); }
}
