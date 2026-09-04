# JobPilot AI API 设计

> 风格：REST + JSON，版本化外部 API 基路径 `/api/v1`，OpenAPI 3.1 为契约源。Phase 1–2 同时提供需求指定的 `/api` 兼容路径。  
> 客户端：Vue Web、Chrome Extension；AI Service 与 Automation Worker 使用独立内部身份。

## 1. 通用约定

### 1.1 URL 与资源 ID

- URL 使用复数名词和 kebab-case，例如 `/api/v1/resume-versions`。
- API 暴露 `publicId`（ULID），不暴露数据库自增/内部主键。
- 行为不是自然 CRUD 时使用子资源或动作端点，例如 `/jobs/{jobId}/match-runs`，避免万能 `/action`。
- 外部 API 通过 `/api/v1` 版本化；内部 AI API 使用 `/internal/v1`，禁止直接暴露公网。

Phase 1 的前端和验收脚本使用需求明确指定的 `/api/auth`、`/api/candidate`、`/api/skills`、`/api/resumes` 与 `/api/dashboard` 路径。Controller 同时映射等价的 `/api/v1/...` 路径，响应和授权逻辑完全共用，不维护两套实现；后续客户端迁移到 `/api/v1` 后再弃用无版本别名。

### 1.2 统一成功响应

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "01K...",
  "timestamp": "2026-09-01T01:30:00.123Z"
}
```

- `code=0` 代表成功。
- HTTP 状态码表达传输/协议结果；业务 `code` 便于客户端稳定分支。
- `204 No Content` 不包响应体；其余成功响应统一包裹。

### 1.3 统一错误响应

```json
{
  "code": 4001001,
  "message": "请求参数不合法",
  "data": {
    "fieldErrors": [
      {"field": "salaryMin", "reason": "must be greater than or equal to 0"}
    ]
  },
  "traceId": "01K...",
  "timestamp": "2026-09-01T01:30:00.123Z"
}
```

错误码段：

| 范围 | 含义 | 示例 |
|---|---|---|
| `4001xxx` | 参数/格式错误 | Validation、文件格式 |
| `4010xxx` | 未认证/Token 错误 | Access Token 过期 |
| `4030xxx` | 权限/授权/合规限制 | 自动化未授权 |
| `4040xxx` | 资源不存在 | Job 不存在 |
| `4090xxx` | 冲突/幂等/非法状态迁移 | Application 状态冲突 |
| `4220xxx` | 内容可读但业务不可处理 | JD 无法解析、AI Schema 不合法 |
| `4290xxx` | 限流/预算超限 | AI Rate Limit |
| `5000xxx` | 内部错误 | 未预期异常 |
| `5020xxx` | 外部 Provider 错误 | LLM/Embedding 不可用 |
| `5030xxx` | 服务暂不可用 | 队列拥堵/维护 |

生产响应不返回堆栈、SQL、Prompt、Token 或 Provider 原始敏感错误。

### 1.4 分页、排序和过滤

优先使用游标分页：

```http
GET /api/v1/jobs?limit=20&cursor=MjAyNi0wOS0wMVQw...&sort=publish_desc
```

```json
{
  "code": 0,
  "message": "success",
  "data": {
    "items": [],
    "nextCursor": "...",
    "hasMore": true
  },
  "traceId": "01K...",
  "timestamp": "2026-09-01T01:30:00.123Z"
}
```

- `limit` 默认 20，最大 100。
- 过滤字段使用显式白名单；未知参数返回 400，不静默忽略。
- 排序参数使用服务端白名单。Phase 2 Job API 支持 `publish_desc` 与 `updated_desc`；后续资源若引入多字段排序，必须另行版本化契约。
- Dashboard 聚合等小结果不分页。

### 1.5 时间、金额和空值

- 时间使用 ISO 8601 UTC，例如 `2026-09-01T01:30:00.123Z`。
- 纯日期使用 `YYYY-MM-DD`；用户时区由 Profile/请求头决定。
- 金额使用 JSON 字符串避免浮点精度问题：`{"amount":"18000.00","currency":"CNY"}`。
- `PATCH` 中省略字段表示“不修改”，显式 `null` 仅用于 Schema 允许清空的字段。

### 1.6 幂等与并发

- 导入、加入队列、创建 Application、外部提交等 POST 支持 `Idempotency-Key`。
- 服务保存请求摘要；同一 Key + 同一请求返回首次结果，同一 Key + 不同请求返回 `4090002`。
- 更新聚合使用 `If-Match: "<version>"`；版本冲突返回 `4090003`。
- API 不接受客户端直接设置审计字段、分数或 AI 调用状态。

### 1.7 异步任务

AI 解析、Embedding、匹配、Tailor、批量导入使用异步任务：

```http
HTTP/1.1 202 Accepted
Location: /api/v1/tasks/01K...
```

```json
{
  "code": 0,
  "message": "accepted",
  "data": {
    "taskId": "01K...",
    "status": "PENDING",
    "resourceId": "01K..."
  },
  "traceId": "01K...",
  "timestamp": "2026-09-01T01:30:00.123Z"
}
```

`GET /api/v1/tasks/{taskId}` 返回 `PENDING/RUNNING/SUCCEEDED/FAILED/CANCELLED/BLOCKED`、安全错误摘要和最终资源链接。前端先轮询，后续可增加 SSE；任务结果仍以业务资源 API 为真相。

## 2. 身份认证与安全

### 2.1 Auth API

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/v1/auth/bootstrap` | 仅首次启动创建本地用户；成功后永久关闭 |
| `POST` | `/api/v1/auth/login` | 用户名/邮箱 + 密码登录 |
| `POST` | `/api/v1/auth/refresh` | 轮换 Refresh Token |
| `POST` | `/api/v1/auth/logout` | 撤销当前 Token Family |
| `GET` | `/api/v1/auth/me` | 当前用户与权限摘要 |
| `POST` | `/api/v1/auth/password` | 修改密码并按策略撤销其他会话 |
| `GET` | `/api/v1/auth/sessions` | 查看活动会话 |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | 撤销指定会话 |

