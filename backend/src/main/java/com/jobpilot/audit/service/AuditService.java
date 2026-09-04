package com.jobpilot.audit.service;

import com.jobpilot.audit.domain.AuditLogEntity;
import com.jobpilot.audit.mapper.AuditLogMapper;
import com.jobpilot.common.logging.TraceContext;
import org.springframework.stereotype.Service;

@Service
public class AuditService {

    private final AuditLogMapper auditLogMapper;

    public AuditService(AuditLogMapper auditLogMapper) {
        this.auditLogMapper = auditLogMapper;
    }

    public void record(Long userId, String action, String resourceType, String resourceId) {
        AuditLogEntity log = new AuditLogEntity();
        log.setUserId(userId);
        log.setAction(action);
        log.setResourceType(resourceType);
        log.setResourceId(resourceId);
        log.setTraceId(TraceContext.getTraceId());
        auditLogMapper.insert(log);
    }
}

