package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.domain.InterviewAnswerNoteEntity;
import com.jobpilot.interview.domain.InterviewQuestionEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.mapper.InterviewAnswerNoteMapper;
import com.jobpilot.interview.mapper.InterviewQuestionMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class InterviewQuestionServiceTest {
    private final InterviewQuestionMapper questions = mock(InterviewQuestionMapper.class);
    private final InterviewAnswerNoteMapper notes = mock(InterviewAnswerNoteMapper.class);
    private final InterviewService interviews = mock(InterviewService.class);
    private final InterviewViewAssembler views = mock(InterviewViewAssembler.class);
    private final JsonCodec json = mock(JsonCodec.class);
    private final AuditService audit = mock(AuditService.class);
    private final InterviewQuestionService service = new InterviewQuestionService(questions, notes, interviews, views, json, audit);

    @Test
    void recordsActualQuestionWithoutChangingItsSourceLabel() {
        InterviewRoundEntity round = new InterviewRoundEntity();
        round.setId(10L); round.setInterviewId(20L);
        when(interviews.ownedRound(1L, "round-1")).thenReturn(round);
        when(json.write(any())).thenReturn("[]");

        service.create(1L, "round-1", new QuestionCreateRequest("ACTUAL", "SYSTEM_DESIGN", "HARD",
                "How did you design it?", "actual", "user memory", null, List.of(), null, 3));

        ArgumentCaptor<InterviewQuestionEntity> captor = ArgumentCaptor.forClass(InterviewQuestionEntity.class);
        verify(questions).insert(captor.capture());
        assertThat(captor.getValue().getSourceType()).isEqualTo("ACTUAL");
        assertThat(captor.getValue().getRoundId()).isEqualTo(10L);
        assertThat(captor.getValue().getInterviewId()).isEqualTo(20L);
    }

    @Test
    void updatePreservesPredictedSourceType() {
        InterviewQuestionEntity predicted = new InterviewQuestionEntity();
        predicted.setId(11L); predicted.setInterviewId(20L); predicted.setPublicId("question-1");
        predicted.setSourceType("PREDICTED"); predicted.setVersion(2);
        when(questions.selectOne(any(Wrapper.class))).thenReturn(predicted);

        service.update(1L, "question-1", new QuestionUpdateRequest("FOLLOW_UP", "MEDIUM",
                "Updated wording", null, null, null, List.of(), null, 4, 2));

        assertThat(predicted.getSourceType()).isEqualTo("PREDICTED");
        assertThat(predicted.getQuestionText()).isEqualTo("Updated wording");
        verify(interviews, org.mockito.Mockito.times(2)).ownedInterviewById(1L, 20L);
    }

    @Test
    void answerNotesAppendVersionsInsteadOfOverwriting() {
        InterviewQuestionEntity actual = new InterviewQuestionEntity();
        actual.setId(11L); actual.setInterviewId(20L); actual.setPublicId("question-1");
        actual.setSourceType("ACTUAL");
        InterviewAnswerNoteEntity versionOne = new InterviewAnswerNoteEntity();
        versionOne.setNoteVersion(1);
        when(questions.selectOne(any(Wrapper.class))).thenReturn(actual);
        when(notes.selectOne(any(Wrapper.class))).thenReturn(null, versionOne);

        AnswerNoteView first = service.addAnswerNote(1L, "question-1", new AnswerNoteRequest("first", 3, null));
        AnswerNoteView second = service.addAnswerNote(1L, "question-1", new AnswerNoteRequest("second", 4, null));

        assertThat(first.noteVersion()).isEqualTo(1);
        assertThat(second.noteVersion()).isEqualTo(2);
        ArgumentCaptor<InterviewAnswerNoteEntity> captor = ArgumentCaptor.forClass(InterviewAnswerNoteEntity.class);
        verify(notes, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues()).extracting(InterviewAnswerNoteEntity::getAnswerText).containsExactly("first", "second");
    }
}
