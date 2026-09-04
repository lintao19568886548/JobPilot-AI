package com.jobpilot.privacy.service;

import static com.jobpilot.privacy.dto.PrivacyDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.privacy.domain.PrivacyOperationRequestEntity;
import com.jobpilot.privacy.mapper.PrivacyOperationRequestMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivacyService {
    public static final String CONFIRMATION_PHRASE = "DELETE MY JOBPILOT DATA";
    private static final List<String> USER_DATA_TABLES = List.of(
            "offer_comparison_items", "offer_comparisons", "offer_deadlines", "offer_benefits", "offers",
            "analytics_snapshots", "analytics_daily", "interview_reminders", "knowledge_gap_evidence", "knowledge_gaps",
            "interview_review_items", "interview_reviews", "interview_answer_notes", "interview_questions", "interview_rounds", "interviews",
            "communication_drafts", "resume_tailor_runs", "resume_version_metrics", "application_queue_items", "applications",
            "recruiters", "job_recommendations", "recommendation_refresh_items", "recommendation_refresh_runs",
            "entity_embeddings", "job_matches", "match_runs", "match_configs", "hard_filter_rules", "job_parse_runs", "job_import_tasks",
            "job_sources", "jobs", "automation_tasks", "extension_devices", "refresh_tokens", "candidate_skills", "projects",
            "experiences", "educations", "candidate_profiles", "resumes");
    private final JdbcTemplate jdbc; private final PrivacyOperationRequestMapper requests; private final JsonCodec json;
    private final ObjectMapper objectMapper; private final AuditService audit;
    public PrivacyService(JdbcTemplate jdbc, PrivacyOperationRequestMapper requests, JsonCodec json,
                          ObjectMapper objectMapper, AuditService audit) {
        this.jdbc=jdbc; this.requests=requests; this.json=json; this.objectMapper=objectMapper; this.audit=audit;
    }

    public ExportView export(Long userId) {
        Map<String,Long> counts=counts(userId); Map<String,Object> data=new LinkedHashMap<>();
        data.put("candidateProfile", rows("SELECT * FROM candidate_profiles WHERE user_id=? AND deleted_at IS NULL",userId));
        data.put("educations",rows("SELECT * FROM educations WHERE user_id=? AND deleted_at IS NULL ORDER BY sort_order,id",userId));
        data.put("experiences",rows("SELECT * FROM experiences WHERE user_id=? AND deleted_at IS NULL ORDER BY sort_order,id",userId));
        data.put("projects",rows("SELECT * FROM projects WHERE user_id=? AND deleted_at IS NULL ORDER BY sort_order,id",userId));
        data.put("candidateSkills",rows("SELECT * FROM candidate_skills WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("resumes",rows("SELECT * FROM resumes WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("resumeVersions",rows("SELECT rv.* FROM resume_versions rv JOIN resumes r ON r.id=rv.resume_id WHERE r.user_id=? AND rv.deleted_at IS NULL ORDER BY rv.id",userId));
        data.put("jobs",rows("SELECT * FROM jobs WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("applications",rows("SELECT * FROM applications WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("applicationTimeline",rows("SELECT application_id,from_status,to_status,event_type,occurred_at,source,note,evidence_json,trace_id,created_at FROM application_logs WHERE user_id=? ORDER BY id",userId));
        data.put("interviews",rows("SELECT * FROM interviews WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("offers",rows("SELECT * FROM offers WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("offerBenefits",rows("SELECT * FROM offer_benefits WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        data.put("offerComparisons",rows("SELECT public_id,comparison_version,name,offer_ids_json,weights_json,input_snapshot_json,currency_group,cash_comparable,summary_text,created_at FROM offer_comparisons WHERE user_id=? AND deleted_at IS NULL ORDER BY id",userId));
        audit.record(userId,"PRIVACY_EXPORT","USER",String.valueOf(userId));
        return new ExportView("PHASE9_V1",OffsetDateTime.now(ZoneOffset.UTC),counts,data);
    }

    @Transactional
    public DeletionPreviewView preview(Long userId,String idempotencyKey) {
        validateKey(idempotencyKey); Map<String,Long> scope=counts(userId); String hash=sha256(json.write(scope));
        PrivacyOperationRequestEntity existing=byKey(userId,idempotencyKey);
        if(existing!=null){Map<String,Long> original=readCounts(existing.getScopeCountsJson());return previewView(existing,!existing.getRequestHash().equals(hash),original);}
        PrivacyOperationRequestEntity entity=new PrivacyOperationRequestEntity();entity.setUserId(userId);entity.setOperationType("DELETE_PREVIEW");
        entity.setStatus("PREVIEWED");entity.setRequestHash(hash);entity.setIdempotencyKey(idempotencyKey.trim());entity.setScopeCountsJson(json.write(scope));requests.insert(entity);
        audit.record(userId,"PRIVACY_DELETE_PREVIEW","USER",String.valueOf(userId));return previewView(entity,false,scope);
    }

    @Transactional
    public DeletionResultView confirm(Long userId,String idempotencyKey,DeletionConfirmRequest request) {
        validateKey(idempotencyKey); PrivacyOperationRequestEntity idempotent=byKey(userId,idempotencyKey);
        if(idempotent!=null){if(!"DELETE_CONFIRM".equals(idempotent.getOperationType()))throw new BusinessException(4099201,"Idempotency key is already used by another privacy operation",HttpStatus.CONFLICT);return result(idempotent);}
        PrivacyOperationRequestEntity preview=owned(userId,request.requestId());
        if(!"DELETE_PREVIEW".equals(preview.getOperationType()))throw new ValidationException("requestId is not a deletion preview");
        if(!CONFIRMATION_PHRASE.equals(request.confirmationPhrase()))throw new ValidationException("Exact deletion confirmation phrase is required");
        if(!preview.getVersion().equals(request.requestVersion()))throw new BusinessException(4099202,"Deletion preview version conflict",HttpStatus.CONFLICT);
        Map<String,Long> current=counts(userId);String currentHash=sha256(json.write(current));
        if(!preview.getRequestHash().equals(currentHash))throw new BusinessException(4099203,"User data changed after preview; create a new deletion preview",HttpStatus.CONFLICT);
        PrivacyOperationRequestEntity execution=new PrivacyOperationRequestEntity();execution.setUserId(userId);execution.setOperationType("DELETE_CONFIRM");
        execution.setStatus("PREVIEWED");execution.setRequestHash(currentHash);execution.setIdempotencyKey(idempotencyKey.trim());execution.setScopeCountsJson(json.write(current));requests.insert(execution);
        audit.record(userId,"PRIVACY_DELETE_CONFIRM","USER",String.valueOf(userId));
        for(String table:USER_DATA_TABLES)jdbc.update("UPDATE "+table+" SET deleted_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE user_id=? AND deleted_at IS NULL",userId);
        jdbc.update("UPDATE resume_sections rs JOIN resume_versions rv ON rv.id=rs.resume_version_id JOIN resumes r ON r.id=rv.resume_id SET rs.deleted_at=UTC_TIMESTAMP(3),rs.updated_at=UTC_TIMESTAMP(3) WHERE r.user_id=? AND rs.deleted_at IS NULL",userId);
        jdbc.update("UPDATE resume_versions rv JOIN resumes r ON r.id=rv.resume_id SET rv.deleted_at=UTC_TIMESTAMP(3),rv.updated_at=UTC_TIMESTAMP(3) WHERE r.user_id=? AND rv.deleted_at IS NULL",userId);
        jdbc.update("UPDATE users SET status='DISABLED',deleted_at=UTC_TIMESTAMP(3),updated_at=UTC_TIMESTAMP(3) WHERE id=? AND deleted_at IS NULL",userId);
        execution.setStatus("COMPLETED");execution.setExecutedAt(LocalDateTime.now(ZoneOffset.UTC));requests.updateById(execution);
        preview.setStatus("COMPLETED");preview.setExecutedAt(execution.getExecutedAt());requests.updateById(preview);
        return result(execution);
    }

    private Map<String,Long> counts(Long userId){Map<String,Long> result=new LinkedHashMap<>();for(String table:USER_DATA_TABLES){Long count=jdbc.queryForObject("SELECT COUNT(*) FROM "+table+" WHERE user_id=? AND deleted_at IS NULL",Long.class,userId);result.put(table,count==null?0:count);}return result;}
    private List<Map<String,Object>> rows(String sql,Long userId){return jdbc.queryForList(sql,userId);}
    private PrivacyOperationRequestEntity byKey(Long userId,String key){return requests.selectOne(new LambdaQueryWrapper<PrivacyOperationRequestEntity>().eq(PrivacyOperationRequestEntity::getUserId,userId).eq(PrivacyOperationRequestEntity::getIdempotencyKey,key.trim()).last("LIMIT 1"));}
    private PrivacyOperationRequestEntity owned(Long userId,String publicId){PrivacyOperationRequestEntity value=requests.selectOne(new LambdaQueryWrapper<PrivacyOperationRequestEntity>().eq(PrivacyOperationRequestEntity::getUserId,userId).eq(PrivacyOperationRequestEntity::getPublicId,publicId).last("LIMIT 1"));if(value==null)throw new ResourceNotFoundException("Privacy Request");return value;}
    private DeletionPreviewView previewView(PrivacyOperationRequestEntity value,boolean changed,Map<String,Long> scope){return new DeletionPreviewView(value.getPublicId(),value.getVersion(),scope,changed,CONFIRMATION_PHRASE,"This disables the account and logically deletes user-owned mutable data. Audit evidence remains.",value.getCreatedAt().atOffset(ZoneOffset.UTC));}
    private DeletionResultView result(PrivacyOperationRequestEntity value){return new DeletionResultView(value.getPublicId(),value.getStatus(),readCounts(value.getScopeCountsJson()),"COMPLETED".equals(value.getStatus()),value.getExecutedAt()==null?null:value.getExecutedAt().atOffset(ZoneOffset.UTC));}
    private Map<String,Long> readCounts(String value){try{return objectMapper.readValue(value,new TypeReference<Map<String,Long>>(){});}catch(JsonProcessingException e){throw new IllegalStateException("Stored privacy scope is invalid",e);}}
    private static void validateKey(String key){if(key==null||key.isBlank()||key.length()>120)throw new ValidationException("Idempotency-Key header is required and must be at most 120 characters");}
    private static String sha256(String value){try{return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}catch(NoSuchAlgorithmException e){throw new IllegalStateException(e);}}
}