登录成功返回短时 Access Token。Refresh Token 默认使用 `HttpOnly + Secure + SameSite=Strict` 本地 Cookie；若桌面部署不能满足 Cookie 模式，则由安全存储适配器托管，绝不写 localStorage。

### 2.2 Chrome Extension 配对

| 方法 | 路径 | 用途 |
|---|---|---|
| `POST` | `/api/v1/extension/pairing-codes` | Web 登录后生成 5 分钟、一次性配对码 |
| `POST` | `/api/v1/extension/pairings` | 扩展兑换配对码，获得受限短时凭据 |
| `POST` | `/api/v1/extension/tokens/refresh` | 轮换扩展凭据 |
| `DELETE` | `/api/v1/extension/pairings/{pairingId}` | 撤销扩展设备 |

扩展 Scope 仅允许岗位采集、查看匹配摘要、入队和生成草稿；不允许账户管理、数据导出或自动提交。

### 2.3 请求安全

- `Authorization: Bearer <access-token>`；日志中完全丢弃该头。
- `X-Trace-Id` 可由可信客户端提供，否则服务生成；响应总返回最终 Trace ID。
- CORS 精确允许本地 Web Origin 与已登记 Extension Origin。
- 上传端点独立大小/频率限制；AI 端点有用户预算和并发限制。

## 3. Candidate Profile API（Phase 1）

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/candidate-profile` | 获取完整 Digital Twin |
| `PUT` | `/api/v1/candidate-profile` | 首次创建/整体更新基础信息 |
| `PATCH` | `/api/v1/candidate-profile` | 局部更新；需 `If-Match` |
| `POST` | `/api/v1/candidate-profile/extraction-runs` | 从指定 Resume Version 抽取候选人事实 |
| `GET` | `/api/v1/candidate-profile/extraction-runs/{runId}` | 查看抽取结果/任务状态 |
| `POST` | `/api/v1/candidate-profile/facts:confirm` | 人工确认选中的抽取事实 |
| `GET/POST` | `/api/v1/candidate-profile/educations` | 教育列表/创建 |
| `PATCH/DELETE` | `/api/v1/candidate-profile/educations/{id}` | 修改/逻辑删除教育 |
| `GET/POST` | `/api/v1/candidate-profile/experiences` | 经历列表/创建 |
| `PATCH/DELETE` | `/api/v1/candidate-profile/experiences/{id}` | 修改/删除经历 |
| `GET/POST` | `/api/v1/candidate-profile/projects` | 项目列表/创建 |
| `PATCH/DELETE` | `/api/v1/candidate-profile/projects/{id}` | 修改/删除项目 |
| `GET/PUT` | `/api/v1/candidate-profile/preferences` | 获取/替换版本化偏好 |
| `GET/PUT` | `/api/v1/candidate-profile/skills` | 获取/更新技能熟练度和证据 |

确认事实请求示例：

```json
{
  "extractionRunId": "01K...",
  "acceptedFacts": [
    {"factId": "fact_education_1", "value": {"schoolName": "..."}},
    {"factId": "fact_skill_1", "value": {"canonicalKey": "spring_boot", "proficiency": 95}}
  ],
  "rejectedFactIds": ["fact_project_unsupported_1"]
}
```

服务只能保存来自用户原文或人工输入的事实；AI 推断必须标记 `UNCONFIRMED`。

## 4. Resume Center API（Phase 1 / 6）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/files/resumes` | 上传 PDF/DOCX；multipart，返回 File ID |
| `GET` | `/api/v1/resumes` | Resume 聚合列表 |
| `POST` | `/api/v1/resumes` | 创建 Master/方向 Resume |
| `GET` | `/api/v1/resumes/{resumeId}` | Resume 详情 |
| `PATCH` | `/api/v1/resumes/{resumeId}` | 名称/状态更新 |
| `GET` | `/api/v1/resumes/{resumeId}/versions` | 版本列表和转化指标 |
| `POST` | `/api/v1/resumes/{resumeId}/versions` | 从上传文件或结构化内容创建版本 |
| `GET` | `/api/v1/resume-versions/{versionId}` | 版本结构化内容 |
| `GET` | `/api/v1/resume-versions/{versionId}/metrics` | Queue/Application/Reply/Interview/Offer 事实指标 |
| `GET` | `/api/v1/resume-versions/{versionId}/download` | 授权下载/短时链接 |
| `GET` | `/api/v1/evidence-ledger` | 当前用户的最新版本化候选人证据快照 |
| `POST` | `/api/v1/evidence-ledger:refresh` | 从 Candidate 与 Master Resume 刷新 Evidence Ledger |
| `GET` | `/api/v1/resume-tailor-runs` | Tailor 历史列表 |
| `POST` | `/api/v1/jobs/{jobId}/resume-tailor-runs` | 为 Job 生成针对性版本（Phase 6） |
| `GET` | `/api/v1/resume-tailor-runs/{runId}` | 获取差异、证据和真实性校验 |
| `POST` | `/api/v1/resume-tailor-runs/{runId}:approve` | 人工批准成为 Resume Version |

