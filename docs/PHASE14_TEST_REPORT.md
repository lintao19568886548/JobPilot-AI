# Phase 14 Test Report

Date: 2026-09-03  
Scope: Account Security & Workspace Settings  
Result: **PASS**

## Automated regression

| Area | Command / evidence | Result |
|---|---|---:|
| Backend clean test | `backend\\mvnw.cmd clean test` | PASS — 440 tests |
| Backend package | `backend\\mvnw.cmd package` | PASS — executable JAR |
| AI Service | pytest + Ruff | PASS — 52 tests, Ruff clean |
| Frontend | `npm run build` | PASS — largest JS chunk 333.81 KiB |
| Extension | npm test + build | PASS — 13 tests |
| Automation Worker | pytest | PASS — 6 tests |

Total automated tests: **511**.

Sixteen Phase 14 backend tests cover password policy/current-password verification, Auth Version and Session revocation, JWT claims, settings whitelist/type validation, ownership, response contracts and Flyway V12 constraints.

## Database and infrastructure

- Existing MySQL: V11 → V12 PASS.
- Clean-room MySQL: V1 → V12 PASS.
- Flyway version: 12; table count: 83.
- `system_settings`: 3 persisted whitelist rows.
- Session Metadata, `PASSWORD_CHANGE`, `SESSION_REVOKE`, `ACCOUNT_UPDATE` and `SETTING_UPDATE` audit rows were directly verified.
- Redis connectivity: `PONG`; Session Family denylist participated in real token invalidation.
- Pre-release consistency backup: PASS, containing the V11/82-table source and one Milvus collection with 28 rows.
- Existing MySQL, Redis and Milvus volumes were not deleted.

## Docker release

Built and ran `jobpilot/backend:0.14.0`, `jobpilot/ai-service:0.14.0` and `jobpilot/frontend:0.14.0`. All three were healthy, non-root, read-only and configured with `cap_drop: ALL`.

## HTTP and persistence validation

| Suite | Checks | Result |
|---|---:|---:|
| Phase 14 Full | 21 | PASS |
| Phase 14 post-restart persistence | 7 | PASS |
| Phase 12 release/security regression | 10 | PASS |
| Total | **38** | **PASS** |

The Full suite changed and restored the password, verified old-token rejection, Refresh rotation, Redis-backed Session revocation, settings validation and audit/trace behavior. The persistence suite first compared values after application restart, then restored the original settings. Password and settings restoration are enforced by the script cleanup path; no credential value is printed.

Acceptance found two real defects: MyBatis inferred a nonexistent `sensitive` column from the boolean accessor, and persistence mode restored settings before comparing them. Both were fixed, followed by targeted tests, package/image rebuild and complete Full/Persistence/release regression.

## Browser validation

Authenticated validation used the real release gateway at `http://127.0.0.1:8180`:

- Settings showed the real account, three preferences and active Web Sessions with current-session identification and masked metadata.
- Compact mode changed the real App Layout and was restored.
- Default landing changed to Application Center and was restored to Dashboard.
- Dashboard quality banner was hidden through Settings and then restored.
- 390 × 844 rendered as one column with no horizontal overflow; password controls remained reachable.
- Final browser console: 0 errors, 0 warnings.

## Safety result

External messages sent: 0  
External submissions: 0  
External mutations: 0  
Automatic Offer decisions: 0  
Destructive data-volume operations: 0

No real recruitment platform action, CAPTCHA handling or unapproved external submission was exercised.
