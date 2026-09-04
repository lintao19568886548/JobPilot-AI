# JobPilot AI — Phase 9 Offer Center 与完整 Analytics 详细执行提示词

你正在继续开发：

`D:\JobPilot AI`

当前基线：Phase 0–8 已通过真实 Backend、AI Service、Frontend、MySQL、Redis、Milvus、HTTP、持久化和 Microsoft Edge 验收。Phase 8 已交付 Interview Center、不可变 Review、人工确认 Knowledge Gap 和站内 Reminder。所有外部消息、会议邀请和自动 Application 更新均为 0。

现在正式执行：

# Phase 9 — Offer Center + Complete Analytics

不得提前实现 Phase 10 Learning to Rank、自动化规则、外部通知、自动谈薪或自动投递。

## 一、阶段目标

完整跑通真实数据库链路：

选择当前用户的 Application → 创建 Offer → 维护薪资/奖金/福利/试用期/截止日期 → 生成确定性可解释比较 → 管理 Offer 状态和站内 Deadline → Dashboard 展示真实 Offer 待办 → 从不可变业务事实重建完整 Analytics → 验证平台/岗位方向/公司/城市/薪资/Resume Version/Match Score Bucket 转化 → 导出当前用户数据 → 预览隐私删除范围 → 重启后数据仍存在。

禁止 Mock API、内存数据、伪造 Offer、伪造转化率、自动接受/拒绝 Offer、自动联系公司、自动发送消息、自动修改 Application、外部日历或 Phase 10 学习排序。

## 二、执行前检查

完整读取并核对：`ARCHITECTURE.md`、`DATABASE_DESIGN.md`、`API_DESIGN.md`、`DEVELOPMENT_ROADMAP.md`、`TASKS.md`、`CHANGELOG.md`、`docs/PHASE8_FINAL_REPORT.md` 和 `docs/PHASE8_TEST_REPORT.md`。

检查 Java 21、Maven Wrapper、Node/npm、Python、Docker Compose、Git、端口及 Backend/AI/Frontend/MySQL/Redis/Milvus 健康。设计冲突必须分析、选择、修改文档并写入 Changelog，禁止静默选择。

## 三、范围与安全边界

本阶段只实现：

- Offer、Offer Benefit、Offer Comparison、Comparison Item、Offer Deadline；
- 当前用户 Application/Job/Company/Resume Version/Match/Interview 事实的只读关联；
- 确定性、版本化、可解释的 Offer Comparison；
- 完整 Analytics 维度与可重建事实快照；
- 当前用户 JSON 数据导出和隐私删除预览/显式确认；
- Offer Center、Analytics 页面和 Dashboard 联动。

禁止：自动接受/拒绝/谈判 Offer，发送邮件/微信/BOSS 消息，创建日历事件，外部模型处理完整 Offer，自动改变 Application/Interview/Candidate/Resume/Match，训练或激活 LTR。

Offer 数据按本地敏感事实处理：API 不记录正文，Audit 只记录资源 ID；日志、错误和 Trace 不包含薪资/福利/备注；不发送 AI Service；所有查询验证当前用户。当前没有独立可轮换字段加密主密钥，因此不得使用 JWT Secret 假加密。本阶段保留本地数据库访问控制与最小暴露边界，并在文档记录字段级加密技术债。

## 四、数据库迁移

使用 Flyway 新建 `V9__offer_center_analytics.sql`，至少实现：

- `offers`
- `offer_benefits`
- `offer_comparisons`
- `offer_comparison_items`
- `offer_deadlines`
- `analytics_snapshots`
- `privacy_operation_requests`

必要时扩展 `analytics_daily` 的维度约束和 Offer 指标，但已发布迁移不可修改。所有表具备用户隔离、外键、索引、审计时间、乐观锁与逻辑删除；Comparison/Snapshot 为不可变事实。

约束：同一 Application 只能有一个活动 Offer；金额使用 `DECIMAL`，禁止浮点；币种使用 ISO 4217 三字母；试用期比例范围 0–1；截止时间保存 UTC 并携带 IANA 时区；活动 Deadline 去重；Offer 删除不物理删除比较历史；隐私删除默认只预览，不直接破坏数据。

## 五、Backend 模块

