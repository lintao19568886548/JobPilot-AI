# JobPilot AI 开发 Roadmap

> 原则：按 Phase 顺序推进；上一 Phase 的 Build、Test、Run 和验收未通过，不进入下一 Phase。  
> 当前状态：Phase 0–13 已完成。产品闭环、本地安全发布和基于真实事实的首次使用引导均已通过真实验收。AI 草稿不发送，外部投递仍需用户显式确认。

## 1. 交付策略

### 1.1 里程碑

| 里程碑 | 覆盖 Phase | 可用结果 |
|---|---:|---|
| Foundation | 0–1 | 可登录，可维护真实候选人画像和多版本简历 |
| Match MVP | 2–3 | 岗位可导入、解析、去重，并得到可解释的 S/A/B/C/D 评分 |
| Daily-use MVP | 4–6 | 推荐、Dashboard、投递队列、CRM、针对性简历和沟通草稿形成闭环 |
| Browser & Interview | 7–8 | 扩展采集/辅助流程、面试预测和复盘可日常使用 |
| Decision & Learning | 9–10 | Offer/分析、保守的反馈学习和合规自动任务 |
| Operations & Release | 11–12 | 可观测、备份恢复证据，以及可复现的本地安全发布 |
| Daily-use Quality | 13 | 首次使用检查、确定性数据质量和前端加载预算 |

### 1.2 每个 Phase 的 Definition of Done

一个 Phase 只有同时满足以下条件才允许在 `TASKS.md` 标记完成：

1. 范围内接口、页面或 Worker 有真实实现，无无效按钮。
2. Demo/测试数据显式带 `DEMO`，不冒充真实平台或 AI 结果。
3. 数据库迁移可从空库重复执行，升级路径已验证。
4. Build 通过：编译、静态检查、类型检查、格式检查均通过。
5. Test 通过：对应单元、Service、Repository、API/契约、组件和 E2E 测试通过。
6. Run 通过：依赖健康、服务启动、健康检查和本 Phase 核心 Smoke Flow 通过。
7. 安全检查通过：日志和响应不泄漏密码、Token、Cookie、API Key、简历敏感全文。
8. 文档、OpenAPI、环境变量示例和任务状态同步更新。
9. 发现的阻断 Bug 已修复并有回归测试；不带 Bug 进入下一阶段。

### 1.3 统一验证命令基线

各 Phase 使用仓库 Wrapper/锁文件执行以下命令，避免开发机 PATH 差异：

```powershell
# Backend build + test
Push-Location backend
.\mvnw.cmd clean test
.\mvnw.cmd package
Pop-Location

# Frontend（业务源码为 JavaScript，不执行 TypeScript typecheck）
npm --prefix frontend run build

# AI Service
.\ai-service\.venv\Scripts\python.exe -m pytest -q .\ai-service
.\ai-service\.venv\Scripts\python.exe -m ruff check .\ai-service

# Runtime
docker compose --env-file .env config --quiet
docker compose --env-file .env up -d mysql redis

# Phase 1 HTTP + persistence smoke
powershell -ExecutionPolicy Bypass -File scripts\phase1-smoke.ps1

# Phase 2 HTTP + persistence smoke
powershell -ExecutionPolicy Bypass -File scripts\phase2-smoke.ps1 -ForceFull
powershell -ExecutionPolicy Bypass -File scripts\phase2-smoke.ps1 -VerifyPersistence
```

`run` 验证必须检查服务健康和真实 API/数据库流转，而不只是“进程没有退出”。Phase 1 的 `scripts/phase1-smoke.ps1` 会执行认证、Candidate、Resume、Dashboard 和重启后持久化验证，且不输出 Token 或密码。

## 2. Phase 0 — 扫描与设计（已完成）

### 范围

- 扫描目标目录和本机工具链。
- 记录已有技术栈、已有/缺失功能、复用与重构结论。
- 确定系统边界、领域模块、数据存储、AI Workflow、安全与合规原则。
- 设计数据库、REST API、开发路线图和 P0–P3 任务清单。

