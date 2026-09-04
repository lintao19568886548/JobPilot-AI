# JobPilot AI — Phase 4 Recommendation Center 与 Dashboard 执行提示词

你现在继续开发本地项目：

`D:\JobPilot AI`

Phase 0、Phase 1、Phase 2、Phase 3 已经完成，并已通过真实 Build、Test、Run、MySQL/Redis/Milvus 持久化、BGE-M3 推理、HTTP Smoke 与浏览器联调。

现在正式进入：

# Phase 4 — Recommendation Center、Dashboard 与 Global Search

本阶段要把 Phase 2 的真实岗位和 Phase 3 的不可变 Match 结果组织成一个可以每天使用的推荐工作台。

本阶段不是重新实现 Matching Engine，也不允许提前实现 Application Queue、Resume Tailor、Chrome Extension 或自动投递。

---

## 一、Phase 4 核心目标

必须完整跑通以下真实链路：

启动 MySQL、Redis、Milvus

→ 启动 AI Service、Backend、Frontend

→ 登录

→ 批量发现需要评估的真实岗位

→ 复用 Phase 3 Match Run/Outbox 创建匹配任务

→ 形成每个岗位的当前 Recommendation 投影

→ 查看全部、S/A/B、未评估、忽略、收藏视图

→ 按匹配度、发布时间、薪资、公司、城市和 AI 推荐顺序筛选/排序

→ 收藏岗位

→ 忽略岗位并保存原因

→ 恢复岗位

→ 查看真实 Dashboard KPI、今日推荐、等级分布和来源分布

→ 使用 Global Search 搜索岗位、公司、技能和状态

→ 重启 Backend

→ 再次读取推荐、收藏、忽略、事件、Dashboard 与搜索结果

所有业务数据必须来自 MySQL。不得使用 Mock API、前端静态数组、随机统计、内存收藏或刷新后丢失的数据。

---

## 二、首先核对现有设计与代码

写代码前必须完整读取：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`
- `PHASE3_EXECUTION_PROMPT.md`

同时扫描：

- Phase 3 Matching API、实体、Mapper、Service、Outbox Worker
- 当前 Dashboard API/UI
- Job Center 列表、详情和 Match Analysis UI
- V1/V2/V3 Flyway Migration

发现冲突时必须分析、选择方案、同步文档并写入 `CHANGELOG.md`，不得静默偏离。

### 必须显式处理的已知边界冲突

Phase 4 Roadmap 提到“已投”视图，但 Application、Application Log 和 Application 状态机属于 Phase 5，目前没有可信投递事实源。

本阶段采用以下方案：

1. Recommendation 页面可以保留“已投”入口；
2. 入口必须 Disabled 或标注 `Phase 5 / Coming Soon`；
3. Backend 能力元数据返回 `applicationTracking=false`；
4. 禁止用 Recommendation 状态、Job 状态或前端本地状态伪造 `APPLIED`；
5. 不创建 Application、Application Queue 或虚假投递记录；
6. 同步修改 Roadmap/API/TASKS 对 Phase 4 “已投”视图的描述并在 Changelog 说明原因。

---

## 三、范围边界

本阶段只实现：

- Recommendation 模块
- 当前推荐投影
- 推荐列表、筛选、稳定排序与分页
- 批量 Match Refresh 编排
- 收藏、取消收藏、忽略、恢复
- 不可变 Recommendation Event
- Phase 4 Analytics Daily 初始聚合
- Dashboard 匹配 KPI、今日推荐、等级分布、来源分布和基础漏斗
- Global Search 第一版
- Job Center 与 Recommendation Center 共用 Match Analysis 组件
- Phase 4 测试、Smoke、持久化与浏览器验收

禁止开发：

- Application Queue
- Application CRM / Application Log
- 自动投递或未授权投递
- Playwright 投递
- Resume Tailor
- Greeting/Communication Agent
- Chrome Extension
- Interview、Offer
- Learning to Rank
- 自动修改 Match 权重
- 新的岗位采集器
- 独立 Elasticsearch/OpenSearch

---

## 四、项目结构

Backend 继续采用 Spring Boot 模块化单体，新增业务模块：

```text
com.jobpilot
├─ recommendation
│  ├─ controller
│  ├─ service
│  ├─ repository
│  ├─ mapper
│  ├─ dto
│  └─ domain
├─ analytics
│  ├─ controller
│  ├─ service
│  ├─ mapper
│  ├─ dto
│  └─ domain
└─ search
   ├─ controller
   ├─ service
   └─ dto
