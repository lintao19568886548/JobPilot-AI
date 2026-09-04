package com.jobpilot.common.exception;

import org.springframework.http.HttpStatus;

public class ForbiddenException extends BusinessException {

    public ForbiddenException(String message) {
        super(4030001, message, HttpStatus.FORBIDDEN);
    }
}

