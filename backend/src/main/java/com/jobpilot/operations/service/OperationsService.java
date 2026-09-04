package com.jobpilot.operations.service;

import static com.jobpilot.operations.dto.OperationsDtos.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobpilot.audit.service.AuditService;
import com.jobpilot.common.config.AiServiceProperties;
import com.jobpilot.common.exception.BusinessException;
import com.jobpilot.common.exception.ResourceNotFoundException;
import com.jobpilot.common.exception.ValidationException;
import com.jobpilot.common.logging.TraceContext;
import com.jobpilot.common.util.JsonCodec;
import com.jobpilot.learning.service.LearningHash;
import com.jobpilot.matching.domain.AiCallLogEntity;
import com.jobpilot.matching.mapper.AiCallLogMapper;
import com.jobpilot.operations.domain.AiBudgetPolicyEntity;
import com.jobpilot.operations.domain.OperationalRunEntity;
import com.jobpilot.operations.mapper.AiBudgetPolicyMapper;
import com.jobpilot.operations.mapper.OperationalRunMapper;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OperationsService {
    private static final Set<String> RUN_TYPES = Set.of("BACKUP","RESTORE_DRILL","DEPENDENCY_SCAN","SECRET_SCAN","LICENSE_SCAN","CONTAINER_SCAN","LOAD_TEST");
    private static final Set<String> RUN_STATUSES = Set.of("RUNNING","SUCCEEDED","FAILED","PARTIAL");
    private static final Pattern SENSITIVE_SUMMARY = Pattern.compile("(?i)(password|authorization|cookie|api[_-]?key|refresh[_-]?token)\\s*[:=]");
    private final AiBudgetPolicyMapper budgets;
    private final OperationalRunMapper operationalRuns;
    private final AiCallLogMapper aiCalls;
    private final AuditService audit;
    private final JsonCodec json;
    private final ObjectMapper objectMapper;
    private final JdbcTemplate jdbc;
    private final RedisConnectionFactory redis;
    private final AiServiceProperties ai;
    private final Environment environment;
    private final MeterRegistry meters;
    private final String milvusHost;
    private final int milvusPort;

    public OperationsService(AiBudgetPolicyMapper budgets, OperationalRunMapper operationalRuns,
                             AiCallLogMapper aiCalls, AuditService audit, JsonCodec json,
                             ObjectMapper objectMapper, JdbcTemplate jdbc, RedisConnectionFactory redis,
                             AiServiceProperties ai, Environment environment, MeterRegistry meters,
                             @Value("${jobpilot.operations.milvus-host:127.0.0.1}") String milvusHost,
                             @Value("${jobpilot.operations.milvus-port:19531}") int milvusPort) {
        this.budgets=budgets; this.operationalRuns=operationalRuns; this.aiCalls=aiCalls; this.audit=audit;
        this.json=json; this.objectMapper=objectMapper; this.jdbc=jdbc; this.redis=redis; this.ai=ai;
        this.environment=environment; this.meters=meters; this.milvusHost=milvusHost; this.milvusPort=milvusPort;
    }

    public OverviewView overview(Long userId) {
        ProviderStatusView provider = providerStatus();
        Map<String,String> health = new LinkedHashMap<>();
        health.put("backend","UP"); health.put("mysql",mysqlHealth()); health.put("redis",redisHealth());
        health.put("aiService",provider.serviceStatus()); health.put("milvus",tcpHealth(milvusHost,milvusPort));
        Map<String,Object> observability = new LinkedHashMap<>();
        observability.put("traceId",TraceContext.getTraceId()); observability.put("tracePropagation",true);
        observability.put("prometheusEnabled",true);
        observability.put("otlpEnabled",environment.getProperty("management.tracing.export.otlp.enabled",Boolean.class,false));
        observability.put("structuredLogging",Arrays.asList(environment.getActiveProfiles()).contains("observability"));
        Map<String,Object> safety = safety();
        return new OverviewView(health,observability,usage(userId,provider),runs(userId,null).stream().limit(12).toList(),safety);
    }

    public UsageView usage(Long userId) { return usage(userId,providerStatus()); }

    private UsageView usage(Long userId, ProviderStatusView providerStatus) {
        LocalDateTime now=LocalDateTime.now(ZoneOffset.UTC);
        LocalDateTime monthStart=LocalDate.now(ZoneOffset.UTC).withDayOfMonth(1).atStartOfDay();
        LocalDateTime dayStart=LocalDate.now(ZoneOffset.UTC).atStartOfDay();
        List<AiCallLogEntity> values=aiCalls.selectList(new LambdaQueryWrapper<AiCallLogEntity>()
                .eq(AiCallLogEntity::getUserId,userId).ge(AiCallLogEntity::getStartedAt,monthStart).le(AiCallLogEntity::getStartedAt,now));
        long callsToday=0,inputToday=0,outputToday=0,inputMonth=0,outputMonth=0;
        Map<String,Long> statuses=new TreeMap<>(); Map<String,CostAccumulator> costs=new TreeMap<>(); Map<String,ProviderAccumulator> providers=new TreeMap<>();
        for(AiCallLogEntity value:values){
            boolean today=value.getStartedAt()!=null&&!value.getStartedAt().isBefore(dayStart);
            int input=value.getInputTokens()==null?0:value.getInputTokens(),output=value.getOutputTokens()==null?0:value.getOutputTokens();
            inputMonth+=input; outputMonth+=output; if(today){callsToday++;inputToday+=input;outputToday+=output;}
            statuses.merge(or(value.getStatus(),"UNKNOWN"),1L,Long::sum);
            String currency=or(value.getCurrency(),"UNKNOWN").toUpperCase(Locale.ROOT); BigDecimal cost=value.getEstimatedCost()==null?BigDecimal.ZERO:value.getEstimatedCost();
            CostAccumulator ca=costs.computeIfAbsent(currency,k->new CostAccumulator()); ca.month=ca.month.add(cost);if(today)ca.today=ca.today.add(cost);
            String provider=or(value.getProvider(),"UNKNOWN"),model=or(value.getModel(),"NOT_CONFIGURED"),key=provider+"\u0000"+model;
            ProviderAccumulator pa=providers.computeIfAbsent(key,k->new ProviderAccumulator(provider,model));pa.calls++;pa.input+=input;pa.output+=output;if("FAILED".equals(value.getStatus()))pa.failures++;
        }
        List<CostView> costViews=costs.entrySet().stream().map(e->new CostView(e.getKey(),e.getValue().today,e.getValue().month)).toList();
        List<ProviderUsageView> providerViews=providers.values().stream().map(v->new ProviderUsageView(v.provider,v.model,v.calls,v.input,v.output,v.failures)).toList();
        return new UsageView(callsToday,values.size(),inputToday,outputToday,inputMonth,outputMonth,statuses,costViews,providerViews,providerStatus,budget(userId,inputToday+outputToday,inputMonth+outputMonth,costViews));
    }

    public BudgetView budget(Long userId) { return usage(userId,providerStatus()).budget(); }

    @Transactional
    public BudgetView updateBudget(Long userId, BudgetRequest request) {
        AiBudgetPolicyEntity entity=findBudget(userId);
        if(entity==null){if(request.version()!=0)throw conflict("Budget version conflict");entity=new AiBudgetPolicyEntity();entity.setUserId(userId);}
        else if(!Objects.equals(entity.getVersion(),request.version()))throw conflict("Budget version conflict");
        if(Boolean.TRUE.equals(request.enabled())&&request.dailyTokenLimit()==null&&request.monthlyTokenLimit()==null&&request.dailyCostLimit()==null&&request.monthlyCostLimit()==null)throw new ValidationException("Enabled budget requires at least one limit");
        entity.setDailyTokenLimit(request.dailyTokenLimit());entity.setMonthlyTokenLimit(request.monthlyTokenLimit());
        entity.setDailyCostLimit(request.dailyCostLimit());entity.setMonthlyCostLimit(request.monthlyCostLimit());
        entity.setCurrency(request.currency().trim().toUpperCase(Locale.ROOT));entity.setWarningThresholdPercent(request.warningThresholdPercent());entity.setEnabled(request.enabled());
        if(entity.getId()==null)budgets.insert(entity);else if(budgets.updateById(entity)!=1)throw conflict("Budget version conflict");
        audit.record(userId,"AI_BUDGET_UPDATE","AI_BUDGET_POLICY",entity.getPublicId());meters.counter("jobpilot.ai.budget.updates").increment();
        return budget(userId);
    }

    public List<RunView> runs(Long userId,String type){
        LambdaQueryWrapper<OperationalRunEntity> query=new LambdaQueryWrapper<OperationalRunEntity>().eq(OperationalRunEntity::getUserId,userId).orderByDesc(OperationalRunEntity::getCreatedAt).last("LIMIT 100");
        if(type!=null&&!type.isBlank()){String normalized=type.trim().toUpperCase(Locale.ROOT);validateRunType(normalized);query.eq(OperationalRunEntity::getRunType,normalized);}
        return operationalRuns.selectList(query).stream().map(this::view).toList();
    }

    public RunView run(Long userId,String id){return view(ownedRun(userId,id));}

    @Transactional
    public RunView createRun(Long userId,String key,RunRequest request){
        if(key==null||key.isBlank()||key.length()>120)throw new ValidationException("Idempotency-Key must contain 1 to 120 characters");
        String type=request.runType().trim().toUpperCase(Locale.ROOT),status=request.status().trim().toUpperCase(Locale.ROOT);validateRunType(type);
        if(!RUN_STATUSES.contains(status))throw new ValidationException("Unsupported operational run status");
        if(!"RUNNING".equals(status)&&request.finishedAt()==null)throw new ValidationException("A completed run requires finishedAt");
        if(request.finishedAt()!=null&&request.finishedAt().isBefore(request.startedAt()))throw new ValidationException("finishedAt cannot precede startedAt");
        String scope=request.scope()==null||request.scope().isBlank()?"USER":request.scope().trim().toUpperCase(Locale.ROOT);if(!Set.of("USER","SYSTEM").contains(scope))throw new ValidationException("Unsupported operational run scope");
        validateArtifactPath(request.artifactManifestPath());if(SENSITIVE_SUMMARY.matcher(request.summary()).find())throw new ValidationException("Operational summary may not contain credentials or secrets");
        String metricsJson=json.write(request.metrics()==null?Map.of():request.metrics());if(metricsJson.length()>32768)throw new ValidationException("Operational metrics exceed 32 KiB");
        String hash=LearningHash.sha256(json.write(request)); OperationalRunEntity existing=findRunByKey(userId,key.trim());
        if(existing!=null){if(!existing.getRequestHash().equals(hash))throw conflict("Idempotency-Key was already used with different operational data");return view(existing);}
        OperationalRunEntity entity=new OperationalRunEntity();entity.setUserId(userId);entity.setScope(scope);entity.setRunType(type);entity.setStatus(status);entity.setBatchId(request.batchId().trim());entity.setIdempotencyKey(key.trim());entity.setRequestHash(hash);entity.setStartedAt(request.startedAt());entity.setFinishedAt(request.finishedAt());entity.setMetricsJson(metricsJson);entity.setSummarySafe(request.summary().trim());entity.setArtifactManifestPath(normalizePath(request.artifactManifestPath()));entity.setArtifactSha256(request.artifactSha256());entity.setRpoSeconds(request.rpoSeconds());entity.setRtoSeconds(request.rtoSeconds());entity.setErrorCode(request.errorCode());entity.setTraceId(TraceContext.getTraceId());entity.setCreatedBy(request.createdBy()==null?"SCRIPT":request.createdBy());
        try{operationalRuns.insert(entity);}catch(DataIntegrityViolationException ex){OperationalRunEntity replay=findRunByKey(userId,key.trim());if(replay!=null&&replay.getRequestHash().equals(hash))return view(replay);throw conflict("Operational run already exists");}
        audit.record(userId,"OPERATIONAL_RUN_CREATE","OPERATIONAL_RUN",entity.getPublicId());meters.counter("jobpilot.operational.run.creations","type",type,"status",status).increment();return view(entity);
    }

    private BudgetView budget(Long userId,long tokensToday,long tokensMonth,List<CostView> costs){
        AiBudgetPolicyEntity b=findBudget(userId);if(b==null)return new BudgetView(null,0,null,null,null,null,"USD",BigDecimal.valueOf(80),false,"DISABLED",null,null,null);
        CostView cost=costs.stream().filter(v->v.currency().equalsIgnoreCase(b.getCurrency())).findFirst().orElse(new CostView(b.getCurrency(),BigDecimal.ZERO,BigDecimal.ZERO));
        BigDecimal dailyToken=BudgetEvaluator.percent(tokensToday,b.getDailyTokenLimit()),monthlyToken=BudgetEvaluator.percent(tokensMonth,b.getMonthlyTokenLimit());
        BigDecimal dailyCost=BudgetEvaluator.percent(cost.today(),b.getDailyCostLimit()),monthlyCost=BudgetEvaluator.percent(cost.month(),b.getMonthlyCostLimit());
        BigDecimal daily=max(dailyToken,dailyCost),monthly=max(monthlyToken,monthlyCost);String state=BudgetEvaluator.status(Boolean.TRUE.equals(b.getEnabled()),b.getWarningThresholdPercent(),daily,monthly);
        return new BudgetView(b.getPublicId(),b.getVersion(),b.getDailyTokenLimit(),b.getMonthlyTokenLimit(),b.getDailyCostLimit(),b.getMonthlyCostLimit(),b.getCurrency(),b.getWarningThresholdPercent(),Boolean.TRUE.equals(b.getEnabled()),state,daily,monthly,b.getUpdatedAt());
    }

    private AiBudgetPolicyEntity findBudget(Long userId){return budgets.selectOne(new LambdaQueryWrapper<AiBudgetPolicyEntity>().eq(AiBudgetPolicyEntity::getUserId,userId));}
    private OperationalRunEntity findRunByKey(Long userId,String key){return operationalRuns.selectOne(new LambdaQueryWrapper<OperationalRunEntity>().eq(OperationalRunEntity::getUserId,userId).eq(OperationalRunEntity::getIdempotencyKey,key));}
    private OperationalRunEntity ownedRun(Long userId,String id){OperationalRunEntity value=operationalRuns.selectOne(new LambdaQueryWrapper<OperationalRunEntity>().eq(OperationalRunEntity::getUserId,userId).eq(OperationalRunEntity::getPublicId,id));if(value==null)throw new ResourceNotFoundException("Operational run");return value;}
    private RunView view(OperationalRunEntity v){return new RunView(v.getPublicId(),v.getVersion(),v.getScope(),v.getRunType(),v.getStatus(),v.getBatchId(),v.getStartedAt(),v.getFinishedAt(),json.readNode(v.getMetricsJson()),v.getSummarySafe(),v.getArtifactManifestPath(),v.getArtifactSha256(),v.getRpoSeconds(),v.getRtoSeconds(),v.getErrorCode(),v.getTraceId(),v.getCreatedBy(),v.getCreatedAt());}
    private void validateRunType(String value){if(!RUN_TYPES.contains(value))throw new ValidationException("Unsupported operational run type");}
    private void validateArtifactPath(String value){if(value==null||value.isBlank())return;String normalized=value.replace('\\','/');Path path=Path.of(normalized);if(path.isAbsolute()||normalized.startsWith("/")||path.normalize().startsWith("..")||normalized.contains("../"))throw new ValidationException("Artifact manifest path must be relative and cannot traverse directories");}
    private String normalizePath(String value){return value==null||value.isBlank()?null:value.replace('\\','/');}
    private Map<String,Object> safety(){Map<String,Object> result=new LinkedHashMap<>();try{Map<String,Object> row=jdbc.queryForMap("SELECT COALESCE(SUM(external_message_sent),0) messages,COALESCE(SUM(external_submission),0) submissions,COALESCE(SUM(external_mutation),0) mutations,COALESCE(MIN(user_confirmation_required),1) confirmation FROM safe_automation_tasks");result.put("externalMessagesSent",number(row.get("messages")));result.put("externalSubmissions",number(row.get("submissions")));result.put("externalMutations",number(row.get("mutations")));result.put("userConfirmationRequired",number(row.get("confirmation"))==1);}catch(Exception ignored){result.put("externalMessagesSent",0);result.put("externalSubmissions",0);result.put("externalMutations",0);result.put("userConfirmationRequired",true);}result.put("automaticOfferDecisions",0);result.put("destructiveDuplicateDeletes",0);return result;}
    private ProviderStatusView providerStatus(){try{HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();HttpRequest req=HttpRequest.newBuilder(URI.create(ai.getBaseUrl()+"/internal/v1/readiness")).timeout(Duration.ofSeconds(3)).GET().build();HttpResponse<String> response=client.send(req,HttpResponse.BodyHandlers.ofString());if(response.statusCode()!=200)throw new IllegalStateException();JsonNode n=objectMapper.readTree(response.body());return new ProviderStatusView(text(n,"status","DOWN"),n.path("llmConfigured").asBoolean(false),text(n,"parserMode","UNKNOWN"),text(n,"circuitBreaker",n.path("llmConfigured").asBoolean()?"UNKNOWN":"NOT_CONFIGURED"),text(n,"embeddingProvider","UNKNOWN"),text(n,"embeddingModel","UNKNOWN"),text(n,"milvusCollection","UNKNOWN"));}catch(Exception ex){return new ProviderStatusView("DOWN",false,"UNKNOWN","UNAVAILABLE","UNKNOWN","UNKNOWN","UNKNOWN");}}
    private String mysqlHealth(){try{return jdbc.queryForObject("SELECT 1",Integer.class)==1?"UP":"DOWN";}catch(Exception ex){return "DOWN";}}
    private String redisHealth(){try(RedisConnection connection=redis.getConnection()){return "PONG".equalsIgnoreCase(connection.ping())?"UP":"DOWN";}catch(Exception ex){return "DOWN";}}
    private String tcpHealth(String host,int port){try(Socket socket=new Socket()){socket.connect(new InetSocketAddress(host,port),1000);return "UP";}catch(Exception ex){return "DOWN";}}
    private String text(JsonNode node,String field,String fallback){String value=node.path(field).asText("");return value.isBlank()?fallback:value;}
    private static String or(String value,String fallback){return value==null||value.isBlank()?fallback:value;}
    private static long number(Object value){return value instanceof Number n?n.longValue():0;}
    private static BigDecimal max(BigDecimal a,BigDecimal b){if(a==null)return b;if(b==null)return a;return a.max(b);}
    private static BusinessException conflict(String message){return new BusinessException(4091101,message,HttpStatus.CONFLICT);}
    private static final class CostAccumulator {private BigDecimal today=BigDecimal.ZERO,month=BigDecimal.ZERO;}
    private static final class ProviderAccumulator {private final String provider,model;private long calls,input,output,failures;private ProviderAccumulator(String provider,String model){this.provider=provider;this.model=model;}}
}
