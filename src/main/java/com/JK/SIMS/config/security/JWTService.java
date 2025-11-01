package com.JK.SIMS.config.security;


import com.JK.SIMS.repository.UserManagement_repo.BlackListTokenRepository;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JWTService {
    @Value("${jwt.secret}")
    private String secretKey;

    @Value("${jwt.access.expiration}") // 1 hour in milliseconds
    private Long accessTokenDurationMs;

    private static final Logger logger = LoggerFactory.getLogger(JWTService.class);
    private final BlackListTokenRepository blackListTokenRepository;
    @Autowired
    public JWTService(BlackListTokenRepository blackListTokenRepository) {
        this.blackListTokenRepository = blackListTokenRepository;
    }

    public String generateAccessToken(String username, String role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role);
        claims.put("type", "ACCESS");

        return Jwts.builder()
                .claims()
                .add(claims)
                .subject(username)
                .issuedAt(new Date(System.currentTimeMillis()))
                .expiration(new Date(System.currentTimeMillis() + accessTokenDurationMs))
                .and()
                .signWith(getKey())
                .compact();
    }


    public boolean isTokenBlacklisted(String token) {
        if (token != null && !token.isEmpty()) {
            return blackListTokenRepository.existsByToken(token);
        }
        return false;
    }

    //Cleanup strategy
    @Scheduled(fixedRate = 7200000) // Run every 2 hours and remove the expired tokens
    @Transactional
    public void cleanBlacklistedTokens() {
        try {
            Date currentTime = new Date();
            Date expirationThreshold = new Date(currentTime.getTime() - (1000 * 60 * 60 * 8));

            blackListTokenRepository.findAll().stream()
                    .filter(token -> token.getBlacklistedAt().before(expirationThreshold))
                    .forEach(blackListTokenRepository::delete);

            logger.info("Cleaned up expired blacklisted tokens");
        } catch (Exception e) {
            logger.error("Error during blacklisted tokens cleanup: {}", e.getMessage());
        }
    }

    private SecretKey getKey() {
        byte[] keys = Decoders.BASE64.decode(secretKey);
        return Keys.hmacShaKeyFor(keys);
    }


    public String extractUsername(String token) {
        // extract the username from jwt token
        return extractClaim(token, Claims::getSubject);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parser()
                .verifyWith(getKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }


    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private <T> T extractClaim(String token, Function<Claims, T> claimResolver) {
        final Claims claims = extractAllClaims(token);
        return claimResolver.apply(claims);
    }

    public boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    public boolean validateToken(String token, UserDetails userDetails) {
        final String userName = extractUsername(token);
        return (userName.equals(userDetails.getUsername()) && !isTokenExpired(token));
    }
}
