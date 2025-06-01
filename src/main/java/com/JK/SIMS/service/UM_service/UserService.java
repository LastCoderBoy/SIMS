package com.JK.SIMS.service.UM_service;

import com.JK.SIMS.models.UM_models.*;
import com.JK.SIMS.repository.UM_repo.BlackListTokenRepository;
import com.JK.SIMS.repository.UM_repo.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.Optional;

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

            if (authentication.isAuthenticated()) {
                UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();
                String role = userPrincipal.getAuthorities().stream()
                        .findFirst()
                        .map(GrantedAuthority::getAuthority) // Extract the role name
                        .orElse(Roles.ROLE_STAFF.name()); // The default role is STAFF if no role is found in the database

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
            // Check if the token is expired before blacklisting
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

    public boolean updateUser(Users user, String jwtToken) throws BadRequestException {
        if (jwtToken == null || jwtToken.isEmpty()) {
            throw new BadRequestException("Invalid Token is provided");
        }
        try {
            String username = jwtService.extractUserName(jwtToken);
            if (username != null ) {
                Optional<Users> userToBeUpdated = userRepository.findByUsername(username);
                if(userToBeUpdated.isPresent()){
                    Users currentUser = userToBeUpdated.get();
                    if(user.getPassword() != null){
                        if(isValidPassword(user.getPassword())){
                            String encodedPassword = passwordsEncoder.encode(user.getPassword());
                            currentUser.setPassword(encodedPassword);
                        }else {
                            throw new IllegalArgumentException("Password does not meet the required criteria. Password must contain at least 8 characters, including 1 uppercase, 1 lowercase, 1 number and 1 special character (@#$%^&*()-_+).");
                        }
                    }
                    if(user.getFirstName() != null){
                        currentUser.setFirstName(user.getFirstName());
                    }
                    if(user.getLastName() != null){
                        currentUser.setLastName(user.getLastName());
                    }
                    userRepository.save(currentUser);
                    logger.info("User '{}' updated successfully.", username);
                    return true;
                }
            }
            return false;
        }catch (ExpiredJwtException e){
            logger.warn("Token is expired: {}", e.getMessage());
            throw new BadRequestException("Token is expired");
        }catch (JwtException e) {
            logger.error("Invalid token while updating the User: {}", e.getMessage());
            throw new BadRequestException("Invalid token format");
        }
    }

    private boolean isValidPassword(String password) {
        String passwordRegex = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[@#$%^&+=_])(?=\\S+$).{8,}$";
        return password.matches(passwordRegex);
    }
}
