package com.jobpilot.extension.service;

import static com.jobpilot.application.dto.ApplicationDtos.QueueEnqueueRequest;
import static com.jobpilot.application.dto.ApplicationDtos.QueueItemView;
import static com.jobpilot.extension.dto.ExtensionDtos.*;
import static com.jobpilot.matching.dto.MatchingDtos.MatchRunRequest;
import static com.jobpilot.matching.dto.MatchingDtos.MatchRunView;
import static com.jobpilot.tailoring.dto.TailoringDtos.DraftCreateRequest;

import com.jobpilot.application.service.ApplicationQueueService;
import com.jobpilot.job.dto.JobDtos.JobDetailView;
import com.jobpilot.job.service.JobService;
import com.jobpilot.matching.dto.MatchingDtos.JobMatchView;
import com.jobpilot.matching.service.MatchService;
import com.jobpilot.resume.dto.ResumeDtos.ResumeSummaryView;
import com.jobpilot.resume.service.ResumeService;
import com.jobpilot.tailoring.dto.TailoringDtos.CommunicationDraftView;
import com.jobpilot.tailoring.service.CommunicationDraftService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class ExtensionWorkspaceService {
    private final JobService jobs;
    private final MatchService matches;
    private final ResumeService resumes;
    private final CommunicationDraftService drafts;
    private final ApplicationQueueService queue;

    public ExtensionWorkspaceService(JobService jobs, MatchService matches, ResumeService resumes,
                                     CommunicationDraftService drafts, ApplicationQueueService queue) {
        this.jobs = jobs; this.matches = matches; this.resumes = resumes; this.drafts = drafts; this.queue = queue;
    }

    public ExtensionWorkspaceView workspace(Long userId, String jobId) {
        JobDetailView job = jobs.get(userId, jobId);
        List<JobMatchView> history = matches.listMatches(userId, jobId);
        JobMatchView latest = history.isEmpty() ? null : history.getFirst();
        List<ResumeSummaryView> resumeList = resumes.list(userId);
        String recommendedVersion = latest == null ? null : latest.recommendedResumeVersionId();
        ResumeSummaryView recommended = resumeList.stream()
                .filter(item -> recommendedVersion != null && recommendedVersion.equals(item.currentVersionId()))
                .findFirst().orElseGet(() -> resumeList.stream().filter(item -> Boolean.TRUE.equals(item.defaultResume()))
                        .findFirst().orElse(null));
        CommunicationDraftView draft = drafts.list(userId).stream()
                .filter(item -> jobId.equals(item.jobId())).findFirst().orElse(null);
        return new ExtensionWorkspaceView(job, latest, recommended, draft, false, false);
    }

    public MatchRunView analyze(Long userId, String jobId, String idempotencyKey, ExtensionAnalyzeRequest request) {
        MatchRunRequest command = new MatchRunRequest(request.resumeVersionId(), idempotencyKey, request.force());
        return matches.start(userId, jobId, command, idempotencyKey);
    }

    public QueueItemView enqueue(Long userId, String jobId, String idempotencyKey, ExtensionQueueRequest request) {
        QueueEnqueueRequest command = new QueueEnqueueRequest(jobId, request.jobMatchId(), request.resumeVersionId(),
                request.draftId(), "ASSIST", request.priority() == null ? 70 : request.priority(), null,
                request.jobMatchId() == null || request.jobMatchId().isBlank());
        return queue.enqueue(userId, idempotencyKey, command);
    }

    public CommunicationDraftView draft(Long userId, String jobId, String idempotencyKey,
                                        ExtensionDraftRequest request) {
        return drafts.create(userId, jobId, idempotencyKey,
                new DraftCreateRequest(request.channel(), "APPLICATION", null, request.resumeVersionId(), null));
    }
}