Tailor 结果必须返回 `changes[]`，每条含 `operation`、`before`、`after`、`reason`、`evidenceRefs[]`；任何无证据新增使任务进入 `FAILED_TRUTH_CHECK`。

## 5. Job 与 Collector API（Phase 2）

### 5.1 导入

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/jobs` | 手工录入一个岗位 |
| `POST` | `/api/v1/job-imports/url` | 导入一个用户提供的公开/授权 URL |
| `POST` | `/api/v1/job-imports/files` | CSV/Excel 导入 |
| `POST` | `/api/v1/extension/job-captures` | 扩展提交当前可见页面快照 |
| `GET` | `/api/v1/job-imports/{importId}` | 导入进度、成功/失败数 |
| `GET` | `/api/v1/job-imports/{importId}/errors` | 行级安全错误信息 |

扩展采集请求示例：

```json
{
  "platform": "BOSS",
  "pageUrl": "https://example.invalid/job/123",
  "capturedAt": "2026-09-01T01:30:00.123Z",
  "userInitiated": true,
  "adapterVersion": "boss-visible-v1",
  "visibleFields": {
    "jobTitle": "Java 后端开发工程师",
    "companyName": "示例公司",
    "salaryText": "15-25K·14薪",
    "city": "杭州",
    "descriptionText": "..."
  },
  "contentHash": "sha256:..."
}
```

Backend 不接收 Cookie、页面 Local Storage、Authorization 或隐藏页面数据。

### 5.2 岗位资源

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/jobs` | 岗位列表、筛选、排序、游标分页 |
| `GET` | `/api/v1/jobs/{jobId}` | 统一 Job Schema、来源、技能与解析历史；Phase 2 不返回匹配分数 |
| `PATCH` | `/api/v1/jobs/{jobId}` | 人工修订允许字段，保留修订审计 |
| `DELETE` | `/api/v1/jobs/{jobId}` | 逻辑删除未有关联申请的岗位 |
| `POST` | `/api/v1/jobs/{jobId}/parse-runs` | 重新解析 JD |
| `GET` | `/api/v1/jobs/{jobId}/sources` | 查看合并来源 |
| `POST` | `/api/v1/jobs/{jobId}/sources` | 手工附加来源 |
| `POST` | `/api/v1/jobs/{jobId}:ignore` | 忽略并记录原因 |
| `POST` | `/api/v1/jobs/{jobId}:restore` | 从忽略恢复 |
| `GET` | `/api/v1/skills` | 技能本体搜索 |

支持的 Phase 2 岗位筛选：`title`、`city`、`salaryMin`、`salaryMax`、`education`、`experienceMin`、`experienceMax`、`company`、`industry`、`platform`、`skill`、`companySize`、`status`、`parseStatus`、`publishFrom`、`publishTo`。排序白名单为 `publish_desc` 与 `updated_desc`，分页使用不透明 `cursor` + `limit`。匹配等级/分数从 Phase 3 起提供，Phase 2 不伪造。

### 5.3 公司资源

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/companies?search=` | 搜索规范公司目录 |
| `GET` | `/api/v1/companies/{companyId}` | 公司详情 |
| `POST` | `/api/v1/companies` | 创建公司 |
| `PATCH` | `/api/v1/companies/{companyId}` | 带乐观锁版本更新 |
| `DELETE` | `/api/v1/companies/{companyId}` | 无有效岗位引用时逻辑删除 |

Phase 2 与 Phase 1 一样同时提供 `/api/...` 兼容映射。URL 导入只允许用户明确触发的公开 HTTP(S) 地址，并在每次重定向后重新执行 SSRF 地址校验、响应类型和大小限制。CSV/XLSX 使用 `Idempotency-Key`，任务返回逐行成功/失败数和安全错误。

## 6. Matching 与 Recommendation API（Phase 3 / 4）

### 6.1 匹配任务

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/jobs/{jobId}/match-runs` | 对一个岗位执行完整匹配 |
| `GET` | `/api/v1/match-runs/{runId}` | 匹配进度与错误 |
| `POST` | `/api/v1/match-runs/{runId}:retry` | 重试 FAILED/DEAD 任务，复用同一任务 ID |
| `GET` | `/api/v1/jobs/{jobId}/matches` | 历史匹配版本 |
| `GET` | `/api/v1/job-matches/{matchId}` | 分数、权重、解释和证据 |
| `GET` | `/api/v1/match-configs` | 配置版本列表 |
| `POST` | `/api/v1/match-configs` | 创建新配置版本 |
| `POST` | `/api/v1/match-configs/{id}:activate` | 激活配置，写审计 |
| `GET/POST` | `/api/v1/hard-filter-rules` | 规则列表/创建 |
| `PATCH` | `/api/v1/hard-filter-rules/{id}` | 更新为新版本或启停 |

