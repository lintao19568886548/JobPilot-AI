package com.jobpilot.common.exception;

import org.springframework.http.HttpStatus;

public class UnauthorizedException extends BusinessException {

    public UnauthorizedException(String message) {
        super(4010001, message, HttpStatus.UNAUTHORIZED);
    }
}

