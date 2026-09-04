package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.domain.InterviewAnswerNoteEntity;
import com.jobpilot.interview.domain.InterviewQuestionEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.mapper.InterviewAnswerNoteMapper;
import com.jobpilot.interview.mapper.InterviewQuestionMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InterviewQuestionService {
    private final InterviewQuestionMapper questions;
    private final InterviewAnswerNoteMapper notes;
    private final InterviewService interviews;
    private final InterviewViewAssembler views;
    private final JsonCodec json;
    private final AuditService audit;

    public InterviewQuestionService(InterviewQuestionMapper questions, InterviewAnswerNoteMapper notes,
                                    InterviewService interviews, InterviewViewAssembler views,
                                    JsonCodec json, AuditService audit) {
        this.questions = questions; this.notes = notes; this.interviews = interviews;
        this.views = views; this.json = json; this.audit = audit;
    }

    @Transactional
    public QuestionView create(Long userId, String roundPublicId, QuestionCreateRequest request) {
        InterviewRoundEntity round = interviews.ownedRound(userId, roundPublicId);
        InterviewQuestionEntity entity = new InterviewQuestionEntity();
        entity.setUserId(userId); entity.setInterviewId(round.getInterviewId()); entity.setRoundId(round.getId());
        entity.setSourceType(request.sourceType()); entity.setCategory(request.category()); entity.setDifficulty(request.difficulty());
        entity.setQuestionText(request.question().trim()); entity.setPurposeText(request.purpose()); entity.setBasisText(request.basis());
        entity.setAnswerFramework(request.answerFramework()); entity.setSuggestedFollowUpsJson(json.write(request.suggestedFollowUps()));
        entity.setRiskNotes(request.riskNotes()); entity.setEvidenceRefsJson("[]");
        entity.setDisplayOrder(request.displayOrder() == null ? nextOrder(round.getId()) : request.displayOrder());
        questions.insert(entity);
        audit.record(userId, "ACTUAL".equals(request.sourceType()) ? "ACTUAL_QUESTION_RECORD" : "MANUAL_QUESTION_RECORD",
                "INTERVIEW_QUESTION", entity.getPublicId());
        return views.question(entity);
    }

    @Transactional
    public QuestionView update(Long userId, String publicId, QuestionUpdateRequest request) {
        InterviewQuestionEntity entity = owned(userId, publicId);
        InterviewService.version(entity.getVersion(), request.version());
        entity.setCategory(request.category()); entity.setDifficulty(request.difficulty()); entity.setQuestionText(request.question().trim());
        entity.setPurposeText(request.purpose()); entity.setBasisText(request.basis()); entity.setAnswerFramework(request.answerFramework());
        entity.setSuggestedFollowUpsJson(json.write(request.suggestedFollowUps())); entity.setRiskNotes(request.riskNotes());
        if (request.displayOrder() != null) entity.setDisplayOrder(request.displayOrder());
        questions.updateById(entity); audit.record(userId, "INTERVIEW_QUESTION_UPDATE", "INTERVIEW_QUESTION", publicId);
        return views.question(owned(userId, publicId));
    }

    @Transactional
    public AnswerNoteView addAnswerNote(Long userId, String questionPublicId, AnswerNoteRequest request) {
        InterviewQuestionEntity question = owned(userId, questionPublicId);
        InterviewAnswerNoteEntity latest = notes.selectOne(new LambdaQueryWrapper<InterviewAnswerNoteEntity>()
                .eq(InterviewAnswerNoteEntity::getUserId, userId).eq(InterviewAnswerNoteEntity::getQuestionId, question.getId())
                .orderByDesc(InterviewAnswerNoteEntity::getNoteVersion).last("LIMIT 1"));
        InterviewAnswerNoteEntity entity = new InterviewAnswerNoteEntity();
        entity.setUserId(userId); entity.setInterviewId(question.getInterviewId()); entity.setQuestionId(question.getId());
        entity.setNoteVersion(latest == null ? 1 : latest.getNoteVersion() + 1); entity.setAnswerText(request.answer().trim());
        entity.setSelfRating(request.selfRating());
        entity.setUserRecordedAt(request.recordedAt() == null ? LocalDateTime.now(ZoneOffset.UTC)
                : LocalDateTime.ofInstant(request.recordedAt().toInstant(), ZoneOffset.UTC));
        notes.insert(entity); audit.record(userId, "ANSWER_NOTE_UPDATE", "INTERVIEW_ANSWER_NOTE", entity.getPublicId());
        return new AnswerNoteView(entity.getPublicId(), entity.getNoteVersion(), entity.getAnswerText(), entity.getSelfRating(),
                InterviewTime.fromUtc(entity.getUserRecordedAt()), InterviewTime.fromUtc(entity.getCreatedAt()));
    }

    public InterviewQuestionEntity owned(Long userId, String publicId) {
        InterviewQuestionEntity value = questions.selectOne(new LambdaQueryWrapper<InterviewQuestionEntity>()
                .eq(InterviewQuestionEntity::getUserId, userId).eq(InterviewQuestionEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Interview Question");
        interviews.ownedInterviewById(userId, value.getInterviewId());
        return value;
    }

    private int nextOrder(Long roundId) {
        InterviewQuestionEntity latest = questions.selectOne(new LambdaQueryWrapper<InterviewQuestionEntity>()
                .eq(InterviewQuestionEntity::getRoundId, roundId).orderByDesc(InterviewQuestionEntity::getDisplayOrder).last("LIMIT 1"));
        return latest == null ? 0 : latest.getDisplayOrder() + 1;
    }
}