请求支持 Header `Idempotency-Key` 与 Body `idempotencyKey`；同 Key + 同输入返回同一 Run，同 Key + 不同输入返回 `409`。`force=false` 可复用相同输入 Hash 的成功结果，`force=true` 创建新版本但仍复用内容 Hash 向量缓存。输入 Hash 覆盖岗位、Profile、技能/项目/教育/经历事实、Resume Version、Match Config、算法和活动 Hard Filter 规则快照。批量重算留到 Phase 4 推荐中心，不在 Phase 3 假实现。

未配置 `LLM_BASE_URL + LLM_API_KEY + LLM_MODEL` 时，返回 `llmStatus=SKIPPED_NOT_CONFIGURED`、`llmScore=null`，其余权重重新归一化；禁止生成替代分数。

匹配结果核心 Schema：

```json
{
  "jobId": "01K...",
  "candidateProfileVersion": 3,
  "resumeVersionId": "01K...",
  "hardFilter": {
    "result": "DOWNGRADE",
    "reasons": [
      {"rule": "experience_years", "expected": ">=3", "actual": "graduate", "action": "DOWNGRADE", "penalty": "8.00"}
    ]
  },
  "scores": {
    "skill": "91.00",
    "embedding": "87.00",
    "llm": "89.00",
    "project": "92.00",
    "preference": "95.00",
    "company": "80.00",
    "penalty": "8.00",
    "overall": "81.80"
  },
  "level": "A",
  "advantages": [
    {"text": "Spring Boot 与 Redis 符合核心要求", "candidateEvidenceRefs": ["skill:01K..."], "jobEvidence": "..."}
  ],
  "gaps": [{"skill": "kubernetes", "severity": "MEDIUM", "evidence": "JD requires ..."}],
  "risks": [],
  "recommendation": "RECOMMEND",
  "reason": "Java 后端主线高度匹配，但经验年限存在门槛风险。",
  "recommendedResumeVersionId": "01K...",
  "algorithmVersion": "match-v1",
  "configVersion": 2,
  "evaluatedAt": "2026-09-01T01:30:00.123Z"
}
```

### 6.2 推荐中心

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/recommendations` | `ALL/TOP/UNEVALUATED/IGNORED/FAVORITE`、等级/公司/城市/薪资/技能/来源筛选、游标分页与白名单排序 |
| `GET` | `/api/v1/recommendations/{id}` | 推荐事实、最新不可变 Match 分析与行为事件 |
| `GET` | `/api/v1/recommendations/{id}/events` | 不可变收藏/忽略历史 |
| `GET` | `/api/v1/recommendations/capabilities` | 返回 Application 等能力边界；Phase 4 的 Application 为不可用 |
| `POST` | `/api/v1/recommendations/{id}:favorite` | 收藏，Body 携带乐观锁 `version` |
| `POST` | `/api/v1/recommendations/{id}:unfavorite` | 取消收藏 |
| `POST` | `/api/v1/recommendations/{id}:ignore` | 忽略，保存原因用于反馈 |
| `POST` | `/api/v1/recommendations/{id}:restore` | 恢复 |
| `POST` | `/api/v1/recommendation-refresh-runs` | 批量刷新；支持 `Idempotency-Key`，复用 Phase 3 Match |
| `GET` | `/api/v1/recommendation-refresh-runs/{id}` | 批次与逐项状态 |
| `POST` | `/api/v1/recommendation-refresh-runs/{id}:retry-failed` | 仅重试失败/Dead 项 |

## 7. Application Queue 与 CRM API（Phase 5）

### 7.1 Queue

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/application-queue` | 队列列表 |
| `POST` | `/api/v1/application-queue/items` | 单个岗位入队；需 Idempotency-Key |
| `POST` | `/api/v1/application-queue/items/batch` | 明确选中的岗位批量入队 |
| `PATCH` | `/api/v1/application-queue/items/{itemId}` | 优先级、Resume、Greeting 引用、计划时间与乐观锁版本；入队后不可切换模式 |
| `DELETE` | `/api/v1/application-queue/items/{itemId}` | 移除未执行项 |
| `POST` | `/api/v1/application-queue/items/{itemId}:approve` | 人工批准 |
| `POST` | `/api/v1/application-queue/items/{itemId}:skip` | 跳过并记录原因 |
| `POST` | `/api/v1/application-queue/items/{itemId}:prepare` | 准备 ASSIST 流程，不提交 |

入队必须校验：Job 活跃、Match 存在或明确允许未评估、Resume 可用、模式符合 Platform Policy、没有活动重复项。

