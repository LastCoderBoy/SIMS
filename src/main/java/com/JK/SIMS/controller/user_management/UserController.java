package com.JK.SIMS.controller.user_management;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.UM_models.LoginRequest;
import com.JK.SIMS.models.UM_models.LoginResponse;
import com.JK.SIMS.exceptionHandler.ErrorResponse;
import com.JK.SIMS.service.UM_service.UserService;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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

    // The Credentials will be provided by the Admin.
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
    @PostMapping("/user/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token){

        try {
            String jwtToken = token.startsWith("Bearer ") ? token.substring(7) : token;
            userService.logout(jwtToken);
            logger.info("User logged out successfully");

            return ResponseEntity.ok()
                    .header("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"")
                    .body(new ApiResponse(true, "User logged out successfully."));

        }catch (BadRequestException e){
            logger.warn("Invalid token provided: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(new ErrorResponse(false, e.getMessage() != null ? e.getMessage() : "Invalid token provided"));
        }catch (Exception e){
            logger.error("Unexpected error occurred while logging out user: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(new ErrorResponse(false, e.getMessage() != null ? e.getMessage() : "Unexpected error occurred"));
        }
    }

    //TODO: Add update user endpoint
}