### 交付

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`

### Phase Gate

- Build：本 Phase 无运行时代码，改为验证 5 个 Markdown 交付物均存在、非空、UTF-8 可读。
- Test：检查必需章节、必需表/模块/API、Phase 顺序和文档交叉引用。
- Run：本 Phase 无可启动服务；从 Phase 1 起所有 Phase 强制执行服务启动和 Smoke Test。这里明确记为 `NOT_APPLICABLE_NO_RUNTIME`，不以假服务冒充运行验证。

## 3. Phase 1 — Identity、Candidate Profile、Resume Center

### 目标

建立可信候选人事实源：用户可以安全登录，编辑并持久化 Candidate Profile，维护结构化 Master Resume 与不可变 Resume Version。

### Backend

- 初始化 Spring Boot 模块化单体、Flyway、MyBatis-Plus、统一响应/异常、Trace ID、结构化日志。
- 实现环境变量 Bootstrap 用户、登录、Refresh Token 轮换、注销和会话撤销。
- 实现 Candidate Profile、Education、Experience、Project、Skill、Preference。
- 实现 Resume、Resume Version、Resume Section、唯一活动 Master 和唯一 Default 约束。
- 建立审计日志和日志脱敏。

### AI Service

- 只初始化 FastAPI 基础目录、依赖清单与健康端点契约，不在本 Phase 实现 Agent 或调用 LLM。
- CandidateAgent、简历事实抽取和 Prompt 执行推迟到后续 AI Phase；Phase 1 通过用户编辑的结构化数据建立真实事实源。

### Frontend

- 初始化 Vue 3/JavaScript 应用、路由、Pinia、API 客户端和基础 Layout。
- 实现登录、真实数据 Dashboard、Candidate Profile 分区编辑、技能熟练度、Resume 列表和版本详情。
- 所有表单有 Validation、加载、空状态和可恢复错误状态。

### 数据

Flyway V1 创建 `users`、`refresh_tokens`、`audit_logs`、Candidate、Skill 和 Resume 共 12 张业务表；密钥不落明文配置表。

### 验收场景

1. 空库迁移 → 环境变量创建本地用户 → 登录 → 刷新 Token并验证旧 Token 失效。
2. 读取/更新 Profile → 保存技能、教育、经历和项目 → Dashboard 汇总真实数据。
3. 创建 Master Resume → 创建 v1/v2 → 查看历史 → 切换 Default/Master；旧版本保持不可变。
4. 越权资源、非法字段和未知 CORS Origin 被安全拒绝，审计日志可追溯。
5. Backend 重启后再次读取全部核心数据，证明 MySQL 持久化成立。

## 4. Phase 2 — Job Center、导入与 JD Parser

### 目标

通过手工、URL、CSV/Excel 和扩展契约保存岗位，形成统一 Job Schema 和技能要求；不做大规模爬虫。

### 实现

- Job、Company、Job Source、Job Skill、Import Task、Dedup Log 迁移与 API。
- 手工录入、URL 导入（仅用户指定且允许访问）、CSV/Excel 导入。
- `JobParserAgent`：职责/要求、Must-have/Nice-to-have、薪资、学历、经验、业务域结构化。
- Skill Ontology、Alias 和标准化流程。
- 精确 + 规则去重；语义去重接口先准备，在 Phase 3 接 Milvus。
- 岗位列表、三栏详情页的 Job Information/JD 部分；AI 栏明确显示“未评估”。

### 验收场景

1. 同一平台 Job ID 重复导入只产生一个来源记录或幂等返回。
2. 同公司/岗位/城市的不同来源合并成一个 Job 并保留多个 Job Source。
3. 解析失败可查看安全错误并重试；原始输入 Hash 与版本可追溯。
4. JD 中技能别名正确映射，不把 Nice-to-have 错标成 Must-have。

## 5. Phase 3 — Matching Engine

### 目标

跑通 `Job → Hard Filter → Skill → Embedding → LLM → Final Score → Explanation`。

### 实现

- 版本化 Match Config、Hard Filter Rule 和规则解释器。
- Skill Match 与项目证据匹配。
- BGE-M3 Embedding Provider、缓存、Milvus Collection 与索引迁移/初始化。
- LangGraph `MatchingAgent` 完整工作流。
- LLM Provider：OpenAI Compatible 基线，支持 Base URL/Key/Model 环境变量；Qwen、DeepSeek、OpenAI 通过兼容配置接入。
- 结构化 LLM 输出、范围校验、超时、重试、限流、成本记录。
- 最终评分、等级、优势/缺口/风险和推荐简历。
- Outbox 与异步 Match Task。

### 验收场景

1. 应届生遇到“3 年以上”规则按配置 Reject/降级，解释可见。
2. Provider 不可用时任务失败可重试，既不伪造分数也不破坏 Job。
3. 同输入/模型/算法/配置重放命中缓存；输入或版本变化产生新 Match。
4. 权重之和、分数范围、S/A/B/C/D 边界和惩罚计算有参数化测试。
5. Resume 无 Kubernetes 事实时只能输出 Gap，不能把它加为优势。

## 6. Phase 4 — Recommendation Center 与 Dashboard

### 目标

让用户每天能快速判断“今天先投什么、为什么”。

### 实现

- 推荐列表：全部、S/A/B、未评估、忽略、收藏；完整筛选和排序。“已投”依赖 Phase 5 Application 事实，本 Phase 只显示禁用边界。
- 岗位详情三栏 AI Analysis：分数、等级、技能证据、优势、缺口、风险、简历与建议。
- Dashboard KPI、今日推荐、推荐漏斗和来源分布；最近面试与投递待办在事实源落地前返回明确不可用状态。
- Global Search 第一版（公司、岗位、技能、状态）。
- 忽略/收藏反馈记录。

### 验收场景

- KPI 与源表核对一致；刷新/筛选不重复计数。
- 分数旁始终有证据和配置版本；未评估/失败不显示伪分数。
- 空数据、少数据、Provider 失败时页面仍可操作且状态清晰。

## 7. Phase 5 — Application Queue 与 CRM

### 目标

形成从推荐到用户确认、投递记录、状态时间线和 Dashboard 更新的可审计闭环。

### 实现

- Queue 状态机、优先级、简历/话术选择、批量入队与幂等。
- Application CRM 状态机和不可变 Application Log。
- Manual 与 Assist 模式；Authorized Automation 只建立策略/授权边界，不在本 Phase 自动提交。
- Recruiter 基础档案和人工沟通记录。
- 投递、回复、面试等事件驱动 Analytics Daily 更新/重算。

### 验收场景

- 重复点击入队/确认不产生重复 Application。
- 所有合法/非法状态迁移有参数化测试。
- Assist 到最终确认前绝不产生外部提交成功记录。
- 修改历史状态需要追加纠正事件，不能覆盖日志。

## 8. Phase 6 — Resume Tailor 与 Communication Agent

状态：已完成并通过 Build、Test、Run、空库迁移、持久化、HTTP 与浏览器 E2E Gate。

### 目标

基于真实事实为岗位生成可审查的简历版本和真人化沟通草稿，完成 Daily-use MVP。

### 实现

- Evidence Ledger、针对性重排/改写/压缩和 ATS 关键词优化。
- 逐项 Diff、事实引用、真实性检查、人工批准后生成 Resume Version。
- BOSS、猎聘、邮件、微信、面试感谢/跟进、Offer 沟通草稿。
- 默认 BOSS 草稿 60–100 字，可配置；AI 只创建 Draft。
- Resume Version 使用/投递/回复/面试/Offer 指标。

### 验收场景

- 未提供的公司、职责、技术、数字、学历、证书无法进入最终版本。
- 每项变更能追溯到 Master Resume/Candidate 事实。
- 话术不复制 JD、不夸大、不自动发送。
- MVP 全链路 E2E：简历 → Profile → Job → Match → Tailor/Greeting → Queue → Confirm → CRM → Dashboard。

## 9. Phase 7 — Chrome Extension 与 Assist Automation

### 目标

在用户当前打开的岗位页中完成保存、匹配、入队和草稿查看；辅助填写默认停在最终确认前。

### 实现

- MV3 工程、受限扩展配对、最小权限和站点 Adapter。
- 侧栏显示岗位摘要、Match、优势/缺口/风险、推荐简历和 Greeting。
- 保存岗位、触发匹配、加入队列、生成草稿、打开 JobPilot。
- 先实现稳定的企业官网/测试夹具 Adapter，再逐平台验证 DOM 解析器。
- Playwright Worker 的 `prepare` 流程、政策检查、授权短 Token、步骤审计和 BLOCKED 人工接管。

### 验收场景

- 扩展不读取/上传 Cookie、Token、隐藏字段；域名权限按需申请。
- 页面结构未知时只允许手工选择文本或保存原文，不猜测并提交。
- 测试证明 Fill 完成后不会触发 Submit；验证码时立即阻断。
- 每个平台上线前记录政策复核日期和允许动作。

## 10. Phase 8 — Interview Center、Agent 与 Review

### 目标

从投递上下文生成准备材料，记录真实问题，复盘并形成可执行知识薄弱点。

### 实现

- 面试日程、轮次、链接/地点、状态、结果和提醒。
- 基于 JD、Resume、Company、历史面试预测问题、答案框架、追问和风险。
- 录入实际问题/回答，生成复盘、表达问题、项目漏洞和下一步复习。
- Knowledge Gap 聚合与趋势。

### 验收场景

- 预测问题与实际问题来源清楚区分。
- AI 建议不是“用户真实回答”，用户确认后才更新知识薄弱点。
- 时区、重复提醒、取消/改期场景有测试。

## 11. Phase 9 — Offer Center 与完整 Analytics

### 目标

安全记录并比较 Offer，用真实事件分析平台、岗位、公司、城市、薪资、Resume 和 MatchScore 的转化。

### 实现

- Offer 字段、截止提醒、可配置比较权重和解释。
- 完整漏斗与各维度转化；保存分子、分母、样本量。
- Resume Version 和 MatchScore Bucket 效果分析。
- 数据导出与隐私删除流程。

### 验收场景

- 金额、薪资月份、试用期比例和时区计算准确。
- 小样本显示不足，不输出伪趋势。
- Analytics 可从源事件全量重算并得到同一结果。

## 12. Phase 10 — Learning to Rank 与 Automation Center

### 目标

使用实际反馈给出保守、可回滚的排序改进，并自动执行安全的整理/提醒任务。

### 实现

- Feedback 特征快照、训练数据质量检查和模型/参数版本。
- 样本少时只生成分析建议；达到阈值后做离线评估和 Shadow Ranking。
- 用户批准后才激活新权重/LTR 版本，一键回滚。
- 每日岗位整理、重评分、重复清理建议、高匹配提醒、跟进/面试/Offer 提醒。
- 不实现违反平台规则的后台刷页和未授权投递。

### 验收场景

- 训练/验证时间切分，避免使用未来结果泄漏。
- 新模型离线指标和 Shadow 结果不达标时不能激活。
- 自动任务幂等、可暂停、可重试、可审计；通知去重。
- Platform Policy 过期或授权撤销后，外部动作自动降级为 Manual。

## 13. 主要风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| 招聘页面频繁变更 | Extension Adapter 失效 | Adapter 版本化、DOM Fixture 测试、失败转手工采集 |
| 平台政策变化 | 自动化不合规 | Policy Registry、默认 Manual、复核时间、授权门禁 |
| LLM 幻觉 | 简历不真实、评分误导 | Evidence Ledger、严格 Schema、规则复核、人工确认 |
| 个人数据泄露 | 高影响隐私事件 | 本地优先、字段最小化、加密、脱敏、无敏感日志 |
| 过早微服务化 | 开发/运维失控 | Java 模块化单体，只有 AI/Automation 因技术与风险边界独立 |
| 样本过少却学习排序 | 权重越调越差 | 样本阈值、置信提示、Shadow、人工激活、可回滚 |
| 本机依赖版本差异 | 构建不可重复 | Maven Wrapper、Python Lock、npm Lock、Docker Pin、仓库验证脚本 |

## 14. 下一步

Phase 14 Account Security & Workspace Settings 已实现：当前用户密码变更、Web Session 列表/撤销、JWT Auth Version、Redis Session Family Denylist、账户资料和三项白名单工作区设置均已真实持久化并接入 UI。Extension 配对保持独立可撤销，不因密码变更被静默删除，也不能绕过 Auth Version。公网/局域网部署、TLS、多用户托管或真实平台自动化必须作为新的独立安全评审范围；不得扩展为后台刷招聘页、外部消息、自动接受/拒绝 Offer 或未授权投递。

## Phase 7 implementation status (2026-09-02)

The MV3 Extension, device authentication, current-page adapters, native Action Popup, default-off local Assist Worker, V7 migration and real persistence/HTTP/Fixture tests are implemented. Microsoft Edge 152 unpacked-extension E2E and the user-approved production workflow passed. Real recruitment-platform automation remains explicitly outside the approved policy boundary. Phase 8 may start only after an explicit user request.

## Phase 8 implementation status (2026-09-02)

Interview Center, multi-round scheduling, source-labelled questions, append-only Answer Notes, immutable Review versions, human-confirmed Knowledge Gaps, in-app Reminders, Dashboard facts, V8 migration and rules-only LangGraph Interview Agent are implemented. Real MySQL upgrade/clean-room migration, Backend restart persistence, HTTP smoke and Microsoft Edge browser E2E passed with zero console errors. External messages, meeting invitations and automatic Application updates remain zero. Phase 9 may start only after an explicit user request.

## Phase 10 implementation status (2026-09-02)

不可变 Feedback、历史 Feature Snapshot、30 条最小样本门禁、时间 80/20 切分、NDCG@10、Model Version、Shadow、人工激活/回滚，以及七类本地安全 Automation Handler 已实现。V9→V10 与 V1→V10 真实 MySQL 迁移、Backend 重启持久化、48-call HTTP smoke 和 Microsoft Edge 页面/控制台验收均通过。Scheduler 默认关闭；数据库约束保证外部消息、外部提交和外部变更均为 0，重复岗位只产生建议。

## Phase 12 implementation status (2026-09-03)

`0.12.0` 本地发布拓扑、三个非 Root 应用镜像、发布前一致备份、可校验发布包和只读回滚预检已实现。417 项 Backend、52 项 AI、13 项 Extension、6 项 Worker 测试以及 Frontend Build 通过；20 次 HTTP、应用容器重启持久化、5 个安全响应头、真实 Operations 桌面/390px 页面与零控制台错误验收通过。未新增 API 或数据库迁移，Flyway 仍为 11/82 tables。

## Phase 13 implementation status (2026-09-03)

Setup Center、九项实时 Readiness、确定性 Data Quality API、Dashboard 联动和前端依赖分包已实现。所有检查来自当前用户的 MySQL 事实，缺失信息不会自动生成或写回。Phase 13 不新增数据库迁移，发布镜像版本为 `0.13.0`。

Backend 424、AI 52、Extension 13、Worker 6 共 495 项测试通过；Frontend Build 最大 JavaScript Chunk 为 333.81 KiB。`0.13.0` 三个应用镜像健康运行，Phase 13 18 次 HTTP 与 Phase 12 安全回归 10 次 HTTP 均通过；重启前后 Readiness 34、3/9 完成和 8 个问题 Code 保持一致。Setup Center 桌面/390px 与 Dashboard 390px 无横向溢出，console error/warning 为 0，五类禁止外部动作均为 0。

## Phase 14 implementation status (2026-09-03)

Account Security 与 Workspace Settings 已实现。V12 将账户时区/语言、密码变更时间、Auth Version 和 Web Session Metadata 纳入真实数据库，并新增 `system_settings`；Redis Denylist 让已撤销 Session 的 Access Token 立即失效。Settings 页面、紧凑模式、默认落地页和 Dashboard 引导开关均使用真实 API。

Backend 440、AI 52、Extension 13、Worker 6 共 511 项测试通过；V11→V12 与 V1→V12 迁移、83 表、Redis、密码恢复、应用重启持久化、38 次 HTTP、桌面/390px Settings UI 和 0 console error/warning 均通过。`0.14.0` 三个应用镜像以非 Root、只读文件系统和最小权限健康运行；外部消息、投递和平台变更仍为 0。
