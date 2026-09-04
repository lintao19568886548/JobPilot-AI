# Phase 11 Security Triage

Date: 2026-09-03  
Final scan batch: `phase11-security-20260903T064024Z`  
Machine-readable summary: `reports/phase11/security-20260903T064024Z/summary.json`

## Decision

`CONTAINER_SCAN=PASS_WITH_FINDINGS`.

All configured scanners executed and generated machine-readable reports. Direct JobPilot application runtimes have no Critical/High dependency finding and Gitleaks found no committed Secret. Container scanners found vulnerable packages in upstream images, so this is explicitly not a clean image scan. The default local-only topology has no confirmed reachable Critical/High exploit path after configuration-specific review; changing the network exposure or enabling the affected optional authentication/features invalidates this decision and must block deployment until patched.

## Application dependency and license result

- Backend OWASP Dependency-Check: 95 raw CPE/advisory matches, 0 Critical, 0 High.
- Frontend, Extension and Automation Worker production npm audits: 0 vulnerabilities.
- AI runtime `pip-audit`: 0 vulnerabilities.
- Gitleaks v8.29.1: 0 findings; report is the empty JSON array.
- License inventory: 5 reports, 262 packages, 0 unknown third-party licenses and 0 prohibited licenses. Three `UNLICENSED` entries are JobPilot's own private npm workspace packages, not third-party dependencies.

## Container result

Trivy v0.72.0 scanned the fixed Compose image tags directly from their registries. Counts are SARIF result occurrences; one CVE may appear in more than one binary inside an image.

| Image | Critical | High | Critical + High | Exposure in default Compose |
|---|---:|---:|---:|---|
| `redis:7.4-alpine` | 0 | 0 | 0 | `127.0.0.1:6379` |
| `mysql:8.4.11` | 1 | 37 | 38 | `127.0.0.1:3306`, authenticated |
| `milvusdb/milvus:v2.6.22` | 2 | 78 | 80 | `127.0.0.1:19531` |
| `quay.io/coreos/etcd:v3.5.33` | 3 | 24 | 27 | internal Compose network only |
| `minio/minio:RELEASE.2025-09-07T16-13-09Z` | 8 | 95 | 103 | internal Compose network only |
| **Total** | **14** | **234** | **248** | — |

## Critical reachability review

1. `CVE-2026-56854` affects `golang.org/x/crypto/ssh` source-address authorization. The affected module is compiled into Milvus/etcd/MinIO binaries, but JobPilot does not enable or expose an SSH server or SSH authentication callback in these services. No reachable path is present in the deployed configuration.
2. `CVE-2025-68121` affects Go `crypto/tls` session resumption when a program mutates CA configuration between handshakes. JobPilot's internal MinIO/etcd/Milvus links are plain HTTP/gRPC on the private Compose network, and MySQL's flagged `gosu` entrypoint helper does not serve TLS. The required path is not used.
3. `CVE-2026-33186` affects gRPC-Go servers using path-based authorization interceptors with a specific deny/fallback-allow policy. JobPilot does not configure gRPC RBAC/path authorization on MinIO. The required policy path is absent.
4. `CVE-2026-33322` is MinIO OIDC JWT algorithm confusion. OIDC is not configured.
5. `CVE-2026-33419` is MinIO LDAP STS username enumeration/brute force. LDAP and STS are not configured.

## Important High residual risk

The old public MinIO Community image also contains MinIO-specific High findings, including service-account/STS escalation, S3 Select memory exhaustion and unsigned-trailer object writes. JobPilot does not create service/STS accounts, expose S3 Select, or publish MinIO ports. Only the Milvus container can reach MinIO on the private Compose network. Therefore these paths are not reachable by a browser/API caller in the current topology.

This is a compensating control, not a patch. If MinIO is ever published to the host/LAN, OIDC/LDAP/STS is enabled, another untrusted container joins the network, or arbitrary S3 calls are accepted, the current image becomes a deployment blocker.

## Version and remediation status

- MySQL was pinned to current 8.4.11, etcd to current compatible 3.5.33 and Milvus to current 2.6.22 during Phase 11.
- The last public `minio/minio` Community image predates later security fixes. Patched 2026 containers are published under MinIO AIStor and cannot be substituted silently because licensing/operational requirements differ.
- Track upstream Milvus/etcd rebuilds that include fixed Go modules and migrate MinIO only after selecting a supported, licensed storage path compatible with Milvus.
- Re-run `scripts/phase11-security-scan.ps1` after every image change and at least monthly while this machine remains in use.

## Guardrails

- Backend, Frontend, AI, MySQL, Redis and Milvus stay bound to loopback.
- MinIO and etcd have no published host port.
- `.env`, scan reports, backups, runtime logs, dependency caches and restore scratch directories remain Git ignored.
- External messages, external submissions, external mutations and automatic Offer decisions remain zero.

References:

- Go vulnerability database: <https://pkg.go.dev/vuln/GO-2026-6303>
- Go TLS advisory: <https://pkg.go.dev/vuln/GO-2026-4337>
- gRPC-Go advisory: <https://pkg.go.dev/vuln/GO-2026-4762>
- MinIO service-account advisory: <https://github.com/minio/minio/security/advisories/GHSA-jjjj-jwhf-8rgr>
- MinIO unsigned-trailer advisory: <https://github.com/minio/minio/security/advisories/GHSA-9c4q-hq6p-c237>
- MinIO 2026 security release notes: <https://dl.min.io/aistor/minio/release/notes/>

