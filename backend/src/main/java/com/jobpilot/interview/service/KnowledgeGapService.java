package com.jobpilot.interview.service;

import static com.jobpilot.interview.dto.InterviewDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.interview.domain.InterviewReviewEntity;
import com.jobpilot.interview.domain.KnowledgeGapEntity;
import com.jobpilot.interview.domain.KnowledgeGapEvidenceEntity;
import com.jobpilot.interview.mapper.InterviewReviewMapper;
import com.jobpilot.interview.mapper.KnowledgeGapEvidenceMapper;
import com.jobpilot.interview.mapper.KnowledgeGapMapper;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeGapService {
    private final KnowledgeGapMapper gaps;
    private final KnowledgeGapEvidenceMapper evidence;
    private final InterviewReviewMapper reviews;
    private final InterviewService interviews;
    private final InterviewViewAssembler views;
    private final AuditService audit;

    public KnowledgeGapService(KnowledgeGapMapper gaps, KnowledgeGapEvidenceMapper evidence,
                               InterviewReviewMapper reviews, InterviewService interviews,
                               InterviewViewAssembler views, AuditService audit) {
        this.gaps = gaps; this.evidence = evidence; this.reviews = reviews;
        this.interviews = interviews; this.views = views; this.audit = audit;
    }

    public List<KnowledgeGapView> list(Long userId, String status) {
        LambdaQueryWrapper<KnowledgeGapEntity> query = new LambdaQueryWrapper<KnowledgeGapEntity>()
                .eq(KnowledgeGapEntity::getUserId, userId).orderByDesc(KnowledgeGapEntity::getUpdatedAt);
        if (status != null && !status.isBlank()) query.eq(KnowledgeGapEntity::getStatus, status);
        return gaps.selectList(query).stream().map(views::gap).toList();
    }

    @Transactional
    public KnowledgeGapView activate(Long userId, String publicId, VersionRequest request) {
        KnowledgeGapEntity gap = owned(userId, publicId); InterviewService.version(gap.getVersion(), request.version());
        if ("ACTIVE".equals(gap.getStatus())) return views.gap(gap);
        if (!"PROPOSED".equals(gap.getStatus())) invalid("Only a PROPOSED Knowledge Gap can be activated");
        InterviewReviewEntity review = reviews.selectById(gap.getSourceReviewId());
        if (review == null || !"CONFIRMED".equals(review.getStatus())) invalid("Confirm the source Review before activating this Knowledge Gap");
        gap.setStatus("ACTIVE"); gap.setConfirmedBy(userId); gap.setConfirmedAt(LocalDateTime.now(ZoneOffset.UTC)); gaps.updateById(gap);
        KnowledgeGapEvidenceEntity item = new KnowledgeGapEvidenceEntity();
        item.setUserId(userId); item.setKnowledgeGapId(gap.getId()); item.setInterviewId(gap.getSourceInterviewId());
        item.setReviewId(gap.getSourceReviewId()); item.setEvidenceType("USER_CONFIRMATION");
        item.setEvidenceText("User explicitly activated the proposed Knowledge Gap"); evidence.insert(item);
        audit.record(userId, "KNOWLEDGE_GAP_ACTIVATE", "KNOWLEDGE_GAP", publicId);
        return views.gap(owned(userId, publicId));
    }

    @Transactional
    public KnowledgeGapView resolve(Long userId, String publicId, VersionRequest request) {
        KnowledgeGapEntity gap = owned(userId, publicId); InterviewService.version(gap.getVersion(), request.version());
        if (!"ACTIVE".equals(gap.getStatus())) invalid("Only an ACTIVE Knowledge Gap can be resolved");
        gap.setStatus("RESOLVED"); gap.setResolvedAt(LocalDateTime.now(ZoneOffset.UTC)); gaps.updateById(gap);
        audit.record(userId, "KNOWLEDGE_GAP_RESOLVE", "KNOWLEDGE_GAP", publicId);
        return views.gap(owned(userId, publicId));
    }

    @Transactional
    public KnowledgeGapView dismiss(Long userId, String publicId, VersionRequest request) {
        KnowledgeGapEntity gap = owned(userId, publicId); InterviewService.version(gap.getVersion(), request.version());
        if (!("PROPOSED".equals(gap.getStatus()) || "ACTIVE".equals(gap.getStatus()))) invalid("Knowledge Gap cannot be dismissed from its current status");
        gap.setStatus("DISMISSED"); gap.setDismissedAt(LocalDateTime.now(ZoneOffset.UTC)); gaps.updateById(gap);
        audit.record(userId, "KNOWLEDGE_GAP_DISMISS", "KNOWLEDGE_GAP", publicId);
        return views.gap(owned(userId, publicId));
    }

    public KnowledgeGapEntity owned(Long userId, String publicId) {
        KnowledgeGapEntity value = gaps.selectOne(new LambdaQueryWrapper<KnowledgeGapEntity>()
                .eq(KnowledgeGapEntity::getUserId, userId).eq(KnowledgeGapEntity::getPublicId, publicId).last("LIMIT 1"));
        if (value == null) throw new ResourceNotFoundException("Knowledge Gap");
        interviews.ownedInterviewById(userId, value.getSourceInterviewId()); return value;
    }

    private static void invalid(String message) { throw new BusinessException(4098007, message, HttpStatus.CONFLICT); }
}
