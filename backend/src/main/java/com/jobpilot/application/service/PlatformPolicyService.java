package com.jobpilot.application.service;

import static com.jobpilot.application.dto.ApplicationDtos.PolicyDecisionView;
import static com.jobpilot.application.dto.ApplicationDtos.PolicyUpdateRequest;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.domain.PlatformPolicyEntity;
import com.jobpilot.application.mapper.PlatformPolicyMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlatformPolicyService {
    private static final int MAX_POLICY_AGE_DAYS = 180;
    private final PlatformPolicyMapper mapper;
    private final AuditService audit;

    public PlatformPolicyService(PlatformPolicyMapper mapper, AuditService audit) {
        this.mapper = mapper;
        this.audit = audit;
    }

    public PolicyDecisionView resolve(String platform) {
        String normalized = normalize(platform);
        PlatformPolicyEntity policy = mapper.selectOne(new LambdaQueryWrapper<PlatformPolicyEntity>()
                .eq(PlatformPolicyEntity::getPlatform, normalized).last("LIMIT 1"));
        if (policy == null) {
            return manual(normalized, false, "Unknown platform defaults to MANUAL_ONLY");
        }
        boolean fresh = "ACTIVE".equals(policy.getStatus()) && policy.getReviewedAt() != null
                && !policy.getReviewedAt().isBefore(LocalDateTime.now().minusDays(MAX_POLICY_AGE_DAYS));
        if (!fresh) {
            return new PolicyDecisionView(normalized, "MANUAL_ONLY", "MANUAL_ONLY", true,
                    true, false, "Policy is stale or disabled; safely downgraded to MANUAL_ONLY",
                    policy.getReviewedAt());
        }
        return new PolicyDecisionView(normalized, policy.getCollectionMode(), policy.getApplicationMode(),
                Boolean.TRUE.equals(policy.getRequiresFinalConfirmation()), true, true,
                "Configured policy", policy.getReviewedAt());
    }

    public void requireMode(String platform, String mode) {
        if ("AUTHORIZED_AUTOMATION".equals(mode)) {
            throw new BusinessException(4035101,
                    "Authorized automation is not executable in Phase 7", HttpStatus.FORBIDDEN);
        }
        PolicyDecisionView decision = resolve(platform);
        if ("ASSIST".equals(mode) && !"ASSIST_ALLOWED".equals(decision.applicationMode())) {
            throw new BusinessException(4035102,
                    "Platform policy allows MANUAL mode only", HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public PolicyDecisionView configure(Long userId, String platform, PolicyUpdateRequest request) {
        String normalized = normalize(platform);
        if (!Boolean.TRUE.equals(request.requiresFinalConfirmation())) {
            throw new BusinessException(4035103,
                    "Final confirmation cannot be disabled in Phase 7", HttpStatus.FORBIDDEN);
        }
        PlatformPolicyEntity entity = mapper.selectOne(new LambdaQueryWrapper<PlatformPolicyEntity>()
                .eq(PlatformPolicyEntity::getPlatform, normalized).last("LIMIT 1"));
        if (entity == null) {
            entity = new PlatformPolicyEntity();
            entity.setPlatform(normalized);
            entity.setRateLimitJson("{}");
            entity.setStatus("ACTIVE");
            entity.setCollectionMode(request.collectionMode());
            entity.setApplicationMode(request.applicationMode());
            entity.setRequiresFinalConfirmation(true);
            entity.setPolicySourceUrl(request.policySourceUrl());
            entity.setReviewedAt(LocalDateTime.now());
            mapper.insert(entity);
        } else {
            entity.setCollectionMode(request.collectionMode());
            entity.setApplicationMode(request.applicationMode());
            entity.setRequiresFinalConfirmation(true);
            entity.setPolicySourceUrl(request.policySourceUrl());
            entity.setReviewedAt(LocalDateTime.now());
            entity.setStatus("ACTIVE");
            mapper.updateById(entity);
        }
        audit.record(userId, "PLATFORM_POLICY_CONFIGURE", "PLATFORM_POLICY", normalized);
        return resolve(normalized);
    }

    private PolicyDecisionView manual(String platform, boolean configured, String reason) {
        return new PolicyDecisionView(platform, "MANUAL_ONLY", "MANUAL_ONLY", true,
                configured, false, reason, null);
    }

    private String normalize(String platform) {
        return platform == null || platform.isBlank() ? "UNKNOWN" : platform.trim().toUpperCase(Locale.ROOT);
    }
}
