# JobPilot AI — Phase 7 Chrome 最终验收执行提示词

你现在继续处理项目：

`D:\JobPilot AI`

当前 Phase 7 的 Backend、Frontend、AI Service、MySQL、Redis、Milvus、Extension Build、Automation Worker Fixture、安全测试、HTTP Smoke 和 Backend 重启持久化均已通过。唯一未完成项是：在用户真实 Chrome 中加载 `extension\dist` 后，对 unpacked Manifest V3 Extension 做最终可视化验收。

本次不是开发 Phase 8，也不是重新设计 Phase 7。目标是用真实 Chrome 完成最后一个验收门槛，并根据真实结果把 `EXTENSION_UNPACKED_E2E` 和 `PHASE_STATUS` 更新为 PASS、FAIL 或 BLOCKED。

## 一、强制安全边界

全程禁止：

- 自动点击任何 Submit、Apply、Send、Confirm 或投递按钮；
- 向真实招聘平台提交申请或发送消息；
- 读取或输出 Cookie、Authorization、localStorage、sessionStorage、密码、验证码、浏览器指纹或隐藏 DOM 内容；
- 破解验证码、绕过登录、绕过风控或模拟真人反检测；
- 把测试范围扩大到真实招聘网站；
- 将 Token、密码或 `.env` 内容写入日志、截图、报告或 Git；
- 为了得到 PASS 而伪造浏览器结果。

只允许使用仓库内的 localhost Fixture。任何页面异常、验证码、权限不确定或提取结果不可信都必须停止，并标记 `NEEDS_REVIEW` 或 `BLOCKED`。

## 二、验收前检查

1. 读取并遵守：
   - `PHASE7_EXECUTION_PROMPT.md`
   - `docs/PHASE7_POLICY_REVIEW.md`
   - `docs/PHASE7_TEST_REPORT.md`
   - `docs/PHASE7_FINAL_REPORT.md`
   - `TASKS.md`
2. 执行只读检查：
   - `git status --short --ignored`
   - Backend `http://127.0.0.1:8088/actuator/health`
   - Frontend `http://127.0.0.1:5173`
   - Docker MySQL、Redis、Milvus 健康状态。
3. 不输出 `.env`。如需启动服务，只从 `.env` 加载配置。
4. Automation Worker 默认必须保持关闭；只有验证 localhost Fixture 的 Assist 时才可在当前进程临时设置 `AUTOMATION_WORKER_ENABLED=true`，完成后必须停止 Worker，并把 Backend 恢复到默认关闭配置。

## 三、确认扩展加载状态

在用户已经打开的 Chrome 中检查 `chrome://extensions`：

- 开发者模式已开启；
- 已通过“加载已解压的扩展程序”选择：
  `D:\JobPilot AI\extension\dist`
- 扩展名称为 `JobPilot AI Assist`；
- 版本为 `0.1.2`；
- 扩展处于启用状态；
- 页面没有 Manifest、Service Worker、CSP 或资源加载错误。

核对扩展权限，只允许：

```text
activeTab
scripting
storage
sidePanel
```

Host Permission 只允许：

```text
http://127.0.0.1:8088/*
```

以下任一权限出现都立即 FAIL：

```text
<all_urls>
cookies
history
webRequest
debugger
nativeMessaging
```

如果当前自动化环境无法连接用户 Chrome，不允许用另一个浏览器冒充。请让用户提供扩展卡片和错误区域截图，然后依据截图继续；不能看到的项目必须标记 BLOCKED。

## 四、创建一次性配对码

1. 打开 `http://127.0.0.1:5173/login`。
2. 用户亲自输入本地账号密码；自动化不得读取、记录或复述密码。
3. 登录后打开 `http://127.0.0.1:5173/extension`。
4. 点击创建一次性配对码。
5. 验证配对码有短时过期提示，且页面显示设备管理与安全说明。
6. 不把配对码写入报告或 Git。

## 五、Action Popup 与配对验收

1. 在 Chrome 打开本地 Fixture：
   `http://127.0.0.1:8020/fixtures/job-page.html`
2. 用户点击 `JobPilot AI Assist` 扩展图标，确认浏览器原生 Action Popup 打开。
3. Action Popup 初始状态必须显示“未配对”。
4. 用户将一次性配对码输入 Action Popup，点击“连接 JobPilot”。
5. 成功后必须显示：
   - 已配对；
   - 当前设备名称；
   - 固定 Extension Scope；
   - 不会自动投递或发送消息的安全提示。
6. 再次兑换同一个配对码必须失败；错误信息不得泄露 Hash、Token 或内部 SQL。

## 六、可见页面提取验收

在 `job-page.html` 点击“提取当前可见岗位”，验证：

```text
职位名称=Senior Java Backend Engineer
公司=Example Technology
城市=Shanghai
薪资=35k-50k · 14薪
JD 包含=Spring Boot, MySQL, Redis
```

同时验证：

