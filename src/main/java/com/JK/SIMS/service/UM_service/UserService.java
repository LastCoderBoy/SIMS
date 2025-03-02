package com.JK.SIMS.service.UM_service;

import com.JK.SIMS.config.UserPrincipal;
import com.JK.SIMS.models.UM_models.LoginResponse;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.service.JWTService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final AuthenticationManager authManager;
    private final JWTService jwtService;
    private static Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    public UserService(AuthenticationManager authManager, JWTService jwtService) {
        this.authManager = authManager;
        this.jwtService = jwtService;
    }

    public LoginResponse verify(Users user) {
        try {
            Authentication authentication = authManager
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            user.getUsername(),
                            user.getPassword()
                    ));

            if (authentication.isAuthenticated()) {
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                String role = userPrincipal.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority) // Extract the role name
                        .orElse("ROLE_STAFF");

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
}
