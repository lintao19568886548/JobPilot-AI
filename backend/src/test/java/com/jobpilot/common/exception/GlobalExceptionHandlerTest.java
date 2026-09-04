package com.jobpilot.common.exception;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void mapsBusinessStatusAndCode() {
        var response = handler.handleBusiness(new ResourceNotFoundException("Resume"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().code()).isEqualTo(4040001);
        assertThat(response.getBody().message()).contains("Resume");
    }

    @Test
    void hidesUnexpectedExceptionDetails() {
        var response = handler.handleUnexpected(new IllegalStateException("secret database detail"));
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        assertThat(response.getBody().message()).isEqualTo("Internal server error");
    }
}
