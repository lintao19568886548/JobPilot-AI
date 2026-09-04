# JobPilot AI Phase 9 Test Report

验收日期：2026-09-02  
范围：Offer Center、Offer Comparison、Offer Deadline、Complete Analytics、Privacy Export/Delete  
结论：PASS

## 构建与自动化测试

| 项目 | 命令 | 结果 |
|---|---|---|
| Backend clean test | `backend\mvnw.cmd clean test` | PASS，399 tests，0 failure/error/skipped |
| Backend package | `backend\mvnw.cmd package` | PASS，399 tests，生成可运行 JAR |
| AI regression | `ai-service\.venv\Scripts\python.exe -m pytest -q` | PASS，52 tests；2 条第三方弃用预警 |
| AI lint | `ai-service\.venv\Scripts\ruff.exe check .` | PASS |
| Frontend build | `npm run build` | PASS，2303 modules transformed |

Phase 9 新增 23 项 Backend 单元/契约测试，覆盖金额、时区、状态机、Comparison、Analytics 零分母、V9 Migration 和 Privacy 安全契约。全仓 Backend 与 AI 自动化测试合计 451 项。

## 数据库与运行验证

| 验证 | 结果 | 证据摘要 |
|---|---|---|
| Backend run | PASS | Actuator `UP`，Swagger HTTP 200 |
| Upgrade migration | PASS | 现有库 V8→V9，Flyway 校验 9 个版本 |
| Clean-room migration | PASS | 临时空库 V1→V9，最终 70 张表 |
| Redis | PASS | `PONG` |
| AI Service | PASS | `/internal/v1/health` 返回 `UP` |
| Frontend dev server | PASS | `/login` HTTP 200 |
| OpenAPI contract | PASS | 9 个 Phase 9 必需路由均存在 |

## HTTP 与持久化

`scripts/phase9-smoke.ps1` 实际执行 31 次 HTTP 验证并通过：

- 创建、读取、全量更新、筛选、状态迁移和逻辑删除 Offer；
- 同一 Application 重复 Offer 返回 409，旧版本更新返回 409；
- 非 Owner 访问返回 404；
- Deadline 创建、完成、状态筛选和 Offer 联动；
- 同币种 Comparison 可比较，跨币种 `cashComparable=false`；
- Comparison 与 Analytics Snapshot 幂等重放返回同一记录，输入冲突返回 409；
- 七阶段漏斗、七类维度、每个维度 REPLIED/INTERVIEW/OFFER 三种转化均存在；
- 零分母 `rate=null`，小样本显式标记；
- Privacy Export、删除预览、错误短语拒绝、隔离测试账号确认删除和禁用登录；
- `externalMessagesSent=0`、`automaticOfferDecisions=0`、`fabricatedExchangeRates=0`。

Backend 完整停止并重新启动后，`scripts/phase9-smoke.ps1 -VerifyPersistence` 又执行 3 次 HTTP 验证，Offer、Comparison 和 Analytics Snapshot 均从 MySQL 恢复。HTTP 验证合计 34 次。

## 浏览器验收

`node scripts/phase9-browser-smoke.mjs` 使用 Microsoft Edge 完成真实登录并验证：

- Dashboard 的真实 Offer KPI、截止事项与完整 Analytics 入口；
- Offer Center 的真实列表、金额、截止历史和 Comparison 区域；
- Analytics 的真实漏斗、七类维度、快照历史和 Privacy 控件；
- Browser console errors = 0；warnings = 0。

## 安全与边界

- `.env`、Backend `target`、Frontend `node_modules/dist`、runtime 与 smoke state 均被 Git 忽略；`.env`、私钥文件未被跟踪。
- Frontend 业务源码中 `.ts/.tsx` 文件数量为 0。
- Privacy destructive confirm 只对隔离的 `phase9_privacy` 测试账号执行。
- 系统没有自动接受/拒绝 Offer、虚构汇率、外部消息或未授权投递路径。
- Offer 用户输入当前依赖本地数据库边界、最小 API 暴露与禁止敏感日志；独立可轮换主密钥字段加密尚未交付，记录为技术债。

