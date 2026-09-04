# JobPilot AI 任务清单

> 优先级含义：`P0` 阻断核心日用闭环；`P1` 重要扩展；`P2` 优化与学习；`P3` 远期候选。  
> 规则：只有代码、迁移、测试、运行验证和文档都完成，任务才能从 `* [ ]` 改为 `* [x]`。

## P0 — 核心闭环与安全基础

### Phase 0：扫描与设计

* [x] 扫描 `D:\JobPilot AI`，确认是全新目录
* [x] 盘点 Java、Maven、Node、Python、Docker 和 Git 工具链
* [x] 记录现有技术栈、现有/缺失功能、可复用与重构结论
* [x] 完成系统上下文、领域模块、核心流程与部署架构
* [x] 完成数据库实体、约束、索引、版本与数据生命周期设计
* [x] 完成 REST、内部 AI、Extension 和 Automation API 契约设计
* [x] 完成 Phase 0–10 开发 Roadmap、验收场景和质量门禁
* [x] 建立 P0/P1/P2/P3 可维护任务清单
* [x] 执行 Phase 0 文档完整性与一致性验证

### Phase 1：工程与身份基础

* [x] 初始化 Git、`.gitignore`、`.editorconfig` 和根 README
* [x] 初始化 Java 21 Spring Boot 3.x 模块化单体与 Maven Wrapper
* [x] 初始化 FastAPI 基础目录（不实现 Agent）
* [x] 初始化 Vue 3 + JavaScript + Vite 前端与依赖锁
* [x] 建立 Docker Compose 的 MySQL、Redis、健康检查和可选 Milvus `ai` Profile
* [x] 实际执行默认 Docker Compose，MySQL 8.4 与 Redis 7.4 容器均通过健康检查
* [x] 实现统一响应、异常处理、Validation、Trace ID 和结构化日志
* [x] 配置 Flyway、MyBatis-Plus、逻辑删除和乐观锁
* [x] 实现只读取环境变量的本地 Bootstrap 用户
* [x] 实现 BCrypt、登录、Access JWT、Refresh Token 轮换、注销和会话撤销
* [x] 实现精确 CORS、认证限流和安全响应头
* [x] 实现审计日志且验证敏感信息不进入 API 响应
* [x] 完成 Auth、JWT、异常和 API 集成测试

### Phase 1：Candidate Profile

* [x] 创建 Candidate Profile、Education、Experience、Project 数据迁移
* [x] 创建 Skill 和 Candidate Skill 数据迁移、分类索引与约束
* [x] 实现当前登录用户的 Candidate Profile GET/PUT/PATCH、完整度和乐观锁
* [x] 实现教育、经历、项目 CRUD、资源归属与后端字段校验
* [x] 实现技能检索/分类、熟练度、年限、来源和核心技能 CRUD
* [x] 实现目标岗位、城市、行业、公司类型、薪资、远程和搬迁偏好
* [x] 为后续 CandidateAgent 保留独立 AI Service 边界，本 Phase 不调用 LLM
* [x] 实现 Candidate Profile 分区前端、技能标签和真实数据状态
* [x] 完成 Candidate Profile、完整度、Education/Experience/Project/Skill 与 ownership 测试

### Phase 1：Resume Center

* [x] 创建 Resume、Resume Version 和 Resume Section 数据迁移
* [x] 实现唯一活动 Master Resume 和唯一 Default Resume 规则
* [x] 实现 Resume CRUD、设置 Master/Default 和逻辑删除
* [x] 实现不可变版本历史、递增版本号、内容 Hash 和来源追溯
* [x] 实现三栏 Resume Center、版本详情和结构化 Section 展示
* [x] 完成结构化内容、不可变版本和资源归属测试
* [x] 执行 Backend/Frontend Build、32 项后端测试、78 次 HTTP 验证和重启持久化 Smoke
* [x] Phase 1 Gate：Docker 空库迁移、50 次完整 HTTP Smoke、容器/Backend 重启、10 次持久化复验和浏览器前后端联动全部通过

### Phase 2：Job Center 与解析

