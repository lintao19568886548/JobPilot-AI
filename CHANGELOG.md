# Changelog

本文件记录会影响既有设计基线的实现决策。普通代码修复由 Git 历史记录。

## 2026-09-03 — Phase 15 简体中文界面

### Added

- 新增 Web 统一展示值转换模块，集中处理状态、枚举、布尔值、计数和已知确定性生成说明的简体中文展示。
- 新增可复用的中文化实施提示词，明确中文覆盖范围、契约边界、安全约束和真实验收门槛。

### Changed

- Web 默认使用简体中文和 Element Plus 中文语言包；导航、页面标题、表单、按钮、提示、错误、空状态和业务数据标签统一改为中文。
- 浏览器扩展的清单名称、侧栏流程、设备名称、警告和安全边界改为中文。
- 后端返回给用户的引导问题、分析标签、学习/自动化安全边界、申请队列和 Offer 比较说明改为中文。
- 保持 API 路径、JSON 字段、数据库 Schema、领域枚举、日志 Key、技术名词和 JobPilot 品牌不变；用户保存的简历、岗位和公司原文不做自动翻译。

### Validation

- Frontend Production Build 通过；Backend 440 项测试、AI Service 52 项测试通过；Extension Typecheck、Lint、13 项测试和 Production Build 通过，共 505 项自动化测试。
- Backend、Frontend、AI Service、MySQL 与 Redis 容器健康运行；Gateway、Dashboard、Resume、Settings、Actuator、OpenAPI 和 AI Readiness 共 7 项 HTTP 回归返回 200。
- 桌面与 390px 窄屏各完成 16 个页面逐页验收：中文标题、已知领域枚举展示和响应式布局通过，无横向溢出，console error/warning 为 0。
- 修复 AI Service 发布镜像构建受官方 PyTorch CDN 中断影响的问题：使用本地 Git 忽略的官方 CPU Wheel 缓存，并在安装前校验官方 SHA-256，不提交第三方二进制文件。

## 2026-09-03 — Phase 14 Account Security & Workspace Settings 实现及验收

### Added

- 新增当前用户密码变更、活动 Web Session 查询/撤销 API，以及账户资料和固定白名单工作区设置 API。
- 新增 Settings 页面，接入紧凑布局、默认登录落地页、Dashboard 引导开关、Session 管理和密码修改。
- 新增 Flyway V12、16 项 Backend 测试、Phase 14 迁移检查和可恢复密码/设置的 HTTP/持久化脚本。

### Changed

- Access/Refresh JWT 增加 `authVersion`，Access Token 绑定 Session Family；密码变更或 Session 撤销通过数据库状态与 Redis Denylist 立即失效。
- `refresh_tokens` 增加客户端标签、User-Agent Hash、脱敏 IP 和最后使用时间；响应与日志不输出 Token、完整 User-Agent 或完整 IP。
- Phase 0 概念表名 `system_setting` 统一为实现使用的复数 `system_settings`。Phase 14 只开放三个非敏感强类型 Key，不以数据库配置替代环境 Secret。
- Extension 配对不因 Web 密码变更被静默删除，同时 Extension 认证增加 Auth Version 校验。
- 本地发布镜像默认版本提升至 `0.14.0`，继续保持 Loopback Gateway、非 Root、只读根文件系统和 `cap_drop: ALL`。

### Validation

- Backend 440、AI Service 52、Extension 13、Automation Worker 6，共 511 项自动化测试通过；Backend Package、Ruff 和 Frontend Production Build 通过。
- 既有库 V11→V12 与临时空库 V1→V12 真实迁移通过；Flyway 12、83 张表、3 条白名单设置、Session Metadata 与 Audit 均已直接查询验证，Redis 返回 `PONG`。
- Phase 14 Full 21 次、重启持久化 7 次、Phase 12 发布安全回归 10 次，共 38 次 HTTP 通过；密码和设置由 `finally`/持久化流程恢复。
- `0.14.0` 三个应用镜像健康运行并完成容器加固检查。Settings 桌面/390px、紧凑模式、默认落地页和 Dashboard 提示联动通过，console error/warning 为 0。
- 验收期间发现并修复 `is_sensitive` MyBatis 列映射和持久化脚本比较顺序问题；修复后重新执行测试、打包、镜像、Full/Persistence/Release 回归。
- 首次真实简历导入发现并修复 Spring Boot 4/Jackson 3 无法反序列化旧 `JsonNode` 请求类型、Experience 切换为“至今”时旧结束日期未清空，以及 Profile 全量替换无法清除旧可空字段的问题；旧 Jackson 仍只用于稳定领域存储格式。

## 2026-09-03 — Phase 13 Guided Onboarding & Data Quality 实现及验收

### Added

