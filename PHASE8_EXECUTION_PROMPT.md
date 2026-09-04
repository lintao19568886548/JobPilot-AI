# JobPilot AI — Phase 8 Interview Center 详细执行提示词

你正在继续开发：

`D:\JobPilot AI`

当前基线：Phase 0–7 已通过真实构建、测试、MySQL/Redis/Milvus、HTTP、持久化、Frontend 与 Microsoft Edge unpacked Extension 验收。Phase 7 的 Extension 只读取用户主动触发的可见字段，草稿不发送，Assist 队列不自动批准，Automation Worker 默认关闭，任何外部投递仍需人工最终确认。

现在只执行：

# Phase 8 — Interview Center + Interview Agent + Knowledge Gap

不得提前开发 Phase 9 或扩大真实招聘平台自动化范围。

## 一、阶段目标

完整跑通真实数据库链路：

创建面试 → 维护轮次 → 记录面试时间/形式/链接 → 生成预测问题 → 区分预测问题与实际问题 → 记录回答 → 创建面试复盘 → 提取薄弱点与知识缺口 → 用户确认后更新 Knowledge Gap → Dashboard 展示真实近期面试与待办 → 重启后数据仍存在。

禁止 Mock API、内存数据、刷新丢失数据、伪造面试结果、自动联系面试官、自动加入第三方会议、自动发送提醒或消息。

## 二、执行前检查

在写代码前完整读取并核对：

