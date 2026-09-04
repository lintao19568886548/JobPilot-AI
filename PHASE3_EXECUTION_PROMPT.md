# JobPilot AI — Phase 3 Matching Engine 执行提示词

你现在继续开发本地项目：

`D:\JobPilot AI`

Phase 0、Phase 1、Phase 2 已完成并通过真实 Build、Test、Run、MySQL/Redis 持久化、HTTP Smoke 与浏览器联调。现在只进入 Phase 3，不提前开发 Recommendation Center、Application Queue、Resume Tailor、Chrome Extension 或自动投递。

## 一、Phase 3 目标

完整跑通真实链路：

候选人事实与默认简历 → 岗位 → 版本化 Match Config → 白名单 Hard Filter → Skill Match → Project Evidence → BGE-M3 Embedding → Milvus 向量存储/缓存 → 可选 LLM 深度分析 → Final Score → S/A/B/C/D → 优势/缺口/风险/证据 → 不可变 Match 历史 → Backend 重启后仍可读取。

所有业务事实必须来自 MySQL；向量必须真实生成并写入 Milvus；Redis 必须用于任务幂等/短锁或任务状态。禁止 Mock API、随机向量、硬编码假分数、静态假 AI 解释、内存持久化和刷新后丢失的数据。

## 二、设计一致性

