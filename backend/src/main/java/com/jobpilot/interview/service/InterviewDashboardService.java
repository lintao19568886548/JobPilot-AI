package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.interview.domain.InterviewEntity;
import com.jobpilot.interview.domain.InterviewReminderEntity;
import com.jobpilot.interview.domain.InterviewReviewEntity;
import com.jobpilot.interview.domain.InterviewRoundEntity;
import com.jobpilot.interview.domain.KnowledgeGapEntity;
import com.jobpilot.interview.mapper.InterviewMapper;
import com.jobpilot.interview.mapper.InterviewReminderMapper;
import com.jobpilot.interview.mapper.InterviewReviewMapper;
import com.jobpilot.interview.mapper.InterviewRoundMapper;
import com.jobpilot.interview.mapper.KnowledgeGapMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InterviewDashboardService {
    private final InterviewMapper interviews;
    private final InterviewRoundMapper rounds;
    private final InterviewReviewMapper reviews;
    private final InterviewReminderMapper reminders;
    private final KnowledgeGapMapper gaps;
    private final InterviewViewAssembler views;

    public InterviewDashboardService(InterviewMapper interviews, InterviewRoundMapper rounds,
                                     InterviewReviewMapper reviews, InterviewReminderMapper reminders,
                                     KnowledgeGapMapper gaps, InterviewViewAssembler views) {
        this.interviews = interviews; this.rounds = rounds; this.reviews = reviews;
        this.reminders = reminders; this.gaps = gaps; this.views = views;
    }

    public InterviewDashboardView get(Long userId) {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        List<InterviewRoundEntity> upcomingRounds = rounds.selectList(new LambdaQueryWrapper<InterviewRoundEntity>()
                .eq(InterviewRoundEntity::getUserId, userId).eq(InterviewRoundEntity::getStatus, "PLANNED")
                .ge(InterviewRoundEntity::getScheduledStartAt, now).orderByAsc(InterviewRoundEntity::getScheduledStartAt).last("LIMIT 20"));
        Map<Long, InterviewView> upcoming = new LinkedHashMap<>();
        for (InterviewRoundEntity round : upcomingRounds) {
            InterviewEntity interview = interviews.selectOne(new LambdaQueryWrapper<InterviewEntity>()
                    .eq(InterviewEntity::getUserId, userId).eq(InterviewEntity::getId, round.getInterviewId()).last("LIMIT 1"));
            if (interview != null && upcoming.size() < 5) upcoming.putIfAbsent(interview.getId(), views.interview(interview, true));
        }
        List<ReminderView> pending = reminders.selectList(new LambdaQueryWrapper<InterviewReminderEntity>()
                .eq(InterviewReminderEntity::getUserId, userId).eq(InterviewReminderEntity::getStatus, "PENDING")
                .orderByAsc(InterviewReminderEntity::getRemindAt).last("LIMIT 10")).stream().map(views::reminder).toList();
        long reviewPending = interviews.selectCount(new LambdaQueryWrapper<InterviewEntity>()
                .eq(InterviewEntity::getUserId, userId).eq(InterviewEntity::getStatus, "COMPLETED")
                .notExists("SELECT 1 FROM interview_reviews rv WHERE rv.interview_id = interviews.id AND rv.deleted_at IS NULL"));
        long activeGaps = gaps.selectCount(new LambdaQueryWrapper<KnowledgeGapEntity>()
                .eq(KnowledgeGapEntity::getUserId, userId).eq(KnowledgeGapEntity::getStatus, "ACTIVE"));
        return new InterviewDashboardView(List.copyOf(upcoming.values()), pending, reviewPending, activeGaps);
    }
}
