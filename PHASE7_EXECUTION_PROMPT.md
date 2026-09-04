# JobPilot AI — Phase 7 Execution Prompt

你正在开发 `D:\JobPilot AI`。Phase 0–6 已通过真实 MySQL、Redis、Milvus、Backend、AI Service、Frontend、HTTP Smoke 与浏览器 E2E。现在只执行 Phase 7：Chrome Manifest V3 Extension 与受控 Assist Foundation。

## 1. 阶段拆分与安全边界

Phase 7 分为两个必须独立验收的子阶段：

- **7A Extension**：当前可见页面提取、受限设备配对、岗位保存、Match、Queue、Draft 与打开 JobPilot。
- **7B Assist Worker Foundation**：默认关闭；只允许显式白名单的本地测试站，能 Fill 但永不 Submit，用于证明安全状态机和人工接管边界。

禁止：读取/上传 Cookie、Authorization、localStorage、sessionStorage、密码、验证码、浏览器指纹或隐藏字段；禁止 `<all_urls>`、后台遍历页面、自动消息发送、自动投递、点击 Submit、按 Enter 提交、验证码处理、登录绕过、反检测和平台风控绕过。

真实招聘平台专用 Adapter 必须先有独立政策复核记录。未复核平台只允许 `GENERIC_VISIBLE` 或人工选择文本，不猜测字段，不启用自动化。

## 2. Extension 最小权限

使用 Manifest V3 + TypeScript + Vite + Vitest，不使用远程代码。Manifest 只允许：

- `activeTab`：用户点击后临时访问当前 Tab；
- `scripting`：仅向当前 Tab 注入本地打包脚本；
- `storage`：凭据只写 `chrome.storage.session`，不持久化 Refresh Token；
- `sidePanel`：承载 JobPilot 侧栏；
- Backend 本地地址是唯一 Host Permission；招聘网站不配置持久 Host Permission。

不得声明 `cookies/history/webRequest/debugger/nativeMessaging/<all_urls>`。

## 3. Extension 配对与凭据

Flyway V7 新增：

- `extension_pairing_codes`：5 分钟一次性 Code Hash、用户、Scope、过期和使用时间；
- `extension_devices`：设备、Extension ID、固定 Scope、状态、配对/最后使用/撤销时间和 Token Version；
- `extension_refresh_tokens`：只保存 SHA-256、过期、轮换和撤销状态；
- `automation_tasks` 与 `automation_task_steps`：只记录受控 Prepare 任务及不可变步骤。

API：

- `POST /api/v1/extension/pairing-codes`：Web JWT 创建一次性码；
- `POST /api/v1/extension/pairings`：公开兑换一次性码；
- `POST /api/v1/extension/tokens/refresh`：轮换 Refresh Token；
- `GET /api/v1/extension/devices`：Web JWT 查看设备；
- `DELETE /api/v1/extension/devices/{id}`：立即撤销设备和 Refresh Token；
- `POST /api/v1/extension/job-captures`：仅 Extension Scope；
- `GET /api/v1/extension/jobs/{jobId}/workspace`：岗位、Match、优劣势/风险、推荐 Resume 和 Draft；
- `POST /api/v1/extension/jobs/{jobId}:analyze`：创建 Match Run；
- `POST /api/v1/extension/jobs/{jobId}/queue`：安全入队，不批准、不提交；
- `POST /api/v1/extension/jobs/{jobId}/communication-drafts`：只生成 Draft；
- `POST /api/v1/extension/queue/{queueId}:prepare`：仅在 Worker 启用、Policy 允许、Queue 已批准时创建 Prepare；永不创建 Application。

Extension Access Token 使用短时 JWT，必须包含 `tokenType=EXTENSION_ACCESS`、device、user、固定 Scope 和 tokenVersion；每个请求重新检查设备 ACTIVE 与版本。Refresh Token 是轮换的随机不透明值，数据库只保存 Hash。Extension Token 只能访问明确的 Extension API，不能访问账户、导出或普通业务 API。

## 4. 当前页面提取

Adapter 接口必须返回严格 `VisibleJobSnapshot`。实现：

- `FIXTURE_V1`：只读取带 `data-jobpilot-field` 的本地测试 Fixture；
- `GENERIC_VISIBLE_V1`：只读取可见标题、正文和标准 Meta；
- `MANUAL_SELECTION_V1`：只读取用户当前选中的可见文本。

