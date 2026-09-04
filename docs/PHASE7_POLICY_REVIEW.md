# Phase 7 Extension / Assist Policy Review

## Decision

Phase 7 may execute only the repository-owned `DEMO_FIXTURE` adapter and local test pages on `127.0.0.1` or `localhost`. No real recruitment platform has been approved for automated form interaction.

Unknown real pages use `GENERIC_VISIBLE_V1` or `MANUAL_SELECTION_V1`. They may capture user-visible text after an explicit click, but they must return `NEEDS_REVIEW` whenever required fields are uncertain. They cannot enter the Playwright Assist Worker allowlist.

## Enforced boundaries

- Manifest Host Permission is limited to `http://127.0.0.1:8088/*`; no recruitment domain and no `<all_urls>`.
- Content extraction ignores form controls, passwords, hidden nodes, `aria-hidden`, script, style and noscript.
- Extension credentials live only in `chrome.storage.session`; server-side refresh values are stored only as SHA-256 hashes.
- Assist requires an ACTIVE device, `ASSIST_ALLOWED` policy, APPROVED ASSIST queue item, local target URL, service authentication and an HMAC task token bound to task/queue/policy/expiry.
- Worker selectors are a fixed four-field allowlist. It never clicks a submit control, presses Enter, calls `form.submit`, sends a message, creates an Application, or claims external submission.
- CAPTCHA, password, 2FA and uncertain-page markers transition to `BLOCKED` before filling.

## Deferred reviews

Every real platform adapter requires a separate review of its current terms, robots/automation policy, authentication boundary, rate limits, permitted fields and final-confirmation behavior. Until such a review is recorded and versioned, the adapter remains unavailable and the platform is `MANUAL_ONLY` for automation.

This resolves the Phase 6 recommendation to build Extension capture before browser automation while retaining Phase 7 as one roadmap phase: Phase 7B proves only a local Fixture safety state machine, not production automation against a recruitment platform.
