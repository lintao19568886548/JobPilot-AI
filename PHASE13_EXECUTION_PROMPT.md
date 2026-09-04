# JobPilot AI Phase 13 Execution Prompt

你现在继续开发并真实验收：

`D:\JobPilot AI`

当前 Phase 0–12 已完成。Phase 13 定义为：

# Phase 13 — Guided Onboarding & Data Quality（首次使用引导与数据质量中心）

本阶段解决“系统已经可运行，但用户不知道还缺哪些真实资料、哪些条件会降低匹配质量”的问题。所有结论必须从当前登录用户的真实 MySQL 数据实时计算；不新增 Mock 数据，不用模型猜测用户事实，不新增招聘网站后台采集、验证码绕过、外部消息、自动投递或自动 Offer 决策。

## 一、执行原则

1. 先读取 Phase 0–12 架构、数据库、API、Roadmap、Task、Changelog、Runbook 和现有实现。
2. 发现旧文案或设计与当前实现冲突时，修正文档并在 Changelog 说明，不静默保留。
3. Data Quality 只给出确定性检查结果；不能把缺失字段自动补成虚构内容。
4. 所有接口只读取当前登录用户，客户端不能传 `userId`。
5. 本阶段默认不新增数据库表；若可从事实表实时重算，就不保存派生状态。
6. 不 commit、不 push，除非用户明确授权。

## 二、Backend 模块

新增 `com.jobpilot.onboarding` 业务模块，至少包含：

- `OnboardingController`
- `OnboardingService`
- `OnboardingDtos`

提供：

- `GET /api/v1/onboarding/overview`
- `GET /api/v1/onboarding/data-quality`

统一使用现有 `ApiResponse`、Trace ID、JWT 和全局异常体系。

## 三、引导检查清单

`overview` 至少实时检查：

1. 基本身份与联系方式；
2. 求职目标、城市和薪资偏好；
3. 技能数量与核心技能；
4. 教育经历；
5. 工作/实习经历；
6. 项目证据；
7. Active Master Resume；
8. Default Resume；
9. Resume Version。

每项必须返回稳定 `key`、状态、当前值、目标值、权重、获得分数、说明、修复页面路径和是否阻断匹配。总分必须为 `0–100`，规则常量放在 Service，不放 Controller。`READY` 只能在达到明确门槛且不存在 Blocker 时返回。

## 四、数据质量检查

`data-quality` 返回按严重级别稳定排序的问题列表。至少覆盖：

- 缺少姓名、邮箱或标题；
- 缺少目标岗位/城市/薪资范围；
- 技能少于 5 个；
- 核心技能少于 2 个；
- 缺少教育、经历或项目；
- 没有 Active Master Resume；
- 没有 Default Resume；
- 简历没有当前 Version；
- 数据已满足时返回空问题列表，而不是伪造建议。

每条问题返回稳定 `code`、`severity=BLOCKER/WARNING/INFO`、标题、说明、资源类型和 `actionPath`。返回 Blocker/Warning/Info 数量和 `generatedAt`，但不持久化派生问题。

## 五、Frontend Setup Center

新增 `/setup` 页面和主导航项 `Setup Center`：

- 顶部展示真实 Readiness 分数和 `READY/NEEDS_WORK`；
- 展示九项检查清单、当前/目标、权重和明确操作入口；
- Data Quality 按严重级别展示，支持刷新；
- 没有问题时展示“当前未发现确定性数据质量问题”；
- 清楚说明系统不会自动编造资料或修改简历；
- Loading、错误和空状态必须完整；
- 390px 不允许横向溢出。

Dashboard 增加轻量 Setup Readiness 提示和入口，但不能破坏已有真实 Analytics。顶部阶段标识更新为 Phase 13。

## 六、前端性能修复

现有路由已经使用动态导入，继续完成：

- Element Plus 改为仅注册仓库真实使用的组件和 Loading 指令；
- ECharts 改为按需注册 Pie/Bar、Tooltip/Grid 和 Canvas Renderer；
- Production Build 不得出现单个 Chunk 超过 500 KiB 的警告；
- 不改变现有页面功能。

## 七、测试要求

Backend 至少测试：

- 空资料总分与 Blocker；
- 部分资料的逐项分数；
- 完整资料 `READY`；
- 技能与核心技能阈值；
- Master/Default/Version 独立检查；
- 问题严重级别和稳定排序；
- 当前用户隔离；
- Controller/Auth/ApiResponse/Trace 集成契约。

Frontend 至少执行 Production Build，并验证不存在大 Chunk 警告。原有 Backend/AI/Extension/Worker 安全边界必须回归。

## 八、真实验收

1. Backend `mvn clean test` 与 `mvn package`；
2. AI pytest 与 Ruff；
3. Frontend production build，确认 Chunk 门槛；
4. Extension 与 Automation Worker 回归；
5. 重建 Backend/Frontend Release 镜像并启动真实 Docker Stack；
6. 登录真实本地账户；
7. HTTP 验证 overview、data-quality、Dashboard 和 Trace；
8. 修改或复用真实 Candidate/Resume 数据验证结果来自 MySQL；
9. 重启 Backend/Frontend，重新读取并确认一致；
10. 浏览器桌面与 390px 验证 Setup Center、操作入口、空/问题状态和 console；
11. 确认外部消息、投递、外部变更、自动 Offer 决策和破坏性删除仍为 0。

## 九、文档与任务

更新：

- `ARCHITECTURE.md`
- `API_DESIGN.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`

新增：

- `docs/PHASE13_TEST_REPORT.md`
- `docs/PHASE13_FINAL_REPORT.md`

## 十、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_13
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
AI_REGRESSION=
FRONTEND_BUILD=
FRONTEND_CHUNK_BUDGET=
EXTENSION_REGRESSION=
AUTOMATION_WORKER_REGRESSION=
ONBOARDING_API=
DATA_QUALITY_API=
CURRENT_USER_ISOLATION=
READINESS_SCORING=
DASHBOARD_INTEGRATION=
MYSQL_SOURCE_OF_TRUTH=
APPLICATION_RESTART=
DESKTOP_UI=
MOBILE_390PX=
CONSOLE_ERRORS=
TRACE_PROPAGATION=
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_SUBMISSIONS=0 / 非0
EXTERNAL_MUTATIONS=0 / 非0
AUTOMATIC_OFFER_DECISIONS=0 / 非0
DESTRUCTIVE_DUPLICATE_DELETES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任一核心 Gate 失败不得声明 Phase 13 Complete。
