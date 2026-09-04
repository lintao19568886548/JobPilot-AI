package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.domain.InterviewReminderEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.mapper.InterviewReminderMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewReminderService {
    private final InterviewReminderMapper reminders;
    private final InterviewService interviews;
    private final InterviewViewAssembler views;
    private final AuditService audit;

    public InterviewReminderService(InterviewReminderMapper reminders, InterviewService interviews,
                                    InterviewViewAssembler views, AuditService audit) {
        this.reminders = reminders; this.interviews = interviews; this.views = views; this.audit = audit;
    }

    public List<ReminderView> list(Long userId, String status) {
        LambdaQueryWrapper<InterviewReminderEntity> query = new LambdaQueryWrapper<InterviewReminderEntity>()
                .eq(InterviewReminderEntity::getUserId, userId).orderByAsc(InterviewReminderEntity::getRemindAt);
        if (status != null && !status.isBlank()) query.eq(InterviewReminderEntity::getStatus, status);
        return reminders.selectList(query).stream().map(views::reminder).toList();
    }

    @Transactional
    public ReminderView create(Long userId, ReminderRequest request) {
        InterviewEntity interview = interviews.ownedInterview(userId, request.interviewId());
        InterviewRoundEntity round = request.roundId() == null || request.roundId().isBlank() ? null : interviews.ownedRound(userId, request.roundId());
        if (round != null && !round.getInterviewId().equals(interview.getId())) throw new ValidationException("roundId does not belong to interviewId");
        LocalDateTime remindAt = InterviewTime.toUtc(request.remindAt(), request.timezone()); InterviewTime.validateFuture(remindAt);
        InterviewReminderEntity entity = new InterviewReminderEntity();
        entity.setUserId(userId); entity.setInterviewId(interview.getId()); entity.setRoundId(round == null ? null : round.getId());
        entity.setReminderType(request.reminderType()); entity.setTitle(request.title().trim()); entity.setRemindAt(remindAt);
        entity.setTimezone(request.timezone()); entity.setStatus("PENDING");
        try { reminders.insert(entity); }
        catch (DataIntegrityViolationException exception) { duplicate(); }
        audit.record(userId, "REMINDER_CREATE", "INTERVIEW_REMINDER", entity.getPublicId()); return views.reminder(entity);
    }

    @Transactional
    public ReminderView update(Long userId, String publicId, ReminderUpdateRequest request) {
        InterviewReminderEntity entity = owned(userId, publicId); InterviewService.version(entity.getVersion(), request.version());
        if (!"PENDING".equals(entity.getStatus())) invalid("Only a PENDING reminder can be rescheduled");
        LocalDateTime remindAt = InterviewTime.toUtc(request.remindAt(), request.timezone()); InterviewTime.validateFuture(remindAt);
        entity.setReminderType(request.reminderType()); entity.setTitle(request.title().trim()); entity.setRemindAt(remindAt); entity.setTimezone(request.timezone());
        try { reminders.updateById(entity); }
        catch (DataIntegrityViolationException exception) { duplicate(); }
        audit.record(userId, "REMINDER_UPDATE", "INTERVIEW_REMINDER", publicId); return views.reminder(owned(userId, publicId));
    }

    @Transactional
    public ReminderView done(Long userId, String publicId, VersionRequest request) {
        InterviewReminderEntity entity = owned(userId, publicId); InterviewService.version(entity.getVersion(), request.version());
        if (!"PENDING".equals(entity.getStatus())) invalid("Only a PENDING reminder can be completed");
        entity.setStatus("DONE"); entity.setCompletedAt(LocalDateTime.now(ZoneOffset.UTC)); reminders.updateById(entity);
        audit.record(userId, "REMINDER_DONE", "INTERVIEW_REMINDER", publicId); return views.reminder(owned(userId, publicId));
    }

    @Transactional
    public ReminderView cancel(Long userId, String publicId, VersionRequest request) {
        InterviewReminderEntity entity = owned(userId, publicId); InterviewService.version(entity.getVersion(), request.version());
        if (!"PENDING".equals(entity.getStatus())) invalid("Only a PENDING reminder can be cancelled");
        entity.setStatus("CANCELLED"); entity.setCancelledAt(LocalDateTime.now(ZoneOffset.UTC)); reminders.updateById(entity);
        audit.record(userId, "REMINDER_CANCEL", "INTERVIEW_REMINDER", publicId); return views.reminder(owned(userId, publicId));
    }

    @Transactional
    public void delete(Long userId, String publicId) {
        InterviewReminderEntity entity = owned(userId, publicId); reminders.deleteById(entity);
        audit.record(userId, "REMINDER_DELETE", "INTERVIEW_REMINDER", publicId);
    }

    public InterviewReminderEntity owned(Long userId, String publicId) {
        InterviewReminderEntity value = reminders.selectOne(new LambdaQueryWrapper<InterviewReminderEntity>()
                .eq(InterviewReminderEntity::getUserId, userId).eq(InterviewReminderEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview Reminder");
        interviews.ownedInterviewById(userId, value.getInterviewId()); return value;
    }

    private static void duplicate() { throw new BusinessException(4098008, "An active duplicate reminder already exists", HttpStatus.CONFLICT); }
    private static void invalid(String message) { throw new BusinessException(4098009, message, HttpStatus.CONFLICT); }
}
