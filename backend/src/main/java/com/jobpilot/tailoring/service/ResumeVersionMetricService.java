package com.jobpilot.tailoring.service;

import static com.jobpilot.tailoring.dto.TailoringDtos.ResumeVersionMetricView;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.ApplicationEntity;
import com.jobpilot.application.domain.ApplicationLogEntity;
import com.jobpilot.application.domain.ApplicationQueueItemEntity;
import com.jobpilot.application.mapper.ApplicationLogMapper;
import com.jobpilot.application.mapper.ApplicationMapper;
import com.jobpilot.application.mapper.ApplicationQueueItemMapper;
import com.jobpilot.resume.domain.ResumeVersionEntity;
import com.jobpilot.tailoring.domain.ResumeVersionMetricEntity;
import com.jobpilot.tailoring.mapper.ResumeVersionMetricMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResumeVersionMetricService {
    private final ResumeVersionMetricMapper metrics;
    private final ApplicationQueueItemMapper queues;
    private final ApplicationMapper applications;
    private final ApplicationLogMapper logs;
    private final TailoringReferenceService references;

    public ResumeVersionMetricService(ResumeVersionMetricMapper metrics, ApplicationQueueItemMapper queues,
                                      ApplicationMapper applications, ApplicationLogMapper logs,
                                      TailoringReferenceService references) {
        this.metrics = metrics; this.queues = queues; this.applications = applications; this.logs = logs; this.references = references;
    }

    @Transactional
    public ResumeVersionMetricView calculate(Long userId, String versionPublicId) {
        ResumeVersionEntity version = references.version(userId, versionPublicId);
        int queueCount = Math.toIntExact(queues.selectCount(new LambdaQueryWrapper<ApplicationQueueItemEntity>()
                .eq(ApplicationQueueItemEntity::getUserId, userId).eq(ApplicationQueueItemEntity::getResumeVersionId, version.getId())));
        List<ApplicationEntity> appList = applications.selectList(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getUserId, userId).eq(ApplicationEntity::getResumeVersionId, version.getId()));
        Set<Long> appIds = appList.stream().map(ApplicationEntity::getId).collect(Collectors.toSet());
        List<ApplicationLogEntity> events = appIds.isEmpty() ? List.of() : logs.selectList(new LambdaQueryWrapper<ApplicationLogEntity>()
                .eq(ApplicationLogEntity::getUserId, userId).in(ApplicationLogEntity::getApplicationId, appIds));
        int replies = reached(events, Set.of("REPLIED"));
        int interviews = reached(events, Set.of("INTERVIEW_1", "INTERVIEW_2", "INTERVIEW_3", "HR_INTERVIEW"));
        int offers = reached(events, Set.of("OFFER"));
        ResumeVersionMetricEntity metric = metrics.selectOne(new LambdaQueryWrapper<ResumeVersionMetricEntity>()
                .eq(ResumeVersionMetricEntity::getUserId, userId).eq(ResumeVersionMetricEntity::getResumeVersionId, version.getId()).last("LIMIT 1"));
        if (metric == null) { metric = new ResumeVersionMetricEntity(); metric.setUserId(userId); metric.setResumeVersionId(version.getId()); }
        metric.setQueueUseCount(queueCount); metric.setApplicationCount(appList.size()); metric.setReplyCount(replies);
        metric.setInterviewCount(interviews); metric.setOfferCount(offers); metric.setCalculatedAt(LocalDateTime.now());
        if (metric.getId() == null) metrics.insert(metric); else metrics.updateById(metric);
        return new ResumeVersionMetricView(versionPublicId, queueCount, appList.size(), replies, interviews, offers, metric.getCalculatedAt());
    }

    private static int reached(List<ApplicationLogEntity> logs, Set<String> statuses) {
        return Math.toIntExact(logs.stream().filter(log -> statuses.contains(log.getToStatus()))
                .map(ApplicationLogEntity::getApplicationId).distinct().count());
    }
}
