package com.jobpilot.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SwaggerUiVersionContractTest {

    @Test
    void configuredSwaggerVersionMatchesPackagedWebjar() throws Exception {
        String applicationYaml = Files.readString(Path.of("src/main/resources/application.yml"));

        assertThat(applicationYaml)
                .contains("version: ${SWAGGER_UI_VERSION:5.32.14}");
        assertThat(getClass().getClassLoader().getResource(
                "META-INF/resources/webjars/swagger-ui/5.32.14/index.html"))
                .as("the configured Swagger UI WebJar entry point")
                .isNotNull();
    }
}