### 7.2 Application CRM

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/applications` | CRM 列表和筛选 |
| `POST` | `/api/v1/applications` | 记录已经在外部完成的 MANUAL/ASSIST 投递；必须提供 `confirmedExternalSubmission=true` 和 `Idempotency-Key` |
| `GET` | `/api/v1/applications/{applicationId}` | 详情、Timeline、Recruiter、面试、Offer |
| `POST` | `/api/v1/applications/{applicationId}/transitions` | 执行合法状态迁移 |
| `GET` | `/api/v1/applications/{applicationId}/logs` | 不可变时间线 |
| `PATCH` | `/api/v1/applications/{applicationId}` | 仅允许备注、关联 Recruiter 等非状态字段 |
| `GET` | `/api/v1/applications/kpis` | Queue 与 Application 各阶段唯一到达数 |
| `GET` | `/api/v1/platform-policies/{platform}` | 读取有效策略；未知或过期时降级 `MANUAL_ONLY` |
| `PUT` | `/api/v1/platform-policies/{platform}` | 人工维护安全策略；Phase 5 不接受 `AUTHORIZED_AUTOMATION` |
| `GET/POST` | `/api/v1/recruiters` | Recruiter 列表/创建 |
| `GET/PATCH` | `/api/v1/recruiters/{id}` | 详情/跟进设置 |
| `GET/POST` | `/api/v1/recruiters/{id}/interactions` | 人工沟通记录；不会发送外部消息 |

状态迁移请求：

```json
{
  "toStatus": "REPLIED",
  "occurredAt": "2026-09-01T06:30:00.000Z",
  "source": "USER",
  "note": "HR 询问到岗时间",
  "evidence": null,
  "version": 2
}
```

非法迁移返回 `4095201`，消息给出当前状态和允许目标，不修改 Application，也不追加 Application Log。Application 创建只接受 `MANUAL/ASSIST`；`ASSIST :prepare` 仅产生待用户操作的准备结果，响应必须保持 `externallySubmitted=false`、`applicationCreated=false`。

## 8. Communication API（Phase 6）

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/jobs/{jobId}/communication-drafts` | 生成 BOSS/邮件/微信等草稿 |
| `GET` | `/api/v1/communication-drafts` | 当前用户的草稿历史，可按 Job 过滤 |
| `GET` | `/api/v1/communication-drafts/{id}` | 草稿与证据 |
| `POST` | `/api/v1/communication-drafts/{id}:approve` | 人工确认草稿 |
| `POST` | `/api/v1/communication-drafts/{id}:mark-used` | 用户实际使用后记录 |

BOSS 打招呼校验为 60–100 字符，内容需要真实性规则检查。所有 Draft 响应显式返回 `externallySent=false`；Phase 6 不存在任何消息发送 API。独立 Follow-up 任务列表留到后续阶段，当前以 `channel=FOLLOW_UP` 生成草稿。

## 9. Interview、Offer 与 Analytics API（Phase 8 / 9）

### 9.1 Interview

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET/POST` | `/api/v1/interviews` | 列表/创建 |
| `GET/PUT/DELETE` | `/api/v1/interviews/{id}` | 详情/乐观锁更新/逻辑删除 |
| `POST` | `/api/v1/interviews/{id}/rounds` | 创建轮次 |
| `PUT/DELETE` | `/api/v1/interview-rounds/{id}` | 更新/逻辑删除轮次 |
| `POST` | `/api/v1/interview-rounds/{id}/questions:predict` | 幂等生成 PREDICTED 问题 |
| `POST` | `/api/v1/interview-rounds/{id}/questions` | 录入 ACTUAL/MANUAL 问题 |
| `PUT` | `/api/v1/interview-questions/{id}` | 更新用户录入的问题 |
| `POST` | `/api/v1/interview-questions/{id}/answer-notes` | 追加 Answer Note 版本 |
| `POST` | `/api/v1/interviews/{id}/reviews:generate` | 生成不可变复盘 |
| `GET` | `/api/v1/interviews/{id}/reviews` | 复盘版本历史 |
| `POST` | `/api/v1/interview-reviews/{id}:confirm` | 人工确认并更新 Knowledge Gap |
| `GET` | `/api/v1/knowledge-gaps` | 薄弱点与复习建议 |
| `POST` | `/api/v1/knowledge-gaps/{id}:activate` | 人工激活 |
| `POST` | `/api/v1/knowledge-gaps/{id}:resolve` | 人工解决 |
| `POST` | `/api/v1/knowledge-gaps/{id}:dismiss` | 人工忽略 |
| `GET/POST` | `/api/v1/interview-reminders` | 站内提醒列表/创建 |
| `PUT/DELETE` | `/api/v1/interview-reminders/{id}` | 改期/逻辑删除 |
| `POST` | `/api/v1/interview-reminders/{id}:done` | 完成提醒 |
| `POST` | `/api/v1/interview-reminders/{id}:cancel` | 取消提醒 |
| `GET` | `/api/v1/interviews/dashboard` | 近期面试、待办、待确认 Review 和活动 Gap |

Interview 列表支持分页及状态、日期、Company、Role、Job、Application 筛选。所有引用和子资源均按当前用户校验；预测/复盘使用 `Idempotency-Key`，回答和 Review 采用追加版本。确认 Review 不会自动激活全部 Gap，必须逐条人工操作，也不会修改 Application、Candidate、Resume 或 Match。

### 9.2 Offer

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET/POST` | `/api/v1/offers` | 分页筛选/创建；创建必须引用当前用户的 OFFER Application |
| `GET/PUT/DELETE` | `/api/v1/offers/{id}` | 详情/全量更新/逻辑删除；更新使用乐观锁版本 |
| `POST` | `/api/v1/offers/{id}:status` | 白名单状态迁移；兼容 `/{id}/status` |
| `GET` | `/api/v1/offers/dashboard` | 进行中、已接受及近期截止事实 |
| `GET/POST` | `/api/v1/offer-deadlines` | 截止事项列表/创建 |
| `PUT/DELETE` | `/api/v1/offer-deadlines/{id}` | 编辑/逻辑删除截止事项 |
| `POST` | `/api/v1/offer-deadlines/{id}:done` | 完成截止事项；兼容 `/{id}/done` |
| `POST` | `/api/v1/offer-deadlines/{id}:cancel` | 取消截止事项；兼容 `/{id}/cancel` |
| `GET/POST` | `/api/v1/offers/comparisons` | 不可变比较历史/创建新版本；兼容 `/offer-comparisons` |
| `GET` | `/api/v1/offers/comparisons/{id}` | 查询不可变输入、权重、结果和解释 |