- 新增当前用户 `Onboarding Overview` 与 `Data Quality` API，返回九项固定权重检查、0–100 Readiness、稳定问题 Code 和 `BLOCKER/WARNING/INFO` 汇总。
- 新增 Setup Center、主导航入口和 Dashboard Readiness 提示；所有修复入口只指向 Candidate Profile 或 Resume Center。
- 新增 7 项 Onboarding Service/Controller 测试和可重复的 6-call Phase 13 HTTP/重启指纹脚本。

### Changed

- Onboarding 结论从 Candidate、Skill、Education、Experience、Project、Resume 和不可变 Version 的当前用户 MySQL 事实实时重算，不新增派生表、不调用 LLM、不自动补写资料。
- Element Plus 改为仅注册实际组件，ECharts 改为按需加载 Bar/Pie/Grid/Tooltip/Canvas，并拆分 Vue、Element、ECharts、ZRender 等 Vendor Chunk。
- 本地发布镜像默认版本提升到 `0.13.0`；三个应用继续使用非 Root、只读根文件系统、`cap_drop: ALL` 和 Loopback Gateway。
- 修复 Dashboard 的 Interview/Funnel 网格在 390px 视口下撑宽页面的问题；Setup Center 与 Dashboard 均无横向溢出。

### Validation

- Backend 424、AI Service 52、Extension 13、Automation Worker 6，共 495 项自动化测试通过；Backend package、Ruff 与 Frontend Production Build 通过。
- Frontend 最大 JavaScript Chunk 为 ECharts 333.81 KiB，未出现超过 500 KiB 警告；`npm audit` 在发布镜像构建中为 0 vulnerability。
- Backend、AI Service、Frontend `0.13.0` 镜像构建并健康运行；三个容器均为非 Root、只读根文件系统并移除全部 Capability。
- Phase 13 共执行 18 次 HTTP，首轮、应用重启和前端最终构建后均通过；Phase 12 安全发布回归追加 10 次 HTTP 通过。重启前后 Readiness 34、完成 3/9、8 个问题 Code 完全一致。
- Flyway 保持 11、MySQL 保持 82 张表，Redis 返回 PONG；桌面/390px 浏览器验收无横向溢出，console error/warning 为 0，五类禁止外部动作均为 0。

## 2026-09-03 — Phase 12 Local Secure Release 实现及验收

### Added

- 新增 Backend、AI Service、Frontend 多阶段发布镜像，以及组合既有数据服务的 Release Compose。
- 新增 Doctor、Build、Up、Down、Release、Smoke 和 Rollback Check 脚本；启动前复用 Phase 11 一致批次备份。
- 新增包含 Artifact SHA-256、镜像 ID/用户、Flyway 版本和 Backup 引用的 Release Manifest，以及 ZIP 校验文件和本地安全发布 Runbook。

### Changed

- 默认 Gateway 改为 `127.0.0.1:8180`，避免与常见本机 `8080` 服务冲突；Backend `8088` 与 AI `8010` 也只绑定 Loopback，数据服务不新增主机端口。
- 三个应用容器统一为非 Root、只读根文件系统、`cap_drop: ALL` 和 `no-new-privileges`；Frontend Nginx 统一代理 SPA/API/Health/Swagger 并返回五项安全头。
- AI 发布镜像明确使用 CPU-only PyTorch，避免把 CUDA Runtime 引入本地单机发布。OTLP 仍默认关闭，LLM 未配置仍明确降级而不伪造结果。
- 回滚设计为只读兼容性预检。实际镜像切换和数据恢复不会自动执行，避免 Schema 不兼容或误操作覆盖活动数据。

### Validation

- Backend 417 项、AI 52 项、Extension 13 项、Automation Worker 6 项测试和 Frontend Build 通过，共 488 项自动化测试。
- 三个发布镜像构建并以非 Root 用户健康运行；镜像与发布目录 Secret Check 通过，Compose 端口只绑定 Loopback。
- 启动前备份通过：Flyway 11、82 张表、3 个 Hash Artifact、1 个 Milvus Collection/28 行。发布 Manifest 固化 71 个 Artifact（含 Phase 12 Runbook/报告）和 3 个镜像。
- 首轮与应用容器重启后各 10 次 HTTP 验证通过；五项安全响应头、Trace 透传、容器加固和持久化均通过。
- Operations 桌面与 390px 视口读取真实状态且无横向溢出，浏览器 console error/warning 为 0；所有外部动作计数为 0。

## 2026-09-03 — Phase 11 Operational Readiness 实现及验收

### Added