所有文本经过长度限制、空白规范化和可见性检查；忽略 `input[type=password]`、hidden、不可见节点、script/style/noscript 和任何存储/Cookie。未知页面返回 `NEEDS_REVIEW`，必须在 Action Popup 由用户编辑并确认后才能 Capture。

## 5. Action Popup

侧栏必须显示：

- 配对状态、设备、Scope 和撤销提示；
- 当前页面 Adapter、来源 URL、提取时间和 `NEEDS_REVIEW/READY`；
- 可编辑的岗位标题、公司、城市、薪资和 JD；
- 保存岗位、触发 Match、刷新 Workspace、加入 Queue、生成 Draft、打开 JobPilot；
- Match Level/Score、优势、缺口、风险、推荐 Resume、Draft；
- 所有按钮明确说明不会自动投递或发送消息。

侧栏不提供 Submit、Send 或绕过入口。

## 6. Assist Worker Foundation

建立独立 `automation-worker`（Node.js + TypeScript + Playwright）：

- 默认 `AUTOMATION_WORKER_ENABLED=false`；
- 默认 URL 白名单仅 `127.0.0.1/localhost`；
- 只接受 Backend 服务 Token；
- 检查 Policy、Task Token、过期、Queue Approval 和 Idempotency；
- 打开页面后先检测 CAPTCHA/二次验证/登录/不确定页面；发现即 `BLOCKED`，不 Fill；
- 只按显式 Selector 白名单调用 `fill()`，不调用 click Submit、press Enter 或 form.submit；
- 返回 `externallySubmitted=false`、`applicationCreated=false`、`finalConfirmationRequired=true`；
- Backend 追加 `OPEN/CHECK/FILL/HANDOFF/BLOCKED` 步骤，不覆盖历史。

使用仓库内本地 Fixture 验证：普通表单能 Fill 且 submitCount 保持 0；验证码 Fixture 必须 BLOCKED 且字段不变。

## 7. Backend 安全

JWT Web 用户增加 `ROLE_USER`；Extension Filter 只处理 `/api/v1/extension/**` 的 `jpe_` Access Token，并授予固定 `SCOPE_EXTENSION`。Security 配置必须保证：

- Pairing Exchange/Refresh 可匿名但受一次性 Token、过期、轮换与错误收敛保护；
- Pairing Code/Device 管理只允许 `ROLE_USER`；
- Capture/Workspace/Analyze/Queue/Draft/Prepare 只允许 `SCOPE_EXTENSION`；
- Extension Token 请求普通 `/api/v1/**` 返回 401/403；
- Web JWT 不能伪装成 Extension Device 调用受限端点。

所有写操作记录 Trace ID 与 Audit。错误不得回显 Token、Hash、内部 SQL 或设备密钥。

## 8. 测试与验收

Backend 测试覆盖：一次性 Code、过期、重复兑换、Refresh 轮换与重放、设备撤销、Scope 隔离、Extension Token 越权、Capture 禁止字段、Ownership、Workspace、Queue/Draft 零提交、Worker Disabled、Policy 拒绝、步骤不可变。

Extension 测试覆盖：Manifest 权限白名单、禁止权限、3 类 Adapter、隐藏字段/密码/Cookie 不读取、未知页面降级、payload 白名单、session storage、API 401 refresh、Action Popup 无 Send/Submit 文案。

Worker 测试覆盖：配置默认关闭、非白名单 URL 拒绝、普通 Fixture Fill 零 Submit、CAPTCHA BLOCKED、Selector 白名单、响应安全字段和幂等。

必须执行：

- Backend `clean test`、`package`、Run；
- Extension `npm ci`、lint、typecheck、Vitest、build；
- Worker `npm ci`、lint、typecheck、Vitest、build、Run；
- V7 现有库迁移和 V1→V7 空库迁移；
- 真实 Pair→Capture→Match→Workspace→Queue→Draft HTTP Smoke；
- Backend 重启后的设备、岗位、Queue、Draft 和 Audit 持久化；
- 本地 Fixture 的 Fill-without-Submit 与 CAPTCHA BLOCKED；
- 加载 unpacked Extension 的 Manifest/Action Popup 浏览器 E2E；
- Phase 1–6 核心回归、Redis/Milvus 回归。

只有实际成功才标记 PASS。任何外部 Submit/Send、敏感数据读取、越权、Migration/Test/Build 失败都使 `PHASE_STATUS=FAIL`。

最终报告输出 Phase 7A/7B 各项状态、文件数量、测试数量、HTTP 数量、数据库表数量、权限清单、外部提交/发送数量、未完成项、阻塞项、技术债务和 Phase 8 建议。不得 push。