- `ARCHITECTURE.md`
- `DATABASE_DESIGN.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `docs/PHASE7_FINAL_REPORT.md`
- `docs/PHASE7_POLICY_REVIEW.md`

执行：Java、Maven Wrapper、Node、npm、Python、Docker Compose、Git、端口、Backend/AI/Frontend/MySQL/Redis/Milvus 健康检查。

若文档冲突，必须分析、选择方案、同步修改文档并记录到 Changelog；禁止静默选择。

## 三、范围边界

本阶段只实现：

- Interview、Interview Round、Question、Answer/Note、Review、Knowledge Gap、Review Recommendation、Reminder；
- Interview Agent 的问题预测、答案框架、追问、风险提示与复盘分析；
- Job、Company、Application、Resume Version、Candidate Evidence 的只读关联；
- Frontend Interview Center、详情、复盘和 Dashboard 联动；
- 用户确认后更新 Knowledge Gap。

本阶段禁止：

- 自动录音、偷录、后台监听麦克风；
- 未经同意上传音视频、聊天记录或会议内容；
- 冒充用户回答面试问题；
- 自动发送邮件、微信、BOSS 消息或会议邀请；
- 自动更改 Application 状态；
- 自动把 AI 推断写成实际面试事实；
- 真实平台自动投递、验证码破解、风控绕过；
- Phase 9 Offer Decision、薪资谈判自动化或外部联系。

## 四、数据库迁移

使用 Flyway 新建 `V8__interview_center.sql`，不得依赖 Hibernate 自动建表。

至少设计：

- `interviews`
- `interview_rounds`
- `interview_questions`
- `interview_answer_notes`
- `interview_reviews`
- `interview_review_items`
- `knowledge_gaps`
- `knowledge_gap_evidence`
- `interview_reminders`

所有表必须具备当前用户隔离、必要外键/索引、审计时间、乐观锁和逻辑删除策略。AI 结果必须保存 `source_type`、`prompt_version`、`model_name`、`ai_call_id`、`evidence_refs_json` 和生成时间。

推荐约束：

- Interview 必须关联 Job 或人工填写的 Company/Role 快照；
- Round 的时间范围、顺序和状态合法；
- Predicted Question 与 Actual Question 使用明确枚举，不允许混淆；
- Review 版本不可静默覆盖；
- Knowledge Gap 更新必须保存用户确认来源；
- 同一 Interview/Round 的活动提醒不得重复；
- 删除 Interview 不物理删除 Review、Question 和审计历史。

## 五、Backend 模块

在模块化单体中新增 `com.jobpilot.interview`，至少包含 controller、service、repository、mapper、dto、vo、domain。禁止把代码堆入全局 controller/service/mapper/entity。

实现统一 ApiResponse、TraceId、全局异常、Validation、Ownership、审计与幂等。

建议状态：

- Interview：`SCHEDULED / IN_PROGRESS / COMPLETED / CANCELLED / NO_SHOW`
- Round：`PLANNED / COMPLETED / CANCELLED`
- Question Source：`PREDICTED / ACTUAL / MANUAL`
- Review：`DRAFT / CONFIRMED`
- Knowledge Gap：`PROPOSED / ACTIVE / RESOLVED / DISMISSED`
- Reminder：`PENDING / DONE / CANCELLED`

Application 状态不得因 Interview 创建或复盘自动变化；只能通过既有明确用户操作更新。

## 六、API

至少实现：

- `GET /api/v1/interviews`
- `POST /api/v1/interviews`
- `GET /api/v1/interviews/{id}`
- `PUT /api/v1/interviews/{id}`
- `DELETE /api/v1/interviews/{id}`
- `POST /api/v1/interviews/{id}/rounds`
- `PUT /api/v1/interview-rounds/{id}`
- `DELETE /api/v1/interview-rounds/{id}`
- `POST /api/v1/interview-rounds/{id}/questions:predict`
- `POST /api/v1/interview-rounds/{id}/questions`
- `PUT /api/v1/interview-questions/{id}`
- `POST /api/v1/interview-questions/{id}/answer-notes`
- `POST /api/v1/interviews/{id}/reviews:generate`
- `GET /api/v1/interviews/{id}/reviews`
- `POST /api/v1/interview-reviews/{id}:confirm`
- `GET /api/v1/knowledge-gaps`
- `POST /api/v1/knowledge-gaps/{id}:activate`
- `POST /api/v1/knowledge-gaps/{id}:resolve`
- `POST /api/v1/knowledge-gaps/{id}:dismiss`
- Interview Reminder CRUD。

列表必须支持分页、状态、日期、Company、Role、Job、Application 筛选。所有资源必须验证属于当前登录用户；客户端不得传 `userId` 修改他人数据。

## 七、Interview Agent

在 FastAPI + LangGraph AI Service 中实现 Interview Agent，但保持清晰边界：

- 输入只允许 Job Description、Match Evidence、选定 Resume Version、Candidate Evidence 和用户填写的 Interview 上下文；
- 预测问题必须标记 `PREDICTED`；
- 答案框架必须引用 Candidate Evidence，不得编造项目、指标、职责或技术经历；
- 缺少证据时输出明确 Gap，不得补写事实；
- 复盘必须区分用户记录的事实与 AI 推断；
- 使用严格 JSON Schema/Pydantic；
- Prompt 和输出 Schema 版本化；
- 记录 AI Call、耗时、模型、状态和 TraceId；
- 未配置 LLM Secret 时明确 `SKIPPED_NOT_CONFIGURED` 或真实规则模式，禁止伪装成 LLM 成功。

预测至少覆盖：技术基础、项目深挖、系统设计、行为问题、岗位风险、追问。输出问题、目的、难度、依据、回答框架、证据引用、风险和建议追问。

## 八、Knowledge Gap 人工确认

AI Review 只能创建 `PROPOSED` Knowledge Gap。只有用户点击确认后才可进入 `ACTIVE`。

每条 Gap 至少包含：

- title
- category
- description
- severity
- evidence
- sourceInterviewId/sourceReviewId
- recommendedActions
- status
- confirmedBy/confirmedAt
- resolvedAt

禁止自动修改 Candidate Skill 熟练度、Profile、Resume 或 Match 权重。

## 九、Frontend

继续使用 Vue 3 + JavaScript + Vite + Element Plus + Pinia + Router + Axios；禁止 TypeScript 业务源码。

实现：

- Interview Center 列表/日历切换；
- 创建/编辑面试；
- Round 时间线；
- Predicted 与 Actual Question 清晰标签；
- Answer Note 编辑；
- Review 页面；
- Proposed Knowledge Gap 确认/忽略；
- Dashboard 真实近期面试、待复盘和待办提醒。

无数据时显示明确空状态，禁止伪造 KPI。时区必须使用用户本地时区展示，并在 API/数据库中采用明确时间语义。

UI 延续浅色、留白、轻边框、专业 SaaS 风格；不要传统深蓝后台、满屏 Card、过度圆角和无意义动画。

## 十、提醒

Phase 8 只实现站内 Reminder 数据和 Dashboard 待办；不得自动发送邮件、短信、微信、系统通知或第三方日历邀请。

验证：时区、夏令时、过去时间、改期、取消、重复提醒、完成状态和重启持久化。

## 十一、审计

至少记录：

- `INTERVIEW_CREATE`
- `INTERVIEW_UPDATE`
- `ROUND_CREATE`
- `QUESTION_PREDICT`
- `ACTUAL_QUESTION_RECORD`
- `ANSWER_NOTE_UPDATE`
- `REVIEW_GENERATE`
- `REVIEW_CONFIRM`
- `KNOWLEDGE_GAP_ACTIVATE`
- `KNOWLEDGE_GAP_RESOLVE`
- `REMINDER_CREATE`

审计包含 userId、action、resourceType、resourceId、traceId、createdAt，且不得写入密码、Token、完整私密回答或 Secret。

## 十二、测试

Backend 至少覆盖：

- Interview/Round CRUD 和 Ownership；
- 状态机、日期/时区、逻辑删除和乐观锁；
- Predicted/Actual Question 区分；
- Review 不覆盖历史；
- Knowledge Gap 必须人工确认；
- Reminder 去重、改期和取消；
- Job/Application/Resume 关联权限；
- GlobalExceptionHandler 与 API Integration Test。

AI Service 至少覆盖：

- Schema；
- Prompt 版本；
- Evidence 引用；
- 不编造事实；
- PREDICTED 标记；
- 缺少 LLM Secret；
- 超时/重试/失败；
- Review 事实与推断分离。

Frontend 至少验证 Login、Interview List、Detail、Question、Review、Knowledge Gap、Dashboard，`npm run build` 通过且无 console error。

禁止 `skipTests`、假数据和假 PASS。

## 十三、真实验收

必须执行：

1. `mvn clean test`、`mvn package`；
2. AI Service 全量测试；
3. Frontend build；
4. Flyway V1→V8 空库迁移与 V7→V8 升级；
5. 启动真实 MySQL、Redis、Milvus、Backend、AI Service、Frontend；
6. 创建 Interview 与两个 Round；
7. 生成 Predicted Questions；
8. 录入 Actual Question 与 Answer Note；
9. 生成 Review；
10. 确认一个 Knowledge Gap，忽略另一个；
11. 创建、改期、完成 Reminder；
12. Dashboard 验证真实数据；
13. Backend 重启后重新读取全部数据；
14. 浏览器验证零 console error；
15. 确认外部消息、会议邀请和自动 Application 更新均为 0。

## 十四、TASKS、Git 与文档

开发中同步更新 `TASKS.md`：完成 `[x]`、未完成 `[ ]`、阻塞 `[!]`。Phase 8 P0 未完成前不得进入 Phase 9。

更新 README、DATABASE_DESIGN、API_DESIGN、DEVELOPMENT_ROADMAP、CHANGELOG、测试报告和最终报告。

检查 `.env`、node_modules、target、venv、日志、音视频、数据库文件和运行时文件均被 Git 忽略。不要 Push，除非用户明确授权。

## 十五、最终门槛

最终必须报告：

```text
PROJECT=JobPilot AI
PHASE=PHASE_8
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
BACKEND_RUN=
AI_SERVICE_TEST=
AI_SERVICE_RUN=
DATABASE_MIGRATION=
MYSQL_PERSISTENCE=
INTERVIEW_CRUD=
ROUND_CRUD=
QUESTION_PREDICTION=
ACTUAL_QUESTION_RECORDING=
ANSWER_NOTE=
REVIEW_GENERATION=
KNOWLEDGE_GAP_CONFIRMATION=
REMINDER_CRUD=
TIMEZONE_VALIDATION=
DASHBOARD_INTEGRATION=
FRONTEND_BUILD=
FRONTEND_RUN=
BROWSER_E2E=
CONSOLE_ERRORS=
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_MEETING_INVITES=0 / 非0
AUTOMATIC_APPLICATION_UPDATES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任何核心项失败则 `PHASE_STATUS=FAIL`；缺少必须人工提供的 Secret、权限或外部依赖则标记 `BLOCKED`。只有真实执行成功才允许写 PASS。

本提示词只生成下一阶段执行规范，不代表已授权立即执行 Phase 8。等待用户明确说“执行 Phase 8”后再开始。
