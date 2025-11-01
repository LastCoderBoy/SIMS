package com.JK.SIMS.service.userAuthenticationService.impl;

import com.JK.SIMS.config.security.JWTService;
import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.exception.*;
import com.JK.SIMS.models.UM_models.*;
import com.JK.SIMS.models.UM_models.dtos.LoginRequest;
import com.JK.SIMS.models.UM_models.dtos.TokenResponse;
import com.JK.SIMS.repository.UserManagement_repo.BlackListTokenRepository;
import com.JK.SIMS.repository.UserManagement_repo.UserRepository;
import com.JK.SIMS.service.userAuthenticationService.RefreshTokenService;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;

@Service
@RequiredArgsConstructor @Slf4j
public class UserService {

    @Value("${jwt.refresh.cookie.name}")
    private String refreshTokenCookieName;

    @Value("${jwt.refresh.cookie.max-age}") // 7 days in seconds
    private int refreshTokenCookieMaxAge;


    private final AuthenticationManager authManager;
    private final JWTService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final BCryptPasswordEncoder passwordsEncoder;
    private final SecurityUtils securityUtils;

    private final BlackListTokenRepository blackListTokenRepository;
    private final UserRepository userRepository;

    public TokenResponse verify(LoginRequest loginRequest, HttpServletResponse response, HttpServletRequest request) {
        try {
            Authentication authentication = authManager
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            loginRequest.getLogin(),
                            loginRequest.getPassword()
                    ));
            if(authentication.isAuthenticated()){
                // Generate Access Token
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                String username = userPrincipal.getUsername();
                String role = userPrincipal.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority)
                        .orElse(Roles.ROLE_STAFF.name());
                String accessToken = jwtService.generateAccessToken(username, role);

                // Generate Refresh Token
                String userAgent = request.getHeader("User-Agent");
                String ipAddress = securityUtils.extractClientIp(request);
                RefreshToken refreshToken = refreshTokenService.createRefreshToken(
                        username,
                        ipAddress,
                        userAgent
                );

                // Set the refresh token in HttpOnly cookie (SECURE)
                setRefreshTokenCookie(response, refreshToken.getToken());

