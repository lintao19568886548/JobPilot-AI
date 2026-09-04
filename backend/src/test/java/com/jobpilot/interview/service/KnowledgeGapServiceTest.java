package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.VersionRequest;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.interview.domain.InterviewReviewEntity;
import com.jobpilot.interview.domain.KnowledgeGapEntity;
import com.jobpilot.interview.domain.KnowledgeGapEvidenceEntity;
import com.jobpilot.interview.mapper.InterviewReviewMapper;
import com.jobpilot.interview.mapper.KnowledgeGapEvidenceMapper;
import com.jobpilot.interview.mapper.KnowledgeGapMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class KnowledgeGapServiceTest {
    @Test
    void requiresConfirmedReviewAndExplicitActivation() {
        KnowledgeGapMapper gaps = mock(KnowledgeGapMapper.class);
        KnowledgeGapEvidenceMapper evidence = mock(KnowledgeGapEvidenceMapper.class);
        InterviewReviewMapper reviews = mock(InterviewReviewMapper.class);
        InterviewService interviews = mock(InterviewService.class);
        KnowledgeGapService service = new KnowledgeGapService(gaps, evidence, reviews, interviews,
                mock(InterviewViewAssembler.class), mock(AuditService.class));
        KnowledgeGapEntity gap = new KnowledgeGapEntity();
        gap.setId(1L); gap.setPublicId("gap-1"); gap.setSourceInterviewId(2L); gap.setSourceReviewId(3L);
        gap.setStatus("PROPOSED"); gap.setVersion(0);
        InterviewReviewEntity review = new InterviewReviewEntity();
        review.setStatus("DRAFT");
        when(gaps.selectOne(any(Wrapper.class))).thenReturn(gap);
        when(reviews.selectById(3L)).thenReturn(review);

        assertThatThrownBy(() -> service.activate(9L, "gap-1", new VersionRequest(0)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Confirm the source Review");
        assertThat(gap.getStatus()).isEqualTo("PROPOSED");

        review.setStatus("CONFIRMED");
        service.activate(9L, "gap-1", new VersionRequest(0));
        assertThat(gap.getStatus()).isEqualTo("ACTIVE");
        ArgumentCaptor<KnowledgeGapEvidenceEntity> captor = ArgumentCaptor.forClass(KnowledgeGapEvidenceEntity.class);
        verify(evidence).insert(captor.capture());
        assertThat(captor.getValue().getEvidenceType()).isEqualTo("USER_CONFIRMATION");
        verify(interviews, org.mockito.Mockito.atLeastOnce()).ownedInterviewById(9L, 2L);
    }
}
