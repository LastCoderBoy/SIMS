package com.JK.SIMS.controller.authenticationController;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.UM_models.dtos.LoginRequest;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.config.security.utils.TokenUtils;
import com.JK.SIMS.models.UM_models.dtos.TokenResponse;
import com.JK.SIMS.service.userAuthenticationService.impl.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthenticationController {
    private final UserService userService;

    // The Credentials for the new Employees will be provided by the "Admin".
    /**
     * Login endpoint - returns access token in response body, refresh token in HttpOnly cookie
     */
    @PostMapping("/login")
    public ResponseEntity<ApiResponse<TokenResponse>> login(@RequestBody LoginRequest loginRequest,
                                   HttpServletResponse response,
                                   HttpServletRequest request) {
        TokenResponse tokenResponse = userService.verify(loginRequest, response, request);
        log.info("User '{}' logged in successfully.", loginRequest.getLogin());
        return ResponseEntity.ok(new ApiResponse<>(true, "User logged in successfully.", tokenResponse));
    }

    /**
     * Refresh token endpoint - reads refresh token from cookie
     * The Client will call it when the Access Token request displays 401 Unauthorized
     */
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<TokenResponse>> refreshToken(HttpServletRequest request,
                                          HttpServletResponse response) {
        log.info("Refresh token requested.");
        TokenResponse tokenResponse = userService.refreshToken(response, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "Token refreshed successfully.", tokenResponse));
    }

    /**
     * Logout endpoint - revokes refresh token and clears cookie
     */
    @PostMapping("/logout")
    public ResponseEntity<?> logout(@RequestHeader("Authorization") String accessToken,
                                    HttpServletResponse response,
                                    HttpServletRequest request){
        String jwtToken = TokenUtils.extractToken(accessToken);
        userService.logout(jwtToken, response, request);
        log.info("User logged out successfully");

        return ResponseEntity.ok()
                .header("Clear-Site-Data", "\"cache\", \"cookies\", \"storage\"")
                .body(new ApiResponse<>(true, "User logged out successfully."));
    }

    /**
     * Logout from all devices - revokes all refresh tokens
     */
    @PostMapping("/logout-all")
    public ResponseEntity<ApiResponse<Void>> logoutAllDevices(HttpServletRequest request, HttpServletResponse response) {
        log.info("UM logoutAllDevices() is calling...");
        userService.logoutAllDevices(response, request);
        return ResponseEntity.ok(new ApiResponse<>(true, "User logged out successfully from all devices."));
    }

    // The User is able to update only Name, Surname, and Password
    @PutMapping("/update")
    public ResponseEntity<?> updateUser(@RequestBody Users user, @RequestHeader("Authorization") String token){
        String jwtToken = TokenUtils.extractToken(token);
        userService.updateUser(user, jwtToken);
        log.info("User updated successfully.");
        return ResponseEntity.ok(new ApiResponse<>(true, "User updated successfully. Please re-login to see the changes."));
    }
}