* [x] 创建 Company、Job、Job Source、Job Skill、Import Task 和 Dedup Log 迁移
* [x] 定义并实现统一 Job Schema
* [x] 实现手工岗位录入与用户指定 URL 导入
* [x] 实现 CSV/Excel 导入、行级错误和幂等
* [x] 实现 Extension Job Capture 契约但暂不开发扩展 UI
* [x] 实现 JobParserAgent、严格 JSON 和 Parser 版本
* [x] 实现薪资、学历、经验、毕业年份、职责和要求解析
* [x] 实现 Must-have / Nice-to-have 技能区分
* [x] 建立 Spring Boot/SpringBoot、MQ、LLM、RAG 等技能别名基线
* [x] 实现平台 ID、URL 和规则指纹去重
* [x] 实现来源合并、冲突记录和人工修订审计
* [x] 实现岗位分页、排序、完整筛选和详情 API
* [x] 实现岗位列表与详情左/中栏；未匹配状态明确展示
* [x] 完成 Parser、Skill Normalization、Import 和 Dedup 测试
* [x] 执行 Phase 2 Build、Test、Run 和端到端 Smoke Test

Phase 2 验收基线：Parser 为明确的 `RULES_ONLY`（未配置 LLM，不伪造调用）；完整 HTTP Smoke 26 次，Backend 重启持久化复验 6 次；浏览器登录、Dashboard、Job Center、筛选与三种导入入口通过且控制台无 warning/error。

### Phase 3：Matching Engine

* [x] 创建 Match Config、Hard Filter Rule、Job Match/Detail/Embedding 迁移
* [x] 实现白名单 Hard Filter DSL 和规则优先级
* [x] 实现毕业年份、学历、城市、经验、岗位类型、薪资和黑名单规则
* [x] 实现 `PASS/DOWNGRADE/REJECT`、惩罚和可解释证据
* [x] 实现技能精确/本体关系匹配与项目证据评分
* [x] 实现 Embedding Provider 接口和 BGE-M3 Provider
* [x] 增加 Milvus Compose、Collection、索引和健康检查
* [x] 实现 Job/Resume/Project/Skill Embedding 与内容 Hash 缓存
* [x] 实现 JD ↔ Resume/Project/Skill/Profile 相似度
* [x] 实现 OpenAI Compatible LLM Provider 和环境变量配置
* [x] 实现 LLM 超时、并发限制、重试、熔断、Token 与成本记录
* [x] 实现 MatchingAgent LangGraph 完整工作流
* [x] 实现 LLM 严格输出、额外字段拒绝和 0–100 范围校验
* [x] 实现可配置权重、等级阈值、算法版本和结果快照
* [x] 实现优势、缺口、风险、推荐理由和推荐简历证据
* [x] 实现 Transactional Outbox、异步任务、幂等和失败重试
* [x] 完成 Hard Filter、Skill、Embedding、LLM、Score 边界与缓存测试
* [x] 完成 Prompt Injection 输入隔离与 Resume 不虚构测试
* [x] 执行 Phase 3 Build、Test、Run 和 Job→Score 真实链路验证

Phase 3 验收基线：Flyway V3 新增 9 张表；后端 66 项、AI Service 32 项测试；最终完整 Smoke 45 次 HTTP（轮询次数随任务完成速度变化）、重启持久化 6 次 HTTP；真实 `BAAI/bge-m3` 1024 维推理、Milvus 缓存/重启、PASS/DOWNGRADE/REJECT、LLM 未配置跳过、Outbox 三次失败进 DEAD 后原任务恢复、浏览器页面重评估与零控制台错误均通过。

### Phase 4：Recommendation 与 Dashboard

* [x] 创建 Job Recommendation、Recommendation Event/Refresh 与 Analytics Daily 迁移
* [x] 实现全部/S/A/B/未评估/忽略/收藏视图；“已投”禁用并明确属于 Phase 5
* [x] 实现匹配度、发布时间、薪资、公司、城市和 AI 推荐排序
* [x] 实现岗位详情三栏 AI Analysis 和证据展示
* [x] 实现收藏、取消收藏、忽略、恢复和不可变反馈事件
* [x] 实现 Dashboard 顶部 KPI 与今日真实推荐岗位
* [x] 实现推荐漏斗与来源分布；最近面试和待办明确标记为后续 Phase 不可用
* [x] 实现公司、岗位、候选人可见技能和状态 Global Search 第一版
* [x] 验证 Dashboard 聚合与源数据一致、投影唯一且无重复计数
* [x] 执行 Phase 4 Build、Test、Run、HTTP Smoke、重启持久化和浏览器 UI E2E

