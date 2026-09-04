# Phase 11 Test Report

Date: 2026-09-03  
Scope: operational readiness, observability, AI budget, backup/restore, security scanning and read-only load baseline

## Automated regression

- Backend `mvnw clean test`: PASS — 54 source files compiled, 412 tests, 0 failures, 0 errors, 0 skipped.
- Backend `mvnw package`: PASS — tests executed again and executable JAR packaged.
- AI Service `pytest -q`: PASS — 52 tests; one Starlette deprecation warning, no failure.
- AI Service `ruff check .`: PASS.
- Frontend `npm run build`: PASS — 2309 modules transformed; chunk-size advisory remains non-blocking.
- Extension: PASS — 13 tests and production build.
- Automation Worker: PASS — 6 tests.
- Total automated tests: 483.

## Database, runtime and observability

- Existing MySQL V10→V11: PASS.
- Isolated MySQL V1→V11: PASS — 11 migrations, schema version 11, 82 tables.
- Backend restart: PASS — Spring Boot 4.1.1/Tomcat 11.0.25, Flyway validated all 11 migrations and reported the schema up to date.
- MySQL persistence: PASS — AI Budget and Operational Run identifiers remained after a second Backend restart.
- Redis: PASS — `PONG`.
- Backend/AI/Frontend: PASS — `UP` / `READY` / HTTP 200.
- Swagger UI and OpenAPI: PASS — `/swagger-ui.html`, UI assets and `/v3/api-docs` returned HTTP 200.
- Trace: PASS — incoming `X-Trace-Id` matched response header, API envelope and request log.
- Prometheus: PASS — HTTP, process, AI-call and Operational Run metrics present.
- Structured logging: PASS — 22/22 sampled observability-profile lines were valid JSON; request trace was present.
- OTLP: PASS — exporter remained disabled by default and no external collector was contacted.

## Backup and restore

- Backup `p11-backup-20260903T063910Z-94ff4d8b`: PASS — 82 tables, 1 Milvus collection/28 rows and 3 artifacts.
- Integrity: PASS — MySQL dump, empty file-store ZIP and Milvus export all matched manifest SHA-256 values.
- Restore drill `p11-restore-6006fe0f6eb7`: PASS — 82 tables, 187 foreign keys and 1 collection/28 rows restored into random temporary resources.
- RPO/RTO: 41s / 22s. Temporary resources were removed and the production database was not modified.

## Security and licenses

- All 17 configured scanner steps executed successfully; scanner failure count: 0.
- Backend runtime dependency scan: 0 Critical, 0 High. Three production npm audits: 0 vulnerabilities. AI runtime `pip-audit`: 0 vulnerabilities.
- Gitleaks: 0 findings. License inventory: 262 packages, 0 unknown third-party licenses, 0 prohibited licenses; 3 `UNLICENSED` entries are private JobPilot workspace packages.
- Trivy: 248 raw Critical/High occurrences across upstream images (14 Critical, 234 High), retained without suppression. The configuration-specific reachability review found no confirmed reachable path in the current loopback/private-network deployment. Result: `PASS_WITH_FINDINGS`; see `PHASE11_SECURITY_TRIAGE.md`.

## Load, HTTP and browser acceptance

- Phase 11 API smoke: PASS — 17 calls; trace, metrics, budget, optimistic lock, ownership, idempotency, path validation and zero external actions.
- Restart persistence smoke: PASS — 4 calls.
- Read-only load: PASS — 90/90 successes, zero errors: Job List P95 209.43ms, Recommendation P95 79.65ms, Dashboard P95 92.35ms.
- In-app browser: PASS — real Operations data, AI budget save, health/scan/backup/restore/load evidence, desktop and 390px responsive layout.
- Browser console: 0 errors and 0 warnings; Vite development connection/debug messages only.

## Safety counters

- External messages sent: 0.
- External submissions: 0.
- External mutations: 0.
- Automatic Offer decisions: 0.
- Destructive duplicate deletes: 0.