Offer 比较只对同币种现金项做归一化。跨币种返回 `cashComparable=false`，不会使用虚构汇率；主观维度必须由用户显式评分，未知项不进入有效权重分母。比较创建和 Analytics 重算都要求 `Idempotency-Key`。

### 9.3 Dashboard 与 Analytics

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/analytics/dashboard` | 真实 Job/Match/Recommendation/Queue/Application KPI；Phase 8 Interview 数据由 `/interviews/dashboard` 合并展示 |
| `GET` | `/api/v1/analytics/levels` | Match 等级分布，含分子/分母/样本量 |
| `GET` | `/api/v1/analytics/sources` | 岗位来源分布，含分子/分母/样本量 |
| `GET` | `/api/v1/analytics/funnel` | 已发现/已评估/S-A-B 推荐/收藏/忽略/入队/投递/回复漏斗；Application 阶段按唯一 Application 到达事件计数 |
| `POST` | `/api/v1/analytics:rebuild` | 按日期范围从事实源重建 `analytics_daily`，最长 366 日 |
| `GET` | `/api/v1/analytics/platforms` | 平台转化率 |
| `GET` | `/api/v1/analytics/job-directions` | 岗位方向转化率 |
| `GET` | `/api/v1/analytics/companies` | 公司转化率 |
| `GET` | `/api/v1/analytics/resume-versions` | Resume Version 转化率 |
| `GET` | `/api/v1/analytics/match-score-buckets` | 评分段回复/面试/Offer 率 |

所有 Analytics 接口必须返回分母、分子和样本量；样本量过小显示 `insufficientSample=true`，不输出误导性结论。

Phase 9 的统一入口为 `GET /api/v1/analytics/complete?from=&to=`：返回 `DISCOVERED/EVALUATED/APPLIED/REPLIED/INTERVIEW/OFFER/ACCEPTED` 漏斗，以及 `PLATFORM/JOB_DIRECTION/COMPANY/CITY/SALARY_BAND/RESUME_VERSION/MATCH_SCORE_BUCKET` 七类维度的回复、面试和 Offer 转化。每条指标包含 `numerator`、`denominator`、`sampleSize`、`rate` 和 `insufficientSample`，零分母时 `rate=null`。

`POST /api/v1/analytics:rebuild` 保存不可变完整快照（兼容 `/analytics/complete:rebuild`）；`GET /api/v1/analytics/snapshots` 与 `/{id}` 查询历史。`GET /api/v1/privacy/export` 导出当前用户业务域数据且不含密码/Token Hash；`POST /api/v1/privacy/deletions:preview` 生成计数快照，`POST /api/v1/privacy/deletions:confirm` 必须提交预览 ID、版本、独立幂等键和精确确认短语。

## 10. Learning、Automation 与 Settings API（Phase 10）

Learning 重建、训练和 Shadow 请求必须提供 `Idempotency-Key`；激活/回滚必须携带当前乐观锁版本并由用户主动触发。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/learning/feedback:rebuild` | 从真实事件幂等重建 Feedback |
| `GET` | `/api/v1/learning/feedback/summary` | 数量、类型与最小样本门禁 |
| `POST` | `/api/v1/learning/models:train` | 时间切分并保存离线评估/版本 |
| `GET` | `/api/v1/learning/models[/{id}]` | 模型历史/详情 |
| `POST` | `/api/v1/learning/models/{id}:shadow` | 保存 Shadow 差异，不改在线排序 |
| `POST` | `/api/v1/learning/models/{id}:activate` | 用户确认激活 |
| `POST` | `/api/v1/learning/models/{id}:rollback` | 回滚至基线 |
| `GET` | `/api/v1/learning/dashboard` | Learning 页面真实聚合 |

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET/POST` | `/api/v1/automation-center/rules` | 规则列表/创建 |
| `PUT` | `/api/v1/automation-center/rules/{id}` | 乐观锁更新、启停 |
| `POST` | `/api/v1/automation-center/rules/{id}:run` | 幂等手工运行固定 Handler |
| `GET` | `/api/v1/automation-center/tasks[/{id}]` | Safe Task 历史/详情 |
| `POST` | `/api/v1/automation-center/tasks/{id}:retry` | 人工重试失败安全任务 |
| `POST` | `/api/v1/automation-center/tasks/{id}:cancel` | 取消未执行任务 |
| `GET` | `/api/v1/automation-center/suggestions` | 非破坏性建议 |
| `POST` | `/api/v1/automation-center/suggestions/{id}:accept|dismiss` | 记录人工决定，不直接删岗位 |
| `GET` | `/api/v1/automation-center/notifications` | 站内通知 |
| `POST` | `/api/v1/automation-center/notifications/{id}:read` | 标记已读 |
| `GET/PUT` | `/api/v1/platform-policies/{platform}` | Phase 5 已交付的安全策略；Phase 10 在此基础上扩展授权与自动任务 |
| `GET/POST` | `/api/v1/automation-center/authorizations` | 查看/登记明确授权 |
| `DELETE` | `/api/v1/automation-center/authorizations/{id}` | 撤销授权 |
| `GET` | `/api/v1/automation-center/policy-decision` | 查看 Policy/Authorization 安全决策 |
| `GET` | `/api/v1/automation-center/dashboard` | Rule/Task/Suggestion/Notification 和安全计数 |
| `GET` | `/api/v1/settings` | 非敏感配置和敏感配置状态 |
| `PUT` | `/api/v1/settings/{group}/{key}` | 更新配置；敏感值只写不读 |
| `GET/POST` | `/api/v1/prompts` | Prompt 版本列表/创建草稿 |
| `POST` | `/api/v1/prompts/{id}:activate` | 校验后激活 Prompt |
| `GET` | `/api/v1/ai-usage` | Token、成本和预算 |

设置 API 对密钥只返回 `configured: true/false`、Provider 和更新时间，不回显密钥。

## 11. Global Search API（Phase 4 后增量扩展）

```http
GET /api/v1/search?q=Redis&types=JOB,COMPANY,SKILL,STATUS&limit=10
```

Phase 4 只搜索当前用户可见的 Job、其引用的 Company、候选人/岗位可见 Skill 和状态字典，返回按类型分组的摘要与目标资源 URL。查询采用类型白名单、长度限制和字面包含语义；`%`、`_`、反斜杠不会变成 SQL 通配符。Recruiter、Interview Question 等类型在对应事实模块交付后再扩展。

## 12. Internal AI Service API

仅 Backend 可访问，使用服务身份、网络隔离、请求签名/短时服务 Token 和严格超时。

| 方法 | 路径 | 输入/输出 |
|---|---|---|
| `GET` | `/internal/v1/health` | 进程健康；不探测昂贵模型 |
| `GET` | `/internal/v1/readiness` | Provider/模型就绪状态 |
| `POST` | `/internal/v1/candidates/extract` | Resume 事实 → 严格 CandidateExtraction |
| `POST` | `/internal/v1/jobs/parse` | JD → 统一 Job Schema |
| `POST` | `/internal/v1/embeddings` | 版本化文本 → 向量引用/值（按部署模式） |
| `POST` | `/internal/v1/matches/evaluate` | 已确认 Candidate + Job → 维度分和证据 |
| `POST` | `/internal/v1/resumes/tailor` | 事实账本 + JD → 带证据的修改集 |
| `POST` | `/internal/v1/communications/draft` | 上下文 → 草稿 |
| `POST` | `/internal/v1/interviews/predict` | 上下文 → 问题预测 |
| `POST` | `/internal/v1/interviews/review` | 实际问答 → 复盘与 Knowledge Gap 建议 |

内部请求包含 `schemaVersion`、`taskId`、`traceId`、`promptVersion`、`deadlineAt`。响应不得携带未在 Schema 中声明的字段；Pydantic 配置 `extra='forbid'`。

## 13. Automation Worker API

Automation Worker 不向浏览器公开端口：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/internal/v1/automation/prepare` | 打开/辅助填写，不提交 |
| `POST` | `/internal/v1/automation/submit` | 仅有效授权 + 允许平台策略 |
| `POST` | `/internal/v1/automation/tasks/{id}:cancel` | 取消未完成任务 |
| `GET` | `/internal/v1/automation/tasks/{id}` | 状态和脱敏步骤 |

