package com.JK.SIMS.exceptionHandler;

public class DatabaseException extends RuntimeException {
    public DatabaseException(String message, Throwable cause) {
        super(message, cause);
    }
    public DatabaseException(String message) {
        super(message);
    }
}