```

不得把 Recommendation、Analytics、Search 代码塞进全局 `controller/service/mapper/entity` 目录。

Frontend 继续使用 Vue 3 + JavaScript，不引入 TypeScript：

```text
src
├─ api
│  ├─ recommendations.js
│  ├─ analytics.js
│  └─ search.js
├─ components
│  ├─ matching
│  ├─ recommendation
│  └─ dashboard
└─ views
   ├─ recommendation
   └─ dashboard
```

---

## 五、数据库迁移

使用 Flyway 新建：

`V4__recommendation_dashboard.sql`

禁止修改已发布的 V1、V2、V3。

至少创建以下表：

### 1. `job_recommendations`

这是当前推荐投影，不是 Match 历史本身。

字段至少包括：

- id
- public_id
- user_id
- job_id
- latest_match_id，可空
- recommendation_status：`UNEVALUATED/READY/MATCH_FAILED/IGNORED`
- favorite
- rank_score，可空
- rank_version
- rank_basis_json
- ignored_reason，可空
- ignored_at，可空
- last_evaluated_at，可空
- created_at
- updated_at
- deleted_at
- version

要求：

- 每个用户和岗位只能有一个有效当前投影；
- 投影可以随最新 Match 更新，但不得修改 `job_matches` 历史；
- `latest_match_id` 必须属于相同用户和岗位；
- `rank_score` 必须在 0–100；
- Favorite 与 Ignore 必须持久化；
- 使用乐观锁避免覆盖并发操作；
- 普通删除使用 logical delete。

### 2. `recommendation_events`

不可变记录：

- id
- public_id
- user_id
- recommendation_id
- job_id
- job_match_id，可空
- event_type：`FAVORITE/UNFAVORITE/IGNORE/RESTORE`
- reason，可空
- previous_state_json
- current_state_json
- feature_snapshot_json
- trace_id
- occurred_at

事件只追加，不更新、不逻辑删除。Phase 10 可以从这些事实事件生成训练 Feedback，但 Phase 4 不训练 LTR。

### 3. `recommendation_refresh_runs`

记录批量刷新：

- id/public_id/user_id
- status：`PENDING/RUNNING/SUCCEEDED/PARTIAL_SUCCESS/FAILED`
- idempotency_key
- request_hash
- total_count
- submitted_count
- reused_count
- succeeded_count
- failed_count
- started_at/finished_at
- error_message_safe
- created_at/updated_at

不得保存 Token、Secret 或候选人敏感全文。

### 4. `analytics_daily`

作为 Phase 4 初始可重建聚合：

- user_id
- metric_date
- dimension_type：至少 `OVERALL/SOURCE/CITY/LEVEL/RECOMMENDATION_STATUS`
- dimension_key
- job_count
- evaluated_count
- high_match_count
- favorite_count
- ignored_count
- created_at/updated_at

唯一索引：

`(user_id, metric_date, dimension_type, dimension_key)`

Phase 5+ 的 applied/replied/interview/offer 指标可以预留字段，但 Phase 4 API 必须用 availability 元数据说明这些数据源尚不存在，禁止把“不可用”伪装成真实 0。

### Migration 验证

必须验证：

1. 现有 V1/V2/V3 数据无丢失；
2. 当前数据库升级 V3→V4 成功；
3. 全新临时数据库 V1→V2→V3→V4 成功；
4. 索引、外键、唯一约束和 CHECK 生效；
5. 临时验证库完成后安全删除，不碰真实库。

---

## 六、Recommendation 投影规则

Recommendation 不得重新计算或篡改 Match 分数。

每个岗位的当前投影规则：

1. 选择当前用户、当前岗位最新的有效 Match；
2. `force=true` 产生新 Match 后，只更新 `latest_match_id` 投影；
3. 历史 Match 仍可从 Phase 3 API 查看；
4. 没有 Match 的岗位为 `UNEVALUATED`，所有分数字段必须为 null；
5. 最新 Match Run 失败且没有成功 Match 时为 `MATCH_FAILED`，不得显示假分数；
6. 用户 Ignore 后为 `IGNORED`，但不删除岗位或 Match；
7. Restore 后恢复到根据真实 Match 推导出的状态；
8. Favorite 与 Ignore 是两个独立状态；收藏后仍可忽略，但 UI 必须清楚展示组合状态；
9. Hard Filter `REJECT` 的岗位显示真实拒绝状态和原因，不参与高推荐数量；
10. 所有读取只允许访问当前登录用户的数据。

Recommendation 投影更新必须幂等。重复同步同一岗位/Match 不得产生重复行或重复事件。

---

## 七、批量 Match Refresh

Phase 3 已交付单岗位 Match Run。Phase 4 实现安全的批量编排，但必须复用 Phase 3 的 MatchService、Outbox、Redis 锁、输入 Hash 和缓存，禁止复制一套评分逻辑。

实现：

- 只选择当前用户的可评估岗位；
- 默认包含 `ACTIVE/PARSED` 且解析成功的岗位；
- 支持筛选 `onlyUnevaluated`、city、level、updatedAfter；
- 有相同成功输入 Hash 时复用，不重复推理；
- `force=false` 默认；
- `force=true` 必须由用户显式触发并生成新 Match 历史；
- 设置单批最大岗位数，默认 100，环境变量可配置；
- 记录 submitted/reused/succeeded/failed；
- 单个岗位失败不导致整批数据丢失；
- 重复 Idempotency-Key + 相同请求返回同一 Refresh Run；
- 相同 Idempotency-Key + 不同请求返回 409；
- 后端重启后可继续读取批次状态；
- Provider 不可用时显示失败状态，禁止回退假分数。

建议 API：

- `POST /api/v1/recommendation-refresh-runs`
- `GET /api/v1/recommendation-refresh-runs/{runId}`
- `POST /api/v1/recommendation-refresh-runs/{runId}:retry-failed`

同时提供项目现有 `/api/...` 兼容路径。

---

## 八、Recommendation API

实现：

- `GET /api/v1/recommendations`
- `GET /api/v1/recommendations/{id}`
- `POST /api/v1/recommendations/{id}:favorite`
- `POST /api/v1/recommendations/{id}:unfavorite`
- `POST /api/v1/recommendations/{id}:ignore`
- `POST /api/v1/recommendations/{id}:restore`
- `GET /api/v1/recommendations/{id}/events`
- `GET /api/v1/recommendations/capabilities`

列表至少支持：

- view：`ALL/TOP/UNEVALUATED/IGNORED/FAVORITE`
- level：`S/A/B/C/D`
- hardFilter：`PASS/DOWNGRADE/REJECT`
- recommendation：`RECOMMEND/CONSIDER/NOT_RECOMMENDED/REJECTED`
- city
- companyId
- companyName
- salaryMin/salaryMax
- publishFrom/publishTo
- skill
- sourcePlatform
- favorite
- keyword
- cursor
- limit
- sort

排序白名单：

- `AI_RECOMMENDED`
- `MATCH_DESC`
- `PUBLISH_DESC`
- `SALARY_DESC`
- `COMPANY_ASC`
- `CITY_ASC`

禁止客户端提交任意 SQL 排序字段。

`AI_RECOMMENDED` 必须是稳定且可解释的排序：

1. 先按 Phase 3 recommendation 等级；
2. 再按 overallScore；
3. 再按 publishAt；
4. 最后按稳定主键；
5. 不调用新的黑盒模型，不加入未记录的隐藏加分；
6. 返回 `rankBasis`，说明排序依据。

列表查询必须避免 N+1，使用游标分页，结果不得重复或漏项。

所有 API 继续使用统一响应：

```json
{
  "code": 0,
  "message": "success",
  "data": {},
  "traceId": "..."
}
```

---

## 九、收藏、忽略、恢复与反馈事实

### Favorite

- Favorite 幂等；重复 Favorite 不重复写事件；
- Unfavorite 幂等；
- 更新 `job_recommendations.favorite`；
- 状态变化时写不可变 Event 与 Audit。

### Ignore

- Ignore 请求必须支持可选 reason；
- reason 长度和字符数必须后端校验；
- Ignore 不删除 Job、Match 或历史；
- 重复相同 Ignore 不重复写事件；
- 状态变化时写 Event 与 Audit。

### Restore

- Restore 恢复真实推导状态；
- 不自动 Favorite/Unfavorite；
- 写不可变 Event 与 Audit。

### Feedback 边界

Phase 4 只记录用户事实事件和 feature snapshot，不学习、不训练、不调整 Match 权重。禁止声称系统已经“越用越聪明”。

---

## 十、Dashboard Phase 4

升级现有 Dashboard，但保留 Phase 1/2 的真实 Candidate 与 Job 指标。

至少展示：

- Profile Completeness
- Total Jobs
- Active Jobs
- Evaluated Jobs
- Unevaluated Jobs
- S/A 高匹配岗位数
- B 级岗位数
- Favorite 数
- Ignored 数
- Match Failed 数
- 今日新增岗位
- 今日完成评估数
- Default Resume
- Last Updated

### 今日推荐

显示真实推荐前 N 条：

- 职位
- 公司
- 城市
- 薪资
- overallScore
- level
- recommendation
- favorite
- evaluatedAt

未评估/失败/拒绝必须显示明确状态，不显示伪分数。

### 图表

使用 ECharts 展示真实：

- S/A/B/C/D 等级分布
- 岗位来源分布
- 推荐状态分布
- Phase 4 基础漏斗：发现 → 已评估 → 推荐 → 收藏/忽略

统计必须使用 `COUNT(DISTINCT job_id)` 或等价去重，避免一个 Job 多个 Source、多个 Match Version 导致重复计数。

### 未开放能力

以下没有可信数据源：

- 已投递
- 已回复
- 笔试
- 面试
- Offer
- 最近面试
- 投递待办

Backend 返回：

```json
{
  "available": false,
  "phase": "PHASE_5_OR_LATER",
  "reason": "Application tracking is not implemented yet"
}
```

Frontend 显示 Coming Soon，不显示虚假 0、假漏斗或假趋势。

建议 API：

- `GET /api/v1/analytics/dashboard`
- `GET /api/v1/analytics/recommendations?from=&to=`
- `GET /api/v1/analytics/sources?from=&to=`
- `GET /api/v1/analytics/levels?from=&to=`
- `GET /api/v1/analytics/funnel?from=&to=`
- `POST /api/v1/analytics:rebuild`

保留 `/api/dashboard` 兼容接口，避免现有前端突然失效。

---

## 十一、Analytics 一致性

Analytics 必须可从源数据完全重建。

要求：

- `analytics_daily` 是可重建投影，不是唯一事实源；
- Recommendation/Match/Event 才是事实源；
- Rebuild 使用当前用户和日期范围；
- 同一范围重复 Rebuild 结果一致；
- 聚合更新使用事务和唯一键；
- 时间边界使用明确时区，默认 `Asia/Shanghai`，环境变量可配置；
- API 返回 numerator、denominator、sampleSize；
- 无数据返回空数组/零值并标记数据状态，不创建假样本；
- 小样本不得输出“趋势显著”等误导性文本；
- 必须用 SQL 对账验证 Dashboard 与源表一致。

---

## 十二、Global Search 第一版

实现：

`GET /api/v1/search?q=Redis&types=JOB,COMPANY,SKILL,STATUS&limit=10`

要求：

- 只搜索当前用户可见数据；
- types 使用白名单；
- q 长度 2–100；
- 正确转义 `%`、`_` 和反斜杠；
- 禁止动态拼接 SQL；
- 初期使用 MySQL 规范字段和必要索引，不引入 Elasticsearch；
- 不使用 Milvus 代替精确业务搜索；
- 返回按类型分组的摘要；
- 每条结果包含资源 ID、标题、副标题、状态和内部目标 URL；
- Highlight 返回安全文本片段，不返回可执行 HTML；
- 搜索岗位标题、公司、技能名及状态显示名；
- 结果排序稳定；
- 空查询、超长查询、非法 types 返回明确校验错误。

---

## 十三、Frontend Recommendation Center

启用左侧导航：

- AI Recommendation

路由建议：

- `/recommendations`

页面建议使用：

- 顶部简洁筛选栏
- 左侧/中部 Recommendation List
- 右侧 Match Analysis Detail
- 可折叠筛选器
- 批量 Refresh 状态条

列表展示：

- Level 与 Score
- Job Title
- Company
- City
- Salary
- Publish Time
- Must/Nice Skills 摘要
- Favorite
- Ignore 状态
- Match 状态

操作：

- 打开详情
- Favorite / Unfavorite
- Ignore，并选择或填写原因
- Restore
- Run Match / Re-evaluate
- 查看 Match History

Match Analysis 必须从 Job Center 抽取为共享组件，不复制两套相互漂移的 UI 逻辑。

“已投”标签必须 Disabled 并标注 Phase 5。

---

## 十四、Frontend Dashboard

继续遵循：

- 现代、简洁、专业求职 SaaS
- 浅色背景
- 清晰层级
- 大量留白
- 轻边框
- 少量合理阴影
- Accent Color 表达重点状态

不要：

- 传统深蓝后台模板
- 满屏 Card
- 大量渐变
- 过多胶囊按钮
- 假 KPI
- 花哨动画

Dashboard 建议结构：

1. 顶部真实 KPI；
2. 今日推荐主区域；
3. Match Level 与 Source Distribution 图表；
4. 基础漏斗；
5. Candidate/Resume 状态摘要；
6. Phase 5+ 模块明确 Coming Soon。

必须支持常见桌面宽度与窄屏，不得横向溢出。

---

## 十五、Global Search UI

在 App Layout 顶部提供搜索入口：

- 点击打开 Search Panel / Command Palette；
- 支持键盘聚焦和关闭；
- Loading、空状态、错误状态清晰；
- 按 Job、Company、Skill、Status 分组；
- 点击结果跳转到真实页面与资源；
- 不允许点击进入空白路由；
- 搜索文本不得使用 `v-html` 直接渲染服务端内容。

---

## 十六、安全、归属与审计

必须保证：

- 客户端不能传 userId 操作他人 Recommendation；
- Recommendation、Event、Refresh Run、Analytics 全部校验 ownership；
- 不存在的资源返回 ResourceNotFound；
- 他人资源返回 Not Found 或 Forbidden，策略保持一致；
- Favorite/Ignore/Restore 写 Audit；
- Analytics Rebuild 写 Audit；
- 批量 Refresh 写 Audit；
- 日志只记录 ID、数量、状态、耗时和 TraceId；
- 不记录 JWT、密码、LLM Secret、简历全文；
- 所有接口保留 TraceId 和统一异常响应；
- 搜索防 SQL Injection；
- 排序、过滤和 type 全部白名单。

Audit 至少新增：

- `RECOMMENDATION_REFRESH_CREATE`
- `RECOMMENDATION_FAVORITE`
- `RECOMMENDATION_UNFAVORITE`
- `RECOMMENDATION_IGNORE`
- `RECOMMENDATION_RESTORE`
- `ANALYTICS_REBUILD`

---

## 十七、Redis、Milvus 与 AI Service

Phase 4 不重新设计 Phase 3 Provider。

Redis 至少继续用于：

- Refresh Run 幂等键；
- 批量编排短锁；
- 可选短时 Dashboard/Search 缓存。

如果使用 Dashboard/Search Cache：

- TTL 必须明确；
- Favorite/Ignore/Restore/Match 完成后必须正确失效；
- 缓存不得成为事实源；
- 重启或清空 Redis 后结果必须能从 MySQL 重建。

Milvus 与 BGE-M3 只由 Phase 3 Match 流程使用。Recommendation 列表不得重新生成随机或重复向量。

没有 LLM Secret 时继续保持 `SKIPPED_NOT_CONFIGURED`，不得为推荐页面伪造 LLM 分数或解释。

---

## 十八、Backend 测试要求

至少覆盖：

- Recommendation 投影创建和更新
- 最新 Match 选择
- 历史 Match 不被覆盖
- UNEVALUATED/READY/MATCH_FAILED/IGNORED
- REJECT 不进入高匹配统计
- Favorite/Unfavorite 幂等
- Ignore/Restore 幂等
- Event 只在真实状态变化时追加
- ownership validation
- 乐观锁冲突
- ALL/TOP/S/A/B/UNEVALUATED/IGNORED/FAVORITE 筛选
- city/company/salary/skill/source/date 筛选
- 所有排序白名单和稳定翻页
- 重复 Source、重复 Match 不重复计数
- 批量 Refresh 幂等、部分失败和 retry
- Analytics 实时值与源表一致
- Analytics Rebuild 可重复
- 日期/时区边界
- Global Search 类型白名单
- `%/_/\` 搜索转义
- SQL Injection 输入
- 搜索 ownership
- Applied capability 未开放
- GlobalExceptionHandler
- API Integration Test

不得 `skipTests`。

---

## 十九、AI Service 与 Frontend 回归

AI Service 虽无新增 Agent，仍必须执行：

- `ruff check app tests`
- `pytest`

确保 Phase 3 Matching、BGE-M3、Milvus 和 LLM 降级没有被破坏。

Frontend 至少验证：

- `npm install`
- `npm run build`
- Dashboard
- Recommendation Center
- 收藏/取消收藏
- 忽略/恢复
- 筛选/排序/分页
- Batch Refresh 状态
- Global Search
- Job Detail 共用 Match Analysis
- 页面刷新后状态不丢失
- 浏览器 Console 无 error

---

## 二十、Backend 与基础设施验证

必须执行：

- `mvn clean test`
- `mvn package`
- `docker compose --profile ai config`
- 启动 MySQL、Redis、etcd、MinIO、Milvus
- 启动 AI Service
- 启动 Backend
- 启动 Frontend

验证：

- Backend `/actuator/health`
- Swagger `/swagger-ui.html`
- OpenAPI `/v3/api-docs`
- AI `/internal/v1/health`
- AI `/internal/v1/readiness`
- Redis PING
- Milvus Collection 与向量数量

---

## 二十一、真实 HTTP Smoke 流程

建立：

`scripts/phase4-smoke.ps1`

至少真实执行：

1. 登录并刷新 JWT；
2. GET Recommendation capabilities；
3. 验证 applicationTracking=false；
4. 获取 Recommendation 列表；
5. 创建 Batch Refresh Run；
6. 重放 Idempotency-Key，确认同一 Run；
7. 轮询批次终态；
8. 验证 evaluated/reused/succeeded/failed 统计；
9. 获取 ALL；
10. 获取 TOP；
11. 获取 S/A/B 筛选；
12. 获取 UNEVALUATED；
13. 验证至少一种排序；
14. 验证游标分页无重复；
15. Favorite；
16. 重复 Favorite，确认幂等；
17. 查询 FAVORITE；
18. Unfavorite；
19. Ignore 并保存原因；
20. 查询 IGNORED；
21. Restore；
22. 查询不可变 Events；
23. 获取 Recommendation Detail 和 Phase 3 Match Evidence；
24. 获取 Dashboard；
25. 用 SQL 对账 KPI；
26. 获取等级分布；
27. 获取来源分布；
28. 获取基础漏斗；
29. 执行 Analytics Rebuild；
30. 再次对账；
31. 搜索 Job；
32. 搜索 Company；
33. 搜索 Skill；
34. 搜索 Status；
35. 验证非法 type 被拒绝；
36. 验证搜索特殊字符安全；
37. 重启 Backend；
38. 再次登录；
39. 验证 Favorite/Ignore/Event/Analytics/Refresh Run 仍存在；
40. 清空可选 Cache 后确认 MySQL 可重建；
41. 浏览器验证 Dashboard、Recommendation 和 Search；
42. 浏览器 Console 必须没有 error。

只有真实执行成功才允许写 PASS。

---

## 二十二、Persistence Test

必须验证：

- Recommendation 投影真实写入 MySQL；
- Favorite/Ignore/Restore 真实写入 MySQL；
- Events 真实写入 MySQL；
- Refresh Run 真实写入 MySQL；
- Analytics Daily 真实写入 MySQL；
- Backend 停止并重启后数据仍存在；
- Redis 清空相关 Cache 后 MySQL 结果仍可读取；
- Phase 3 Match 和 Milvus 向量没有丢失。

---

## 二十三、UI 浏览器验收

必须用真实浏览器完成：

- 登录；
- 打开 Dashboard；
- 核对真实 KPI；
- 打开 Recommendation Center；
- 切换全部、TOP、未评估、收藏、忽略；
- 执行 Favorite/Unfavorite；
- 执行 Ignore/Restore；
- 打开 Recommendation Detail；
- 验证分数、证据、配置/算法/模型版本；
- 未评估、失败和 LLM 未配置状态显示正确；
- 执行 Global Search 并跳转；
- “已投”显示 Phase 5，不产生假数据；
- 检查响应式布局；
- 检查 Network 失败；
- 检查 Console error/warning。

---

## 二十四、文档与 TASKS

同步更新：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`
- `.env.example`

