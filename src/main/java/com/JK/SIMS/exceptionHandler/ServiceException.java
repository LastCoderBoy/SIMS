package com.JK.SIMS.exceptionHandler;

public class ServiceException extends RuntimeException {
    public ServiceException(String message, Throwable cause) {
        super(message, cause);
    }
    public ServiceException(String message){
        super(message);
    }
}

