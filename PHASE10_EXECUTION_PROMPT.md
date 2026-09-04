# JobPilot AI — Phase 10 Learning to Rank 与安全 Automation Center 详细执行提示词

你正在继续开发：

`D:\JobPilot AI`

当前基线：Phase 0–9 已完成真实 Backend、AI Service、Frontend、MySQL、Redis、Milvus、HTTP、重启持久化和 Microsoft Edge 验收。Phase 9 已交付 Offer Center、不可变 Offer Comparison、完整 Analytics、数据导出与可审计删除。

现在正式执行：

# Phase 10 — Conservative Learning to Rank + Safe Automation Center

本阶段不得实现外部自动投递、自动接受/拒绝 Offer、自动谈薪、自动发送消息、后台刷招聘网站、验证码处理或绕过平台政策。所有自动化只允许本地整理、重评分任务编排、建议和站内提醒。

## 一、阶段目标

跑通真实数据库链路：

不可变 Match/Application/Reply/Interview/Offer 事实 → 重建不可变 Feedback → 固化训练 Feature Snapshot → 时间切分训练/验证 → 最小样本门槛 → 离线指标 → 创建 LTR Model Version → Shadow Ranking → 用户显式激活 → 一键回滚；同时实现 Automation Rule → 安全 Task → 本地 Handler → Suggestion/站内 Notification → 暂停、重试、去重、审计和重启持久化。

禁止 Mock API、内存状态、伪训练指标、随机排名、未来数据泄漏、自动激活、静默覆盖旧模型、破坏性重复岗位删除、外部通知和任何外部提交。

## 二、执行前检查与设计冲突

完整读取 `ARCHITECTURE.md`、`DATABASE_DESIGN.md`、`API_DESIGN.md`、`DEVELOPMENT_ROADMAP.md`、`TASKS.md`、`CHANGELOG.md`、`docs/PHASE9_FINAL_REPORT.md` 和测试报告。检查 Java 21、Maven Wrapper、Node/npm、Python、Docker Compose、Git、端口与全部服务健康。

Phase 7 已有 `automation_tasks`，并由数据库约束永久限定为 `ASSIST_PREPARE`、零外部提交和最终人工确认。Phase 10 禁止放宽该表约束；新建独立 `safe_automation_tasks` 承载本地规则执行，在设计文档和 Changelog 中记录这一冲突解决。

## 三、数据库与不可变性

使用 Flyway `V10__learning_automation.sql`，至少新增：

- `feedback`
- `ltr_training_runs`
- `ltr_model_versions`
- `ltr_shadow_results`
- `automation_rules`
- `safe_automation_tasks`
- `automation_suggestions`
- `automation_notifications`
- `automation_authorizations`

Feedback、Shadow Result 为追加式不可变记录；Model Version 不覆盖。每个用户最多一个活动 LTR Model。Rule、Task、Suggestion、Notification、Authorization 必须有 Ownership、索引、逻辑删除/状态约束与乐观锁。Task 数据库 CHECK 强制外部消息、外部提交和外部修改均为 false。

## 四、Feedback 与 Feature Snapshot

从真实事实生成 `VIEWED/APPLIED/REPLIED/INTERVIEW/OFFER` Feedback。每条记录保存来源类型/ID、事件时间、标签值、Feature Schema Version、Feature Snapshot 和稳定 Hash；同一来源幂等。

Feature 只允许使用结果发生前已经存在的特征：Skill、Embedding、LLM、Project、Preference、Company、Penalty、当时 Overall、Job/Company/Resume/Skill 摘要。必须选择 `evaluated_at <= outcome_occurred_at` 的 Match；没有合格 Match 就跳过，禁止回填未来 Match。

## 五、保守 LTR

