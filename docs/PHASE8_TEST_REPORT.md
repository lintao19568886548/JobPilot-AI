# Phase 8 Verification Report

Date: 2026-09-02 (Asia/Shanghai)

## Automated gates

- Backend `mvnw.cmd clean test`: 376 tests, 0 failures, 0 errors, 0 skipped.
- Backend `mvnw.cmd package`: 376 tests passed again and executable Spring Boot JAR was created.
- Phase 8 Backend coverage: 26 tests for migration contract, time zone/DST/range validation, policy/state rules, reference ownership, Interview/Round/Question/Reminder services, idempotency and HTTP/AI contract.
- AI Service: 52 Pytest tests passed; Ruff returned `All checks passed`. Phase 8 tests cover strict schemas, Prompt versions, `PREDICTED` labelling, evidence references, missing evidence disclosure, rules-only mode, Review fact/inference separation, invalid answers and Provider timeout/retry failure.
- Frontend production build: Vite transformed 2,299 modules successfully. Business source remains JavaScript; no `.ts/.tsx` file exists under `frontend/src`.

## Runtime and data

- Backend `UP` on `127.0.0.1:8088`; Swagger/OpenAPI returned HTTP 200.
- AI Service `UP/READY` on `127.0.0.1:8010`; no LLM Secret is configured, so Interview Agent truthfully reports `RULES_ONLY`.
- Frontend returned HTTP 200 on `127.0.0.1:5173`.
- Docker Compose MySQL, Redis, Milvus, etcd and MinIO are running; health-enabled services are healthy. Redis returned `PONG`.
- Existing MySQL upgraded from V7 to V8. A temporary empty database migrated from V1 through V8, produced 63 tables, then was removed. The V8 schema contributes 9 tables.

## HTTP and persistence

The repeatable `scripts/phase8-smoke.ps1` full run passed 42 HTTP calls against the real Backend, AI Service, MySQL and Redis:

- Interview create/update/filter/logical-delete and optimistic-lock conflict.
- Three Round creates, one logical delete, two retained Round reads and status update.
- Six idempotent `PREDICTED` questions across all required categories.
- Two user-recorded `ACTUAL` questions and three append-only Answer Note versions.
- Two immutable Review versions; an idempotent replay returned the original version.
- Activation before Review confirmation returned 409. After confirmation, one Gap became `ACTIVE` and another `DISMISSED`; two suggestions from the newer draft remain `PROPOSED`.
- Duplicate Reminder returned 409; reschedule, `DONE`, `CANCELLED` and `PENDING` states persisted.
- Cross-user Interview and Round access returned 404.
- The dedicated Phase 8 user has 0 Applications, 0 Application Logs and 0 Communication Drafts.

After stopping and restarting Backend, the 7-call persistence run read back the Interview status, two Round records, six predicted/two actual questions, three Answer Notes, two Review versions, ACTIVE/DISMISSED Gap decisions, PENDING/DONE/CANCELLED Reminders and Dashboard facts from MySQL.

## Browser verification

`scripts/phase8-browser-smoke.mjs` launched installed Microsoft Edge headlessly and passed real login, Dashboard, Interview Detail, Review History, Knowledge Gap and Reminder views. It verified the visible Predicted/Actual labels, Answer Note v2, two Review versions and persisted lifecycle states. Browser console errors: 0; warnings: 0.

The first strict browser run exposed `/favicon.ico` returning 404. An explicit SVG favicon was added and the entire build/browser gate was rerun successfully.

## Safety result

- External messages sent: 0.
- External meeting invites: 0.
- Automatic Application updates: 0.
- No recording, microphone, meeting join, email/SMS/calendar, external-send or automatic Application mutation capability was added.
- AI Call logs store hashes and safe status metadata rather than full private Answer Note content.

## Non-blocking warnings

- Flyway reports MySQL 8.4 is newer than its tested-through MySQL 8.1 range; both real migration paths passed.
- Vite reports two large shared chunks; functional build and browser runtime passed.
- Pytest reports two dependency deprecation warnings; all tests passed.
