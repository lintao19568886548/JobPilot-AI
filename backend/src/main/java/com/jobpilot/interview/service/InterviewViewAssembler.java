package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.interview.domain.*;
import com.jobpilot.interview.mapper.*;
import com.jobpilot.job.domain.JobEntity;
import com.jobpilot.job.mapper.JobMapper;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.resume.mapper.ResumeVersionMapper;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class InterviewViewAssembler {
    private final InterviewMapper interviews;
    private final InterviewRoundMapper rounds;
    private final InterviewQuestionMapper questions;
    private final InterviewAnswerNoteMapper notes;
    private final InterviewReviewItemMapper reviewItems;
    private final InterviewReviewMapper reviews;
    private final ApplicationMapper applications;
    private final JobMapper jobs;
    private final ResumeVersionMapper versions;
    private final JsonCodec json;

    public InterviewViewAssembler(InterviewMapper interviews, InterviewRoundMapper rounds, InterviewQuestionMapper questions,
                                  InterviewAnswerNoteMapper notes, InterviewReviewItemMapper reviewItems, InterviewReviewMapper reviews,
                                  ApplicationMapper applications, JobMapper jobs,
                                  ResumeVersionMapper versions, JsonCodec json) {
        this.interviews = interviews; this.rounds = rounds; this.questions = questions; this.notes = notes;
        this.reviewItems = reviewItems; this.reviews = reviews;
        this.applications = applications; this.jobs = jobs; this.versions = versions; this.json = json;
    }

    public InterviewView interview(InterviewEntity entity, boolean details) {
        List<RoundView> roundViews = details ? rounds.selectList(new LambdaQueryWrapper<InterviewRoundEntity>()
                .eq(InterviewRoundEntity::getUserId, entity.getUserId())
                .eq(InterviewRoundEntity::getInterviewId, entity.getId())
                .orderByAsc(InterviewRoundEntity::getRoundNo)).stream().map(this::round).toList() : List.of();
        ApplicationEntity application = entity.getApplicationId() == null ? null : applications.selectById(entity.getApplicationId());
        JobEntity job = entity.getJobId() == null ? null : jobs.selectById(entity.getJobId());
        ResumeVersionEntity version = entity.getResumeVersionId() == null ? null : versions.selectById(entity.getResumeVersionId());
        return new InterviewView(entity.getPublicId(), application == null ? null : application.getPublicId(),
                job == null ? null : job.getPublicId(), version == null ? null : version.getPublicId(),
                entity.getCompanyNameSnapshot(), entity.getRoleSnapshot(), entity.getStatus(), entity.getResult(),
                entity.getTimezone(), entity.getNotes(), entity.getVersion(), InterviewTime.fromUtc(entity.getCreatedAt()),
                InterviewTime.fromUtc(entity.getUpdatedAt()), roundViews);
    }

    public RoundView round(InterviewRoundEntity entity) {
        List<QuestionView> questionViews = questions.selectList(new LambdaQueryWrapper<InterviewQuestionEntity>()
                .eq(InterviewQuestionEntity::getUserId, entity.getUserId())
                .eq(InterviewQuestionEntity::getRoundId, entity.getId())
                .orderByAsc(InterviewQuestionEntity::getDisplayOrder).orderByAsc(InterviewQuestionEntity::getId))
                .stream().map(this::question).toList();
        return new RoundView(entity.getPublicId(), entity.getRoundNo(), entity.getRoundType(), entity.getTitle(),
                InterviewTime.fromUtc(entity.getScheduledStartAt()), InterviewTime.fromUtc(entity.getScheduledEndAt()),
                entity.getTimezone(), entity.getFormat(), entity.getMeetingLink(), entity.getLocation(),
                entity.getInterviewerName(), entity.getStatus(), entity.getResult(), entity.getNotes(),
                entity.getVersion(), InterviewTime.fromUtc(entity.getCreatedAt()), InterviewTime.fromUtc(entity.getUpdatedAt()), questionViews);
    }

    public QuestionView question(InterviewQuestionEntity entity) {
        List<AnswerNoteView> answerNotes = notes.selectList(new LambdaQueryWrapper<InterviewAnswerNoteEntity>()
                .eq(InterviewAnswerNoteEntity::getUserId, entity.getUserId())
                .eq(InterviewAnswerNoteEntity::getQuestionId, entity.getId())
                .orderByDesc(InterviewAnswerNoteEntity::getNoteVersion)).stream().map(item ->
                new AnswerNoteView(item.getPublicId(), item.getNoteVersion(), item.getAnswerText(), item.getSelfRating(),
                        InterviewTime.fromUtc(item.getUserRecordedAt()), InterviewTime.fromUtc(item.getCreatedAt()))).toList();
        return new QuestionView(entity.getPublicId(), entity.getSourceType(), entity.getCategory(), entity.getDifficulty(),
                entity.getQuestionText(), entity.getPurposeText(), entity.getBasisText(), entity.getAnswerFramework(),
                json.readStringList(entity.getSuggestedFollowUpsJson()), entity.getRiskNotes(),
                json.readStringList(entity.getEvidenceRefsJson()), entity.getPromptVersion(), entity.getModelName(),
                entity.getDisplayOrder(), entity.getVersion(), InterviewTime.fromUtc(entity.getGeneratedAt()),
                InterviewTime.fromUtc(entity.getCreatedAt()), answerNotes);
    }

    public ReviewView review(InterviewReviewEntity entity) {
        List<ReviewItemView> items = reviewItems.selectList(new LambdaQueryWrapper<InterviewReviewItemEntity>()
                .eq(InterviewReviewItemEntity::getUserId, entity.getUserId())
                .eq(InterviewReviewItemEntity::getReviewId, entity.getId())
                .orderByAsc(InterviewReviewItemEntity::getDisplayOrder)).stream().map(item ->
                new ReviewItemView(item.getPublicId(), item.getItemType(), item.getTitle(), item.getDescriptionText(),
                        item.getSeverity(), item.getEvidenceText(), json.readStringList(item.getEvidenceRefsJson()),
                        json.readStringList(item.getRecommendedActionsJson()), item.getDisplayOrder())).toList();
        return new ReviewView(entity.getPublicId(), entity.getReviewVersion(), entity.getStatus(), entity.getSummaryText(),
                entity.getSourceType(), entity.getPromptVersion(), entity.getModelName(),
                json.readStringList(entity.getEvidenceRefsJson()), InterviewTime.fromUtc(entity.getGeneratedAt()),
                InterviewTime.fromUtc(entity.getConfirmedAt()), entity.getVersion(), items);
    }

    public KnowledgeGapView gap(KnowledgeGapEntity entity) {
        return new KnowledgeGapView(entity.getPublicId(), publicInterview(entity.getSourceInterviewId()),
                publicReview(entity.getSourceReviewId()), entity.getTitle(), entity.getCategory(),
                entity.getDescriptionText(), entity.getSeverity(), json.readNode(entity.getEvidenceJson()),
                json.readStringList(entity.getRecommendedActionsJson()), entity.getStatus(),
                InterviewTime.fromUtc(entity.getConfirmedAt()), InterviewTime.fromUtc(entity.getResolvedAt()),
                InterviewTime.fromUtc(entity.getDismissedAt()), entity.getVersion(), InterviewTime.fromUtc(entity.getCreatedAt()));
    }

    public ReminderView reminder(InterviewReminderEntity entity) {
        return new ReminderView(entity.getPublicId(), publicInterview(entity.getInterviewId()),
                publicRound(entity.getRoundId()), entity.getReminderType(), entity.getTitle(),
                InterviewTime.fromUtc(entity.getRemindAt()), entity.getTimezone(), entity.getStatus(),
                InterviewTime.fromUtc(entity.getCompletedAt()), InterviewTime.fromUtc(entity.getCancelledAt()),
                entity.getVersion(), InterviewTime.fromUtc(entity.getCreatedAt()), InterviewTime.fromUtc(entity.getUpdatedAt()));
    }

    private String publicInterview(Long id) {
        if (id == null) return null;
        InterviewEntity value = interviews.selectById(id);
        return value == null ? null : value.getPublicId();
    }

    private String publicRound(Long id) {
        if (id == null) return null;
        InterviewRoundEntity value = rounds.selectById(id);
        return value == null ? null : value.getPublicId();
    }

    private String publicReview(Long id) {
        if (id == null) return null;
        InterviewReviewEntity value = reviews.selectById(id);
        return value == null ? null : value.getPublicId();
    }
}