实现前完整读取并核对：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`

发现冲突时必须分析、选择方案、同步修改文档并在 Changelog 记录原因，不得静默偏离。

## 三、范围边界

本阶段只开发：

- Matching Engine 后端模块
- Hard Filter DSL 与规则
- Match Config 版本管理
- Skill/Project/Preference/Company 评分
- BGE-M3 Embedding Provider
- Milvus Collection、索引、向量缓存
- OpenAI-Compatible LLM Provider 与 MatchingAgent
- Transactional Outbox、异步任务、幂等、重试
- Match API 与 Job Center 匹配分析 UI
- Phase 3 测试、Smoke、持久化和浏览器验证

禁止开发 Phase 4+ 的推荐列表、收藏反馈、投递队列、CRM、Tailor、扩展 UI、Playwright 投递、面试、Offer、LTR。

## 四、数据库迁移

使用 Flyway 新建 `V3__matching_engine.sql`，不得修改已发布 V1/V2。至少建立：

- `match_configs`
- `hard_filter_rules`
- `match_runs`
- `job_matches`
- `job_match_details`
- `entity_embeddings`
- `ai_call_logs`
- `outbox_events`

要求：

- 所有用户资源有 `user_id` 和必要归属索引。
- Match Config、Match 结果、AI 调用、Outbox 为可追溯记录。
- 重算创建新 Match，不覆盖旧 Match。
- 使用 `DECIMAL(5,2)` 与 CHECK 保证 0–100。
- 保存算法版本、配置版本、权重/阈值/惩罚快照、输入 Hash、Prompt/模型/Embedding 版本。
- Outbox 支持 `PENDING/PROCESSING/SUCCEEDED/FAILED/DEAD`、attempt、availableAt、safe error。
- 迁移必须验证现有 V1/V2 数据不丢失，并在全新临时数据库从 V1→V2→V3 成功执行。

## 五、Match Config

实现不可变版本配置：

- 默认权重：skill 30、embedding 20、llm 25、project 10、preference 10、company 5。
- 默认等级：S≥90、A≥80、B≥70、C≥60、D<60。
- 权重必须非负且总和为 100。
- 同一用户只能有一个 active 配置。
- 修改配置创建新版本，不覆盖旧版本。
- 激活配置写 Audit。

API：

- `GET /api/v1/match-configs`
- `POST /api/v1/match-configs`
- `POST /api/v1/match-configs/{id}:activate`
- 同时提供项目现有 `/api/...` 兼容路径。

## 六、Hard Filter

只允许服务端白名单 DSL，不执行脚本、SpEL、SQL 或任意表达式。支持：

- `GRADUATION_YEAR`
- `EDUCATION`
- `CITY`
- `EXPERIENCE_YEARS`
- `JOB_TYPE`
- `SALARY`
- `COMPANY_BLACKLIST`
- `JOB_KEYWORD_BLACKLIST`

操作符只允许：`EQ/NE/GTE/LTE/IN/NOT_IN/CONTAINS/RANGE`，并根据 ruleType 校验合法组合。

每条规则有 priority、action（`REJECT/DOWNGRADE/WARN`）、penalty、operand、active、version。结果聚合为 `PASS/DOWNGRADE/REJECT`，返回 expected、actual、证据、penalty 和解释。客户端不得提交 userId 或伪造 Hard Filter 结果。

API：

- `GET /api/v1/hard-filter-rules`
- `POST /api/v1/hard-filter-rules`
- `PATCH /api/v1/hard-filter-rules/{id}`（生成新版本或切换 active）

## 七、确定性评分

Backend 是最终分数真相：

- Skill Score：Must-have 权重大于 Nice-to-have，精确技能、Alias 和已配置 Ontology Relation 可解释。
- 缺少 Must-have 必须成为 Gap；不能被 LLM 改写成优势。
- Project Score：只从真实 Project 技术栈、描述、职责、成果提取证据引用。
- Preference Score：目标城市、远程、岗位方向、行业、公司类型、薪资偏好。
- Company Score：公司行业/规模/风险标记与候选人偏好。
- 所有维度 0–100，使用 BigDecimal 统一舍入。
- `REJECT` 不继续产生伪造的软分数，overall 可为空或 0，并明确拒绝原因。
- `DOWNGRADE` 从加权分中扣除显式 penalty。
- 保存每一维度的证据详情。

## 八、Embedding 与 Milvus

实现真实 Provider：

- 默认模型 `BAAI/bge-m3`，模型名、版本、维度从环境变量配置。
- 禁止随机/常量/Fake 向量进入真实运行链路。
- 文本归一化后计算 SHA-256；同 entity/model/contentHash 命中 MySQL/Milvus 缓存，不重复计算。
- 对 Job、Profile、Resume Version、Project、Skill Evidence 生成真实向量。
- 使用余弦相似度并规范化到 0–100。
- Milvus Collection 使用版本化名称，至少包含 vectorId、userId、entityType、entityPublicId、contentHash、model、createdAt。
- 建立适合维度与数据量的 COSINE 索引；Collection/索引初始化可重复执行。
- `docker compose --profile ai up -d` 必须真实启动 etcd、MinIO、Milvus 并通过健康/连接检查。
- Provider 不可用时任务失败并可重试，禁止回退随机分数。

## 九、LLM 与 MatchingAgent

FastAPI 使用 LangGraph 实现：

`validate_input → embedding_similarity → llm_analysis(optional) → validate_output → finalize`

提供：

- `POST /internal/v1/embeddings`
- `POST /internal/v1/matches/evaluate`

要求：

- Pydantic v2 `extra='forbid'`。
- 输入只包含必要、已确认的候选人/简历事实和 JD 数据。
- 网页 Prompt Injection 文本只能作为数据，系统提示与数据边界分离。
- 输出优势、缺口、风险、recommendation、reason、llmScore 和 evidenceRefs。
- evidenceRefs 必须属于请求提供的事实 ID；无证据内容拒绝。
- 额外字段、越界分数、未知 evidenceRef、非法 JSON 都失败。
- OpenAI-Compatible Provider 从 `LLM_BASE_URL/LLM_API_KEY/LLM_MODEL` 读取。
- 实现 timeout、有限重试、并发限制、熔断和安全错误映射。
- AI 调用记录 provider/model/promptVersion/hash、token、耗时、估算成本和状态，不记录 Secret 或候选人敏感全文。

本机若没有 LLM Secret：

- 不得伪造调用或 `llmScore`。
- 结果标记 `SKIPPED_NOT_CONFIGURED`。
- Final Score 使用配置快照中的 `effectiveWeights` 对可用维度重新归一化，并在 API/UI 明确显示。
- Provider 契约、非法输出、超时、重试、熔断仍须由自动测试真实覆盖。
- `LLM_LIVE_CALL` 报告 `NOT_CONFIGURED`，不能写 `PASS`；只要禁用/降级行为符合契约，不阻断其他 Phase 3 核心验收。

## 十、异步任务与幂等

`POST /jobs/{jobId}/match-runs` 在同一事务创建 Match Run + Outbox Event，返回 202 任务资源。Worker 使用数据库领取、Redis 短锁和幂等输入 Hash：

- 同 job/profile/resume/config/algorithm/inputHash 的成功结果可复用。
- `force=true` 创建新运行和新 Match 历史。
- 指数退避，最大次数可配置；超过阈值进入 DEAD。
- Backend/AI 重启后任务和结果不丢失。
- 错误只保存安全摘要。

API：

- `POST /api/v1/jobs/{jobId}/match-runs`
- `GET /api/v1/match-runs/{runId}`
- `POST /api/v1/match-runs/{runId}:retry`
- `GET /api/v1/jobs/{jobId}/matches`
- `GET /api/v1/job-matches/{matchId}`

## 十一、Frontend

继续使用 Vue 3 JavaScript，不引入 TypeScript。Job Center 在现有 Job Detail 右栏真实展示：

- 未评估、排队、运行中、失败、已完成状态。
- Run Match、Retry 按钮及 Loading/错误反馈。
- overall score、S/A/B/C/D、Hard Filter 状态。
- 六维分数、effectiveWeights、penalty。
- 优势、缺口、风险、推荐理由。
- candidateEvidenceRef 与 jobEvidence。
- 算法、配置、Embedding、Prompt/模型版本。
- LLM 未配置时明确显示 `Skipped — not configured`，不显示假 LLM 分数。
- Match 历史可查看，旧结果不被覆盖。

不实现 Phase 4 推荐列表、收藏或排行页面。

## 十二、安全与测试

Backend 至少覆盖：

- Match Config 版本与激活
- Hard Filter 每类规则、operator 白名单、priority
- PASS/DOWNGRADE/REJECT 与 penalty
- Skill Must/Nice、Alias/Ontology、项目证据
- Embedding 缓存、相似度边界、Provider 失败
- 评分权重/范围/等级边界
- Match ownership、不可变历史、幂等与 force
- Outbox retry/dead/restart
- AI Schema extra/score/evidence 校验

AI Service 至少覆盖：

- BGE-M3 Provider 与 content hash cache
- Milvus Provider contract
- MatchingAgent Graph
- Prompt Injection 隔离
- 不虚构 evidence
- LLM invalid JSON/extra/range/unknown evidence
- timeout/retry/circuit breaker
- 无 Secret 的明确降级

执行：

- `mvn clean test`
- `mvn package`
- `pytest`
- `ruff check`
- `npm run build`
- Docker Compose config 与 AI profile
- Backend、AI Service、Frontend 真实启动

禁止 `skipTests` 或假写 PASS。

## 十三、真实验收流程

至少真实执行：

1. 启动 MySQL、Redis、etcd、MinIO、Milvus。
2. 迁移 V3，检查表、约束、索引。
3. 启动 AI Service，验证 health/readiness、BGE-M3、Milvus。
4. 启动 Backend、Frontend并登录。
5. 保证 Candidate/Profile/Skill/Project/Default Resume 有真实数据。
6. 选择包含 Must/Nice 技能和经验门槛的真实数据库 Job。
7. 创建/读取默认 Match Config。
8. 创建 Hard Filter，验证 PASS、DOWNGRADE、REJECT。
9. 发起 Match Run，轮询到终态。
10. 验证真实 Embedding 写入 Milvus、MySQL 元数据与缓存命中。
11. 验证 Skill、Project、Preference、Company、penalty 和 overall。
12. 验证优势/缺口/风险只引用真实证据。
13. 重放相同输入验证幂等/缓存；force 重算验证历史不可变。
14. 模拟 Provider 不可用，验证安全失败与 Retry。
15. 重启 Backend、AI Service、Milvus 后再次读取 Match 与向量。
16. 浏览器验证 Job Center 的匹配状态、分数、证据、版本和错误状态，控制台无 error。

## 十四、文档与任务

同步更新：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`
- `.env.example`

