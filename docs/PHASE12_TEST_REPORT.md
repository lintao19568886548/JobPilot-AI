# Phase 12 验收报告

验收日期：2026-09-03  
发布版本：`0.12.0`  
拓扑：Windows + Docker Desktop，本地 Loopback 发布

## 结果

| Gate | 结果 | 证据摘要 |
|---|---|---|
| Preflight / Compose | PASS | 必要配置存在，Compose 有效，76.5 GiB 可用，发布端口仅绑定 Loopback |
| Backend regression | PASS | 417 tests，0 failures/errors/skips；`package` 通过 |
| AI regression | PASS | 52 pytest；Ruff 通过 |
| Frontend build | PASS | Vite production build 通过 |
| Extension regression | PASS | 13 tests；build 通过 |
| Automation Worker regression | PASS | 6 tests；Fixture 只填充不提交，验证码阻断 |
| Test total | PASS | 488 automated tests |
| Release images | PASS | Backend、Frontend、AI Service 共 3 个镜像构建并可启动 |
| Container identity | PASS | UID/GID 分别为 `10001:10001`、`101:101`、`10002:10002`，均非 Root |
| Image secret check | PASS | 应用目录中没有 `.env`/`*.env`；发布 Artifact Secret Check 通过 |
| Health/readiness | PASS | Backend/MySQL/Redis/Milvus `UP`，AI `READY`，全部应用容器 healthy |
| Security headers | PASS | 5/5：nosniff、frame、referrer、permissions、CSP |
| Pre-release backup | PASS | Flyway 11，82 tables，3 hashed artifacts，1 Milvus collection/28 rows |
| Release manifest | PASS | 71 hashed artifacts，3 immutable image references |
| HTTP smoke | PASS | 首轮 10 calls；应用容器重启后持久化复验 10 calls，共 20 |
| Trace propagation | PASS | Gateway Header、API Envelope 与调用方 `X-Trace-Id` 一致 |
| Runtime hardening | PASS | 3/3 应用容器只读、非 Root、移除 Capability、no-new-privileges |
| Rollback dry run | PASS | Hash、镜像、备份、Flyway 兼容；数据库和容器均未改变 |
| Browser desktop | PASS | Operations 读取真实状态，无横向溢出 |
| Browser 390px | PASS | Operations 健康与安全信息可见，无横向溢出 |
| Browser console | PASS | error/warning 0 |
| External actions | PASS | message/submission/mutation/offer decision/destructive duplicate delete 均为 0 |

## 真实持久化验证

三个应用容器 `frontend/backend/ai-service` 被实际重启；MySQL、Redis 和 Milvus 未重建。重启后登录、当前用户、Operations、健康、Trace 与安全计数仍通过，证明数据来自持久化服务而非应用内存。

## 已知非阻断项

- Vite 报告单个 Chunk 大于 500 KiB，属于后续按路由拆包的性能技术债，不影响构建或当前本地运行。
- AI 测试有一条 Starlette `TestClient` 弃用提示，不影响测试结果，后续依赖升级时处理。
- AI 镜像使用 CPU-only PyTorch，避免引入 CUDA Runtime；首次构建仍需要较长依赖下载时间。
- 本阶段只验收 Loopback 单机发布，不代表已具备公网、局域网 TLS 或多用户 SaaS 发布条件。
- 实际镜像切换和生产数据恢复刻意不自动化；避免错误版本自动覆盖活动数据库。
