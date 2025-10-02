package com.JK.SIMS.config.security;

import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class SecurityUtils {

    private final JWTService jwtService;
    @Autowired
    public SecurityUtils(JWTService jwtService) {
        this.jwtService = jwtService;
    }

    public boolean hasAccess(){
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(r ->
                        r.getAuthority().equals("ROLE_ADMIN") ||
                                r.getAuthority().equals("ROLE_MANAGER"));

    }

    public String validateAndExtractUsername(String jwtToken) throws BadRequestException {
        String username = jwtService.extractUsername(jwtToken);
        if (username == null || username.isEmpty()) {
            throw new BadRequestException("Invalid JWT token: Cannot determine user.");
        }
        return username;
    }

}
