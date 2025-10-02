package com.JK.SIMS.config.security;

import com.JK.SIMS.config.security.secFilter.JWTFilter;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.service.userManagement_service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.logging.Logger;


@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private static final Logger logger = Logger.getLogger(SecurityConfig.class.getName());

    @Autowired
    private UserDetailsServiceImpl userDetailsServiceImpl;

    @Autowired
    private BCryptPasswordEncoder passwordEncoder;

    @Autowired
    private JWTFilter jwtFilter;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .csrf(customizer -> customizer.disable())
                .authorizeHttpRequests(request -> request
                        .requestMatchers("/CSS/**", "/JS/**", "/HTML/**").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasAuthority("ROLE_ADMIN")  // Only admins
                        .requestMatchers("/api/v1/priority/**").hasAnyAuthority("ROLE_MANAGER", "ROLE_ADMIN") // Only Managers and Admins
                        .requestMatchers("/api/v1/user/login").permitAll() // Everyone can log in
                        .requestMatchers("/api/v1/SIMS/**").permitAll() // Used for Confirmation in Email
                        .anyRequest().authenticated())
                .exceptionHandling(exception -> exception
                        // Handle access denied (403) - user is authenticated but lacks permission
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            logger.info("Access Denied: " + accessDeniedException.getMessage());
                            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
                            response.setContentType("application/json");
                            ApiResponse apiResponse = new ApiResponse(false, "You do not have access to this resource");
                            ObjectMapper mapper = new ObjectMapper();
                            response.getWriter().write(mapper.writeValueAsString(apiResponse));
                        })
                        // Handle authentication failures (401) - invalid/missing/expired tokens
                        .authenticationEntryPoint((request, response, authException) -> {
                            logger.info("Authentication Failed: " + authException.getMessage());
                            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                            response.setContentType("application/json");
                            response.setCharacterEncoding("UTF-8");

                            // If JWT filter has a specific error message
                            String errorMessage = (String) request.getAttribute("jwt_error_message");
                            if (errorMessage == null) {
                                errorMessage = "Authentication failed: " + authException.getMessage();
                            }

                            ApiResponse apiResponse = new ApiResponse(false, errorMessage);
                            ObjectMapper mapper = new ObjectMapper();
                            response.getWriter().write(mapper.writeValueAsString(apiResponse));
                        }))
                .httpBasic(Customizer.withDefaults())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(){
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setPasswordEncoder(passwordEncoder);
        provider.setUserDetailsService(userDetailsServiceImpl);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration config) throws Exception {
        return config.getAuthenticationManager();
    }
}