环境变量至少补充：

- `RECOMMENDATION_BATCH_MAX_SIZE`
- `RECOMMENDATION_BATCH_POLL_INTERVAL_MS`
- `ANALYTICS_TIMEZONE`
- `DASHBOARD_CACHE_TTL_SECONDS`
- `GLOBAL_SEARCH_MAX_RESULTS`

不得提交真实 Secret。

只有实际完成的任务改为 `[x]`；失败或人工阻塞使用 `[!]` 并写明原因。

---

## 二十五、Git

执行前后检查：

- `git status`
- `.env` 未进入 Git
- `node_modules` 未进入 Git
- `target`、`.venv`、`dist`、`runtime`、日志、数据库文件未进入 Git

当前仓库如果仍没有基线提交，必须如实说明无法从 Git 精确区分 Phase 4 新增和修改文件，不得伪造数量。

不要 Push，除非用户明确授权。

---

## 二十六、Phase 4 验收门槛

最终至少达到：

```text
BACKEND_BUILD=PASS
BACKEND_TEST=PASS
BACKEND_RUN=PASS
AI_SERVICE_REGRESSION=PASS
AI_SERVICE_RUN=PASS
FRONTEND_BUILD=PASS
FRONTEND_RUN=PASS
DOCKER_COMPOSE=PASS
DATABASE_MIGRATION=PASS
CLEAN_DATABASE_MIGRATION=PASS
MYSQL_PERSISTENCE=PASS
REDIS_CONNECTION=PASS
MILVUS_REGRESSION=PASS
RECOMMENDATION_PROJECTION=PASS
BATCH_REFRESH=PASS
LATEST_MATCH_PROJECTION=PASS
RECOMMENDATION_ALL=PASS
RECOMMENDATION_TOP=PASS
RECOMMENDATION_UNEVALUATED=PASS
RECOMMENDATION_IGNORED=PASS
RECOMMENDATION_FAVORITE=PASS
RECOMMENDATION_SORTING=PASS
RECOMMENDATION_PAGINATION=PASS
FAVORITE_IDEMPOTENCY=PASS
IGNORE_RESTORE=PASS
RECOMMENDATION_EVENTS=PASS
DASHBOARD_KPI=PASS
TODAY_RECOMMENDATIONS=PASS
MATCH_LEVEL_DISTRIBUTION=PASS
SOURCE_DISTRIBUTION=PASS
BASIC_FUNNEL=PASS
ANALYTICS_REBUILD=PASS
ANALYTICS_SOURCE_RECONCILIATION=PASS
GLOBAL_SEARCH=PASS
GLOBAL_SEARCH_SECURITY=PASS
OWNERSHIP_VALIDATION=PASS
NO_DUPLICATE_COUNTING=PASS
PHASE5_CAPABILITY_BOUNDARY=PASS
FRONTEND_BACKEND_INTEGRATION=PASS
HTTP_SMOKE_TEST=PASS
BROWSER_UI_TEST=PASS
```

