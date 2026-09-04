# Phase 7 Verification Report

Date: 2026-09-02 (Asia/Shanghai)

## Passed

- Backend `clean test`: 350 tests, 0 failures.
- Backend package: executable Spring Boot JAR built successfully.
- Extension: TypeScript, ESLint, 13 Vitest tests, production build and npm high-severity audit passed.
- Automation Worker: TypeScript, ESLint, 6 Vitest tests, production build and npm high-severity audit passed.
- Worker Playwright Fixture: allowed fields were filled and handed off with `submitCount=0`; CAPTCHA fixture returned `BLOCKED` before fill.
- Flyway: existing database migrated from V6 to V7; schema has 54 tables, including all 5 Phase 7 tables.
- Flyway clean-room: a temporary empty MySQL database migrated from V1 through V7 and produced the same 54-table schema; the temporary database was removed after verification.
- HTTP: the repeatable `phase7-smoke.ps1` full flow passed 22 calls against real Backend, MySQL, Redis and Worker. It includes Web/Extension token isolation and rejection of a capture payload containing `cookie`.
- Persistence: after a second Backend restart, the newly created Job, Queue, Automation Task and 7 immutable steps were read back from MySQL in a 6-call persistence flow.
- Regression: Phase 1–3 persistence flows, the Phase 4 56-call full flow, and Phase 5–6 persistence flows passed. Redis returned `PONG`; Milvus-backed Phase 3 state remained readable.
- Safety result: `externallySubmitted=false`, `applicationCreated=false`, `finalConfirmationRequired=true`, 0 external messages and 0 external submissions.
- Post-test runtime: the explicitly enabled Worker was stopped and Backend was restarted with `AUTOMATION_WORKER_ENABLED=false`; Backend remains `UP` and the Worker is not reachable.
- Unpacked Extension E2E: Playwright was switched from the host's broken bundled Chromium to installed Microsoft Edge 152.0.4191.53. The isolated extension loaded, extracted `FIXTURE_V1`, excluded password/hidden session data, exposed no Submit control and produced zero page errors.
- Real Edge acceptance: production extension 0.1.1 paired successfully and captured `Senior Java Backend Engineer` from `http://127.0.0.1:8020/fixtures/job-page.html`. MySQL stored the `EXTENSION/DEMO_FIXTURE` source; forbidden hidden-value search returned 0.
- Real workflow: Match persisted as `SUCCEEDED / 77.59 / B`; communication content persisted as `DRAFT / BOSS`; the queue persisted as `ASSIST / READY` with no approval, no Automation Task and no Application.
- Device revocation: device `01M1GYYSD2NB3NMNNMKMRGVZZW` became `REVOKED`, token version advanced, and active Refresh Token count became 0. Extension 0.1.2 adds and tests automatic session clearing after a revoked-device 401.
- Browser quality: Fixture warning/error console count was 0. The Action popup had no horizontal overflow, no Submit/Send control and retained the explicit human-approval language.

## Browser compatibility decision

The initial `openPanelOnActionClick` and programmatic Side Panel variants were unreliable in the user's Edge build: one did not grant `activeTab`, while the other did not open consistently. The final manifest uses the native Action popup (`default_popup=sidepanel.html`). It keeps the same capture/review/workspace UI, and opening the popup is the explicit user gesture that grants `activeTab`. No `tabs`, `<all_urls>`, cookie, history, debugger or native-messaging permission was added.
