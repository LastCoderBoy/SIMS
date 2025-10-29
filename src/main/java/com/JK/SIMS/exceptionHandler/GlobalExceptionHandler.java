package com.JK.SIMS.exceptionHandler;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.nio.file.AccessDeniedException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    private static final String LOG_PREFIX = "APP: ";


    private ResponseEntity<ErrorObject> handleException(Exception ex, HttpStatus status, String logMessage) {
        log.warn(logMessage, ex.getMessage());
        ErrorObject errorObject = new ErrorObject(
                status.value(),
                ex.getMessage(),
                new Date()
        );
        return new ResponseEntity<>(errorObject, status);
    }

    private ResponseEntity<ErrorObject> handleException(Exception ex, HttpStatus status, LogLevel logLevel, String logMessage) {
        if (logLevel == LogLevel.ERROR) {
            log.error(logMessage, ex.getMessage());
        } else {
            log.warn(logMessage, ex.getMessage());
        }
        ErrorObject errorObject = new ErrorObject(
                status.value(),
                ex.getMessage(),
                new Date()
        );
        return new ResponseEntity<>(errorObject, status);
    }

    // Client Errors (4xx)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorObject> handleHttpMessageNotReadableException(HttpMessageNotReadableException ex) {
        return handleException(ex, HttpStatus.BAD_REQUEST,
                "{}Invalid JSON input: {}");
    }

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ErrorObject> handleValidationException(ValidationException ex) {
        return handleException(ex, HttpStatus.BAD_REQUEST,
                "{}Validation error: {}");
    }

    @ExceptionHandler({BadRequestException.class, InvalidTokenException.class, PasswordValidationException.class, InsufficientStockException.class})
    public ResponseEntity<ErrorObject> handleBadRequestExceptions(Exception ex) {
        return handleException(ex, HttpStatus.BAD_REQUEST,
                "{}Bad request: {}");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        log.warn("{}Validation failed: {}", LOG_PREFIX, ex.getMessage());

        Map<String, Object> response = new HashMap<>();
        Map<String, String> errors = new HashMap<>();

        ex.getBindingResult().getFieldErrors().forEach(error ->
                errors.put(error.getField(), error.getDefaultMessage())
        );

        response.put("success", false);
        response.put("message", "Validation failed");
        response.put("errors", errors);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(response);
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ErrorObject> handleResourceNotFoundException(ResourceNotFoundException ex) {
        return handleException(ex, HttpStatus.NOT_FOUND,
                "{}Resource not found: {}");
    }


    // Authentication/Authorization Errors (401/403)
    @ExceptionHandler({AccessDeniedException.class, AuthorizationDeniedException.class})
    public ResponseEntity<ErrorObject> handleAccessDeniedException(AccessDeniedException ex) {
        return handleException(ex, HttpStatus.FORBIDDEN,
                "{}Access denied: {}");
    }

    @ExceptionHandler({JwtAuthenticationException.class, ExpiredJwtException.class, JwtException.class, AuthenticationFailedException.class})
    public ResponseEntity<ErrorObject> handleAuthenticationExceptions(Exception ex) {
        return handleException(ex, HttpStatus.UNAUTHORIZED,
                "{}Authentication error: {}");
    }

    @ExceptionHandler(NoSuchKeyException.class)
    public ResponseEntity<ErrorObject> handleNoSuchKeyException(NoSuchKeyException ex) {
        return handleException(ex, HttpStatus.NOT_FOUND,
                "{}S3 object not found: {}");
    }

    @ExceptionHandler(NoSuchBucketException.class)
    public ResponseEntity<ErrorObject> handleNoSuchBucketException(NoSuchBucketException ex) {
        return handleException(ex, HttpStatus.NOT_FOUND,
                "{}S3 bucket not found: {}");
    }


    // Server Errors (5xx)
    @ExceptionHandler({DatabaseException.class, InventoryException.class})
    public ResponseEntity<ErrorObject> handleServerExceptions(Exception ex) {
        return handleException(ex, HttpStatus.INTERNAL_SERVER_ERROR, LogLevel.ERROR,
                "{}Server error: {}");
    }

    @ExceptionHandler(ServiceException.class)
    public ResponseEntity<ErrorObject> handleServiceException(ServiceException ex) {
        return handleException(ex, HttpStatus.INTERNAL_SERVER_ERROR, LogLevel.ERROR,
                "{}Service error: {}");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorObject> handleGenericException(Exception ex) {
        log.error("{}Unexpected error occurred: {}", LOG_PREFIX, ex.getMessage(), ex);
        ErrorObject errorObject = new ErrorObject(
                HttpStatus.INTERNAL_SERVER_ERROR.value(),
                "An unexpected error occurred. Please contact support.",
                new Date()
        );
        return new ResponseEntity<>(errorObject, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    // Custom S3Exception (for your wrapper)
    @ExceptionHandler(S3Exception.class)
    public ResponseEntity<ErrorObject> handleCustomS3Exception(S3Exception ex) {
        return handleException(ex, HttpStatus.INTERNAL_SERVER_ERROR, LogLevel.ERROR,
                "{}Custom S3 error: {}");
    }
}