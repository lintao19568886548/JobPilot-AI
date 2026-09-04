# JobPilot AI Phase 11 执行提示词

你现在继续开发项目：

`D:\JobPilot AI`

Phase 0–10 已完成。现在进入：

# Phase 11 — Operational Readiness

本阶段只建设可观测性、AI 成本治理、备份恢复、安全扫描和性能基线。不得开发新的求职业务能力，不得扩大浏览器自动化、外部消息、自动投递或自动 Offer 决策边界。

## 一、执行原则

1. 先读取 `ARCHITECTURE.md`、`DATABASE_DESIGN.md`、`API_DESIGN.md`、`DEVELOPMENT_ROADMAP.md`、`TASKS.md`、`CHANGELOG.md` 和 Phase 10 报告。
2. 所有运行状态、预算、备份、恢复、扫描和性能结果必须来自真实进程、数据库或工具，不使用 Mock/Fake KPI。
3. Secret、Token、密码、Cookie、简历正文和岗位敏感全文不得进入日志、指标标签、报告或 Git。
4. 不得为了通过验收而关闭测试、降低已有安全约束或伪造扫描结果。
5. 发现设计冲突时更新文档和 Changelog，说明选择原因。
6. 不 commit、不 push，除非用户明确授权。

## 二、Observability

Backend 接入 Micrometer Prometheus 和 OpenTelemetry 兼容 tracing：

- 保留 `X-Trace-Id` 与 MDC；响应继续返回同一个 Trace ID。
- HTTP、JVM、Hikari、Redis/数据库健康指标通过 Actuator 暴露。
- `/actuator/prometheus` 可被本机监控抓取，但不得匿名暴露业务管理 API。
- OTLP Exporter 默认关闭；只有显式配置 Endpoint 时才导出。
- 采样率、Service Name、Exporter Endpoint、Metrics/Health 暴露通过环境变量配置。
- 提供可选 `observability` Profile 的结构化 JSON 日志；默认开发日志仍可读。
- 自定义指标至少包括 AI 调用、预算告警、Automation Task、Backup/Restore/Scan/Load Test 结果，不把 userId、岗位名或 Secret 放进高基数标签。

Trace 必须验证：传入 `X-Trace-Id` 后 Backend Response Header、API Response、MDC 日志一致；未传时生成新 ID。

## 三、Operations 数据模型

使用 Flyway V11，至少建立：

- `ai_budget_policies`
- `operational_runs`

`ai_budget_policies`：当前用户、日/月 Token 上限、日/月成本上限、币种、告警阈值、启用状态、乐观锁、审计时间。

`operational_runs`：运行类型、状态、批次 ID、开始/结束时间、指标 JSON、安全摘要、Artifact Manifest 路径、RPO/RTO、错误码、Trace ID、创建者。运行类型至少支持 `BACKUP`、`RESTORE_DRILL`、`DEPENDENCY_SCAN`、`SECRET_SCAN`、`LICENSE_SCAN`、`CONTAINER_SCAN`、`LOAD_TEST`。

Manifest 只保存相对路径和 Hash，不保存 Secret。业务查询必须按当前用户隔离；系统级运行可通过明确 `scope=SYSTEM` 展示，但不能泄露其他用户数据。

## 四、AI Usage、Budget 与 Provider 状态

从真实 `ai_call_logs` 聚合：

- 今日/本月调用数；
- 输入、输出和总 Token；
- 实际已记录成本和币种；
- 成功/失败/跳过数量；
- Provider/Model 分组；
- 当前 Budget 使用率与 `OK/WARNING/EXCEEDED/DISABLED` 状态。

预算必须由用户显式配置。未配置成本或汇率时不推断、不换算；不同币种分组展示。预算超限只阻止可选 AI 调用或给出警告，不得影响规则模式和数据读取。

Provider 看板必须展示现有真实配置/健康、最近错误和 Circuit Breaker 状态；没有配置 LLM 时显示 `NOT_CONFIGURED`，不能伪装成健康云模型。

## 五、Operations API

至少实现：

- `GET /api/v1/operations/overview`
- `GET /api/v1/operations/ai-usage`
- `GET /api/v1/operations/ai-budget`
- `PUT /api/v1/operations/ai-budget`
- `GET /api/v1/operations/runs`
- `GET /api/v1/operations/runs/{id}`
- `POST /api/v1/operations/runs`

写入 Run 必须校验固定类型、状态、JSON 大小、相对 Artifact 路径和 Hash；使用 Idempotency-Key，审计创建操作。客户端不能传 userId。

## 六、备份与恢复

提供可重复执行的 PowerShell 脚本：

- `scripts/phase11-backup.ps1`
- `scripts/phase11-restore-drill.ps1`

Backup 要求：

1. 预检 MySQL、Redis、Milvus；
2. 创建唯一批次目录；
3. 使用 MySQL 一致性逻辑快照；
4. 归档用户上传/文件存储；
5. 导出 Milvus Collection Schema、索引和可恢复数据；无 Collection 时明确保存空清单；
6. 生成 SHA-256 Manifest、开始/结束时间、源版本和非敏感计数；
7. Artifact 默认写入被 Git 忽略的 `backups/`；
8. 任一步失败则批次状态为 FAILED，不把残缺备份标为成功。

Restore Drill 要求：

