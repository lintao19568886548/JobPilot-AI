package com.jobpilot.operations.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class OperationalMetrics {
    private static final String[] TASK_STATUSES={"PENDING","RUNNING","SUCCEEDED","FAILED","BLOCKED","CANCELLED"};
    private static final String[] RUN_TYPES={"BACKUP","RESTORE_DRILL","DEPENDENCY_SCAN","SECRET_SCAN","LICENSE_SCAN","CONTAINER_SCAN","LOAD_TEST"};
    private final JdbcTemplate jdbc;
    private final MeterRegistry registry;
    private final AtomicLong aiCalls=new AtomicLong();
    private final AtomicLong budgetAlerts=new AtomicLong();
    private final Map<String,AtomicLong> taskCounts=new LinkedHashMap<>(),runCounts=new LinkedHashMap<>();

    public OperationalMetrics(JdbcTemplate jdbc,MeterRegistry registry){this.jdbc=jdbc;this.registry=registry;}

    @PostConstruct
    void register(){
        Gauge.builder("jobpilot.ai.calls",aiCalls,AtomicLong::get).description("Persisted AI call records").register(registry);
        Gauge.builder("jobpilot.ai.budget.alerts",budgetAlerts,AtomicLong::get).description("Enabled budgets at warning or exceeded state").register(registry);
        for(String status:TASK_STATUSES){AtomicLong value=new AtomicLong();taskCounts.put(status,value);Gauge.builder("jobpilot.automation.tasks",value,AtomicLong::get).tag("status",status.toLowerCase()).register(registry);}
        for(String type:RUN_TYPES){AtomicLong value=new AtomicLong();runCounts.put(type,value);Gauge.builder("jobpilot.operational.runs",value,AtomicLong::get).tag("type",type.toLowerCase()).register(registry);}
        refresh();
    }

    @Scheduled(fixedDelayString="${jobpilot.operations.metrics-refresh-ms:30000}")
    public void refresh(){
        try{aiCalls.set(count("SELECT COUNT(*) FROM ai_call_logs"));}catch(Exception ignored){aiCalls.set(0);}
        try{budgetAlerts.set(count("""
                SELECT COUNT(*)
                  FROM ai_budget_policies p
                 WHERE p.enabled=1 AND p.deleted_at IS NULL
                   AND (
                     (p.daily_token_limit IS NOT NULL AND
                       (SELECT COALESCE(SUM(COALESCE(a.input_tokens,0)+COALESCE(a.output_tokens,0)),0)
                          FROM ai_call_logs a WHERE a.user_id=p.user_id AND a.started_at>=UTC_DATE())*100
                         >=p.daily_token_limit*p.warning_threshold_percent)
                     OR (p.monthly_token_limit IS NOT NULL AND
                       (SELECT COALESCE(SUM(COALESCE(a.input_tokens,0)+COALESCE(a.output_tokens,0)),0)
                          FROM ai_call_logs a WHERE a.user_id=p.user_id AND a.started_at>=DATE_FORMAT(UTC_TIMESTAMP(),'%Y-%m-01'))*100
                         >=p.monthly_token_limit*p.warning_threshold_percent)
                     OR (p.daily_cost_limit IS NOT NULL AND
                       (SELECT COALESCE(SUM(COALESCE(a.estimated_cost,0)),0)
                          FROM ai_call_logs a WHERE a.user_id=p.user_id AND UPPER(COALESCE(a.currency,''))=UPPER(p.currency) AND a.started_at>=UTC_DATE())*100
                         >=p.daily_cost_limit*p.warning_threshold_percent)
                     OR (p.monthly_cost_limit IS NOT NULL AND
                       (SELECT COALESCE(SUM(COALESCE(a.estimated_cost,0)),0)
                          FROM ai_call_logs a WHERE a.user_id=p.user_id AND UPPER(COALESCE(a.currency,''))=UPPER(p.currency) AND a.started_at>=DATE_FORMAT(UTC_TIMESTAMP(),'%Y-%m-01'))*100
                         >=p.monthly_cost_limit*p.warning_threshold_percent)
                   )
                """));}catch(Exception ignored){budgetAlerts.set(0);}
        taskCounts.forEach((status,value)->value.set(safeCount("SELECT COUNT(*) FROM safe_automation_tasks WHERE status=?",status)));
        runCounts.forEach((type,value)->value.set(safeCount("SELECT COUNT(*) FROM operational_runs WHERE run_type=? AND deleted_at IS NULL",type)));
    }

    private long count(String sql,Object...args){Long value=jdbc.queryForObject(sql,Long.class,args);return value==null?0:value;}
    private long safeCount(String sql,Object...args){try{return count(sql,args);}catch(Exception ignored){return 0;}}
}
