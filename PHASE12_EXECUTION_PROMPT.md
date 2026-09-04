# JobPilot AI Phase 12 Execution Prompt

你现在继续开发并真实验收：

`D:\JobPilot AI`

当前 Phase 0–11 已完成。Phase 12 定义为：

# Phase 12 — Local Secure Release（本地安全发布与一键运维）

本阶段只把已经完成的系统封装成可重复构建、可验证、可启动、可停止、可回滚应用版本的本机发布形态。不得新增招聘网站后台采集、验证码绕过、外部消息、自动投递、自动接受/拒绝 Offer 或新的第三方账号权限。

## 一、执行原则

1. 先读取 Phase 0 设计、Phase 11 Prompt/Runbook/Test/Final/Security Triage、`TASKS.md`、`CHANGELOG.md` 和当前代码。
2. 发现冲突时先分析并更新设计文档和 Changelog，不能静默选择。
3. 所有验收必须使用真实 Docker、真实 MySQL/Redis/Milvus、真实 Backend/AI/Frontend 进程；禁止 Mock/Fake PASS。
4. Secret、密码、JWT、Cookie、简历/JD 原文不得写入镜像层、日志、Release Manifest 或 Git。
5. 不删除命名数据卷，不执行 Flyway Clean，不覆盖现有备份；数据库迁移只允许向前。
6. 不 commit、不 push，除非用户明确授权。

## 二、目标发布拓扑

提供 `deploy/docker-compose.release.yml`，与根 `docker-compose.yml` 合并运行：

- Frontend：静态 Production Build，由非特权 Nginx 提供，主机默认仅绑定 `127.0.0.1:8180`；选择 8180 是为了避开本机已经存在且不属于 JobPilot 的 8080 服务；
- Backend：Java 21 JRE、非 root、只读根文件系统，主机仅绑定 `127.0.0.1:8088`；
- AI Service：Python 3.11、非 root、只读根文件系统，模型缓存使用独立命名卷，主机仅绑定 `127.0.0.1:8010`；
- MySQL、Redis、Milvus 延续已有命名卷；MySQL/Redis/Milvus 只绑定 loopback；
- etcd、MinIO 不发布主机端口；
- 服务使用健康检查、`restart: unless-stopped`、日志轮转、`no-new-privileges`、`cap_drop: ALL`（确需能力时记录例外）；
- Frontend 同源代理 `/api`、`/actuator/health`、`/v3/api-docs` 和 `/swagger-ui` 到 Backend。

不得把数据库或对象存储暴露到 `0.0.0.0`。

## 三、镜像与构建

建立多阶段 Dockerfile：

- `deploy/backend/Dockerfile`：Maven Wrapper 构建，Java 21 JRE 运行；
- `deploy/frontend/Dockerfile`：Node 构建，非特权 Nginx 运行；
- `deploy/ai-service/Dockerfile`：安装运行时 requirements，非 root Uvicorn 运行；
- 根 `.dockerignore` 排除 `.env`、`.git`、备份、报告、运行目录、依赖缓存、构建产物、上传和录音。

镜像必须：

- 使用固定 Phase 12 版本标签；
- 不包含 `.env`；
- 运行用户 UID 不得为 0；
- 支持容器健康检查；
- 将日志输出到 stdout/stderr；
- 不在构建参数中传 Secret。

## 四、Production 配置

Backend 增加 `production` Profile：

- `server.address=0.0.0.0` 仅用于容器内部，主机发布仍必须 loopback；
- 禁止返回异常堆栈或敏感绑定信息；
- Graceful Shutdown；
- 健康和 Prometheus 保留；
- OTLP 默认关闭；
- Swagger 仅通过本机发布入口可访问；
- CORS 只允许明确本机来源，不允许 `* + credentials`。

AI Service 保持无 LLM Secret 时 `RULES_ONLY / NOT_CONFIGURED`，不得伪装成 LLM 成功。

Frontend Nginx 必须提供：

- SPA History fallback；
- API 反向代理；
- `X-Content-Type-Options`、`Referrer-Policy`、`Permissions-Policy`、合理 CSP；
- 静态资源缓存，HTML 不长期缓存；
- 不暴露 Server 版本。

## 五、一键运维脚本

新增 PowerShell 脚本：

- `scripts/phase12-common.ps1`：安全读取 `.env`，不输出值；
- `scripts/phase12-doctor.ps1`：检查 Java/Docker/Compose、必需环境变量、Compose Schema、磁盘和端口；
- `scripts/phase12-build.ps1`：运行 Backend/AI/Frontend 回归后构建固定标签镜像；
- `scripts/phase12-release.ps1`：生成 Git 忽略的 Release Manifest 和 ZIP，记录版本、Git 状态、迁移版本、镜像 ID、文件 SHA-256；
- `scripts/phase12-up.ps1`：可选执行 Phase 11 备份后启动/等待全部健康；
- `scripts/phase12-down.ps1`：只停止应用容器，默认不删除基础设施和命名卷；
- `scripts/phase12-smoke.ps1`：真实验证发布入口、代理、登录、Operations、安全 Header、容器硬化和零外部动作；
- `scripts/phase12-rollback-check.ps1`：只验证回滚前置条件和 Manifest/镜像是否存在，不自动降级数据库、不自动执行回滚。

所有脚本必须在失败时非零退出；不得吞掉 Docker/HTTP 错误。

## 六、Release Manifest

