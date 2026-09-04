package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.domain.InterviewReminderEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.mapper.InterviewReminderMapper;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class InterviewReminderServiceTest {
    private final InterviewReminderMapper mapper = mock(InterviewReminderMapper.class);
    private final InterviewService interviews = mock(InterviewService.class);
    private final InterviewViewAssembler views = mock(InterviewViewAssembler.class);
    private final AuditService audit = mock(AuditService.class);
    private final InterviewReminderService service = new InterviewReminderService(mapper, interviews, views, audit);

    @Test
    void mapsDatabaseDuplicateToStableConflict() {
        InterviewEntity interview = new InterviewEntity(); interview.setId(1L);
        InterviewRoundEntity round = new InterviewRoundEntity(); round.setId(2L); round.setInterviewId(1L);
        when(interviews.ownedInterview(9L, "interview-1")).thenReturn(interview);
        when(interviews.ownedRound(9L, "round-1")).thenReturn(round);
        when(mapper.insert(any(InterviewReminderEntity.class))).thenThrow(new org.springframework.dao.DataIntegrityViolationException("duplicate"));
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusHours(3);

        assertThatThrownBy(() -> service.create(9L, new ReminderRequest("interview-1", "round-1", "PREPARE",
                "Prepare", future, "Asia/Shanghai")))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).getCode()).isEqualTo(4098008);
    }

    @Test
    void reschedulesThenCancelsPendingReminder() {
        InterviewReminderEntity reminder = new InterviewReminderEntity();
        reminder.setId(1L); reminder.setPublicId("reminder-1"); reminder.setInterviewId(5L);
        reminder.setStatus("PENDING"); reminder.setVersion(0);
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(reminder);
        OffsetDateTime future = OffsetDateTime.now(ZoneOffset.ofHours(8)).plusHours(4);

        service.update(9L, "reminder-1", new ReminderUpdateRequest("CUSTOM", "New time", future,
                "Asia/Shanghai", 0));
        assertThat(reminder.getTitle()).isEqualTo("New time");
        service.cancel(9L, "reminder-1", new VersionRequest(0));
        assertThat(reminder.getStatus()).isEqualTo("CANCELLED");
        verify(mapper, org.mockito.Mockito.times(2)).updateById(reminder);
        verify(interviews, org.mockito.Mockito.atLeastOnce()).ownedInterviewById(9L, 5L);
    }
}