只有实际完成且验证通过的 Phase 3 任务才能改为 `[x]`；阻塞项使用 `[!]` 并写原因。

## 十五、最终报告

严格输出：

```text
PROJECT=JobPilot AI
PHASE=PHASE_3
PHASE_STATUS=PASS/FAIL/BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_SERVICE_TEST=
AI_SERVICE_RUN=
FRONTEND_BUILD=
FRONTEND_RUN=
DOCKER_COMPOSE=
DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
REDIS_CONNECTION=
MILVUS_RUN=
BGE_M3_EMBEDDING=
EMBEDDING_CACHE=
MATCH_CONFIG=
HARD_FILTER_DSL=
HARD_FILTER_PASS=
HARD_FILTER_DOWNGRADE=
HARD_FILTER_REJECT=
SKILL_MATCH=
PROJECT_EVIDENCE=
PREFERENCE_SCORE=
COMPANY_SCORE=
LLM_PROVIDER_CONTRACT=
LLM_LIVE_CALL=
MATCHING_AGENT=
STRICT_AI_SCHEMA=
PROMPT_INJECTION_ISOLATION=
EVIDENCE_TRUTH_CHECK=
FINAL_SCORE=
LEVEL_BOUNDARIES=
MATCH_VERSIONING=
OUTBOX=
IDEMPOTENCY=
RETRY_DEAD_LETTER=
FRONTEND_BACKEND_INTEGRATION=
HTTP_SMOKE_TEST=
BROWSER_UI_TEST=
GIT_STATUS=
```

然后报告新增/修改文件、测试、HTTP 验证、数据库表、Milvus Collection/向量数量、完成/未完成任务、阻塞项、技术债务和 Phase 4 建议。

只有全部核心项真实通过才能声明 Phase 3 Complete；没有 LLM Secret 时必须如实报告 `LLM_LIVE_CALL=NOT_CONFIGURED`。
