# Phase 10 Test Report

Date: 2026-09-02  
Scope: Learning to Rank and local-only Safe Automation Center

## Automated regression

- Backend `mvnw clean package`: PASS — 49 suites, 405 tests, 0 failures, 0 errors, 0 skipped.
- AI Service `pytest -q`: PASS — 52 tests; two dependency deprecation warnings, no failure.
- AI Service `ruff check .`: PASS.
- Frontend `npm run build`: PASS — 2307 modules transformed; large-chunk advisory remains non-blocking.
- Docker Compose configuration: PASS.

## Database and runtime

- Existing MySQL V9→V10: PASS.
- Isolated MySQL V1→V10: PASS — 10 migrations, schema version 10, 80 tables.
- Backend restart: PASS — Flyway validated all 10 migrations and reported schema up to date.
- Persistence: PASS — 36 Feedback rows, 1 Model Version, 7 Rules and 8 Safe Tasks remained after restart.
- Redis: PASS — `PONG`.
- Backend/AI/Frontend: PASS — `UP` / `UP` / HTTP 200.

## HTTP and browser acceptance

- Full Phase 10 smoke: PASS — 48 HTTP calls.
- Restart persistence smoke: PASS — 4 HTTP calls.
- Feedback rebuild: 36 facts; repeated rebuild idempotent.
- Training split: 28 train / 8 validation, chronological, leakage safe.
- Shadow/activate/rollback: PASS; Shadow did not change online ranking.
- Automation: all seven fixed handlers, idempotent replay, attempt-2 retry, cancellation, Rule pause, Suggestion decision, Notification dedup, Authorization revoke and Policy downgrade passed.
- Microsoft Edge: Learning and Automation Center loaded real API data; console errors=0 and warnings=0.

## Safety counters

- External messages sent: 0.
- External submissions: 0.
- External mutations: 0.
- Automatic Offer decisions: 0.
- Destructive duplicate deletes: 0.
- Direct database attempt to set an external action flag to 1: rejected by Check Constraint.

## Advisories

- Flyway logs that MySQL 8.4 is newer than its latest tested MySQL 8.1 target; migrations and validation passed. Upgrade Flyway during dependency-maintenance work.
- Vite reports large output chunks; production build passes. Route/vendor chunk splitting remains performance debt.
- Python dependencies emit two deprecation warnings; tests pass. Address during dependency-maintenance work.
