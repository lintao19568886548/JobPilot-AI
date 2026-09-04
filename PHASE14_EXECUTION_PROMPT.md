# JobPilot AI Phase 14 Execution Prompt

你现在继续开发并真实验收：

`D:\JobPilot AI`

当前 Phase 0–13 已完成。Phase 14 定义为：

# Phase 14 — Account Security & Workspace Settings（账户安全与工作区设置）

本阶段补齐 Phase 0 API 设计中已经存在但尚未实现的修改密码、活动会话、会话撤销和 Settings Center。所有配置必须进入真实 MySQL，所有会话撤销必须同步作用于 MySQL 与 Redis；不增加招聘网站采集、验证码处理、外部消息、自动投递或自动 Offer 决策。

## 一、执行原则

1. 先复核 Architecture、Database、API、Roadmap、Tasks、Changelog 和 Phase 13 验收结果。
2. 只实现当前登录用户；任何 Account、Settings、Session API 都不接受客户端 `userId`。
3. 密码、Refresh Token、Token Hash、Authorization、Cookie 和完整 User-Agent 不得出现在响应、日志、测试输出或 Git。
4. 密码修改和会话撤销必须真实失效，不能只改变前端状态。
5. Settings 只允许固定白名单键；禁止用任意 Key/JSON 建立无边界配置写入口。
6. 不修改已发布 Flyway 文件；新增 V12 迁移。生产 Schema 禁止依赖 Hibernate 自动建表。
7. 不 commit、不 push，除非用户明确授权。

## 二、数据库迁移

新增 `V12__account_security_and_settings.sql`：

- `users` 增加 `timezone`、`locale`、`password_changed_at`、`auth_version`；
- `refresh_tokens` 增加最小会话元数据：`client_type`、安全客户端标签、User-Agent Hash、脱敏 IP、`last_used_at`；
- 新增 `system_settings`，包含当前用户、group/key、类型化 JSON 值、敏感标志、有效时间、乐观锁和必要唯一索引；
- 当前阶段只写非敏感白名单配置，`encrypted_value` 预留但不得用 JWT Secret 代替独立加密密钥；
- 升级库与 V1→V12 空库迁移都必须通过。

## 三、Web Token 与 Session 安全

Access/Refresh JWT 增加当前用户 `authVersion`；Access Token 同时携带 Web Session Family。Backend 每次认证必须验证：

- 用户存在且 ACTIVE；
- Token `authVersion` 与数据库一致；
- Session Family 未在 Redis 撤销。

Refresh 轮换继续保持单次使用。会话撤销需要：

- 撤销该 Family 的所有活动 Refresh Token；
- 删除对应 Redis Refresh Key；
- 写入带 TTL 的 Family Revocation 标记，使现有短时 Access Token 立即失效；
- 保留历史记录，不物理删除。

密码修改需要：

- 校验当前密码；
- 新密码至少 12 位、最多 128 位，必须与当前密码不同并与确认字段一致；
- 使用 BCrypt 保存；
- 增加 `auth_version`；
- 撤销全部 Web Refresh Session；
- 记录 `PASSWORD_CHANGE` 审计；
- 不自动撤销独立管理的 Extension Device Pairing，但旧 Web Token 必须全部失效。

## 四、Auth API

补齐：

- `POST /api/v1/auth/password`
- `GET /api/v1/auth/sessions`
- `DELETE /api/v1/auth/sessions/{sessionId}`

Session 响应只返回公开 ID、安全客户端标签、客户端类型、脱敏 IP、是否当前会话、创建/最近使用/过期时间；不得返回 Token、Hash 或 Family ID。

删除不属于当前用户的 Session 返回 Not Found。撤销当前 Session 合法，但前端随后必须退出。

## 五、Settings API

新增 `com.jobpilot.settings` 模块，提供：

- `GET /api/v1/settings`
- `PUT /api/v1/settings/account`
- `PUT /api/v1/settings/{group}/{key}`

Account 支持：

- `displayName`
- `email`
- `timezone`（有效 IANA ZoneId）
- `locale`（当前白名单 `zh-CN/en-US`）
- 乐观锁 `version`

Settings 固定白名单：

- `workspace.defaultLandingPage`：`/dashboard`、`/setup`、`/jobs`、`/recommendations`、`/applications`；
- `workspace.compactMode`：Boolean；
- `onboarding.showDashboardBanner`：Boolean。

