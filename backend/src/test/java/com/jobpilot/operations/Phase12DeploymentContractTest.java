package com.jobpilot.operations;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class Phase12DeploymentContractTest {
    private static final Path ROOT = Path.of("..");

    @Test
    void productionProfileFailsClosedAndKeepsOtlpOff() throws Exception {
        String yaml = read("backend/src/main/resources/application-production.yml");
        assertThat(yaml)
                .contains("address: 0.0.0.0")
                .contains("include-stacktrace: never")
                .contains("show-details: never")
                .contains("enabled: ${OTEL_EXPORTER_OTLP_ENABLED:false}");
    }

    @Test
    void dockerContextExcludesSecretsAndRuntimeData() throws Exception {
        String ignore = read(".dockerignore");
        assertThat(ignore)
                .contains(".env\n")
                .contains("backups\n")
                .contains("reports\n")
                .contains("runtime\n")
                .contains("**/node_modules\n")
                .contains("**/.venv\n");
    }

    @Test
    void releaseComposeUsesLoopbackAndHardenedApplicationContainers() throws Exception {
        String compose = read("deploy/docker-compose.release.yml");
        assertThat(compose).contains("127.0.0.1:${BACKEND_PORT:-8088}:8088")
                .contains("127.0.0.1:${AI_SERVICE_PORT:-8010}:8010")
                .contains("127.0.0.1:${JOBPILOT_GATEWAY_PORT:-8180}:8080")
                .contains("read_only: true")
                .contains("cap_drop: [ALL]")
                .contains("security_opt: [no-new-privileges:true]")
                .contains("condition: service_healthy");
        assertThat(compose.split("read_only: true", -1)).hasSize(4);
    }

    @Test
    void nginxProvidesSpaProxyAndSecurityHeaders() throws Exception {
        String nginx = read("deploy/frontend/nginx.conf");
        assertThat(nginx)
                .contains("try_files $uri $uri/ /index.html")
                .contains("proxy_pass http://backend:8088")
                .contains("X-Content-Type-Options")
                .contains("Referrer-Policy")
                .contains("Permissions-Policy")
                .contains("Content-Security-Policy")
                .contains("server_tokens off");
    }

    @Test
    void rollbackIsDryRunAndRequiresExactHashesAndFlywayVersion() throws Exception {
        String rollback = read("scripts/phase12-rollback-check.ps1");
        assertThat(rollback)
                .contains("mode='DRY_RUN'")
                .contains("Get-Phase12Sha256")
                .contains("imageId")
                .contains("currentFlyway -eq")
                .contains("databaseChanged=$false")
                .doesNotContain("docker compose down -v", "flyway clean", "DROP DATABASE");
    }

    private static String read(String path) throws Exception {
        return Files.readString(ROOT.resolve(path)).replace("\r\n", "\n");
    }
}
