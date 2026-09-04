# Phase 11 Operations Runbook

Date: 2026-09-03  
Scope: local-first JobPilot runtime, observability, AI budget, backup/restore, security scanning and read-only load baseline

## Runtime baseline

JobPilot is a local-only deployment by default. Backend, AI Service, MySQL, Redis and Milvus host ports bind to `127.0.0.1`; etcd and MinIO remain internal to the Compose network. Keep `OTEL_EXPORTER_OTLP_ENABLED=false` unless a trusted local collector is explicitly configured.

```powershell
Set-Location 'D:\JobPilot AI'
docker compose --profile ai up -d
powershell -ExecutionPolicy Bypass -File .\scripts\run-ai-service.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-backend.ps1
powershell -ExecutionPolicy Bypass -File .\scripts\run-frontend.ps1
```

Expected endpoints:

- Frontend: `http://127.0.0.1:5173`
- Backend health: `http://127.0.0.1:8088/actuator/health`
- Prometheus: `http://127.0.0.1:8088/actuator/prometheus`
- Swagger: `http://127.0.0.1:8088/swagger-ui.html`
- AI readiness: `http://127.0.0.1:8010/internal/v1/readiness`

## Observability

Normal development uses concise text logs. The `observability` Spring profile emits ECS-compatible JSON. Request correlation uses the caller's `X-Trace-Id` when present, otherwise a UUID; the same request trace is returned in the response header and API envelope. Internal OpenTelemetry trace/span identifiers stay separate from the public request trace.

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-observability-check.ps1
```

The script starts a temporary Backend on port `18092`, verifies parseable JSON and request-trace correlation, then terminates only that temporary process. It does not enable or contact an OTLP exporter.

## AI usage and budget

Open **Operations** after login. Usage is aggregated from persisted `ai_call_logs`; missing LLM credentials are reported as `NOT_CONFIGURED`, not as zero-cost LLM success. Budget limits are user-owned, validated, optimistic-locked and currency-specific. JobPilot never invents exchange rates.

Budget states:

- `DISABLED`: budget enforcement is intentionally off.
- `OK`: usage is below the warning threshold.
- `WARNING`: a configured warning threshold is reached.
- `EXCEEDED`: at least one configured limit is reached.

## Backup

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-backup.ps1
```

One batch contains:

- a transaction-consistent MySQL logical dump;
- a file-object ZIP (empty upload stores are represented without inventing user files);
- a Milvus collection/row export;
- `manifest.json` with the batch id, UTC time, Flyway version, counts, sizes and SHA-256 hashes.

Backups are written below the Git-ignored `backups/` directory. Copy a completed batch to protected storage using an operator-controlled process. Do not place credentials in the backup directory or commit a backup.

## Restore drill

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-restore-drill.ps1
# Or select a particular batch:
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-restore-drill.ps1 -ManifestPath 'D:\JobPilot AI\backups\<batch>\manifest.json'
```

The drill fails closed if an artifact is missing or its SHA-256 differs. It creates a random isolated MySQL database and random temporary Milvus collections, verifies Flyway/table/foreign-key and vector counts, records RPO/RTO, then removes only those exact temporary resources. The active database name is explicitly rejected as a restore target. There is no Web button that overwrites production data.

## Security scans

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-security-scan.ps1
```

The scan produces machine-readable reports below Git-ignored `reports/phase11/security-*`:

- OWASP Dependency-Check for the Backend;
- `npm audit --omit=dev` for Frontend, Extension and Automation Worker;
- `pip-audit` against AI runtime requirements;
- Gitleaks with redaction;
- Maven, npm and Python license inventories;
- pinned Trivy container-image scans for Critical/High findings.

A scanner execution failure is not treated as a clean report. Raw findings remain in the artifacts; exploitability decisions and compensating controls belong in the Phase 11 security triage, not in scanner suppression rules.

## Load baseline

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\phase11-load-test.ps1
```

The script authenticates an isolated smoke account without printing its password or token. It runs warm-up traffic and concurrent read-only requests against Job List, Recommendation List and Dashboard, records per-endpoint count/errors/P50/P95/max and fails if errors are non-zero or P95 exceeds the Phase 11 local baseline of 1000 ms.

## Incident checks

1. Confirm `docker compose --profile ai ps` reports healthy dependencies.
2. Check Backend and AI readiness before restarting anything.
3. Reuse the request `X-Trace-Id` to correlate the API envelope and structured log.
4. Check the latest Operational Run and its local manifest instead of trusting a UI badge alone.
5. Never paste `.env`, tokens, cookies, prompts containing candidate facts, or unredacted scanner logs into external systems.
6. Keep external messages, submissions, mutations and automatic Offer decisions at zero; Phase 11 adds no handler capable of those actions.

