# Deployment boundary

Phase 1 infrastructure is declared in the root `docker-compose.yml`.

- Default profile: MySQL and Redis.
- `ai` profile: etcd, MinIO, and Milvus for later phases only.
- Secrets are supplied from the local `.env`; only `.env.example` belongs in Git.

Production manifests are intentionally deferred until the application has a deployment target.
