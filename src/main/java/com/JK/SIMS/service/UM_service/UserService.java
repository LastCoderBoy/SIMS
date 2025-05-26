package com.JK.SIMS.service.UM_service;

import com.JK.SIMS.models.UM_models.*;
import com.JK.SIMS.repository.UM_repo.BlackListTokenRepository;
import io.jsonwebtoken.JwtException;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserService {

    private final AuthenticationManager authManager;
    private final JWTService jwtService;
    private static Logger logger = LoggerFactory.getLogger(UserService.class);

    private final BlackListTokenRepository blackListTokenRepository;

    @Autowired
    public UserService(AuthenticationManager authManager, JWTService jwtService, BlackListTokenRepository blackListTokenRepository) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.blackListTokenRepository = blackListTokenRepository;
    }

    public LoginResponse verify(LoginRequest loginRequest) {
        try {
            Authentication authentication = authManager
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    ));

            if (authentication.isAuthenticated()) {
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                String role = userPrincipal.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority) // Extract the role name
                        .orElse(Roles.ROLE_STAFF.name()); // Default role is STAFF if no role is found in the database

                String token = jwtService.generateToken(userPrincipal.getUsername(), role);
                logger.info("User '{}' authenticated successfully. Role: {}", userPrincipal.getUsername(), role);

                return new LoginResponse(token, role);
            }
        } catch (BadCredentialsException e) {
            logger.warn("Authentication failed: Bad credentials.");
        } catch (Exception e) {
            logger.error("Unexpected authentication error", e);
        }

        return new LoginResponse(null, "AUTH_FAILED");
    }

    public void logout(String jwtToken) throws BadRequestException {
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new BadRequestException("Token cannot be null or empty");
        }

        try {
            // Check if token is expired before blacklisting
            if (jwtService.isTokenExpired(jwtToken)) {
                throw new BadRequestException("Token is already expired");
            }

            blackListTokenRepository.save(new BlacklistedToken(jwtToken, new Date()));
            logger.info("Token has been blacklisted");
        } catch (JwtException e) {
            logger.error("Invalid token during logout: {}", e.getMessage());
            throw new BadRequestException("Invalid token format");
        }

    }
}