如果任一核心项 FAIL：

`PHASE4=FAIL`

不能宣称 Phase 4 Complete。

---

## 二十七、最终报告格式

完成后严格输出：

```text
PROJECT=JobPilot AI
PHASE=PHASE_4
PHASE_STATUS=PASS/FAIL/BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_SERVICE_REGRESSION=
AI_SERVICE_RUN=
FRONTEND_BUILD=
FRONTEND_RUN=
DOCKER_COMPOSE=
DATABASE_MIGRATION=
CLEAN_DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
REDIS_CONNECTION=
MILVUS_REGRESSION=
RECOMMENDATION_PROJECTION=
BATCH_REFRESH=
LATEST_MATCH_PROJECTION=
RECOMMENDATION_ALL=
RECOMMENDATION_TOP=
RECOMMENDATION_UNEVALUATED=
RECOMMENDATION_IGNORED=
RECOMMENDATION_FAVORITE=
RECOMMENDATION_SORTING=
RECOMMENDATION_PAGINATION=
FAVORITE_IDEMPOTENCY=
IGNORE_RESTORE=
RECOMMENDATION_EVENTS=
DASHBOARD_KPI=
TODAY_RECOMMENDATIONS=
MATCH_LEVEL_DISTRIBUTION=
SOURCE_DISTRIBUTION=
BASIC_FUNNEL=
ANALYTICS_REBUILD=
ANALYTICS_SOURCE_RECONCILIATION=
GLOBAL_SEARCH=
GLOBAL_SEARCH_SECURITY=
OWNERSHIP_VALIDATION=
NO_DUPLICATE_COUNTING=
PHASE5_CAPABILITY_BOUNDARY=
FRONTEND_BACKEND_INTEGRATION=
HTTP_SMOKE_TEST=
BROWSER_UI_TEST=
GIT_STATUS=
```

