package com.JK.SIMS.controller.user_management;

import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.UM_models.LoginRequest;
import com.JK.SIMS.models.UM_models.LoginResponse;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.service.TokenUtils;
import com.JK.SIMS.service.userManagement_service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class UserController {

    private static final Logger logger = LoggerFactory.getLogger(UserController.class);
    private final UserService userService;
    @Autowired
    public UserController(UserService userService) {
        this.userService = userService;
    }

    // The Credentials for the new Employees will be provided by the "Admin".
    @PostMapping("/user/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        LoginResponse response = userService.verify(loginRequest);
        if (response.getToken() == null) {
            throw new InvalidTokenException("UM: Invalid credentials provided. Failed to generate token.");
        }
        logger.info("User '{}' logged in successfully.", loginRequest.getLogin());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/user/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String token){
        String jwtToken = TokenUtils.extractToken(token);
        userService.logout(jwtToken);
        logger.info("User logged out successfully");

        return ResponseEntity.ok()
                .header("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"")
                .body(new ApiResponse(true, "User logged out successfully."));
    }

    // The User is able to update only Name, Surname, and Password
    @PutMapping("/user/update")
    public ResponseEntity<?> updateUser(@RequestBody Users user, @RequestHeader("Authorization") String token){
        String jwtToken = TokenUtils.extractToken(token);
        userService.updateUser(user, jwtToken);
        logger.info("User updated successfully.");
        return ResponseEntity.ok(new ApiResponse(true, "User updated successfully."));
    }
}