Phase 4 验收基线：Flyway V4 新增 5 张表，总表数 37；Backend 91 项、AI Service 32 项测试；完整 HTTP Smoke 52 次、Backend 重启后 7 次持久化复验；7 个唯一 Recommendation、12 条不可变事件、19 条 Analytics Daily 聚合、Redis 与 Milvus（单集合 18 个持久化向量）回归、浏览器真实登录/推荐视图/搜索/Candidate/Resume 且控制台零错误均通过。

### Phase 5：Application Queue 与 CRM

* [x] 创建 Application Queue、Application 和 Application Log 迁移
* [x] 实现 Queue 全状态机、优先级和唯一活动项
* [x] 实现单个/批量入队、移除、Resume 选择和 Greeting 关联
* [x] 实现入队与确认 Idempotency-Key
* [x] 实现 CRM 状态机和所有合法迁移
* [x] 拒绝并测试所有非法状态迁移
* [x] 每次状态变化同事务追加不可变 Application Log
* [x] 实现 Manual 与 Assist 模式边界
* [x] 实现 Platform Policy 基础门禁，未知平台默认 Manual
* [x] 实现投递列表、详情 Timeline、状态操作和安全错误反馈
* [x] 实现 Recruiter 基础档案和人工沟通记录
* [x] 由 Application 事件更新/重算 Dashboard Analytics
* [x] 执行 Phase 5 Build、Test、Run 和 Queue→CRM E2E

Phase 5 验收基线：Flyway V5 新增 6 张表并扩展 `analytics_daily`，总表数 43；Backend 321 项、AI Service 32 项测试；完整 HTTP Smoke 43 次、Backend 重启后 7 次持久化复验；Queue 单个/批量幂等、唯一活动项、Assist Prepare 零提交、显式确认、全部状态对测试、非法迁移零写入、阶段到达唯一计数、4 段不可变 Timeline、Recruiter Interaction、Ownership、Analytics 对账和浏览器零控制台错误均通过。

### Phase 6：Resume Tailor 与 Communication

* [x] 实现 Candidate Evidence Ledger
* [x] 创建 Prompt Template、复用并扩展 AI Call Log、增加 Communication Draft 迁移
* [x] 实现 ResumeAgent 的选择、排序、改写、压缩和 ATS 优化契约
* [x] 每项 Tailor 修改输出 Before/After/Reason/Evidence Refs
* [x] 拒绝任何无候选人证据的经历、技术、数据、学历或证书
* [x] 实现 Tailor 人工批准后创建不可变 Resume Version
* [x] 实现 CommunicationAgent 和 BOSS 60–100 字约束
* [x] 实现猎聘、邮件、微信、感谢、跟进和 Offer 沟通草稿
* [x] 保证 AI 只能生成 Draft，不能发送消息
* [x] 实现 Resume Version 使用与转化指标
* [x] 完成真实性、Prompt 版本、草稿长度和不自动发送测试
* [x] 执行 Phase 6 Build、Test、Run 和完整 Daily-use MVP E2E

Phase 6 验收基线：Flyway V6 新增 6 张表并扩展 `resume_versions` 与既有 `ai_call_logs`，总表数 49；Backend 342 项、AI Service 43 项测试；完整 HTTP Smoke 34 次、Backend 重启后 8 次持久化复验；15 条 Evidence、2 项带引用 Diff、Master Hash 不变、Tailor/Draft 幂等、BOSS 100 字、零外部发送、Queue/Application/Reply 指标各 1、Ownership、Redis、Milvus 18 个向量回归和浏览器零控制台错误均通过。

## P1 — 浏览器辅助、面试与决策分析

### Phase 7：Chrome Extension