- Flyway V11 新增用户级 AI Budget Policy 与不可变 Operational Run 共 2 张表；完整 Schema 共 82 张表。
- 新增真实 Operations API/UI：Backend、MySQL、Redis、AI Service、Milvus 健康状态，Trace/Prometheus/OTLP 状态，AI 调用与成本，Provider 熔断状态，备份/恢复/扫描/负载运行证据和安全计数器。
- 新增一致批次 MySQL、文件存储、Milvus 备份，隔离数据库/临时 Collection 恢复演练，以及带 SHA-256、RPO/RTO 和清理验证的 Manifest。
- 新增 OWASP Dependency-Check、npm audit、pip-audit、Gitleaks、License Inventory、Trivy 镜像扫描和三类只读 API 并发负载基线脚本。

### Changed

- Backend 升级到 Spring Boot 4.1.1、Tomcat 11.0.25、MySQL Connector/J 26.7.0 和 MyBatis-Plus Boot 4 Starter；保留 Jackson 2 业务兼容层，避免无关领域序列化语义漂移。
- OpenTelemetry 只在显式配置时导出，默认 `OTEL_EXPORTER_OTLP_ENABLED=false`；外部请求 `X-Trace-Id` 与内部 OTel Trace/Span 分离，确保响应头、API Envelope 和日志使用同一个请求关联 ID。
- Compose 镜像固定到 MySQL 8.4.11、etcd 3.5.33、Milvus 2.6.22、Redis 7.4 Alpine 和最后公开的 MinIO Community 版本；MinIO/etcd 不发布主机端口。
- Springdoc 的 Swagger UI 资源版本显式对齐已修补 WebJar 5.32.14，修复 Spring Boot 4 下 `/swagger-ui.html` 重定向后找不到静态资源的问题。
- 容器扫描保留全部 248 个 Critical/High 原始结果，不做抑制；按当前仅本机、私有 Compose 网络和未启用 OIDC/LDAP/STS/SSH 的实际配置完成可达性评审，结论为 `PASS_WITH_FINDINGS`。任何网络暴露或功能开关变化都必须重新阻断评审。

### Validation

- Backend `clean test` 与 `package` 各通过 412 项测试；AI Service 52 项 pytest 与 Ruff、Frontend、Extension 和 Automation Worker 构建/测试回归通过。
- MySQL V10→V11 与临时空库 V1→V11 迁移通过，共 11 个 Flyway 版本、82 张表；Backend 重启后 Budget 和 Operational Run 从 MySQL 恢复，Redis 返回 PONG。
- 备份生成 3 个通过 SHA-256 校验的 Artifact，恢复演练验证 82 张表、187 个外键、1 个 Milvus Collection/28 行，RPO 41 秒、RTO 22 秒，生产数据库未触碰。
- 负载测试 90/90 请求成功：Job List P95 209.43ms、Recommendation P95 79.65ms、Dashboard P95 92.35ms；均低于 1000ms 门槛。
- Operations 浏览器桌面/390px 视口均读取真实 API，控制台错误为 0；外部消息、投递、外部变更、自动 Offer 决策和破坏性重复删除均为 0。

## 2026-09-02 — Phase 10 Learning to Rank 与安全 Automation Center 实现及验收

### Added

- Flyway V10 新增 Feedback/Rebuild、LTR Training/Model/Shadow、Automation Rule/Safe Task/Suggestion/Notification/Authorization 共 10 张表；完整 Schema 共 80 张表。
- 新增 `com.jobpilot.learning`：真实事件 Feedback、历史 Feature Snapshot、30 条最小样本门禁、时间 80/20 切分、NDCG@10、Model Version、Shadow、人工激活和回滚。
- 新增 `com.jobpilot.automation` 的本地安全中心：七类固定 Handler、幂等 Task、指数退避/人工重试/取消、非破坏 Suggestion、去重站内 Notification 和可撤销 Authorization。
- 新增 Learning、Automation Center 页面及可重复的 48-call HTTP、空库迁移和重启持久化脚本。

### Changed

- Phase 0 的概念 Automation 路径收敛到 `/api/v1/automation-center/*`，更新采用 `PUT + version`；原因是 Rule 是完整聚合且必须执行乐观锁，避免 `PATCH` 造成部分状态静默覆盖。
- Phase 10 新任务使用 `safe_automation_tasks`，与 Phase 7 的 Assist Prepare `automation_tasks` 分离，避免把本地整理/提醒与浏览器辅助任务混为一个安全边界。
- LTR 不直接覆盖 Phase 3 Match；Shadow 只保存比较结果，只有用户显式激活后 Recommendation 投影才读取活动版本。样本不足和指标不合格均不可激活。
- Scheduler 默认 `AUTOMATION_CENTER_ENABLED=false`。Policy/Authorization 只建立门禁，不新增外部 Handler；数据库约束强制外部消息、提交和变更为 0。

### Validation

