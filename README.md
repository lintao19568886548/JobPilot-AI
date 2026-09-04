# JobPilot AI

JobPilot AI 是个人使用的智能求职中控系统。当前仓库已完成 **Phase 14 Account Security & Workspace Settings**：在 Phase 0–13 完整产品与本地发布基础上，提供密码变更、Web Session 撤销、账户资料与真实持久化工作区设置；所有招聘平台外部动作边界保持不变。

## 已实现功能

- BCrypt 密码、Access/Refresh JWT、MySQL 轮换记录和 Redis TTL 元数据。
- Candidate Profile、教育、工作/实习、项目与结构化技能 CRUD。
- Master/Default Resume、不可变 Resume Version 和结构化 Section。
- Vue 3 + JavaScript 登录、Dashboard、Candidate Profile 和 Resume Center。
- Company、Job、Job Source、Job Skill、Skill Alias、解析/去重/修订历史。
- 手动岗位、用户指定公开 URL、CSV、XLSX 与受限 Extension Capture 契约。
- FastAPI + LangGraph Job Parser；无 LLM Secret 时明确运行真实 `RULES_ONLY` 模式。
- Job Center 列表、游标分页、排序、组合筛选、详情、忽略/恢复和导入结果。
- 版本化 Match Config、白名单 Hard Filter DSL，以及 `PASS/DOWNGRADE/REJECT` 证据与惩罚。
- 技能精确/本体关系、项目证据、求职偏好、公司偏好和 S/A/B/C/D 最终评分。
- FastAPI + LangGraph MatchingAgent、真实 `BAAI/bge-m3` 1024 维向量、Milvus COSINE/AUTOINDEX 与内容 Hash 缓存。
- OpenAI-Compatible LLM Provider 契约、超时/重试/并发限制/熔断；未配置 Secret 时明确 `SKIPPED_NOT_CONFIGURED`，不伪造 LLM 分数。
- Transactional Outbox 匹配任务、Redis Worker 锁、幂等、退避重试、DEAD 与同任务恢复。
- Job Center 匹配分析、六维分数、有效权重、证据、历史版本与真实页面重评估。
- Recommendation 唯一投影、全部/TOP/未评估/忽略/收藏视图、六种排序、组合筛选和游标分页。
- 批量刷新复用 Phase 3 Match/Outbox；收藏/取消、忽略/恢复保存幂等不可变事件。
- Dashboard 展示真实匹配/投递 KPI、今日推荐、等级/来源分布、近期面试、待办提醒、待确认复盘和活动 Knowledge Gap。
- Global Search 搜索当前用户可见的 Job、Company、Skill 与状态；`Ctrl+K` 打开。
- Application Queue 支持单个/批量入队、优先级、Resume/Greeting 引用、唯一活动项、幂等、批准、跳过、逻辑移除和 Assist Prepare。
- Application CRM 支持显式外部投递确认、白名单状态机、乐观锁、不可变 Timeline、当前用户隔离和真实漏斗聚合。
- Platform Policy 未知或过期时默认 `MANUAL_ONLY`；Phase 5 明确拒绝 `AUTHORIZED_AUTOMATION`，Assist Prepare 不打开网站、不提交、不创建 Application。
- Recruiter 基础档案与人工沟通记录；保存记录不会自动发送任何消息。
- Candidate Evidence Ledger 从 Profile、教育、经历、项目、技能与 Master Resume Section 生成不可变事实版本和稳定快照 Hash。
- Resume Tailor 输出逐项 Before/After/Reason/Evidence Refs，经后端真实性校验和人工批准后才创建新的不可变 Derived Resume Version；Master Resume 不变。
- Communication Agent 支持 BOSS、猎聘、邮件、微信、感谢、跟进与 Offer 草稿；状态为 `DRAFT → APPROVED → USED`，所有响应保持 `externallySent=false`，不存在外部发送接口。
- Resume Version 详情展示 Prompt/AI Call/父版本/目标岗位溯源，以及 Queue Use、Application、Reply、Interview、Offer 事实指标。
- Flyway 管理 MySQL Schema；所有普通业务删除使用逻辑删除。
- Trace ID、统一响应/异常、基础审计、Swagger 和 Actuator 健康检查。
- Chrome Manifest V3 最小权限扩展、一次性设备配对、短时 Extension JWT、Refresh 轮换、设备撤销和 session-only 浏览器凭据。
- 当前可见页面 `FIXTURE/GENERIC/MANUAL_SELECTION` 提取、原生 Action Popup 编辑确认、岗位保存、Match、Draft、Assist Queue 与 JobPilot 跳转。
- 默认关闭的本地 Playwright Worker；只允许 `127.0.0.1/localhost` Fixture、固定 Selector、HMAC 任务令牌，并在 Fill 后人工接管，永不 Submit。
- Interview Center 支持面试列表/日程、多个 Round、时区、状态、结果、站内 Reminder 和逻辑删除。
- Interview Agent 生成明确标记的 `PREDICTED` 问题、答案框架、追问与风险；实际问题和用户 Answer Note 独立保存并追加版本。
- Interview Review 保留不可变版本和事实/推断证据；AI 只提出 `PROPOSED` Knowledge Gap，用户确认 Review 后仍需逐条激活。
- Offer Center 支持真实 Offer、福利、奖金、试用期、截止事项、状态机、乐观锁、逻辑删除和 Dashboard 联动。
- Offer Comparison 固化权重、输入和结果版本；同币种现金可比，跨币种明确不可比，未知主观项不伪造分数。
- 完整 Analytics 从事实源重算七阶段漏斗和七类维度的回复/面试/Offer 转化，保留分子、分母、样本量、小样本标志和不可变快照。
- Privacy Center 支持当前用户业务数据导出，以及带预览、版本、精确短语、幂等和审计的删除确认流程。
- Learning Center 从真实 Viewed/Applied/Replied/Interview/Offer 事实重建不可变 Feedback，保存历史 Feature Snapshot，执行 30 条样本门禁、时间 80/20 切分和 NDCG@10 离线评估。
- LTR Model Version 支持 Shadow、用户主动激活和回滚；Shadow 不改当前推荐，激活后 Recommendation 投影才使用新版本。
- Automation Center 提供七类固定本地 Handler、幂等 Safe Task、退避重试、取消、Suggestion、站内 Notification 和可撤销 Authorization；Scheduler 默认关闭。
- Operations 汇总 Backend/MySQL/Redis/AI/Milvus 真实健康、Prometheus/Trace/OTLP 状态、AI Call Log 用量、用户预算、Provider 熔断状态和不可变运维运行证据。
- Phase 11 脚本提供 MySQL/文件/Milvus 同批次 SHA-256 备份、随机隔离恢复演练、Dependency/Secret/License/Container 扫描和三类只读 API P95 负载基线。
- Phase 12 提供 Backend/AI/Frontend 多阶段非 Root 镜像、只读文件系统与最小 Capability、Loopback Gateway、发布 Manifest/ZIP/SHA-256、启动前备份和回滚兼容性预检。
- Phase 13 提供 Setup Center、九项确定性 Readiness 检查、Blocker/Warning/Info 数据质量说明和 Dashboard 入口；派生结果实时重算，不保存伪事实。
- Phase 14 提供账户设置、密码变更、Web Session 列表/撤销、JWT Auth Version、Redis 即时失效和三项白名单工作区偏好；Extension 配对保持独立可撤销。