生成 `releases/jobpilot-ai-0.12.0/manifest.json`，至少包含：

- schemaVersion；
- releaseVersion；
- createdAtUtc；
- sourceGitStatus；
- flywayVersion；
- databaseTableCount；
- 三个应用镜像的标签和不可变 Image ID；
- Compose/Dockerfile/Nginx/Backend JAR/Frontend Dist/AI requirements 的 SHA-256；
- Phase 11 最新备份 Manifest 的相对路径和 Hash；
- safetyBoundary；
- 不包含 Secret。

发布 ZIP 必须有独立 SHA-256 文件。重复构建可以产生新时间戳，但 Manifest 内的业务版本必须固定且可追溯。

## 七、启动、停止与回滚边界

- 启动前检查端口冲突；只允许精确停止已确认属于 JobPilot 的旧宿主机开发进程。
- Release Up 不删除、重建或清空命名数据卷。
- Release Down 默认只停止 `frontend/backend/ai-service`，基础设施继续运行。
- 回滚只切换到已存在且 Manifest 校验通过的旧应用镜像；数据库必须兼容当前 Flyway 版本。
- 若 Manifest、镜像或备份缺失，回滚检查必须 `BLOCKED`，不得猜测或拉取不明版本。

## 八、测试要求

至少补充：

- Production Profile 安全配置契约；
- `.dockerignore` Secret/Runtime 排除契约；
- Compose loopback/非 root/只读/能力/健康检查契约；
- Nginx Header、SPA 和 Proxy 契约；
- Release Manifest Schema、Hash 和 Secret 负向检查；
- 回滚 Manifest/版本/Flyway 兼容性检查；
- 原有安全边界回归。

不允许 `skipTests` 作为最终测试结果。

## 九、真实验收顺序

1. Backend `mvn clean test` 与 `mvn package`；
2. AI `pytest` 与 Ruff；
3. Frontend Build；Extension/Automation Worker 回归；
4. Docker Compose 配置验证；
5. 构建三个固定版本应用镜像；
6. 检查镜像不含 `.env`、容器用户非 root；
7. 生成 Release Manifest/ZIP 并验证全部 Hash；
8. 在启动前生成或复用最近一次通过的 Phase 11 备份；
9. 停止精确识别的开发进程，启动 Release Stack；
10. 验证所有容器状态与健康检查；
11. 通过 `127.0.0.1:8180` 验证 Frontend、SPA、API Proxy 和安全 Header；
12. 验证 Backend/Swagger/OpenAPI、AI Readiness、MySQL、Redis、Milvus；
13. 登录并读取 Dashboard/Operations；
14. 重启应用容器，验证 MySQL 数据和预算/运维记录仍存在；
15. 执行回滚 Dry Run，不实际降级数据库；
16. 使用内置浏览器完成桌面与 390px 页面验收并检查 console；
17. 确认外部消息、投递、外部变更、自动 Offer 决策和破坏性重复删除仍为 0。

## 十、文档与任务

更新：

- `ARCHITECTURE.md`
- `DEVELOPMENT_ROADMAP.md`
- `TASKS.md`
- `CHANGELOG.md`
- `README.md`

新增：

- `docs/PHASE12_RELEASE_RUNBOOK.md`
- `docs/PHASE12_TEST_REPORT.md`
- `docs/PHASE12_FINAL_REPORT.md`

`.env`、镜像导出、Release ZIP、Manifest 运行实例、`target`、`dist`、`node_modules`、`.venv` 和运行日志必须保持 Git 忽略。

## 十一、最终门槛

```text
PROJECT=JobPilot AI
PHASE=PHASE_12
PHASE_STATUS=PASS / FAIL / BLOCKED
BACKEND_BUILD=
BACKEND_TEST=
AI_REGRESSION=
FRONTEND_BUILD=
EXTENSION_REGRESSION=
AUTOMATION_WORKER_REGRESSION=
COMPOSE_VALIDATION=
BACKEND_IMAGE=
AI_IMAGE=
FRONTEND_IMAGE=
IMAGE_NON_ROOT=
IMAGE_SECRET_CHECK=
READ_ONLY_ROOTFS=
LOOPBACK_BINDINGS=
HEALTHCHECKS=
RELEASE_MANIFEST=
RELEASE_HASHES=
RELEASE_ARCHIVE=
BACKUP_PRECONDITION=
RELEASE_UP=
FRONTEND_GATEWAY=
API_PROXY=
SECURITY_HEADERS=
BACKEND_RUN=
AI_RUN=
MYSQL_PERSISTENCE=
REDIS_CONNECTION=
MILVUS_CONNECTION=
APPLICATION_RESTART=
ROLLBACK_DRY_RUN=
OPERATIONS_UI=
BROWSER_E2E=
MOBILE_390PX=
CONSOLE_ERRORS=
EXTERNAL_MESSAGES_SENT=0 / 非0
EXTERNAL_SUBMISSIONS=0 / 非0
EXTERNAL_MUTATIONS=0 / 非0
AUTOMATIC_OFFER_DECISIONS=0 / 非0
DESTRUCTIVE_DUPLICATE_DELETES=0 / 非0
HTTP_SMOKE_TEST=
GIT_STATUS=
```

任一核心 Gate 失败不得声明 Phase 12 Complete。扫描或上游镜像的已知残余风险继续沿用 Phase 11 的显式 `PASS_WITH_FINDINGS`，不得隐藏。