- Backend `clean package` 通过 405 项测试；AI Service 52 项 pytest 与 Ruff、Frontend production build 全部通过。
- MySQL V9→V10 和临时空库 V1→V10 迁移通过，共 10 个 Flyway 版本、80 张表；Redis PONG，Backend 重启后 Feedback、Model、Rule、Task、Suggestion 和 Notification 均恢复。
- 完整 smoke 48 次 HTTP：36 条真实时间序列 Feedback、28/8 时间切分、NDCG@10、Shadow 不改在线顺序、激活/回滚、七类 Handler、幂等、重试/取消/暂停、通知去重、授权撤销和 Policy 降级全部通过。
- Microsoft Edge 的 Learning/Automation 页面和真实 API 联动通过，console error/warning 均为 0；数据库负向约束测试确认外部消息、外部提交、外部变更、自动 Offer 决策和破坏性重复删除均为 0。

## 2026-09-02 — Phase 9 Offer Center、完整 Analytics 与 Privacy 实现及验收

### Added

- Flyway V9 新增 Offer、Benefit、Comparison/Item、Deadline、Analytics Snapshot 和 Privacy Operation Request 共 7 张表；完整 Schema 共 70 张表。
- 新增 `com.jobpilot.offer` 模块，覆盖 Application 约束、金额校验、福利、状态机、截止事项、乐观锁、Ownership、逻辑删除和审计。
- 新增同币种确定性 Offer Comparison，持久化固定八维权重、主观输入、输入 Hash、版本化快照和逐项解释；跨币种明确不可比较现金。
- 新增七阶段完整漏斗、七类维度的回复/面试/Offer 转化、不可变 Analytics Snapshot，以及业务数据导出和两阶段可审计删除。
- 新增 Offer Center、Analytics 页面、Dashboard KPI，以及可重复的 31-call HTTP、空库迁移、重启持久化和 Microsoft Edge E2E 脚本。

### Changed

- Phase 0 概念 API 收敛为 REST CRUD 加命令式 `:status/:done/:cancel` 路由，同时保留斜杠兼容路由；Offer 更新采用全量 `PUT` 和版本号，防止静默覆盖。
- 完整 Analytics 不把零分母显示成 0%，而是返回 `rate=null`；样本量小于 5 明确标记 `insufficientSample=true`。每个维度分别输出回复、面试和 Offer 三个事实转化率。
- Offer 现金比较没有可信汇率源时禁止跨币种归一化；主观维度缺失时从有效分母排除。系统不会替用户自动接受或拒绝 Offer。
- Offer/Privacy 字段按本地优先、最小化 API 和禁止敏感日志处理。当前没有独立可轮换字段加密主密钥，因此未使用 JWT Secret 冒充加密，通用字段加密列为后续安全技术债。

### Validation

- Backend `clean test` 与 `package` 各通过 399 项测试；AI Service 52 项测试与 Ruff、Frontend 2303 模块 production build 全部通过。
- MySQL V8→V9 和临时空库 V1→V9 迁移通过，共 9 个 Flyway 版本、70 张表；Redis PONG，Backend 重启后 Offer、Comparison 和 Analytics Snapshot 均恢复。
- 完整 smoke 31 次 HTTP，Ownership、重复 Offer、乐观锁、状态机、Deadline、跨币种边界、幂等、逻辑删除、隐私精确短语与隔离账户禁用通过；外部消息、自动 Offer 决策和虚构汇率均为 0。
- Microsoft Edge 真实登录、Dashboard、Offer Center、Analytics 和 Privacy 控件通过，console error/warning 均为 0。

## 2026-09-02 — Phase 8 Interview Center、Review 与 Knowledge Gap 实现及验收

### Added

- Flyway V8 新增 Interview、Round、Question、Answer Note、Review、Review Item、Knowledge Gap/Evidence 和 Reminder 共 9 张表；现有库升级后共 63 张表。
- 新增 `com.jobpilot.interview` 模块、完整 Ownership/Validation/逻辑删除/乐观锁/审计，以及 Interview Center 和真实 Dashboard 联动。
- 新增 FastAPI + LangGraph Interview Prediction/Review 工作流、严格 Pydantic Schema 和版本化 Prompt；本机未配置 LLM Secret 时明确使用 `RULES_ONLY`。
- 新增可重复的 42-call HTTP smoke、重启持久化验证和 Microsoft Edge 浏览器验收脚本。

### Changed