真实招聘平台自动化、自动消息发送与自动投递仍未启用。当前只有 `MANUAL` 与安全的 `ASSIST` 准备流程；AI 只能生成草稿，所有外部提交事实必须由用户明确确认。

## 环境要求

- Java 21
- Maven Wrapper（仓库已包含；无需全局安装 Maven）
- Node.js 22+ / npm
- Docker Desktop + Docker Compose

## Quick Start

1. 复制 `.env.example` 为 `.env`，替换所有密码和 `JWT_SECRET`。`JWT_SECRET` 至少 32 个字符。
2. 启动基础设施（Phase 3 使用 `ai` Profile 启动 Milvus/etcd/MinIO）：

   ```powershell
   docker compose --profile ai up -d
   ```

3. 初始化并启动 AI Service（默认端口 `8010`；首次匹配会下载 BGE-M3 到 `HF_HOME`）：

   ```powershell
   python -m venv .\ai-service\.venv
   .\ai-service\.venv\Scripts\python.exe -m pip install -r .\ai-service\requirements.txt
   # 在独立终端中保持运行
   powershell -ExecutionPolicy Bypass -File .\scripts\run-ai-service.ps1
   ```

4. 构建并启动 Backend：

   ```powershell
   .\backend\mvnw.cmd -f .\backend\pom.xml package
   # 在独立终端中保持运行
   powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
   ```

5. 安装并启动 Frontend：

   ```powershell
   npm --prefix frontend install
   npm --prefix frontend run dev
   ```

6. 打开 `http://localhost:5173`。默认 Backend 为 `http://localhost:8088`，AI Service 为 `http://127.0.0.1:8010`。

### Phase 7 Extension 与本地 Assist