* [x] 初始化 Manifest V3 + TypeScript 工程和最小权限 Manifest
* [x] 实现一次性配对码、受限 Scope 和设备撤销
* [x] 实现 Adapter 接口、DOM Fixture 和未知页面安全降级
* [x] 实现手工选择文本/通用页面 Capture
* [!] 真实平台专用解析器：没有平台通过独立政策复核，按安全设计保持禁用；当前使用 GENERIC_VISIBLE/人工选择
* [x] 实现扩展 Action Popup 岗位摘要、Match、优势、缺口和风险（替代 Edge 中不可靠的 Side Panel 启动）
* [x] 实现保存、AI 分析、入队、生成话术和打开 JobPilot
* [x] 验证扩展不读取/上传 Cookie、Token 或隐藏页面数据
* [x] 完成 Manifest 权限、三类 Adapter、隐藏字段和 API Payload 契约测试

### Phase 7：Playwright Assist

* [x] 初始化独立 Automation Worker，默认关闭
* [x] 实现短时单任务授权、Policy Mode 和 Queue Approval 绑定校验
* [x] 实现打开页面和辅助填写但不提交的 Prepare 流程
* [!] 已实现不可变步骤审计、幂等和人工接管；同步 Foundation 暂无可实际触达的运行中取消窗口，取消 API 留作异步 Worker
* [x] 检测验证码/二次验证/密码/页面不确定并进入 BLOCKED
* [x] 使用本地测试站证明 Assist 不触发 Submit
* [!] Authorized Automation Adapter 未实现：没有真实平台完成政策/授权审查，禁止跨越安全边界
* [x] Backend/Extension/Worker Build、Test、Run、HTTP、Fixture、持久化与真实 Edge unpacked Extension E2E 全部通过

### Phase 8：Interview Center

* [x] 创建 Interview、Question、Answer Note、Review、Knowledge Gap 和 Reminder 的 Flyway V8 迁移
* [x] 实现面试轮次、时间、形式、链接、结果和站内提醒
* [x] 实现 InterviewAgent 的问题预测、难度、答案框架、追问和风险
* [x] 明确区分 Predicted、Actual 与 Manual Question
* [x] 实现实际问题、追加版本 Answer Note 和不可变 Interview Review
* [x] 实现薄弱点、表达问题、项目漏洞、证据与复习建议
* [x] Review 确认后仍需逐条人工激活 Knowledge Gap
* [x] 实现近期面试、提醒、待确认 Review 和活动 Gap 的 Dashboard 联动
* [x] 完成时区、夏令时、过去时间、改期、取消、重复提醒、Ownership、AI Schema/证据/失败测试
* [x] Phase 8 Backend/AI/Frontend Build、Test、Run、HTTP、重启持久化与 Microsoft Edge E2E 全部通过

### Phase 9：Offer Center

* [x] 创建 Flyway V9 Offer 迁移；敏感信息按本地优先、最小返回、禁止日志输出保护，独立轮换密钥字段加密列为安全技术债
* [x] 实现薪资、月份、奖金、住房、福利、工时、试用期和 Deadline
* [x] 实现固定维度、显式权重、输入快照与不可变版本的 Offer Comparison Score 和解释
* [x] 实现 Offer 截止提醒、编辑同步、状态机和逻辑删除
* [x] 验证金额精度、跨币种不可比、IANA 时区、未来截止时间和试用期计算

### Phase 9：完整 Analytics

* [x] 实现平台、岗位方向、公司、城市和薪资的回复/面试/Offer 转化率
* [x] 实现 Resume Version 回复/面试/Offer 转化率
* [x] 实现 MatchScore Bucket 回复/面试/Offer 转化率
* [x] 所有指标返回分子、分母、样本量、百分比和不足提示；零分母返回 null
* [x] 实现从源事件全量重算、不可变 Analytics Snapshot 和幂等对账
* [x] 实现个人数据导出、删除预览、精确确认短语、审计和账户禁用
* [x] Phase 9 Backend/AI/Frontend Build、Test、Run、HTTP、空库迁移、重启持久化与 Edge E2E 全部通过

## P2 — 反馈学习与安全自动化

### Phase 10：Learning to Rank

