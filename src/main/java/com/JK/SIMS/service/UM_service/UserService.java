package com.JK.SIMS.service.UM_service;

import com.JK.SIMS.exceptionHandler.AuthenticationFailedException;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.exceptionHandler.PasswordValidationException;
import com.JK.SIMS.models.UM_models.*;
import com.JK.SIMS.repository.UM_repo.BlackListTokenRepository;
import com.JK.SIMS.repository.UM_repo.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class UserService {

    private final AuthenticationManager authManager;
    private final JWTService jwtService;
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);
    private final BCryptPasswordEncoder passwordsEncoder;

    private final BlackListTokenRepository blackListTokenRepository;
    private final UserRepository userRepository;

    @Autowired
    public UserService(AuthenticationManager authManager, JWTService jwtService, BCryptPasswordEncoder passwordsEncoder, BlackListTokenRepository blackListTokenRepository, UserRepository userRepository) {
        this.authManager = authManager;
        this.jwtService = jwtService;
        this.passwordsEncoder = passwordsEncoder;
        this.blackListTokenRepository = blackListTokenRepository;
        this.userRepository = userRepository;
    }

    public LoginResponse verify(LoginRequest loginRequest) {
        try {
            Authentication authentication = authManager
                    .authenticate(new UsernamePasswordAuthenticationToken(
                            loginRequest.getUsername(),
                            loginRequest.getPassword()
                    ));


            if (!authentication.isAuthenticated()) {
                logger.warn("Authentication failed for user: {}", loginRequest.getUsername());
                throw new AuthenticationFailedException("Invalid credentials");
            }

            UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
            String role = userPrincipal.getAuthorities().stream()
                    .findFirst()
                    .map(GrantedAuthority::getAuthority)
                    .orElse(Roles.ROLE_STAFF.name());

            String token = jwtService.generateToken(userPrincipal.getUsername(), role);
            logger.info("User '{}' authenticated successfully. Role: {}", userPrincipal.getUsername(), role);

            return new LoginResponse(token, role);
        } catch (BadCredentialsException e) {
            throw new AuthenticationFailedException("Invalid credentials");
        } catch (Exception e) {
            throw new AuthenticationFailedException("Unexpected authentication error");
        }
    }

    public void logout(String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new InvalidTokenException("Token cannot be null or empty");
        }

        try {
            // Check if the token is expired before blacklisting
            if (jwtService.isTokenExpired(jwtToken)) {
                throw new InvalidTokenException("Token is already expired");
            }

            blackListTokenRepository.save(new BlacklistedToken(jwtToken, new Date()));
            logger.info("Token has been blacklisted");
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token format");
        }
    }

    public void updateUser(Users user, String jwtToken){
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new InvalidTokenException("Invalid token provided");
        }

        try {
            String username = jwtService.extractUserName(jwtToken);
            if (username == null) {
                throw new InvalidTokenException("Could not extract username from token");
            }

            Users currentUser = userRepository.findByUsername(username)
                    .orElseThrow(() -> new EntityNotFoundException("User not found"));

            updateUserFields(currentUser, user);
            userRepository.save(currentUser);
            logger.info("User '{}' updated successfully.", username);

        } catch (ExpiredJwtException e) {
            throw new InvalidTokenException("Token is expired");
        } catch (JwtException e) {
            throw new InvalidTokenException("Invalid token format");
        }
    }

    private void updateUserFields(Users currentUser, Users newUser) {
        if (newUser.getPassword() != null) {
            if (!isValidPassword(newUser.getPassword())) {
                throw new PasswordValidationException(
                        "Password must contain at least 8 characters, including 1 uppercase, " +
                                "1 lowercase, 1 number and 1 special character (@#$%^&*()-_+)."
                );
            }
            currentUser.setPassword(passwordsEncoder.encode(newUser.getPassword()));
        }

        if (newUser.getFirstName() != null) {
            currentUser.setFirstName(newUser.getFirstName());
        }

        if (newUser.getLastName() != null) {
            currentUser.setLastName(newUser.getLastName());
        }
    }


    private boolean isValidPassword(String password) {
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=_])(?=\\S+$).{8,}$";
        return password.matches(passwordRegex);
    }
}
