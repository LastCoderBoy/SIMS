package com.JK.SIMS.exception;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class ValidationException extends RuntimeException {
    private final List<String> errors;

    public ValidationException(String message) {
        super(message);
        this.errors = Collections.singletonList(message);
    }

    public ValidationException(List<String> errors) {
        super(String.join(", ", errors));
        this.errors = errors;
    }
}