* [x] 创建 Feedback、Training Run、LTR Model Version 和 Shadow Result 迁移
* [x] 从 Applied/Viewed/Replied/Interview/Offer 生成不可变 Feedback
* [x] 固化训练 Feature Snapshot、Feature Hash 和 Schema 版本
* [x] 设置 30 条最小样本阈值；不足时只返回统计建议
* [x] 实现按时间 80/20 切分、NDCG@10 离线指标和未来数据泄漏保护
* [x] 实现 Shadow Ranking，验证期间不改变当前推荐顺序或分数
* [x] 实现用户主动激活和一键回滚，保留完整 Model Version 历史
* [x] 使用岗位、技能、公司、Embedding 和 Resume 证据生成保守排序参数与解释

### Phase 10：Automation Center

* [x] 创建 Automation Rule/Safe Task、Suggestion、Notification 和 Authorization 迁移；复用 Phase 5 Platform Policy
* [x] 实现每日岗位摘要和安全重评分
* [x] 实现重复岗位清理建议；接受建议也不直接删除岗位
* [x] 实现高匹配、待跟进、面试和 Offer Deadline 站内提醒
* [x] 实现任务幂等、指数退避、人工重试、取消、Rule 暂停和审计
* [x] Policy 过期或 Authorization 撤销时自动降级 `MANUAL_ONLY`
* [x] Scheduler 默认关闭；禁止后台刷招聘页、外部消息和未授权自动投递
* [x] Phase 10 Backend 405 tests、AI 52 tests、Frontend Build、V9→V10/空库迁移、48-call HTTP、重启持久化和 Edge E2E 全部通过

### 运维与质量增强

* [x] 接入 OpenTelemetry Trace、Prometheus Metrics 和 ECS JSON 结构化日志；OTLP 默认关闭
* [x] 建立真实 AI Token/成本预算、告警状态和 Provider 熔断看板
* [x] 实现 MySQL、文件存储和 Milvus 一致批次备份与 SHA-256 Manifest
* [x] 执行隔离空环境恢复演练并记录 RPO 41s / RTO 22s；生产数据库未触碰
* [x] 建立依赖漏洞、Secret、License 和容器镜像扫描；应用依赖 0 Critical/High、Secret 0、License 0 禁止项，容器 248 个原始 Critical/High 已完成当前本机私有拓扑可达性评审并标记 `PASS_WITH_FINDINGS`
* [x] 建立负载测试基线：Job List P95 209.43ms、Recommendation P95 79.65ms、Dashboard P95 92.35ms，90/90 成功
* [x] Phase 11 Backend 412 tests、AI 52 tests、Frontend Build、V10→V11/空库迁移、17-call HTTP、重启持久化、备份恢复、安全扫描、负载基线和浏览器 E2E 全部通过

### Phase 12：本地安全发布

* [x] 创建 Backend、AI Service、Frontend 多阶段固定运行时镜像
* [x] 三个应用镜像以非 Root 用户运行，启用只读文件系统、最小 Capability 与 `no-new-privileges`
* [x] 创建 Loopback-only Release Compose；默认 Gateway 为 `127.0.0.1:8180`
* [x] Nginx 同源代理 SPA、API、Actuator、Swagger/OpenAPI，并提供 5 项安全响应头
* [x] 创建环境、工具链、磁盘、Compose、端口和运行态加固 Doctor
* [x] 启动前执行 MySQL/文件/Milvus 一致批次备份，不依赖尚未启动的 Backend
* [x] 生成 Artifact SHA-256、镜像 ID/用户、Flyway 与 Backup 引用的 Release Manifest、ZIP 和校验文件
* [x] 创建无副作用回滚预检；不自动改数据库、不自动切换容器、不覆盖活动数据
* [x] 完成 417 Backend、52 AI、13 Extension、6 Worker 共 488 项测试和 Frontend Build
* [x] 完成 20-call HTTP、重启持久化、Trace、安全头、容器加固与零外部动作验证
* [x] 完成 Operations 桌面/390px 浏览器验收，console error/warning 为 0
* [x] 完成 Release Runbook、Test Report、Final Report 与 Phase 0 基线文档同步

### Phase 13：首次使用引导与数据质量