新增 `com.jobpilot.offer` 与扩展 `com.jobpilot.analytics`，按照业务模块组织 controller/service/repository/mapper/dto/domain。复用 ApiResponse、TraceId、Validation、GlobalExceptionHandler、Ownership、Audit 和逻辑删除。

Offer 状态建议：`DRAFT / RECEIVED / CONSIDERING / ACCEPTED / DECLINED / EXPIRED / WITHDRAWN`。

Deadline：`PENDING / DONE / CANCELLED`。

Comparison：不可变版本，权重快照必须总和为 100；至少包括：总现金、奖金、福利、成长、工作生活、稳定性、地点/远程、个人偏好。缺失项明确 `UNKNOWN`，不得填 0 冒充差值。

所有状态变更必须由用户显式请求；Offer `ACCEPTED/DECLINED` 不自动修改 Application。

## 六、Offer API

至少实现：

- `GET /api/v1/offers`
- `POST /api/v1/offers`
- `GET /api/v1/offers/{id}`
- `PUT /api/v1/offers/{id}`
- `DELETE /api/v1/offers/{id}`
- `POST /api/v1/offers/{id}:status`
- `GET /api/v1/offer-deadlines`
- `POST /api/v1/offer-deadlines`
- `PUT /api/v1/offer-deadlines/{id}`
- `POST /api/v1/offer-deadlines/{id}:done`
- `POST /api/v1/offer-deadlines/{id}:cancel`
- `POST /api/v1/offers/comparisons`
- `GET /api/v1/offers/comparisons`
- `GET /api/v1/offers/comparisons/{id}`

Offer 列表支持分页、状态、公司、岗位、币种、截止区间和 Application 筛选。所有资源严格验证归属；客户端不能传 `userId`。

## 七、金额与比较规则

- 金额使用 `BigDecimal`，指定精度和舍入规则；年现金由月薪×薪资月数+确定奖金计算。
- 不确定奖金不得计入确定现金，可单独显示潜在上限。
- 多币种不进行伪汇率换算；没有用户明确提供的汇率时按币种分组，跨币种现金维度标记不可比较。
- 试用期工资按比例计算并展示影响，不伪造税后收入。
- Comparison 算法、权重和输入快照版本化；相同幂等键返回同一结果，不覆盖历史。
- 每个维度输出原值、归一化分、权重、贡献、解释和缺失标记；样本不足时明确提示。

## 八、完整 Analytics

所有指标只来自当前用户真实 Job、Recommendation、Queue、Application Log、Interview、Offer 和 Resume Version 事实。至少实现：

- Overall Funnel；
- Platform；
- Job Direction；
- Company；
- City；
- Salary Band；
- Resume Version；
- Match Score Bucket。

每行必须返回 `numerator`、`denominator`、`sampleSize`、`rate` 和 `insufficientSample`。率的分母必须明确，分母为 0 时 `rate=null`，禁止展示 0%。Interview/Offer 阶段按首次到达的不可变事件或真实存在事实统计，不因当前状态覆盖历史。

实现 `POST /api/v1/analytics:rebuild`：按日期范围从事实源事务化重建并保存不可变 `analytics_snapshots`，同一输入 Hash 幂等；重建前后结果必须对账一致。

## 九、数据导出与隐私操作

实现：

- `GET /api/v1/privacy/export`：导出当前用户结构化 JSON，包含 Schema 版本、生成时间和各领域记录数，不包含密码 Hash、Refresh Token、内部 Secret、AI Provider Key；
- `POST /api/v1/privacy/deletions:preview`：返回各表影响数量和不可逆警告，不修改数据；
- `POST /api/v1/privacy/deletions:confirm`：必须同时校验显式确认短语、请求版本和一次性幂等键。为避免破坏本阶段验收主用户，只在独立测试用户上验证。

删除采用服务编排、审计与逻辑删除；受外键保护的不可变审计/历史按保留策略处理，不允许直接 `DELETE FROM users` 或绕过约束。

## 十、Frontend

继续 Vue 3 + JavaScript + Vite + Element Plus + Pinia + Router + Axios，禁止 TypeScript 业务源码。

实现：