GET 必须返回默认值、来源（DEFAULT/STORED）、版本和安全摘要；PUT 必须验证类型、值域、Ownership 和乐观锁。所有更新记录 Audit。

## 六、Frontend Settings Center

新增 `/settings` 并将侧栏 Settings 从 Coming Soon 移到正式导航：

- Account 区域编辑显示名、邮箱、时区和语言；
- Workspace 区域编辑默认首页、紧凑模式和 Dashboard Setup 提示；
- Security 区域展示真实活动 Web Sessions，可撤销指定会话；
- 修改密码必须输入当前密码、新密码和确认，成功后清空浏览器 Session Token 并返回登录页；
- 设置保存后刷新页面仍存在；
- 登录成功后使用真实 `defaultLandingPage`；
- `compactMode` 必须真实影响 Layout；
- `showDashboardBanner=false` 必须真实隐藏 Dashboard Readiness 提示；
- Loading、空状态、错误提示和危险操作确认必须完整；
- 390px 不允许横向溢出。

页面清楚说明：Web Session 与 Extension Pairing 是两个独立安全域；修改密码不会偷偷执行外部动作。

## 七、测试要求

Backend 至少测试：

- JWT `authVersion` 和 Session Family；
- 密码正确修改、当前密码错误、弱密码、相同密码、确认不一致；
- 密码修改后 Access/Refresh Token 失效；
- Session 列表最小字段、当前会话标记、Ownership；
- 单 Session 撤销同步 MySQL/Redis/Family Denylist；
- Settings 默认值、白名单、类型和值域；
- Account 邮箱、IANA 时区、Locale、乐观锁和唯一冲突；
- Controller/Auth/ApiResponse/Trace 契约。

继续回归全部 Backend、AI、Frontend、Extension 和 Automation Worker。

## 八、真实验收

1. Backend `mvn clean test` 与 `mvn package`；
2. AI pytest 与 Ruff；
3. Frontend Production Build，单个 JavaScript Chunk 仍低于 500 KiB；
4. Extension 与 Automation Worker 回归；
5. V11→V12 和临时空库 V1→V12 迁移；
6. 构建并运行 Backend/Frontend/AI `0.14.0` 发布镜像；
7. 使用专用本地 Smoke 账户验证 Settings 默认/更新/刷新；
8. 建立第二 Web Session，撤销并验证其 Access 与 Refresh 都失败；
9. 临时修改专用 Smoke 账户密码，验证旧 Token 失效，再恢复原密码；
10. 重启应用容器，验证 Settings 与 Account 状态仍存在；
11. 浏览器桌面与 390px 验证 Settings、Dashboard 开关和 console；
12. 确认密码和 Token 未输出，五类禁止外部动作仍为 0。

真实账号修改必须使用可恢复的测试账户和 `try/finally`；任何失败都要尽力恢复原密码与原设置，不能影响用户日常账号。

## 九、文档与任务

更新：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`

新增：

- `scripts/phase14-smoke.ps1`
- `docs/PHASE14_TEST_REPORT.md`
- `docs/PHASE14_FINAL_REPORT.md`

## 十、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_14
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
AI_REGRESSION=
FRONTEND_BUILD=
FRONTEND_CHUNK_BUDGET=
EXTENSION_REGRESSION=
AUTOMATION_WORKER_REGRESSION=
FLYWAY_V12=
EMPTY_DATABASE_MIGRATION=
SETTINGS_API=
ACCOUNT_SETTINGS=
PASSWORD_CHANGE=
AUTH_VERSION_INVALIDATION=
SESSION_LIST=
SESSION_REVOCATION=
REDIS_FAMILY_DENYLIST=
CURRENT_USER_ISOLATION=
MYSQL_PERSISTENCE=
APPLICATION_RESTART=
DESKTOP_UI=
MOBILE_390PX=
CONSOLE_ERRORS=
TRACE_PROPAGATION=
SECRETS_EXPOSED=0 / 非0
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_SUBMISSIONS=0 / 非0
EXTERNAL_MUTATIONS=0 / 非0
AUTOMATIC_OFFER_DECISIONS=0 / 非0
DESTRUCTIVE_DUPLICATE_DELETES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任一核心 Gate 失败不得声明 Phase 14 Complete。