- Feature Schema 和训练参数版本化；
- 默认最小样本不少于 30，可通过非敏感环境变量提高但不能由请求降低；
- 按事件时间做严格训练/验证切分，验证数据必须晚于训练数据；
- 保存 Baseline 与 Candidate NDCG@10、样本量、正例率、切分时间和 Leakage Check；
- 样本不足只返回 `INSUFFICIENT_DATA` 分析，不创建可激活模型；
- 指标未达到门槛的 Model 只能 `REJECTED/DRAFT`，不能 Shadow/Activate；
- Shadow Ranking 只写 `ltr_shadow_results`，不得修改当前推荐顺序；
- 用户显式激活后才让 Recommendation Projection 使用活动模型；
- 激活和回滚都必须审计、可重复、不可覆盖旧版本。

## 六、Learning API

至少实现：

- `POST /api/v1/learning/feedback:rebuild`
- `GET /api/v1/learning/feedback/summary`
- `POST /api/v1/learning/models:train`
- `GET /api/v1/learning/models`
- `GET /api/v1/learning/models/{id}`
- `POST /api/v1/learning/models/{id}:shadow`
- `POST /api/v1/learning/models/{id}:activate`
- `POST /api/v1/learning/models/{id}:rollback`
- `GET /api/v1/learning/dashboard`

重建、训练和 Shadow 使用 `Idempotency-Key`。激活/回滚使用版本校验并要求用户主动操作。

## 七、安全 Automation Center

Rule Type 只允许：

- `DAILY_JOB_DIGEST`
- `SAFE_REEVALUATION`
- `DUPLICATE_CLEANUP_SUGGESTION`
- `HIGH_MATCH_REMINDER`
- `FOLLOW_UP_REMINDER`
- `INTERVIEW_REMINDER`
- `OFFER_DEADLINE_REMINDER`

Rule 只接受预定义 Handler、Spring 六段 Cron 和 IANA 时区。Scheduler 默认由 `AUTOMATION_CENTER_ENABLED=false` 关闭；手工运行可验收。Handler 只能创建本地 Match Refresh、Suggestion 或站内 Notification。重复岗位只能产生建议，接受建议也不得直接删除岗位。

Task 必须幂等、保存安全输入/输出、attempt/maxAttempts、nextRetryAt、错误码和安全错误消息；支持暂停 Rule、取消未执行 Task、失败任务指数退避和人工重试。通知使用稳定 dedup key，不能调用邮件、短信、微信、日历或招聘平台。

## 八、Policy 与 Authorization

复用 Phase 5 `platform_policies`，未知、禁用或超过 180 天均强制 `MANUAL_ONLY`。Authorization 必须有 Scope、证据、授予、过期和撤销时间；授权缺失/过期/撤销时决策为 denied。即使有授权，本阶段也没有外部自动提交 Handler，`AUTHORIZED_AUTOMATION` 继续不可执行。

## 九、Automation API

至少实现：

- `GET/POST /api/v1/automation-center/rules`
- `PUT /api/v1/automation-center/rules/{id}`
- `POST /api/v1/automation-center/rules/{id}:run`
- `GET /api/v1/automation-center/tasks`
- `GET /api/v1/automation-center/tasks/{id}`
- `POST /api/v1/automation-center/tasks/{id}:retry`
- `POST /api/v1/automation-center/tasks/{id}:cancel`
- `GET /api/v1/automation-center/suggestions`
- `POST /api/v1/automation-center/suggestions/{id}:accept`
- `POST /api/v1/automation-center/suggestions/{id}:dismiss`
- `GET /api/v1/automation-center/notifications`
- `POST /api/v1/automation-center/notifications/{id}:read`
- `GET/POST /api/v1/automation-center/authorizations`
- `DELETE /api/v1/automation-center/authorizations/{id}`
- `GET /api/v1/automation-center/policy-decision`
- `GET /api/v1/automation-center/dashboard`

## 十、Frontend

继续 Vue 3 + JavaScript，禁止 TypeScript 业务源码。实现：