- Phase 0 概念设计把轮次字段放在单条 Interview 上，并让每场面试只有一条 Review。Phase 8 改为 Interview 聚合下的多 Round、追加版本 Answer Note 和不可变多版本 Review，避免改期、复盘重算和用户回答被覆盖。
- Review 确认与 Knowledge Gap 激活分成两个显式人工动作。AI 只创建 `PROPOSED` 建议，禁止把推断自动写成用户事实或修改 Candidate Skill、Resume、Match 权重和 Application 状态。
- Reminder 只保存站内待办；未增加邮件、短信、第三方日历或会议邀请能力。Dashboard 的 Interview 能力由“不可用”改为真实事实，Offer/外部动作继续显式不可用。
- 概念稿曾写 `meeting_link_encrypted`，但当前仓库没有独立、可轮换的字段加密主密钥。Phase 8 选择把用户主动填写的链接作为本地 Interview 数据保存且禁止日志输出，不使用 JWT Secret 冒充加密密钥；通用字段加密与密钥迁移列为后续安全技术债。
- Java 到 Uvicorn 的 Interview AI 请求固定 HTTP/1.1，避免 h2c Upgrade 造成无效请求；AI 幂等 Hash 排除 Trace/Deadline/Task 等动态传输字段，只覆盖业务语义。
- 增加 SVG favicon，消除 Edge 自动请求 `/favicon.ico` 引发的控制台 404。

### Validation

- Backend `clean test` 和 `package` 各通过 376 项测试；AI Service 52 项测试与 Ruff、Frontend production build 均通过。
- 真实 MySQL 完成 V7→V8 和临时空库 V1→V8；重启 Backend 后 Interview、Round、Question、Answer Note、两版 Review、Gap 决策、Reminder 状态和 Dashboard 均从数据库恢复。
- 完整 smoke 42 次 HTTP，持久化复验 7 次 HTTP；Ownership、幂等、Review 不可变、Gap 人工确认、重复 Reminder、改期/完成/取消、时区和逻辑删除均通过。
- Microsoft Edge 登录、Dashboard、Interview Detail、Review History、Knowledge Gap 与 Reminder E2E 通过，console error/warning 均为 0；外部消息、会议邀请和自动 Application 更新均为 0。

## 2026-09-02 — Phase 6 Evidence-bound Resume Tailor 与 Communication Draft 实现及验收

### Added

- Flyway V6 新增 `prompt_templates`、`candidate_evidence_items`、`resume_tailor_runs`、`resume_tailor_changes`、`communication_drafts`、`resume_version_metrics` 共 6 张表，并扩展 `resume_versions` 的父版本/岗位/Prompt/AI Call/模型/真实性溯源字段。
- 增加 Profile、Education、Experience、Project、Skill 和 Master Resume Section 的不可变 Evidence Ledger 与快照 Hash。
- 增加 LangGraph ResumeAgent、逐项 Diff/Evidence Refs、后端 Truth Check、人工批准以及不可变 Derived Resume Version。
- 增加 BOSS、猎聘、邮件、微信、感谢、跟进、Offer Communication Draft 与 `DRAFT → APPROVED → USED` 生命周期；系统没有外部发送 API。
- 增加 AI Studio、Recommendation Tailor 入口，以及 Resume Center 的 Prompt/父版本/目标岗位溯源和事实指标。

### Changed

- Phase 0 设计中已有 `ai_call_log` 概念，Phase 3 已实际创建 `ai_call_logs`。Phase 6 选择扩展既有表的 `prompt_template_id` 外键，而不是创建第二套 AI 调用日志，避免审计事实分裂。
- Tailor 只接受当前用户活动 Master Resume 的当前版本作为事实源；批准时新增 Derived Resume/Version，绝不覆盖 Master 或旧版本。
- Draft 的“批准”和“已使用”仅记录本地状态，所有 API 继续返回 `externallySent=false`；外部投递仍沿用 Phase 5 的显式确认边界。

### Validation

- Backend `clean test` 与 `package` 均通过，342 项测试零失败；AI Service 43 项测试与 Ruff 通过；Frontend Production Build 通过。
- 现有库 V5→V6 与独立空库 V1→V6 均迁移成功，共 49 张表、2 个活动 Prompt；Redis PONG，Milvus 18 个持久化向量回归通过。
- 完整 Smoke 覆盖 34 次 HTTP，重启后 8 次持久化复验；验证 15 条 Evidence、2 项带引用 Diff、Master Hash 不变、Tailor/Draft 幂等、BOSS 100 字、零外发、Queue→Application→Reply 指标和 Ownership。
- 内置浏览器完成 AI Studio、Resume Version 指标与 Application Center 回归，控制台错误为 0；并修复岗位下拉公司名映射。

## 2026-09-01 — Phase 5 Application Queue 与 CRM 实现及验收

### Added

