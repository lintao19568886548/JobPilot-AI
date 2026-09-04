# JobPilot AI — Phase 5 执行提示词

你现在继续开发 `D:\JobPilot AI`，正式进入 Phase 5：Application Queue 与 CRM。

## 目标

在不进行任何外部自动提交的前提下，跑通：Recommendation → Queue → 人工确认 → Application CRM → 不可变时间线 → Dashboard/Analytics。所有数据必须真实写入 MySQL，Redis 必须用于入队/确认幂等短锁，刷新和 Backend 重启后数据不得丢失。

## 范围

1. 建立 Flyway V5：`application_queue_items`、`applications`、`application_logs`、`recruiters`、`recruiter_interactions`、`platform_policies`，并扩展 `analytics_daily`。
2. Queue 支持列表、单个/批量入队、优先级/Resume 调整、移除、批准、跳过与 Assist Prepare。
3. 一个用户/岗位最多一个活动 Queue Item；`Idempotency-Key` 相同请求必须返回同一结果，不得重复创建。
4. Application 支持列表、详情、人工确认创建、非状态字段更新、合法状态迁移与不可变日志查询。
5. 状态机白名单由代码集中定义，非法迁移返回 HTTP 409，Application 与日志均不得改变。
6. Recruiter 只实现基础档案与用户手工录入的沟通记录；不得自动发送消息。
7. Platform Policy 未知、过期或禁用时默认 `MANUAL_ONLY`。
8. `MANUAL` 与 `ASSIST` 是本阶段唯一可执行模式；`AUTHORIZED_AUTOMATION` 只保留为未来策略边界并明确拒绝。
9. Assist Prepare 只能准备资料、进入待人工操作状态；不得调用招聘平台、不得创建已投递 Application、不得写 SUCCESS/APPLIED。
10. Dashboard/Analytics 显示真实 Queue、Applied、Viewed、Replied、Written Test、Interview-stage、Offer 与终态计数；Interview/Offer 独立模块仍标注后续 Phase。

## 状态模型

- Queue：`WAITING → READY/NEED_REVIEW`，`READY/NEED_REVIEW → APPROVED`，`APPROVED → PREPARED`；非终态均可 `SKIPPED/BLOCKED`。`SUCCESS` 仅表示用户明确确认已在外部完成投递并成功建立 CRM 记录，不表示系统自动提交。
- Application：`APPLIED → VIEWED/REPLIED/REJECTED/WITHDRAWN/CLOSED`；`VIEWED → REPLIED/REJECTED/WITHDRAWN/CLOSED`；`REPLIED → WRITTEN_TEST/INTERVIEW_1/HR_INTERVIEW/REJECTED/WITHDRAWN/CLOSED`；面试阶段按顺序推进并可进入 `OFFER`；允许的活动状态可进入 `REJECTED/WITHDRAWN/CLOSED`。历史纠正只追加事件，不覆盖日志。

## API

- Queue：`GET /api/v1/application-queue`、`POST /items`、`POST /items/batch`、`PATCH/DELETE /items/{id}`、`POST /items/{id}:approve|skip|prepare`。
- CRM：`GET/POST /api/v1/applications`、`GET/PATCH /api/v1/applications/{id}`、`POST /{id}/transitions`、`GET /{id}/logs`。
- Recruiter：`GET/POST /api/v1/recruiters`、`GET/PATCH /api/v1/recruiters/{id}`、`GET/POST /api/v1/recruiters/{id}/interactions`。
- Policy：`GET /api/v1/platform-policies/{platform}`，未知平台返回有效的 Manual-only 决策视图而不是 404。

所有资源必须按当前 JWT 用户做 Ownership 校验，客户端不得传 `userId`。所有响应继续使用统一 `ApiResponse` 与 TraceId。

## Frontend

新增 Application Center，包含 Queue、CRM、Timeline、Recruiter/人工沟通四个真实数据区域；Recommendation 提供真实“加入队列”入口；Dashboard 展示真实投递漏斗与待处理 Queue。风格延续浅色、轻边框、大留白，不使用假 KPI，不提供无效自动提交按钮。

## 验证

必须实际执行：

- `mvn clean test`、`mvn package`
- AI Service 既有测试与 Ruff 回归
- `npm run build`
- Docker Compose 配置与 MySQL/Redis/Milvus 健康检查
- 现有库 V4→V5、全新临时库 V1→V5
- 完整 HTTP Smoke：登录、入队、批量、幂等、唯一活动项、批准、Assist Prepare 无提交、人工确认创建 Application、合法/非法迁移、日志不变性、Recruiter/Interaction、Analytics、Ownership
- 重启 Backend 后持久化复验
- 浏览器真实登录和 Application Center E2E，控制台零错误

任一核心项未实际通过则不得声明 Phase 5 Complete。

## 禁止跨阶段

不得实现 Resume Tailor、AI Communication Draft、消息自动发送、Chrome Extension、Playwright 自动投递、验证码破解、风控绕过、未授权采集或 Authorized Automation 外部提交。

## 最终报告

严格输出 `PROJECT`、`PHASE`、`PHASE_STATUS`、各 Build/Test/Run、Migration、Persistence、Redis/Milvus regression、Queue/CRM/State Machine/Immutable Log/Ownership/Policy/Manual-Assist boundary、Recruiter、Analytics、Frontend Integration、HTTP Smoke、Browser E2E 和 Git Status 的 PASS/FAIL/BLOCKED，以及文件数、测试数、HTTP 数、表数、已完成/未完成/阻塞/技术债务/下一阶段建议。
