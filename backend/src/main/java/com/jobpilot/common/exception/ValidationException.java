package com.jobpilot.common.exception;

public class ValidationException extends BusinessException {

    public ValidationException(String message) {
        super(4001001, message);
    }
}