- Learning 页面：Feedback 数量/类型、样本门槛、训练/验证切分、离线指标、模型历史、Shadow 差异、激活与回滚；
- Automation Center：规则、启停、手工运行、Task 状态/重试、Suggestion、站内 Notification、Authorization 和安全边界；
- Dashboard：活动模型、待处理建议、未读通知、失败 Task；
- 小样本与空状态清晰，不显示伪造提升。

UI 延续浅色、留白、轻边框和专业 SaaS 风格。

## 十一、审计与安全

至少审计 Feedback Rebuild、LTR Train/Shadow/Activate/Rollback、Rule Create/Update/Run、Task Retry/Cancel、Suggestion Decision、Notification Read、Authorization Grant/Revoke。Audit 不写 Feature 全文、Token、Secret、Cookie、密码或外部页面数据。

必须确认：`externalMessagesSent=0`、`externalSubmissions=0`、`externalMutations=0`、`automaticOfferDecisions=0`、`destructiveDuplicateDeletes=0`。

## 十二、测试与真实验收

Backend 覆盖 Feedback 幂等、时间泄漏、最小样本、时间切分、NDCG、模型状态机、Shadow 不改现排名、激活/回滚、Rule Cron/Timezone、Handler 白名单、Task 幂等/重试/取消、通知去重、Policy/Authorization、Ownership 和数据库安全约束。

必须实际执行：

1. `mvn clean test` 和 `mvn package`；
2. AI Service 全量 pytest 与 Ruff；
3. Frontend production build；
4. MySQL V9→V10 和临时空库 V1→V10；
5. Redis PONG、Backend/AI/Frontend 健康；
6. 构造不少于最小门槛的真实时间序列 Match/Application 事实；
7. Feedback Rebuild 两次幂等且无未来特征；
8. 验证低样本不能激活；验证足量样本的时间切分、指标和 Model Version；
9. Shadow 前后当前推荐顺序/分数不变；显式激活后使用新版本；回滚恢复旧策略；
10. 创建并运行全部安全 Rule Handler，验证建议/通知去重且没有外部动作；
11. 验证 Policy 过期和 Authorization 撤销安全降级；
12. 重启 Backend 后复验 Feedback、Model、Rule、Task、Suggestion、Notification；
13. Microsoft Edge 验证 Dashboard、Learning、Automation Center，console error=0。

禁止 `skipTests` 和假 PASS。

## 十三、TASKS、Git 与报告

同步更新 `TASKS.md`、README、ARCHITECTURE、DATABASE_DESIGN、API_DESIGN、DEVELOPMENT_ROADMAP、CHANGELOG、测试报告和最终报告。Phase 10 P0 未全部完成前不得进入后续阶段。检查 `.env`、target、node_modules、venv、日志、数据库、导出和运行状态均被 Git 忽略。不 commit、不 push，除非用户明确授权。

## 十四、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_10
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_REGRESSION=
DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
FEEDBACK_REBUILD=
FEATURE_SNAPSHOT=
LEAKAGE_GUARD=
MIN_SAMPLE_GATE=
TIME_SPLIT=
OFFLINE_METRICS=
MODEL_VERSIONING=
SHADOW_RANKING=
MODEL_ACTIVATION=
MODEL_ROLLBACK=
AUTOMATION_RULES=
AUTOMATION_TASKS=
AUTOMATION_RETRY=
SUGGESTION_SAFETY=
NOTIFICATION_DEDUP=
POLICY_DOWNGRADE=
AUTHORIZATION_REVOKE=
DASHBOARD_INTEGRATION=
FRONTEND_BUILD=
FRONTEND_RUN=
BROWSER_E2E=
CONSOLE_ERRORS=
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_SUBMISSIONS=0 / 非0
EXTERNAL_MUTATIONS=0 / 非0
AUTOMATIC_OFFER_DECISIONS=0 / 非0
DESTRUCTIVE_DUPLICATE_DELETES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任一核心项失败则 Phase 10 不能声明完成。