- Adapter 为 Fixture Adapter 或等价的明确标识；
- 页面 URL 为 localhost Fixture；
- 所有字段在保存前均可人工编辑；
- 密码输入框内容没有被读取；
- hidden 节点中的 `hidden-cookie=session-secret` 没有出现在 Action Popup、网络 Payload、日志或数据库；
- 不存在 Cookie、Authorization、Storage 或浏览器指纹字段；
- 未知页面只能进入 `NEEDS_REVIEW`，不能静默猜测并直接保存。

如果需要检查 DevTools Network，只查看请求字段名称和安全布尔值，不展开或复制任何 Token。

## 七、真实 Backend 工作流验收

依次执行，并在每一步确认真实接口成功且刷新后数据仍存在：

1. “确认并保存岗位”；
2. “分析匹配”；
3. 刷新 Workspace，查看 Match Level、Score、优势、缺口、风险和推荐 Resume；
4. “生成沟通草稿”，确认状态为 Draft/仅供人工编辑，没有发送；
5. “加入 Assist 队列”，确认只创建 Queue Item，没有批准、没有 Application、没有外部投递；
6. “在 JobPilot 中查看完整详情”，确认打开本地 Frontend 的对应岗位详情。

验收期间必须始终满足：

```text
externallySubmitted=false
applicationCreated=false
messageSent=false
finalConfirmationRequired=true
```

## 八、零提交与阻断验收

临时启动受控 Automation Worker，只允许 localhost Fixture，然后运行仓库已有 Worker Fixture 测试：

```powershell
npm --prefix automation-worker run test
```

必须确认：

- 普通表单只填写批准字段；
- `#submit-count` 始终为 `0`；
- Submit 按钮从未被点击；
- 未执行 Enter、`form.submit()` 或外部消息发送；
- CAPTCHA Fixture 在任何填写之前返回 `BLOCKED`；
- CAPTCHA 不被识别、破解或绕过。

完成后停止 Worker，按 `.env` 默认配置重启 Backend，并确认：

```text
AUTOMATION_WORKER_ENABLED=false
Backend health=UP
Worker endpoint=unreachable
```

## 九、设备撤销验收

1. 回到 Frontend 的 Extension 管理页。
2. 确认新配对设备真实出现在设备列表。
3. 撤销该设备。
4. 回到 Action Popup 再次刷新 Workspace。
5. 请求必须返回 401/未授权并退出已配对状态。
6. 已轮换的 Refresh Token 不能恢复被撤销设备。

## 十、浏览器质量检查

检查 Extension 页面、Action Popup、Fixture 和 JobPilot Frontend：

- 无 console error；
- 无 CSP violation；
- 无失败资源；
- 无横向溢出或按钮遮挡；
- 中文文案清晰；
- Action Popup 没有 Submit、Send、自动投递或绕过入口。

允许记录不含凭据和 Token 的截图作为本地验收证据，但不要提交包含账号信息的截图。

## 十一、文档与任务状态

只有全部浏览器检查真实通过后，才允许：

- 将 `TASKS.md` 的 unpacked Extension E2E 项改为 `[x]`；
- 将 `docs/PHASE7_TEST_REPORT.md` 的浏览器阻塞改为 Passed，并记录实际 Chrome 版本、Extension ID、Fixture URL、零提交结果和检查时间；
- 将 `docs/PHASE7_FINAL_REPORT.md` 更新为：
  - `PHASE_STATUS=PASS`
  - `PHASE_7A_EXTENSION=PASS`
  - `EXTENSION_UNPACKED_E2E=PASS`
- 更新 `README.md`、`CHANGELOG.md` 和 `DEVELOPMENT_ROADMAP.md`；
- 生成 Phase 8 的详细执行提示词，但不要自动开始 Phase 8，除非用户明确要求执行。

如果任一核心检查失败，必须保持 `PHASE_STATUS=FAIL` 或 `BLOCKED`，并记录可复现步骤。禁止假通过。

## 十二、最终输出格式

```text
PROJECT=JobPilot AI
PHASE=PHASE_7_CHROME_ACCEPTANCE
PHASE_STATUS=PASS / FAIL / BLOCKED
CHROME_RUNNING=
UNPACKED_EXTENSION_LOADED=
MANIFEST_PERMISSIONS=
HOST_PERMISSIONS=
SERVICE_WORKER=
ACTION_POPUP_OPEN=
PAIRING=
PAIRING_REPLAY_REJECTED=
VISIBLE_EXTRACTION=
HIDDEN_DATA_EXCLUDED=
PASSWORD_EXCLUDED=
CAPTURE_SAVE=
MATCH_ANALYSIS=
WORKSPACE_READ=
DRAFT_CREATED_NOT_SENT=
QUEUE_CREATED_NOT_APPROVED=
EXTERNAL_SUBMISSIONS=0 / 非 0
EXTERNAL_MESSAGES_SENT=0 / 非 0
FIXTURE_SUBMIT_COUNT=
CAPTCHA_BLOCKED=
DEVICE_REVOCATION=
CONSOLE_ERRORS=
POST_TEST_WORKER_STATE=
DOCUMENTS_UPDATED=
GIT_STATUS=
```

随后报告：实际检查数量、截图证据数量、失败项、阻塞项、技术债务，以及是否允许生成/执行 Phase 8。
