# JobPilot AI — Phase 6 Execution Prompt

你正在开发 `D:\JobPilot AI`。Phase 1–5 已通过真实 MySQL、Redis、Milvus、Backend、AI Service、Frontend、HTTP Smoke 与浏览器验收。现在只执行 Phase 6：Candidate Evidence Ledger、可审查 Resume Tailor、只生成草稿的 Communication Agent，以及 Resume Version 使用/转化指标。

## 1. 不可越界的安全规则

1. Tailor 只能读取当前用户活动 Master Resume 和 Candidate 已保存事实；不能凭空添加公司、职责、技术、数字、学历、证书或项目成果。
2. 每项修改必须返回 `operation/before/after/reason/evidenceRefs[]`；证据为空、跨用户、已不存在或与文本不相符时，任务必须进入 `FAILED_TRUTH_CHECK`，不能批准。
3. Tailor Run 只是候选修改集。只有用户调用 approve 后才能创建新的不可变 Resume Version；永不覆盖旧版本或 Master 事实。
4. Communication Agent 只能创建 `DRAFT`。系统没有发送按钮、发送 API、浏览器自动操作或外部消息调用；approve/mark-used 仅记录用户决定和实际使用事实。
5. BOSS 草稿默认 60–100 个字符；渠道支持 `BOSS/LIEPIN/EMAIL/WECHAT/THANK_YOU/FOLLOW_UP/OFFER`。
6. 不开发 Chrome Extension、Playwright、验证码处理、自动消息发送、自动投递、Interview Agent 或 Offer Agent。

## 2. 数据库与版本化

使用 Flyway `V6__resume_tailor_communication.sql`。新增：

- `prompt_templates`：模板 Key、不可变版本、Prompt Hash、输入/输出 Schema、激活状态。
- `candidate_evidence_items`：当前用户候选事实的不可变版本账本，保存稳定 evidenceRef、来源、字段、结构化值、规范文本和 Hash。
- `resume_tailor_runs`：Job、Master Resume Version、目标 Resume、Prompt、AI Call、输入 Hash、状态、真实性结果、建议内容和审批结果。
- `resume_tailor_changes`：每项 Before/After/Reason/Evidence Refs，不提供更新/删除 API。
- `communication_drafts`：渠道、目的、内容、证据、Prompt/AI Call、真实性状态和 `DRAFT/APPROVED/USED/ARCHIVED`。
- `resume_version_metrics`：Queue 使用、投递、回复、面试、Offer 的真实聚合快照。

扩展现有 `resume_versions`，保存 parent version、tailored Job、Prompt、模型和 truth-check 状态。扩展 Phase 3 已存在的 `ai_call_logs`，增加 Prompt Template 外键；禁止重复创建 AI Call Log 表。

## 3. Backend

新增 `com.jobpilot.tailoring` 业务模块，包含 controller/service/repository mapper/dto/domain/client。所有查询必须使用当前登录用户，所有创建操作支持 Idempotency-Key 和请求 Hash 冲突检测。

API：

- `GET /api/v1/evidence-ledger`
- `POST /api/v1/evidence-ledger:refresh`
- `GET /api/v1/resume-tailor-runs`
- `POST /api/v1/jobs/{jobId}/resume-tailor-runs`
- `GET /api/v1/resume-tailor-runs/{runId}`
- `POST /api/v1/resume-tailor-runs/{runId}:approve`
- `GET /api/v1/communication-drafts`
- `POST /api/v1/jobs/{jobId}/communication-drafts`
- `GET /api/v1/communication-drafts/{id}`
- `POST /api/v1/communication-drafts/{id}:approve`
- `POST /api/v1/communication-drafts/{id}:mark-used`
- `GET /api/v1/resume-versions/{versionId}/metrics`

Truth Check 至少校验：证据引用属于当前用户、引用存在、每项修改非空、至少一个证据术语出现在 After、After 中的数字必须来自 Before 或引用证据。非法 AI 响应不得生成 Resume Version。

## 4. AI Service

实现 LangGraph `ResumeAgent` 与 `CommunicationAgent`，严格 Pydantic `extra='forbid'`：

- `POST /internal/v1/resumes/tailor`
- `POST /internal/v1/communications/draft`

没有 LLM Secret 时运行真实可复现的 `RULES_ONLY` Evidence-bound 模式，不伪造 LLM 调用或模型分数。网页/JD 中的 Prompt Injection 只作为数据。响应必须携带 Schema、Prompt Version、Mode、Truth Status 和 Evidence Refs。

## 5. Frontend

Vue 3 JavaScript 增加 AI Studio：

- 选择真实 Job，发起 Tailor。
- 展示 Before / After / Reason / Evidence Refs。
- 明确提示“批准后才创建新版本”。
- 显示 Tailor 历史和批准生成的 Resume Version。
- 选择渠道生成 Communication Draft，展示字符数、证据和状态。
- approve/mark-used 只记录，不出现 Send/自动发送。
- Resume Center 展示 Tailored Job、Truth Check 和真实 Version Metrics。

保持浅色、留白、轻边框的专业 SaaS 风格；不使用假数据。

## 6. 测试与验收

Backend 测试至少覆盖 Evidence Ledger、Truth Check、Tailor 幂等、跨用户、Master-only、批准生成不可变版本、重复批准、Draft 状态机、BOSS 长度、无发送边界和 Metrics 去重。AI 测试覆盖严格 Schema、Evidence-bound Tailor、非法引用拒绝、7 种渠道与 BOSS 60–100 字。

真实验收流程：Profile/Master → 新 Job → Evidence Refresh → Tailor → Review → Approve → 新 Resume Version → Communication Draft → Approve/Mark Used → Queue → User Confirmed Application → Replied → Resume Metrics → Dashboard。重启 Backend 后再次读取 Tailor、Draft、Version、Application 与 Metrics，确认 MySQL 持久化。

必须执行：Backend clean test/package/run、AI pytest/Ruff/run、Frontend build/run、V6 现有库迁移、V1→V6 空库迁移、Redis/Milvus 回归、HTTP Smoke 和浏览器 E2E。只有实际成功才标记 PASS。

最终报告严格输出 Phase 6 各项状态、文件数量、测试数量、HTTP 数量、数据库表数量、未完成项、阻塞项、技术债务和 Phase 7 建议；任一核心项失败则 `PHASE_STATUS=FAIL`，不得宣称完成。