```powershell
npm --prefix extension ci
npm --prefix extension run build
# Chrome 开发者模式中加载：D:\JobPilot AI\extension\dist
```

在 Web 的「Browser Extension」页面生成一次性配对码，再在扩展 Action Popup 完成配对。扩展只在用户点击图标并点击提取时读取当前可见页面。Edge 中加载的生产目录必须是 `extension\dist`；当前 Manifest 版本为 0.1.2。

Automation Worker 正常使用时保持 `AUTOMATION_WORKER_ENABLED=false`。只在本地 Fixture 验收时设置随机 `AUTOMATION_WORKER_TOKEN`、显式启用 Worker，并在独立终端执行：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\run-automation-worker.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase7-smoke.ps1 -ForceFull
# 重启 Backend 后
powershell -ExecutionPolicy Bypass -File .\scripts\phase7-smoke.ps1 -VerifyPersistence
```

`npm --prefix extension run test:e2e` 会在隔离的 Microsoft Edge 配置中加载 unpacked 扩展；测试副本只额外获得 localhost Fixture 权限，生产 Manifest 仍使用 `activeTab`。

### Phase 8 Interview Center

登录后打开 `http://127.0.0.1:5173/interviews`。Interview、Round、Question、Answer Note、Review、Knowledge Gap 和 Reminder 都写入 MySQL；Reminder 只在 Dashboard/Interview Center 显示，不发送外部通知。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase8-smoke.ps1
# 重启 Backend 后
powershell -ExecutionPolicy Bypass -File .\scripts\phase8-smoke.ps1 -VerifyPersistence
node .\scripts\phase8-browser-smoke.mjs
```

验收脚本使用从本地 Secret 单向派生的独立测试凭据，既不打印也不提交密码或 Token。

### Phase 9 Offer Center 与完整 Analytics

登录后打开 `http://127.0.0.1:5173/offers` 和 `http://127.0.0.1:5173/analytics`。Offer 必须关联真实 OFFER Application；系统不会自动接受/拒绝 Offer，也不会发送外部消息。跨币种只分组展示，不进行无来源汇率换算。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase9-smoke.ps1
# 重启 Backend 后
powershell -ExecutionPolicy Bypass -File .\scripts\phase9-smoke.ps1 -VerifyPersistence
powershell -ExecutionPolicy Bypass -File .\scripts\phase9-migration-check.ps1
node .\scripts\phase9-browser-smoke.mjs
```

Privacy 删除确认会禁用被测账户。完整 smoke 只对隔离的 `phase9_privacy` 测试用户执行破坏性确认，不会删除日常账户。

### Phase 10 Learning 与安全 Automation Center

登录后打开 `http://127.0.0.1:5173/learning` 和 `http://127.0.0.1:5173/automation-center`。训练只使用事件发生时已经存在的 Match Feature Snapshot；样本不足不会生成可激活模型。Automation Handler 只创建本地重评分、建议或站内提醒，不访问招聘页面、不发送外部消息，也不提交申请。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase10-smoke.ps1 -ForceFull
# 重启 Backend 后
powershell -ExecutionPolicy Bypass -File .\scripts\phase10-smoke.ps1 -VerifyPersistence
powershell -ExecutionPolicy Bypass -File .\scripts\phase10-migration-check.ps1
```

常规运行保持 `AUTOMATION_CENTER_ENABLED=false`。需要执行某条规则时在 Web 中由用户明确点击手工运行；即使登记了 Authorization，本阶段仍不存在任何外部提交 Handler。

### Phase 11 Operations

登录后打开 `http://127.0.0.1:5173/operations`。页面只展示真实健康、AI 用量/预算、Provider/Breaker、最近 Backup/Restore/Scan/Load Test 和零外部动作安全计数。OTLP 默认关闭；结构化 JSON 日志通过独立 `observability` Profile 验证。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-smoke.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-migration-check.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-observability-check.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-backup.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-restore-drill.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-security-scan.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-load-test.ps1
```

操作细节、失败处理和恢复安全边界见 `docs/PHASE11_OPERATIONS_RUNBOOK.md`。`backups/`、`reports/` 和 `runtime/` 均被 Git 忽略。

### Phase 12 本地安全发布

默认发布入口为 `http://127.0.0.1:8180`。完整构建会执行 Backend、AI、Frontend、Extension 与 Automation Worker 回归，然后构建三个应用镜像；启动前默认创建一致批次备份。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-doctor.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-build.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-up.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-smoke.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-release.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-rollback-check.ps1
```

详细启动、重启持久化、停止和故障处理见 `docs/PHASE12_RELEASE_RUNBOOK.md`。`releases/`、`.env` 和运行数据均被 Git 忽略；Phase 12 只面向本机，不代表已允许公网或局域网暴露。

### Phase 13 Setup Center

登录后访问 `http://127.0.0.1:8180/setup`。系统从当前用户的 Candidate Profile、技能、教育、经历、项目、Master/Default Resume 和不可变 Version 实时计算，不调用 LLM、不自动补写资料。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase13-smoke.ps1
# 重启 Backend 与 Frontend 后
powershell -ExecutionPolicy Bypass -File .\scripts\phase13-smoke.ps1 -VerifyPersistence
```

Frontend 对 Element Plus 和 ECharts 采用按需组件与独立 Vendor Chunk，Production Build 的单个 JavaScript Chunk 保持在 500 KiB 以下。

Phase 13 验收通过：495 项自动化测试、18 次阶段 HTTP、10 次发布安全回归、应用重启持久化和桌面/390px 浏览器验证均为 PASS。完整证据见 `docs/PHASE13_TEST_REPORT.md` 与 `docs/PHASE13_FINAL_REPORT.md`。

### Phase 14 Account Security & Settings

登录后访问 `http://127.0.0.1:8180/settings`。可更新显示名、时区和语言区域，选择默认落地页、紧凑布局与 Dashboard 引导提示，查看或撤销 Web Session，以及修改当前账户密码。密码修改会撤销全部 Web Session；Extension 配对不会被静默删除，但仍受账户 Auth Version 校验。

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase14-migration-check.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\phase14-smoke.ps1
# 重启应用容器后
powershell -ExecutionPolicy Bypass -File .\scripts\phase14-smoke.ps1 -VerifyPersistence
```

Phase 14 验收通过：511 项自动化测试、Flyway 12/83 表、38 次 HTTP、密码与设置恢复、应用重启持久化和桌面/390px 浏览器验证均为 PASS。完整证据见 `docs/PHASE14_TEST_REPORT.md` 与 `docs/PHASE14_FINAL_REPORT.md`。

Bootstrap 用户只在 `BOOTSTRAP_USER_ENABLED=true` 且用户名不存在时创建；密码只从环境变量读取，不存在源码或迁移中。首次登录后建议立即关闭 Bootstrap。

## API 与健康检查

- Health：`http://localhost:8088/actuator/health`
- Swagger UI：`http://localhost:8088/swagger-ui.html`
- OpenAPI：`http://localhost:8088/v3/api-docs`
- AI Health：`http://127.0.0.1:8010/internal/v1/health`
- API：`/api/...`，同时支持等价 `/api/v1/...` 路由。