`submit` 必须同时提供短时授权令牌、Queue Approval ID、Platform Policy Version 和 Idempotency Key；任一缺失即拒绝。

## 14. OpenAPI 与契约治理

- Backend 代码生成/注解产出 OpenAPI，并在 CI 中与提交的契约快照比较。
- Frontend 业务源码保持 JavaScript；从 OpenAPI 生成 JavaScript Client/JSDoc 契约或执行运行时契约测试，禁止复制维护重复 DTO。Extension 到其独立 Phase 后可生成 TypeScript 类型。
- Backend ↔ AI 使用单独 JSON Schema 包；Java 和 Python 双方执行契约测试。
- 枚举增加遵守向后兼容；删除/重命名字段必须进入新 API/Schema 版本。
- API 示例中的公司、岗位和简历若作为 Seed，必须明确标记 `DEMO`。

## 15. API 测试基线

每个资源至少覆盖：

1. 正常创建/查询/更新。
2. 未认证、越权访问和资源归属隔离。
3. Validation 与未知字段。
4. Idempotency Key 重放与冲突。
5. `If-Match` 并发冲突。
6. 逻辑删除后不可读取/更新。
7. 敏感字段不出现在响应、日志或 OpenAPI 示例。
8. 状态机所有合法/非法迁移。
9. AI 超时、非法 JSON、额外字段、越界分数和 Provider 限流。
10. Platform Policy/Authorization 阻止未授权自动提交。
## Phase 7 Extension API

