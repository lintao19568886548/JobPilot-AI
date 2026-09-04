# Phase 13 Test Report

Date: 2026-09-03  
Scope: Guided Onboarding & Data Quality  
Result: **PASS**

## Automated regression

| Area | Command / evidence | Result |
|---|---|---:|
| Backend clean test | `backend\\mvnw.cmd clean test` | PASS — 424 tests, 0 failure/error/skipped |
| Backend package | `backend\\mvnw.cmd package` | PASS — executable JAR, 424 tests rerun |
| AI Service | pytest + Ruff | PASS — 52 tests, Ruff clean |
| Frontend | `npm run build` | PASS — 1,782 modules |
| Extension | npm test + build | PASS — 13 tests |
| Automation Worker | pytest | PASS — 6 tests |

Total automated tests: **495**.

The AI regression retained one existing Starlette `TestClient` deprecation warning. Maven retained existing compiler deprecation and Mockito dynamic-agent warnings. None produced a failed or skipped test.

## Onboarding test coverage

Seven new Service/Controller tests cover:

- empty candidate data and blockers;
- exact partial-data scoring;
- complete data returning `READY` with no quality issues;
- skill and primary-skill thresholds;
- independent Master, Default and current Version checks;
- deterministic severity ordering and stable issue codes;
- authenticated current-user ownership, `ApiResponse` and controller contract.

The nine weights total 100. `READY` requires score >= 80 and zero blockers.

## Frontend build budget

Production build completed without a large-chunk warning. Largest JavaScript outputs:

| Chunk | Size |
|---|---:|
| ECharts | 333.81 KiB |
| Element Plus | 325.49 KiB |
| ZRender | 175.24 KiB |
| Vue stack | 110.62 KiB |

All are below the Phase 13 limit of 500 KiB. ECharts uses Bar/Pie/Grid/Tooltip/Canvas registration only; Element Plus registers the components used by the repository.

## Docker and persistence

Built and ran:

- `jobpilot/backend:0.13.0` as `10001:10001`;
- `jobpilot/ai-service:0.13.0` as `10002:10002`;
- `jobpilot/frontend:0.13.0` as `101:101`.

All three containers became healthy with read-only root filesystems and `cap_drop: ALL`. MySQL remained at Flyway 11 / 82 tables and Redis returned `PONG`.

The real `phase10_smoke` local account produced:

- Readiness: 34/100;
- status: `NEEDS_WORK`;
- completed steps: 3/9;
- quality issues: 8 (1 blocker, 4 warnings, 3 info).

These values are not seeded by Phase 13. They are derived from the account's existing Candidate and Resume rows. The application containers were restarted, then the score, status, completed-step count and ordered issue-code fingerprint were compared and remained identical.

## HTTP validation

`scripts/phase13-smoke.ps1` performs six requests per run:

1. `/setup` SPA fallback;
2. authenticated login;
3. `/api/v1/onboarding/overview`;
4. `/api/v1/onboarding/data-quality`;
5. `/api/analytics/dashboard`;
6. `/api/v1/operations/overview`.

Three Phase 13 runs passed: initial full run, post-restart persistence run, and final post-frontend-rebuild persistence run — **18 HTTP checks**. A separate Phase 12 release/security regression passed **10 HTTP checks**. Total HTTP validation: **28**.

The smoke test verified response envelopes, nine unique step keys, score range, weight sum, severity ordering, unique issue codes, approved action paths, Dashboard availability, exact Trace ID propagation and all five forbidden external-action counters at zero. It never prints the password, JWT or refresh token.

## Browser validation

The real release gateway at `http://127.0.0.1:8180` was validated in the in-app browser:

- Setup Center desktop 1440 × 1000: correct heading, 34 score, 9 steps, 8 issues, no horizontal overflow;
- Setup Center 390 × 844 override: same facts, responsive one-column layout, no horizontal overflow;
- Dashboard 390 × 844 override: real Setup Readiness strip visible and no horizontal overflow after the responsive-grid fix;
- browser console: 0 errors, 0 warnings.

## Safety result

External messages sent: 0  
External submissions: 0  
External mutations: 0  
Automatic Offer decisions: 0  
Destructive duplicate deletes: 0

No real recruitment platform, CAPTCHA, external message, automated submission or Offer decision was exercised.