## 验证

```powershell
Push-Location backend
.\mvnw.cmd clean test
.\mvnw.cmd package
Pop-Location
npm --prefix frontend run build
.\ai-service\.venv\Scripts\python.exe -m pytest -q .\ai-service
.\ai-service\.venv\Scripts\python.exe -m ruff check .\ai-service
```

Phase 1–9 回归继续保留对应 `scripts/phaseN-smoke.ps1`。Phase 10 使用 `scripts/phase10-smoke.ps1 -ForceFull`；Phase 11 使用 Operations 脚本；Phase 12 使用 `scripts/phase12-smoke.ps1`；Phase 13/14 分别使用对应阶段脚本，并在重启应用容器后追加 `-VerifyPersistence`。脚本不会输出 Token 或密码。

## Milvus 与可选 LLM

Milvus 已在 Phase 3 投产链路使用，默认以 `ai` Profile 启动：

```powershell
docker compose --profile ai up -d
```

LLM 只有在 `LLM_BASE_URL`、`LLM_API_KEY`、`LLM_MODEL` 三项全部配置时才调用。未配置是受支持状态，不影响确定性评分与 Embedding；不要把 Secret 写入 Vite 变量或提交到 Git。

## Security

- 不提交 `.env`、数据库卷、日志、Token、Cookie 或 API Key。
- Vite 环境变量会进入浏览器包，只允许配置公开 API 地址，禁止存放 Secret。
- Refresh Token 服务端仅保存 SHA-256 摘要；每次 Refresh 都轮换并撤销旧 Token。
- CORS 使用明确 Origin 列表，不配置通配 Origin + Credentials。
- Backend 到 AI Service 使用独立内部 Token；URL 导入拒绝本机、内网、链路本地、元数据地址和危险重定向。
- Extension Capture 仅接收白名单可见字段，明确拒绝 Cookie、Authorization、Local/Session Storage、隐藏字段和验证码数据。