- Flyway V5 新增 `platform_policies`、`application_queue_items`、`applications`、`application_logs`、`recruiters`、`recruiter_interactions` 共 6 张表，并扩展 `analytics_daily` 的真实投递阶段计数。
- 增加 Queue 单个/批量入队、唯一活动项、Idempotency-Key、优先级、Resume/Greeting 引用、批准、跳过、逻辑移除和 Assist Prepare。
- 增加显式外部投递确认、Application 白名单状态机、乐观锁、不可变 Timeline、当前用户隔离和状态到达 KPI。
- 增加 Platform Policy Registry：未知或过期策略安全降级为 `MANUAL_ONLY`，Phase 5 明确拒绝 `AUTHORIZED_AUTOMATION`。
- 增加 Recruiter 基础档案和只保存人工事实的 Interaction；没有任何外部消息发送能力。
- 增加 Application Center、Recommendation 入队入口及 Dashboard Queue/Application 漏斗。

### Changed

- Phase 0 草案允许在 Queue 更新时切换模式。实现收敛为入队后不可修改 `mode`，避免已经审批的安全边界被静默替换；需要切换时应移除非成功项并重新入队。
- `POST /applications` 只接受 `MANUAL/ASSIST`，且必须显式传入 `confirmedExternalSubmission=true`。Assist Prepare 本身绝不创建 Application，也不声明外部提交成功。
- 投递阶段 KPI 改为从不可变 Application Log 按唯一 Application 计数，因此已从 REPLIED 进入 WRITTEN_TEST 的申请仍保留“曾收到回复”的真实漏斗事实。
- Recruiter 基础档案与人工 Interaction 从原 Phase 6 草案提前到 Phase 5 CRM；AI Communication Draft 和所有外部消息发送仍留在 Phase 6/更后阶段。

### Validation

- Backend 321 项测试和 Package、AI Service 32 项测试与 Ruff、Frontend Production Build 已通过（最终复验结果见本次 Phase 5 报告）。
- 现有库 V4→V5 和全新临时库 V1→V5 均迁移成功，共 43 张表；Redis、Milvus 和 Phase 1–4 数据链路完成回归。
- 完整 Smoke 覆盖 43 次 HTTP，Backend 重启后 7 次持久化复验；包含单个/批量幂等、唯一活动项、Assist 零提交、显式确认、合法/非法迁移、不可变日志、Recruiter、Analytics 和 Ownership。
- 内置浏览器完成真实登录、Dashboard、Queue、CRM Timeline 与 Recruiter 页面验证，控制台错误为 0。

## 2026-09-01 — Phase 4 Recommendation Center 与 Dashboard 实现及验收

### Added

- Flyway V4 新增 `job_recommendations`、`recommendation_events`、`recommendation_refresh_runs`、`recommendation_refresh_items` 和 `analytics_daily` 共 5 张表。
- 增加基于最新不可变 Match 的唯一 Recommendation 投影、五类视图、六种稳定排序、组合筛选和游标分页；未评估保持空分，不生成假 0 分。
- 增加批量 Recommendation Refresh，复用 Phase 3 Match/Outbox/幂等能力，并保存批次和逐项状态。
- 增加收藏/取消收藏、忽略/恢复的乐观锁、幂等语义、不可变事件快照与 Audit。
- 增加真实 Dashboard KPI、今日推荐、等级/来源/状态分布、Phase 4 漏斗、可重建日聚合，以及当前用户隔离的 Job/Company/Skill/Status Global Search。
- 增加 Recommendation Center、共享 Match Analysis、真实 Dashboard 图表和 `Ctrl+K` 全局搜索 UI。

### Changed

- Phase 0 Roadmap 曾把“已投”、最近面试和待办放入 Phase 4 展示范围，但这些指标在 Application/Interview 事实表出现前没有可信来源。Phase 4 只返回显式 `available=false` 能力状态并禁用入口；“已投/投递 CRM”留在 Phase 5，最近面试留在 Phase 8。
- `job_recommendation` 概念表收敛为复数 `job_recommendations`，状态使用 `UNEVALUATED/READY/MATCH_FAILED/IGNORED`；收藏是正交布尔状态，用户行为另存不可变 `recommendation_events`。
- Phase 4 冒烟发现 MySQL JSON 列规范化键顺序会造成字符串比较误判和投影版本持续增长；改为 JSON 语义比较，并加入防版本抖动回归测试。

### Validation

- Backend 91 项测试和 Package、AI Service 32 项测试与 Ruff、Frontend Production Build 全部通过。
- 现有库 V3→V4 与全新临时库 V1→V4 均迁移成功，最终 37 张表；Redis PONG，Milvus 单集合 18 个持久化 BGE-M3 向量（MySQL 有 16 个当前内容引用，旧内容 Hash 向量按缓存策略保留）。
- 完整 Smoke 52 次 HTTP、Backend 重启后 7 次持久化复验通过；7 个唯一投影、批量刷新、视图/排序/分页、收藏幂等、忽略恢复、12 条不可变事件、19 条日聚合和搜索安全均已验证。
- 内置浏览器完成真实登录、Dashboard、Recommendation、Global Search、Candidate 与 Resume 回归；“已投”禁用，控制台错误为 0。

