package com.jobpilot.common.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(String resourceName) {
        super(4040001, resourceName + " not found", HttpStatus.NOT_FOUND);
    }
}

