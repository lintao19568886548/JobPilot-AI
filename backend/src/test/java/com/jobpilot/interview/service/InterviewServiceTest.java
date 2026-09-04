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
import com.jobpilot.interview.mapper.InterviewMapper;
import com.jobpilot.interview.mapper.InterviewReminderMapper;
import com.jobpilot.interview.mapper.InterviewRoundMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewServiceTest {
    private final InterviewMapper mapper = mock(InterviewMapper.class);
    private final InterviewRoundMapper rounds = mock(InterviewRoundMapper.class);
    private final InterviewReminderMapper reminders = mock(InterviewReminderMapper.class);
    private final InterviewReferenceService references = mock(InterviewReferenceService.class);
    private final InterviewViewAssembler views = mock(InterviewViewAssembler.class);
    private final AuditService audit = mock(AuditService.class);
    private final InterviewService service = new InterviewService(mapper, rounds, reminders, references, views, audit);

    @Test
    void createsManualInterviewWithResolvedSnapshots() {
        when(references.resolve(1L, null, null, null, " Example ", " Backend "))
                .thenReturn(new InterviewReferenceService.References(null, null, null, null, "Example", "Backend"));

        service.create(1L, new InterviewCreateRequest(null, null, null, " Example ", " Backend ", "Asia/Shanghai", "note"));

        ArgumentCaptor<InterviewEntity> captor = ArgumentCaptor.forClass(InterviewEntity.class);
        verify(mapper).insert(captor.capture());
        assertThat(captor.getValue().getCompanyNameSnapshot()).isEqualTo("Example");
        assertThat(captor.getValue().getRoleSnapshot()).isEqualTo("Backend");
        assertThat(captor.getValue().getStatus()).isEqualTo("SCHEDULED");
        assertThat(captor.getValue().getResult()).isEqualTo("PENDING");
    }

    @Test
    void rejectsStaleOptimisticVersionBeforeMutation() {
        InterviewEntity current = new InterviewEntity();
        current.setPublicId("interview-1"); current.setVersion(3); current.setStatus("SCHEDULED");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);

        assertThatThrownBy(() -> service.update(1L, "interview-1", new InterviewUpdateRequest(
                "Example", "Backend", "IN_PROGRESS", "PENDING", "Asia/Shanghai", null, 2)))
                .isInstanceOf(BusinessException.class)
                .extracting(value -> ((BusinessException) value).getCode()).isEqualTo(4098004);
        assertThat(current.getStatus()).isEqualTo("SCHEDULED");
    }

    @Test
    void deleteUsesLogicalDeleteMapperAndKeepsHistoryReferences() {
        InterviewEntity current = new InterviewEntity();
        current.setId(8L); current.setPublicId("interview-1");
        when(mapper.selectOne(any(Wrapper.class))).thenReturn(current);
        when(reminders.selectList(any(Wrapper.class))).thenReturn(java.util.List.of());

        service.delete(1L, "interview-1");

        verify(mapper).deleteById(current);
        verify(audit).record(1L, "INTERVIEW_DELETE", "INTERVIEW", "interview-1");
    }
}