- Web JWT: `POST /api/v1/extension/pairing-codes`, `GET /api/v1/extension/devices`, `DELETE /api/v1/extension/devices/{id}`.
- Anonymous exchange with one-time/rotation checks: `POST /api/v1/extension/pairings`, `POST /api/v1/extension/tokens/refresh`.
- Extension scope only: `POST /api/v1/extension/job-captures`, `GET /api/v1/extension/jobs/{jobId}/workspace`, `POST /api/v1/extension/jobs/{jobId}:analyze`, `POST /api/v1/extension/jobs/{jobId}/queue`, `POST /api/v1/extension/jobs/{jobId}/communication-drafts`, `POST /api/v1/extension/queue/{queueId}:prepare`, `GET /api/v1/extension/automation-tasks/{id}`.

Extension Capture accepts exactly `platform,pageUrl,capturedAt,userInitiated,adapterVersion,visibleFields,contentHash`. Worker Prepare always returns `externallySubmitted=false`, `applicationCreated=false`, `finalConfirmationRequired=true` and a sequence of sanitized audit steps.

## Phase 11 Operations API

全部 Operations 业务 API 要求当前 Web 用户 JWT。健康和指标只暴露预定义、低基数和脱敏字段；运行记录的 Artifact 只允许仓库内相对路径。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/operations/overview` | Backend/MySQL/Redis/AI/Milvus、可观测性、预算、最近运行和安全边界聚合 |
| `GET` | `/api/v1/operations/ai-usage` | 从 `ai_call_logs` 聚合今日/月度 Provider、Token、成本、错误和熔断状态 |
| `GET` | `/api/v1/operations/ai-budget` | 当前用户预算策略和真实使用率/状态 |
| `PUT` | `/api/v1/operations/ai-budget` | 校验币种/阈值并以乐观锁更新当前用户预算 |
| `GET` | `/api/v1/operations/runs` | 当前用户可见的运行证据列表 |
| `POST` | `/api/v1/operations/runs` | 脚本写入幂等、脱敏的运行摘要；要求 `Idempotency-Key` |
| `GET` | `/api/v1/operations/runs/{id}` | 按 Ownership 读取单条运行证据 |

`/actuator/health`、`/actuator/info`、`/actuator/metrics` 和 `/actuator/prometheus` 用于本地运维。Prometheus 不包含用户名、Job ID、Trace ID 等高基数标签；OTLP 默认关闭。

## Phase 13 Onboarding API

两个接口都要求当前 Web 用户 JWT，不接受 `userId`、资源 ID 或可改变评分规则的客户端参数。响应沿用统一 `ApiResponse` 和 `X-Trace-Id`。

| 方法 | 路径 | 说明 |
|---|---|---|
| `GET` | `/api/v1/onboarding/overview` | 九项 Readiness、0–100 分、READY/NEEDS_WORK、完成数与质量摘要 |
| `GET` | `/api/v1/onboarding/data-quality` | 稳定 Code、严重级别、说明、资源类型和本地修复路径 |

Readiness 权重为：基本身份 15、求职目标 15、技能/核心技能 20、教育 10、经历 10、项目 10、Active Master Resume 10、Active Default Resume 5、当前 Resume Version 5。总分达到 80 且没有 Blocker 才可为 `READY`。

这两个接口是实时派生查询，不创建数据质量表，不把缺失信息自动写回 Candidate/Resume。问题为空时返回 `issues=[]` 和全零 Summary，不生成通用或模型猜测建议。

## Phase 14 Account Security & Settings API

全部接口要求当前 Web 用户 JWT，沿用统一 `ApiResponse` 与 `X-Trace-Id`。客户端不能传入 `userId`，Session 撤销必须验证归属。

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/v1/auth/password` | 校验当前密码、确认密码和 12–128 位新密码；更新 BCrypt Hash、提升 Auth Version 并撤销全部 Web Session |
| `GET` | `/api/v1/auth/sessions` | 返回当前用户活动 Web Session、客户端标签、User-Agent Hash 摘要、脱敏 IP、最近使用时间和当前 Session 标志 |
| `DELETE` | `/api/v1/auth/sessions/{sessionId}` | 撤销归属当前用户的单个 Web Session；Refresh Token 与既有 Access Token 同时失效 |
| `GET` | `/api/v1/settings` | 返回账户资料与固定白名单工作区设置 |
| `PUT` | `/api/v1/settings/account` | 更新当前用户显示名、时区和语言区域，不允许修改身份或状态 |
| `PUT` | `/api/v1/settings/{group}/{key}` | 更新单个白名单设置并校验类型和值域 |

Phase 14 白名单仅包括 `workspace.defaultLandingPage`、`workspace.compactMode` 和 `onboarding.showDashboardBanner`。默认落地页只接受 `/dashboard`、`/setup`、`/jobs`、`/recommendations`、`/applications`；两个开关只接受 Boolean。密码变更、Session 撤销、账户更新和设置更新都写入 Audit。响应和日志不回显密码、JWT、Refresh Token、完整 User-Agent 或完整 IP。
