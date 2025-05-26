package com.JK.SIMS.controller.user_management;

import com.JK.SIMS.models.UM_models.LoginRequest;
import com.JK.SIMS.models.UM_models.LoginResponse;
import com.JK.SIMS.exceptionHandler.ErrorResponse;
import com.JK.SIMS.service.UM_service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.naming.AuthenticationException;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/user/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        try {
            LoginResponse response = userService.verify(loginRequest);
            if (response.getToken() != null) {
                logger.info("User '{}' logged in successfully.", loginRequest.getUsername());
                return ResponseEntity.ok(response);
            }
            throw new AuthenticationException("Invalid credentials");
        } catch (AuthenticationException e) {
            logger.warn("User '{}' failed to login: {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new ErrorResponse(false, e.getMessage() != null ? e.getMessage() : "Invalid credentials"));
        } catch (Exception e) {
            logger.error("Unexpected error occurred while logging in user '{}': {}", loginRequest.getUsername(), e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, e.getMessage() != null ? e.getMessage() : "Unexpected error occurred"));
        }
    }

    //TODO: Add logout endpoint
    //TODO: Add update user endpoint
}