1. 只恢复到随机临时数据库、临时文件目录和临时 Milvus Collection；
2. 恢复前校验全部 Hash；
3. 验证 Flyway 版本、表数量、关键事实数量和外键一致性；
4. 验证文件清单和 Milvus 数据计数；
5. 记录实际 RPO、RTO 和验证结果；
6. 清理临时资源，不覆盖当前 `jobpilot` 数据库，不删除生产卷。

## 七、安全、依赖和 License 扫描

提供 `scripts/phase11-security-scan.ps1`，真实执行并保存机器可读报告：

- Maven 依赖清单与漏洞扫描；
- npm audit（Frontend、Extension、Automation Worker）；
- Python 依赖漏洞扫描；
- Secret 扫描，必须排除 `.env`、备份、运行目录和依赖缓存；
- License 清单与禁止 License 检查；
- 当前 Compose 镜像漏洞扫描或等价容器扫描；
- 输出 Tool Version、时间、范围、发现数量和安全摘要。

扫描工具缺失时允许联网安装到仓库外缓存或使用临时容器。不得把“工具未运行”标为 PASS。发现问题必须按严重度列出；只有已确认的运行时 Critical/High 或真实 Secret 泄露才能阻断 Phase，开发依赖/无可利用路径的问题可记录为技术债，但不能隐藏。

## 八、负载测试基线

提供 `scripts/phase11-load-test.ps1`，针对真实本地 Backend：

- 登录只用于获取短时 Token，报告不保存 Token；
- 测试 Job List、Recommendation/Match Read 和 Dashboard；
- 支持 Warmup、并发数、每路请求数和超时参数；
- 输出请求数、成功/失败、吞吐量、P50/P95/P99、最大值；
- 每个 Endpoint 独立结果；
- 默认安全阈值：错误率 0，P95 < 1000ms；
- 不触发外部 URL、AI 付费调用或任何写入型求职动作。

性能结果写入 `operational_runs` 并保存到被 Git 忽略的 `reports/phase11/`。

## 九、Frontend Ops Center

Vue 3 + JavaScript 实现 `/operations`：

- Runtime Health：Backend、MySQL、Redis、AI、Milvus；
- Observability：Trace、Prometheus、OTLP 是否启用；
- AI Usage/Budget：真实 Token、成本、预算状态、Provider/Breaker；
- Operational Runs：Backup、Restore、Security、Load Test 的最近状态和摘要；
- Safety Boundary：外部消息、提交、外部变更、自动 Offer 决策继续为 0；
- 空状态、未配置状态和失败状态必须真实展示。

页面保持现有浅色专业 SaaS 风格，不伪造曲线，不显示 Secret，不提供危险的一键覆盖恢复按钮。

## 十、测试

Backend 至少覆盖：

- Budget 校验、使用率和跨币种边界；
- AI Usage 聚合；
- Run 类型/状态、幂等、Ownership、Artifact 路径遍历和 JSON 上限；
- Trace ID 透传；
- Actuator 安全暴露；
- V11 Migration Contract；
- Operations API Integration。

脚本至少验证：

- Backup Manifest Hash；
- Restore 绝不指向当前数据库；
- 临时资源清理；
- 扫描器退出码和报告 Schema；
- Load Test 统计和阈值判定。

## 十一、真实验收顺序

1. `mvn clean test`、`mvn package`；
2. AI Service 全量 pytest 与 Ruff；
3. Frontend production build；
4. MySQL V10→V11 和临时空库 V1→V11；
5. 启动 Backend、AI、Frontend，验证健康与 Redis PONG；
6. 验证 Prometheus 指标、Trace ID 和结构化日志 Profile；
7. 配置并读取当前用户 AI Budget，验证真实 Usage；
8. 创建真实 Backup，校验 Manifest；
9. 执行隔离 Restore Drill，验证 RPO/RTO 和清理；
10. 执行 Dependency、Secret、License、Container Scan，记录全部发现；
11. 执行三类读取 API 的并发负载测试并记录 P95；
12. 重启 Backend，确认 Budget 与 Operational Run 持久化；
13. 浏览器验证 Operations 页面真实数据和 console error=0；
14. 确认外部消息、投递、外部变更、自动 Offer 决策和破坏性删除均为 0。

## 十二、TASKS、文档与 Git

同步更新 `TASKS.md`、`README.md`、`ARCHITECTURE.md`、`DATABASE_DESIGN.md`、`API_DESIGN.md`、`DEVELOPMENT_ROADMAP.md`、`CHANGELOG.md`，并创建 Phase 11 Test/Final Report。

`.env`、`backups/`、`reports/`、`runtime/`、`target/`、`node_modules/`、`.venv/`、扫描缓存和临时恢复目录必须被 Git 忽略。

## 十三、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_11
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_REGRESSION=
FRONTEND_BUILD=
FRONTEND_RUN=
DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
REDIS_CONNECTION=
TRACE_PROPAGATION=
PROMETHEUS_METRICS=
STRUCTURED_LOGGING=
OTLP_DEFAULT_OFF=
AI_USAGE=
AI_BUDGET=
PROVIDER_BREAKER_STATUS=
BACKUP_CREATE=
BACKUP_INTEGRITY=
RESTORE_DRILL=
RPO_RTO_RECORDED=
DEPENDENCY_SCAN=
SECRET_SCAN=
LICENSE_SCAN=
CONTAINER_SCAN=
LOAD_TEST=
JOB_LIST_P95=
RECOMMENDATION_P95=
DASHBOARD_P95=
OPERATIONS_UI=
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

任一核心门槛失败，或扫描工具未实际执行，不得声明 Phase 11 完成。
