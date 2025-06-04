package com.JK.SIMS.config.secFilter;

import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.exceptionHandler.JwtAuthenticationException;
import com.JK.SIMS.service.TokenUtils;
import com.JK.SIMS.service.UM_service.JWTService;
import com.JK.SIMS.service.UserDetailsServiceImpl;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JWTFilter extends OncePerRequestFilter {
    @Autowired
    private JWTService jwtService;

    @Autowired
    ApplicationContext context;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        try {
            String authHeader = request.getHeader("Authorization");
            String token = null;
            String username = null;

            if (authHeader != null && authHeader.startsWith("Bearer ")) {
                try {
                    token = TokenUtils.extractToken(authHeader);
                    if (jwtService.isTokenBlacklisted(token)) {
                        throw new JwtAuthenticationException("Token has been blacklisted");
                    }
                    username = jwtService.extractUserName(token);
                } catch (InvalidTokenException e) {
                    throw new JwtAuthenticationException("Invalid token format");
                }
            }

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = context.getBean(UserDetailsServiceImpl.class).loadUserByUsername(username);
                if (jwtService.validateToken(token, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails, null, userDetails.getAuthorities()
                    );
                    authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authToken);
                } else {
                    throw new JwtAuthenticationException("Invalid token");
                }
            }

            filterChain.doFilter(request, response);
        }
        catch (ExpiredJwtException e) {
            throw new JwtAuthenticationException("Token has expired");
        } catch (JwtException e) {
            throw new JwtAuthenticationException("Invalid token format");
        }
    }
}