## 2026-09-01 — Phase 3 Matching Engine 实现与验收

### Added

- Flyway V3 新增 Match Config、Hard Filter、Skill Relation、Match Run/Result/Detail、AI Call Log、Embedding 元数据和 Outbox 共 9 张表。
- 增加白名单 Hard Filter DSL、六维确定性评分、等级边界与不可变配置/结果快照；活动规则版本进入输入 Hash。
- 增加真实 `BAAI/bge-m3` SentenceTransformer、Milvus 2.6 COSINE/AUTOINDEX、Job/Profile/Resume/Project/Skill Evidence 向量和内容 Hash 缓存。
- 增加 LangGraph MatchingAgent、严格 Pydantic/Java 响应校验、Prompt Injection 数据隔离和候选证据引用检查。
- 增加 OpenAI-Compatible LLM Provider 的超时、重试、并发限制和熔断；本机未提供 LLM Secret，运行验收明确记录 `SKIPPED_NOT_CONFIGURED`、空 LLM 分和权重重归一化。
- 增加 Transactional Outbox、Redis Worker Lease、幂等、退避重试、DEAD、同任务恢复与陈旧 Lease 恢复。
- 增加 Job Center 匹配分析、六维有效权重、证据、模型/算法版本和历史结果 UI。

### Changed

- Compose 的 Milvus Profile 固定为本机实际验证的 Milvus 2.6.22、etcd 3.5.25 和 MinIO 2024-12-18，并显式透传 MinIO 凭据，避免依赖组件使用不一致默认凭据。
- Phase 3 没有配置 LLM Secret 时不阻塞确定性匹配，也不伪造 LLM 调用或分数；这项支持状态已同步到 API 和 Roadmap。
- 匹配输入 Hash 除 Profile 本身外还覆盖技能、项目、教育和经历事实，避免子资源更新错误复用旧 Match。
- Phase 0 API 草案中的批量 Match 暂留 Phase 4；Phase 3 只交付单岗位可靠异步闭环。

### Validation

- Backend 66 项测试、AI Service 32 项测试与 Ruff、Frontend Production Build 全部通过。
- 首次真实 BGE-M3 请求生成 2 条 1024 维向量；重复请求 2/2 缓存命中。完整匹配得到可追溯六维分和等级，Milvus 重启后向量仍存在。
- 最终完整 Smoke 45 次 HTTP（轮询次数随任务完成速度变化）、Backend/AI/Milvus 重启后 6 次持久化复验通过；Hard Filter 三分支、幂等/强制重算、Outbox 3 次失败进入 DEAD 与恢复均已验证。
- 内置浏览器触发真实“重新评估”并显示最新 BGE-M3 Match；响应式页面无横向溢出，控制台无 warning/error。

## 2026-09-01 — Phase 2 Job Center 实现与验收

### Added

- Flyway V2 新增 10 张 Job Center 表，落地 Company、Job、多来源、技能别名、解析运行、导入任务/行错误、去重和人工修订历史。
- 增加手工、用户指定 URL、CSV、XLSX 和受限 Extension Capture 契约；URL 导入具有 SSRF/重定向/类型/大小防护，文件导入具有幂等和逐行错误。
- 增加 FastAPI + LangGraph Job Parser、严格 Pydantic Schema、版本化 Prompt、规则解析与 OpenAI-Compatible Provider 抽象；本机未配置 LLM Secret，验收结果明确为 `RULES_ONLY`。
- 增加 Job Center UI 和真实 Dashboard 岗位统计；Phase 3 匹配区域只显示边界说明，不伪造分数。

### Changed

- AI Service 使用 `8010`，因为本机 `8000` 已由用户工作区外的无关 Python 服务占用；未终止或修改该服务。
- Phase 0 API 文档的通用 `sort=-publishAt` 表达在 Phase 2 实现中收敛为白名单 `publish_desc/updated_desc`，便于游标值稳定编码；文档已同步。
- V2 首次真实迁移发现 MySQL 8.4 将 `row_number` 视为保留词。确认失败迁移产生的 Phase 2 表均为空后，只清理这些空表和失败历史，改用 `source_row_number`；Phase 1 用户/Profile/Resume 数据全程保留。
- Company 规范化改为最长后缀优先，避免无序集合先剥离“公司”后残留“有限”并破坏规则去重。
- Java 内部 AI 调用强制 HTTP/1.1，避免默认 h2c Upgrade 与 Uvicorn 不兼容；失败解析历史被保留并通过新解析运行成功恢复。

### Validation