                return new TokenResponse(accessToken, "Bearer ", 3600L, username, role);
            }
            throw new BadCredentialsException("Invalid credentials");
        }
        catch (BadCredentialsException e) {
            log.warn("UM (verify): Invalid credentials for user: {}", loginRequest.getLogin());
            throw new AuthenticationFailedException("Invalid credentials ", e);
        } catch (Exception e) {
            log.error("Unexpected authentication error for user: {}. Reason: {}", loginRequest.getLogin(), e.getMessage(), e);
            throw new AuthenticationFailedException("UM (verify): Unexpected authentication error");
        }
    }

    public TokenResponse refreshToken(HttpServletResponse response, HttpServletRequest request) {
        try {
            // Extract refresh token from cookie
            String requestRefreshToken = extractRefreshTokenFromCookie(request);

            if (requestRefreshToken == null) {
                throw new TokenRefreshException("UM (refreshToken): Request does not contain a refresh token");
            }
            RefreshToken refreshToken = refreshTokenService.verifyExpiration(requestRefreshToken);

            // Verify IP hasn't changed significantly (detect token theft)
            String currentIp = securityUtils.extractClientIp(request);
            String storedIp = refreshToken.getIpAddress();
            if (!currentIp.equals(storedIp)) {
                log.warn("Refresh token used from different IP. Original: {}, Current: {}",
                        storedIp, currentIp);
                throw new TokenRefreshException("IP mismatch detected");
            }

            // Generate a new access token
            String username = refreshToken.getUser().getUsername();
            String role = refreshToken.getUser().getRole().name();
            String accessToken = jwtService.generateAccessToken(username, role);

            // Rotate refresh token for better security
            String userAgent = request.getHeader("User-Agent");
            RefreshToken newRefreshToken = refreshTokenService.rotateRefreshToken(
                    refreshToken,
                    currentIp,
                    userAgent
            );
            // Update refresh token cookie
            setRefreshTokenCookie(response, newRefreshToken.getToken());

            return new TokenResponse(accessToken, "Bearer ", 3600L, username, role);
        } catch (TokenRefreshException e) {
            clearRefreshTokenCookie(response); // Clear invalid cookie
            log.warn("UM (refreshToken): Token refresh failed - {}", e.getMessage());
            throw e;
        } catch (ExpiredJwtException e) {
            log.warn("UM (refreshToken): Refresh token has expired");
            throw new TokenRefreshException("UM (refreshToken): Refresh token has expired", e);
        } catch (JwtException e) {
            log.error("UM (refreshToken): Invalid refresh token format - {}", e.getMessage());
            throw new TokenRefreshException("UM (refreshToken): Invalid refresh token format", e);
        } catch (Exception e) {
            log.error("UM (refreshToken): Unexpected error during token refresh - {}", e.getMessage(), e);
            throw new TokenRefreshException("UM (refreshToken): Failed to refresh token", e);
        }
    }

    @Transactional
    public void logout(String jwtToken, HttpServletResponse response, HttpServletRequest request) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new InvalidTokenException("UM (logout): Token cannot be null or empty");
        }
        try {
            // Invalidate the access token
            String username = jwtService.extractUsername(jwtToken);
            if (jwtService.isTokenBlacklisted(jwtToken)) {
                log.warn("UM (logout): Token has already been blacklisted");
                return;
            }
            blackListTokenRepository.save(new BlacklistedToken(jwtToken, new Date()));

            // Revoke refresh token
            String refreshToken = extractRefreshTokenFromCookie(request);
            if (refreshToken != null) {
                refreshTokenService.revokeToken(refreshToken);
            }
            // Clear cookie
            clearRefreshTokenCookie(response);
            log.info("User '{}' logged out successfully.", username);
        } catch (JwtException e) {
            throw new InvalidTokenException("UM (logout): Invalid token format", e);
        } catch (JwtAuthenticationException e){
            throw new JwtAuthenticationException("UM (logout): Invalid token", e);
        }
    }

    public void logoutAllDevices(HttpServletResponse response, HttpServletRequest request) {
        try {
            // Extract refresh token from cookie
            String requestRefreshToken = extractRefreshTokenFromCookie(request);

            if (requestRefreshToken == null) {
                throw new TokenRefreshException("UM (refreshToken): Request does not contain a refresh token");
            }
            RefreshToken refreshToken = refreshTokenService.findByToken(requestRefreshToken);
            Users user = refreshToken.getUser();
            refreshTokenService.revokeAllUserTokens(user);
            clearRefreshTokenCookie(response);
        } catch (TokenRefreshException e) {
            clearRefreshTokenCookie(response);
            log.warn("UM (logoutAllDevices): Token refresh failed - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            clearRefreshTokenCookie(response);
            log.error("UM (logoutAllDevices): Unexpected error occurred - {}", e.getMessage(), e);
            throw new TokenRefreshException("Internal Service Error occurred", e);
        }
    }

    @Transactional
    public void updateUser(Users user, String currentAccessToken) {
        if (currentAccessToken == null || currentAccessToken.isEmpty()) {
            throw new InvalidTokenException("UM (updateUser): Invalid token provided");
        }

        try {
            String username = jwtService.extractUsername(currentAccessToken);
            if (username == null) {
                throw new InvalidTokenException("UM (updateUser): Could not extract username from token");
            }

            Users currentUser = userRepository.findByUsernameOrEmail(username)
                    .orElseThrow(() -> new ResourceNotFoundException("UM (updateUser): User not found"));

            updateUserFields(currentUser, user, currentAccessToken);
            userRepository.save(currentUser);
            log.info("User '{}' updated successfully.", username);

        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("UM (updateUser): Token is expired", e);
        } catch (JwtException e) {
            throw new InvalidTokenException("UM (updateUser): Invalid token format", e);
        }
    }

    private void updateUserFields(Users currentUser, Users newUserInfo, String currentAccessToken) throws JwtAuthenticationException {
        if (newUserInfo.getPassword() != null) {
            String newPassword = newUserInfo.getPassword();
            if (!isValidPassword(newPassword)) {
                log.warn("Update user failed: Invalid password format");
                throw new PasswordValidationException(
                        "Password must contain at least 8 characters, including 1 uppercase, " +
                                "1 lowercase, 1 number and 1 special character (@#$%^&*()-_+)."
                );
            }
            currentUser.setPassword(passwordsEncoder.encode(newPassword));
            blackListTokenRepository.save(new BlacklistedToken(currentAccessToken, new Date()));
            log.info("User '{}' password updated. Token invalidated - re-login required.", currentUser.getUsername());
        }

        if (newUserInfo.getFirstName() != null) {
            currentUser.setFirstName(newUserInfo.getFirstName());
        }

        if (newUserInfo.getLastName() != null) {
            currentUser.setLastName(newUserInfo.getLastName());
        }
    }

    private void setRefreshTokenCookie(HttpServletResponse response, String refreshToken) {
        Cookie cookie = new Cookie(refreshTokenCookieName, refreshToken);
        cookie.setHttpOnly(true);  // Prevents JavaScript access (XSS protection)
        cookie.setSecure(false);    // Set to false for local dev, true for production
        cookie.setPath("/api/v1/auth"); // Cookie only sent to auth endpoints
        cookie.setMaxAge(refreshTokenCookieMaxAge); // 7 days
        cookie.setAttribute("SameSite", "Strict"); // CSRF protection
        response.addCookie(cookie);
    }

    /**
     * Clears refresh token cookie
     */
    private void clearRefreshTokenCookie(HttpServletResponse response) {
        Cookie cookie = new Cookie(refreshTokenCookieName, "");
        cookie.setHttpOnly(true);
        cookie.setSecure(true);
        cookie.setPath("/api/v1/auth");
        cookie.setMaxAge(0); // Expire immediately
        cookie.setAttribute("SameSite", "Strict");
        response.addCookie(cookie);
    }

    /**
     * Extracts refresh token from the cookie
     */
    private String extractRefreshTokenFromCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies != null) {
            for (Cookie cookie : cookies) {
                if (refreshTokenCookieName.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        return null;
    }

    //At least one digit (0-9)
    //At least one lowercase letter
    //At least one uppercase letter
    //At least one special character from the set @#$%^&+=_
    //No whitespace characters allowed
    //Minimum 8 characters in length
    private boolean isValidPassword(String password) {
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=_])(?=\\S+$).{8,}$";
        return password.matches(passwordRegex);
    }
}
