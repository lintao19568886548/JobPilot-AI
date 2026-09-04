# Phase 12 本地安全发布运行手册

## 1. 适用范围

本手册用于把 JobPilot AI `0.12.x` 作为单机、本地优先应用运行。默认拓扑只绑定 `127.0.0.1`，不提供公网或局域网发布能力；如需网络暴露，必须另行完成 TLS、反向代理、可信 Origin、Secret 管理、访问控制和安全复核。

Phase 12 不新增招聘平台外部动作。浏览器 Assist 仍须人工批准，并且只准备表单、不点击提交；系统不会自动发送消息、自动投递、自动接受/拒绝 Offer 或破坏性删除重复岗位。

## 2. 前置条件

- Windows PowerShell、Docker Desktop、Docker Compose、Git、Java 21、Node.js 22+ 可用。
- 从 `.env.example` 创建本机 `.env`，替换所有示例 Secret；`.env` 不得提交或放入发布包。
- 至少保留 10 GiB 可用磁盘。
- 默认入口为 `http://127.0.0.1:8180`。可通过 `JOBPILOT_GATEWAY_PORT` 改端口；Backend 默认 `8088`，AI Service 默认 `8010`。

先执行只读预检：

```powershell
Set-Location 'D:\JobPilot AI'
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-doctor.ps1
```

预检会验证必要环境变量、JWT 最低长度、工具链、Docker Engine、Compose 配置、磁盘空间和全部主机端口仅绑定 Loopback。

## 3. 构建与启动

执行完整回归并构建三个非 Root 应用镜像：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-build.ps1
```

只有已经单独完成回归时，才可使用 `-SkipRegression`。启动前默认创建 Phase 11 一致批次备份，然后启动 MySQL、Redis、Milvus 及三个应用服务：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-up.ps1
```

`-SkipBackup` 只适用于已经核验同一时点备份的受控场景。`-Build` 会在启动时重建镜像。

## 4. 验证

完整 HTTP、安全头、Trace、容器加固、健康和零外部动作校验：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-smoke.ps1
```

验证重启持久化：

```powershell
docker compose -f .\docker-compose.yml -f .\deploy\docker-compose.release.yml --profile ai restart frontend backend ai-service
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-smoke.ps1 -VerifyPersistence
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-doctor.ps1 -Runtime
```

浏览器访问 `http://127.0.0.1:8180/operations`，确认 Backend/MySQL/Redis/Milvus 为 `UP`、AI 为 `READY`，且安全计数器均为零。

## 5. 生成发布包

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-release.ps1
```

输出位于 `releases/jobpilot-ai-0.12.0/` 和对应 ZIP。Manifest 保存文件 SHA-256、镜像不可变 ID、非 Root 用户、Flyway 版本和启动前备份引用；ZIP 旁生成 `.sha256`。发布目录只包含 `.env.example`，不会打包 `.env`。

发布前必须执行无副作用回滚预检：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-rollback-check.ps1
```

该命令只校验 Manifest、Artifact Hash、镜像 ID/用户、备份 Hash 和 Flyway 兼容性，不修改数据库或容器。

## 6. 停止、恢复与回滚边界

安全停止应用容器并保留数据服务和 Volume：

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase12-down.ps1
```

禁止使用带 `-v` 的 Compose Down、直接删除 Volume 或覆盖活动数据库。出现发布故障时：

1. 停止 Backend、Frontend、AI Service，保留数据服务。
2. 对目标发布包运行 `phase12-rollback-check.ps1 -ManifestPath <manifest>`。
3. 若 Flyway 版本不相容，停止自动回滚；先按 Phase 11 Runbook 在隔离环境验证备份恢复。
4. 只有 Manifest、备份和 Schema 全部兼容时，才人工切换到已验证镜像，再运行完整 Smoke。

真正的数据恢复不会通过一键 Web 操作或自动回滚触发。Phase 11 恢复脚本仅在随机隔离数据库、临时文件目录和临时 Milvus Collection 演练。

## 7. 故障处理

- `8180` 被占用：设置新的 `JOBPILOT_GATEWAY_PORT`，同步重启；不要停止不属于 JobPilot 的进程或容器。
- `8010`/`8088` 被旧本机进程占用：确认命令行和工作目录确属 `D:\JobPilot AI` 后再停止旧进程。
- 健康检查失败：执行 `docker compose ... ps` 与 `docker compose ... logs <service>`，日志不得复制到公开渠道前先脱敏。
- Secret 扫描失败：轮换泄漏凭据、删除 Artifact 并重新生成发布包；不得仅添加忽略规则绕过。
- 回滚预检失败：保持服务停止或当前稳定版本运行，不得强制覆盖数据库。

## 8. 运维安全要求

- 不把 Token、Cookie、密码、API Key、完整简历/JD 或 Prompt 写入日志。
- 应用容器保持非 Root、只读文件系统、`cap_drop: ALL` 和 `no-new-privileges`。
- OTLP 默认关闭；只有显式配置可信本地 Collector 才启用。
- LLM 未配置是受支持状态，系统继续使用规则与本地 Embedding，不伪造 AI 结果。
- 任何公网、局域网、多用户或真实平台自动化需求都必须作为新的安全评审范围处理。