- Backend 45 项测试、AI Service 19 项测试及 Ruff、Frontend Build、Docker MySQL/Redis 健康均通过。
- Phase 2 完整 HTTP Smoke 26 次、Backend 重启后持久化复验 6 次通过；MySQL V2、Redis 登录元数据、URL/CSV/XLSX、行错误、精确/规则去重、多来源合并、人工字段保护和真实 Dashboard 均已验证。
- 内置浏览器完成登录、Dashboard、Job Center、城市+技能筛选、详情、URL/CSV/XLSX/手工入口验证，控制台无 warning/error。

## 2026-09-01 — Phase 1 范围校准

### Changed

- Frontend 从 Phase 0 的 TypeScript 方案调整为 JavaScript。原因：Phase 1 明确要求所有前端业务源码禁止 TypeScript。
- MySQL Phase 1 物理表从概念单数名调整为复数名，例如 `app_user → users`、`candidate_profile → candidate_profiles`。原因：Phase 1 明确给出复数迁移表名；复数 `users` 同样规避保留词冲突。
- 保留 `/api/v1` 作为长期版本化路径，同时为 Phase 1 提供需求指定的 `/api/...` 兼容映射。两套路由共用同一 Controller/Service，不复制业务逻辑。
- Phase 1 Resume 只实现结构化 Resume、不可变 Version 和 Section，不实现 PDF/DOCX 上传、渲染或 AI Tailor。
- AI Service 只初始化基础边界，不实现 CandidateAgent、Embedding、LLM 或 Prompt 执行。这些能力不属于本次明确的 Phase 1 核心链路。

### Security

- 登录响应按需求返回 Access/Refresh Token；Refresh Token 服务端只保存哈希/摘要，并同时使用 MySQL 轮换记录和 Redis TTL 元数据。Frontend 使用会话级存储，不把长期凭据写入源码。

## 2026-09-01 — Phase 1 实现与验收

### Added

- 初始化 Spring Boot 模块化单体、Vue 3 JavaScript 前端、FastAPI 边界目录、Docker Compose 和部署说明。
- Flyway V1 建立 12 张业务表；实现 Auth、Candidate、Resume、Dashboard、Audit、Trace ID、Swagger 和健康检查。
- 增加 32 项后端测试及可重复的 `scripts/phase1-smoke.ps1`，覆盖登录/刷新、Candidate CRUD、Resume 不可变版本、Dashboard 和重启持久化。

### Validation

- Backend `clean test`、`package`，Frontend `build`，真实 MySQL 迁移/重启持久化、Redis 协议连接和浏览器前后端联动均已通过。
- Docker Desktop 修复前曾使用官方 MySQL Windows 包和 Redis 兼容的 Memurai 临时验证业务链路；该结果没有冒充 Compose 验收，相关状态保留在 Git 忽略的本地运行目录中。
- Docker Desktop 修复后，默认 Compose 的 MySQL 8.4/Redis 7.4 均达到 `healthy`。在全新容器卷上完成 Flyway V1、50 次完整 HTTP Smoke，并在重启两个容器和 Backend 后完成 10 次持久化复验。
- 使用 Vue 开发服务器连接容器数据进行浏览器登录、Dashboard、Candidate Profile 和 Resume Center 验证，控制台无 warning/error。Phase 1 Gate 从 `BLOCKED` 更新为 `PASS`，Phase 2 尚未开始。
## 2026-09-02 — Phase 7 controlled browser Assist foundation

- Added a Manifest V3 TypeScript extension with only `activeTab`, `scripting`, `storage`, `sidePanel` and localhost Backend host access.
- Added one-time pairing codes, device-bound Extension JWT, rotating hashed Refresh Tokens, device revocation and Web device management.
- Added Fixture, generic visible and manual-selection adapters plus an editable extension Action Popup for Capture, Match, Draft and Assist Queue workflows.
- Added Flyway V7 with 5 extension/automation tables and immutable task-step audit records.
- Added a default-off local Playwright Worker with strict localhost/selector allowlists, service authentication and task/queue/policy/expiry-bound HMAC tokens.
- Verified normal Fixture Fill without Submit, CAPTCHA blocking, HTTP idempotency, token replay rejection, revocation and MySQL restart persistence.
- Resolved the roadmap/recommendation tension by limiting Phase 7B to a repository-owned Fixture; real-platform adapters remain disabled pending per-platform policy review.
- Switched unpacked Extension E2E to the installed Microsoft Edge because the bundled Playwright Chromium had a broken Windows side-by-side runtime.
- Replaced unreliable Edge Side Panel launch behavior with the native Manifest Action popup, retaining `activeTab` least privilege and the same review workflow.
- Passed the real Edge flow: pair, visible capture, MySQL save, 77.59/B match, non-sent Draft, unapproved Assist Queue and device revocation; no Application or Automation Task was created.
- Added revoked-device 401 session clearing and regression coverage; final extension version is 0.1.2 with 13 passing tests.
