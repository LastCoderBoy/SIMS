package com.JK.SIMS.config.security.utils;

import com.JK.SIMS.config.security.JWTService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.apache.coyote.BadRequestException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SecurityUtils {

    private final JWTService jwtService;

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

    public String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isEmpty()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

}