- Offer Center 列表、详情、创建/编辑、状态、福利和 Deadline；
- Offer Comparison 选择与可解释矩阵；
- Analytics 页面：漏斗及 Platform/Direction/Company/City/Salary/Resume/Match Bucket 视图；
- 小样本与空数据状态，不显示虚假趋势；
- Dashboard 真实 Offer 数量、最近截止日期与 Phase 9 Analytics 入口；
- 数据导出和隐私删除预览入口，确认删除必须有清晰危险提示。

UI 延续浅色、留白、轻边框、专业 SaaS 风格；金额、币种、时区和未知值清晰展示。

## 十一、审计

至少记录：`OFFER_CREATE`、`OFFER_UPDATE`、`OFFER_STATUS_UPDATE`、`OFFER_DELETE`、`OFFER_DEADLINE_CREATE`、`OFFER_DEADLINE_UPDATE`、`OFFER_COMPARISON_CREATE`、`ANALYTICS_REBUILD`、`PRIVACY_EXPORT`、`PRIVACY_DELETE_PREVIEW`、`PRIVACY_DELETE_CONFIRM`。

Audit 只记录 userId、action、resourceType、resourceId、traceId 和时间，不写入薪资、奖金、福利、备注、Token 或 Secret。

## 十二、测试

Backend 至少覆盖：金额精度、薪资月数、确定/不确定奖金、试用期比例、币种、状态机、Deadline 时区/过去时间/去重/取消、Ownership、逻辑删除、乐观锁、Comparison 权重/幂等/不可变、多币种、各 Analytics 分母分子、小样本、重建对账、导出脱敏和删除预览/确认。

Frontend 至少验证 Login、Offer List/Detail、Comparison、Analytics、Dashboard、Privacy，生产 build 通过且浏览器 console error 为 0。

禁止 `skipTests`、假数据和假 PASS。

## 十三、真实验收

必须执行：

1. Backend `mvn clean test` 和 `mvn package`；
2. AI Service 全量回归与 Ruff；
3. Frontend production build；
4. Flyway V1→V9 空库迁移与 V8→V9 升级；
5. 启动真实 MySQL、Redis、Milvus、Backend、AI Service、Frontend；
6. 使用独立测试用户创建真实 Application 上下文和两个以上 Offer；
7. 验证金额、福利、试用期、Deadline、状态和重启持久化；
8. 创建两个不可变 Comparison 版本并验证幂等；
9. 构造真实 Application/Interview/Offer 事件并验证所有 Analytics 维度；
10. 重建 Analytics 两次并对账；
11. 验证导出不含 Secret/密码/Token，删除预览零写入，并在隔离用户验证显式确认；
12. Microsoft Edge 验证 Dashboard、Offer Center、Comparison、Analytics、Privacy，console error=0；
13. 确认外部消息、会议邀请、自动 Offer 决策和自动 Application 更新均为 0。

## 十四、TASKS、Git 与文档

同步更新 `TASKS.md`。完成 `[x]`、未完成 `[ ]`、阻塞 `[!]`。Phase 9 P0 未全部完成前不得进入 Phase 10。

更新 README、ARCHITECTURE、DATABASE_DESIGN、API_DESIGN、DEVELOPMENT_ROADMAP、CHANGELOG、测试报告和最终报告。检查 `.env`、node_modules、target、venv、日志、数据库、导出文件和运行状态均被 Git 忽略。不 commit、不 push，除非用户明确授权。

## 十五、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_9
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_REGRESSION=
DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
OFFER_CRUD=
OFFER_STATUS=
OFFER_DEADLINE=
OFFER_COMPARISON=
MONEY_PRECISION=
TIMEZONE_VALIDATION=
ANALYTICS_FUNNEL=
ANALYTICS_DIMENSIONS=
ANALYTICS_REBUILD=
PRIVACY_EXPORT=
PRIVACY_DELETE=
DASHBOARD_INTEGRATION=
FRONTEND_BUILD=
FRONTEND_RUN=
BROWSER_E2E=
CONSOLE_ERRORS=
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_MEETING_INVITES=0 / 非0
AUTOMATIC_OFFER_DECISIONS=0 / 非0
AUTOMATIC_APPLICATION_UPDATES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任何核心项失败则 `PHASE_STATUS=FAIL`；缺少必须人工提供的权限或外部依赖则标记 `BLOCKED`。只有实际成功才允许写 PASS。
