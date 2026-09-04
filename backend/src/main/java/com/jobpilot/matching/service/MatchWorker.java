package com.jobpilot.matching.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.common.config.MatchingProperties;
import com.jobpilot.matching.domain.MatchRunEntity;
import com.jobpilot.matching.domain.OutboxEventEntity;
import com.jobpilot.matching.mapper.MatchRunMapper;
import com.jobpilot.matching.mapper.OutboxEventMapper;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class MatchWorker {
    private static final Logger log = LoggerFactory.getLogger(MatchWorker.class);
    private final OutboxEventMapper outboxMapper;
    private final MatchRunMapper runMapper;
    private final MatchService matchService;
    private final StringRedisTemplate redis;
    private final MatchingProperties properties;

    public MatchWorker(OutboxEventMapper outboxMapper, MatchRunMapper runMapper, MatchService matchService,
                       StringRedisTemplate redis, MatchingProperties properties) {
        this.outboxMapper=outboxMapper;this.runMapper=runMapper;this.matchService=matchService;this.redis=redis;this.properties=properties;
    }

    @Scheduled(fixedDelayString = "${jobpilot.matching.poll-interval-ms:1000}")
    public void poll() {
        recoverStaleProcessing();
        OutboxEventEntity event=outboxMapper.selectOne(new LambdaQueryWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getEventType,"MATCH_REQUESTED")
                .in(OutboxEventEntity::getStatus,List.of("PENDING","FAILED"))
                .le(OutboxEventEntity::getAvailableAt,LocalDateTime.now()).orderByAsc(OutboxEventEntity::getId).last("LIMIT 1"));
        if(event==null)return;
        MatchRunEntity run=runMapper.selectById(event.getAggregateId());
        if(run==null)return;
        if("SUCCEEDED".equals(run.getStatus())){event.setStatus("SUCCEEDED");event.setPublishedAt(LocalDateTime.now());outboxMapper.updateById(event);return;}
        if("DEAD".equals(run.getStatus())){event.setStatus("DEAD");outboxMapper.updateById(event);return;}
        if("RUNNING".equals(run.getStatus())&&run.getStartedAt()!=null&&run.getStartedAt().isBefore(LocalDateTime.now().minusMinutes(15))){run.setStatus("FAILED");run.setAvailableAt(LocalDateTime.now());runMapper.updateById(run);event.setStatus("FAILED");event.setAvailableAt(LocalDateTime.now());outboxMapper.updateById(event);}
        if(!List.of("PENDING","FAILED").contains(run.getStatus()))return;
        String lockKey="jobpilot:match:run:"+run.getPublicId();
        Boolean locked=redis.opsForValue().setIfAbsent(lockKey,properties.getWorkerId(),Duration.ofMinutes(15));
        if(!Boolean.TRUE.equals(locked))return;
        try {
            MDC.put("traceId",run.getTraceId());
            int attempt=run.getAttemptCount()+1;run.setAttemptCount(attempt);run.setStatus("RUNNING");run.setStartedAt(LocalDateTime.now());runMapper.updateById(run);
            event.setAttemptCount(event.getAttemptCount()+1);event.setStatus("PROCESSING");event.setLockedBy(properties.getWorkerId());event.setLockedAt(LocalDateTime.now());outboxMapper.updateById(event);
            matchService.executeRun(run.getId());
            event=outboxMapper.selectById(event.getId());event.setStatus("SUCCEEDED");event.setPublishedAt(LocalDateTime.now());event.setLockedBy(null);event.setLockedAt(null);event.setLastErrorSafe(null);outboxMapper.updateById(event);
        } catch (Exception exception) {
            run=runMapper.selectById(run.getId());boolean dead=run.getAttemptCount()>=run.getMaxAttempts();run.setStatus(dead?"DEAD":"FAILED");run.setErrorCode("MATCH_EXECUTION_FAILED");run.setErrorMessageSafe("Matching provider or evaluation failed safely");run.setAvailableAt(LocalDateTime.now().plusSeconds(backoff(run.getAttemptCount())));run.setFinishedAt(dead?LocalDateTime.now():null);runMapper.updateById(run);
            event=outboxMapper.selectById(event.getId());event.setStatus(dead?"DEAD":"FAILED");event.setAvailableAt(run.getAvailableAt());event.setLastErrorSafe("Matching provider or evaluation failed safely");event.setLockedBy(null);event.setLockedAt(null);outboxMapper.updateById(event);
            log.warn("Match run {} attempt {} failed with safe error code {}",run.getPublicId(),run.getAttemptCount(),run.getErrorCode());
        } finally {
            MDC.remove("traceId");redis.delete(lockKey);
        }
    }

    private void recoverStaleProcessing(){
        OutboxEventEntity stale=outboxMapper.selectOne(new LambdaQueryWrapper<OutboxEventEntity>()
                .eq(OutboxEventEntity::getEventType,"MATCH_REQUESTED").eq(OutboxEventEntity::getStatus,"PROCESSING")
                .le(OutboxEventEntity::getLockedAt,LocalDateTime.now().minusMinutes(15)).orderByAsc(OutboxEventEntity::getId).last("LIMIT 1"));
        if(stale==null)return;
        MatchRunEntity run=runMapper.selectById(stale.getAggregateId());
        boolean dead=stale.getAttemptCount()>=stale.getMaxAttempts();
        stale.setStatus(dead?"DEAD":"FAILED");stale.setAvailableAt(LocalDateTime.now());stale.setLockedBy(null);stale.setLockedAt(null);
        stale.setLastErrorSafe("Recovered a stale matching worker lease");outboxMapper.updateById(stale);
        if(run!=null&&"RUNNING".equals(run.getStatus())){run.setStatus(dead?"DEAD":"FAILED");run.setAvailableAt(LocalDateTime.now());run.setErrorCode("STALE_WORKER_RECOVERED");run.setErrorMessageSafe("Matching task was recovered after an interrupted worker");run.setFinishedAt(dead?LocalDateTime.now():null);runMapper.updateById(run);}
    }

    private long backoff(int attempt){return Math.min(300L,(long)properties.getInitialBackoffSeconds()*(1L<<Math.min(attempt-1,8)));}
}
