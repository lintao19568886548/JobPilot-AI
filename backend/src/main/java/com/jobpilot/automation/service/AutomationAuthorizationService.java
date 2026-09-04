package com.jobpilot.automation.service;

import static com.jobpilot.automation.dto.AutomationCenterDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.jobpilot.application.service.PlatformPolicyService;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.automation.domain.AutomationAuthorizationEntity;
import com.jobpilot.automation.mapper.AutomationAuthorizationMapper;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.learning.service.LearningHash;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AutomationAuthorizationService {
    private static final Set<String> SCOPES=Set.of("JOB_READ","MATCH_REFRESH","SUGGESTION_WRITE","NOTIFICATION_WRITE","ASSIST_PREPARE","EXTERNAL_APPLICATION","EXTERNAL_MESSAGE");
    private final AutomationAuthorizationMapper mapper;private final PlatformPolicyService policies;private final JsonCodec json;private final AuditService audit;
    public AutomationAuthorizationService(AutomationAuthorizationMapper mapper,PlatformPolicyService policies,JsonCodec json,AuditService audit){this.mapper=mapper;this.policies=policies;this.json=json;this.audit=audit;}

    @Transactional
    public AuthorizationView grant(Long userId,AuthorizationRequest request){String platform=normalize(request.platform());List<String> scopes=request.scopes().stream().map(v->v.trim().toUpperCase(Locale.ROOT)).distinct().sorted().toList();if(scopes.isEmpty()||!SCOPES.containsAll(scopes))throw new ValidationException("Authorization contains an unsupported scope");LocalDateTime now=LocalDateTime.now();if(!request.expiresAt().isAfter(now)||ChronoUnit.DAYS.between(now,request.expiresAt())>365)throw new ValidationException("Authorization expiry must be in the future and within 365 days");String scopeJson=json.write(scopes),hash=LearningHash.sha256(scopeJson);AutomationAuthorizationEntity entity=new AutomationAuthorizationEntity();entity.setUserId(userId);entity.setPlatform(platform);entity.setScopesJson(scopeJson);entity.setScopeHash(hash);entity.setEvidenceType(request.evidenceType().trim().toUpperCase(Locale.ROOT));entity.setEvidenceRef(request.evidenceRef().trim());entity.setGrantedAt(now);entity.setExpiresAt(request.expiresAt());entity.setStatus("ACTIVE");try{mapper.insert(entity);}catch(DataIntegrityViolationException e){throw new BusinessException(4091020,"An active authorization already exists for these scopes",HttpStatus.CONFLICT);}audit.record(userId,"AUTOMATION_AUTHORIZATION_GRANT","AUTOMATION_AUTHORIZATION",entity.getPublicId());return view(entity);}
    public List<AuthorizationView> list(Long userId){return mapper.selectList(new LambdaQueryWrapper<AutomationAuthorizationEntity>().eq(AutomationAuthorizationEntity::getUserId,userId).orderByDesc(AutomationAuthorizationEntity::getCreatedAt)).stream().map(this::refresh).map(this::view).toList();}
    @Transactional public AuthorizationView revoke(Long userId,String id){AutomationAuthorizationEntity value=owned(userId,id);if("ACTIVE".equals(value.getStatus())){value.setStatus("REVOKED");value.setRevokedAt(LocalDateTime.now());mapper.updateById(value);audit.record(userId,"AUTOMATION_AUTHORIZATION_REVOKE","AUTOMATION_AUTHORIZATION",id);}return view(value);}
    public PolicyDecisionView decision(Long userId,String platform,String scope){String normalizedPlatform=normalize(platform),normalizedScope=scope==null?"":scope.trim().toUpperCase(Locale.ROOT);if(!SCOPES.contains(normalizedScope))throw new ValidationException("Unsupported authorization scope");var policy=policies.resolve(normalizedPlatform);AutomationAuthorizationEntity authorization=mapper.selectList(new LambdaQueryWrapper<AutomationAuthorizationEntity>().eq(AutomationAuthorizationEntity::getUserId,userId).eq(AutomationAuthorizationEntity::getPlatform,normalizedPlatform).eq(AutomationAuthorizationEntity::getStatus,"ACTIVE")).stream().map(this::refresh).filter(value->"ACTIVE".equals(value.getStatus())&&json.readStringList(value.getScopesJson()).contains(normalizedScope)).findFirst().orElse(null);boolean external=normalizedScope.startsWith("EXTERNAL_");boolean allowed=policy.effective()&&authorization!=null&&!external;String reason=!policy.effective()?"Unknown, stale or disabled platform policy forces MANUAL_ONLY":authorization==null?"No active authorization for requested scope":external?"Phase 10 has no external execution handler; manual action is required":"Active authorization permits only this local safe scope";return new PolicyDecisionView(normalizedPlatform,normalizedScope,policy.effective(),authorization!=null,allowed,allowed?"LOCAL_SAFE":"MANUAL_ONLY",reason);}
    private AutomationAuthorizationEntity refresh(AutomationAuthorizationEntity value){if("ACTIVE".equals(value.getStatus())&&!value.getExpiresAt().isAfter(LocalDateTime.now())){value.setStatus("EXPIRED");mapper.updateById(value);}return value;}
    private AutomationAuthorizationEntity owned(Long userId,String id){AutomationAuthorizationEntity value=mapper.selectOne(new LambdaQueryWrapper<AutomationAuthorizationEntity>().eq(AutomationAuthorizationEntity::getUserId,userId).eq(AutomationAuthorizationEntity::getPublicId,id).last("LIMIT 1"));if(value==null)throw new ResourceNotFoundException("Automation authorization");return value;}
    private AuthorizationView view(AutomationAuthorizationEntity v){return new AuthorizationView(v.getPublicId(),v.getVersion(),v.getPlatform(),json.readStringList(v.getScopesJson()),v.getEvidenceType(),v.getEvidenceRef(),v.getGrantedAt(),v.getExpiresAt(),v.getRevokedAt(),v.getStatus());}
    private static String normalize(String value){return value==null||value.isBlank()?"UNKNOWN":value.trim().toUpperCase(Locale.ROOT);}
}