* [x] 新增当前用户 `Onboarding Overview` 与 `Data Quality` 只读 API
* [x] 使用九项固定权重计算 0–100 Readiness，并保证权重总和为 100
* [x] 区分 `BLOCKER`、`WARNING`、`INFO`，使用稳定问题 Code 和确定性排序
* [x] 从 Candidate、Skill、Education、Experience、Project、Resume 和 Version 的 MySQL 事实实时计算
* [x] 新增 Setup Center，提供首次使用清单、质量问题和明确修复入口
* [x] Dashboard 接入真实 Readiness，不生成岗位或候选人假数据
* [x] Element Plus 与 ECharts 按需导入和手工分包，生产构建单个 JS Chunk 低于 500 KiB
* [x] 完成 Onboarding Service/Controller 的空资料、部分资料、完整资料、Ownership 和 Envelope 测试
* [x] 完成 `0.13.0` Docker 运行、HTTP Smoke、重启持久化和桌面/390px 浏览器验收
* [x] 完成 Phase 13 Test Report、Final Report 和基线文档最终同步

### Phase 14：账户安全与工作区设置

* [x] 新增当前用户密码修改 API，强制当前密码、确认密码、12–128 位和新旧密码不同
* [x] 为 Access/Refresh JWT 增加 Auth Version，并使用 Redis Session Family Denylist 实现立即撤销
* [x] 新增活动 Web Session 列表与按 Ownership 撤销，Token、完整 User-Agent 和完整 IP 不回显
* [x] 保持 Extension 配对生命周期独立，并在扩展认证时校验当前 Auth Version
* [x] 新增账户资料与 `system_settings` 固定白名单持久化 API
* [x] 新增 Settings 页面，并接入紧凑布局、默认落地页与 Dashboard 引导开关
* [x] 新增 Flyway V12，完成 V11→V12、V1→V12、83 表与 Redis 真实验证
* [x] 完成密码/设置恢复、Session 撤销、重启持久化和 38 次 HTTP 验证
* [x] 完成 511 项自动化测试、Frontend Build 与 `0.14.0` 加固容器运行
* [x] 完成 Settings 桌面/390px 浏览器验收，console error/warning 为 0
* [x] 完成 Phase 14 Test Report、Final Report 和基线文档最终同步

### Phase 15：简体中文界面

* [x] Web 默认启用简体中文和 Element Plus 中文语言包，统一导航、页签标题、表单、按钮、空状态和提示语
* [x] 完成 Dashboard、Setup、岗位、推荐、申请、面试、Offer、分析、学习、自动化、运维、AI Studio、候选人、简历和设置页面中文化
* [x] 建立统一展示值映射，中文显示状态、枚举、布尔值和已知确定性说明，同时保持 API、数据库和领域枚举契约不变
* [x] 完成浏览器扩展名称、配对、提取、分析、草稿、Assist 队列和安全边界提示中文化
* [x] 完成后端面向用户的引导、分析、学习、自动化、申请与 Offer 说明中文化，不翻译用户保存的简历和岗位原文
* [x] Frontend Production Build、Backend 440 Tests、Extension Typecheck/Lint/13 Tests/Build 通过
* [x] 新版 Backend/Frontend/AI Service Docker 镜像健康运行并完成 7 项 HTTP 回归
* [x] 完成桌面与 390px 窄屏各 16 个页面逐页中文验收，无横向溢出，console error/warning 为 0

## P3 — 有真实需求和数据后再评估

* [ ] 评估从 Outbox Worker 引入 RabbitMQ 的必要性
* [ ] 评估将独立领域从模块化单体拆为微服务的必要性
* [ ] 评估独立全文搜索引擎，当前 MySQL 搜索不足时再引入
* [ ] 评估本地离线 LLM 与 Embedding 一键部署
* [ ] 评估移动端/PWA，仅在桌面日用链路稳定后开始
* [ ] 评估日历连接器，同步前必须设计最小权限与撤销
* [ ] 评估更复杂的 LTR/因果分析，仅在样本量足够后开始
* [ ] 评估多用户/SaaS 化；当前数据模型兼容但不为其提前增加运维复杂度