然后报告：

- 新增文件数量
- 修改文件数量
- Backend 测试数量
- AI 回归测试数量
- HTTP 验证数量
- 数据库总表数量
- Phase 4 新增表数量
- Recommendation 数量
- Recommendation Event 数量
- Analytics Daily 数量
- 已完成任务
- 未完成任务
- 阻塞项
- 技术债务
- Phase 5 建议

---

## 二十八、最重要的规则

1. Recommendation 是 Phase 3 Match 的投影和工作流组织，不是另一套评分引擎。
2. 不覆盖 Match 历史。
3. 不把未评估或失败岗位显示成 0 分。
4. 不重复计算同一岗位或重复统计多个 Match Version。
5. 不伪造“已投、回复、面试、Offer”。
6. 不因没有 LLM Secret 而生成假 LLM 结果。
7. Favorite、Ignore、Restore、Analytics 必须真实持久化。
8. Dashboard 必须能和源表 SQL 对账。
9. Search 必须按用户隔离并防 SQL Injection。
10. Phase 4 所有 P0 完成前不得进入 Phase 5。
11. 只有真实执行成功才写 PASS。
12. 不 Push 远程仓库。

---

## 二十九、立即执行

现在直接开始执行 Phase 4。

第一步：

1. 读取全部设计与 Phase 3 实现；
2. 扫描当前 Git 状态；
3. 检查 Java、Maven Wrapper、Node、npm、Python、Docker、Docker Compose；
4. 检查 MySQL、Redis、Milvus、Backend、AI Service、Frontend 和端口；
5. 核对 Phase 4 与 Phase 5 数据边界；
6. 创建 `V4__recommendation_dashboard.sql`；
7. 按 Backend → Frontend → Tests → Smoke → Persistence → Browser 顺序持续实现并验收。

除非遇到管理员权限、Docker Desktop 无法启动、必须人工提供的 Secret、不可逆操作或无法从本地推断的重大产品决策，否则不要中途询问普通技术问题。

只在 Phase 4 已真实验收完成，或者存在无法绕过的人工阻塞时停止。
