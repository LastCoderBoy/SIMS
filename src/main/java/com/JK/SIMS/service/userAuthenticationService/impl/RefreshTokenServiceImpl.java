package com.JK.SIMS.service.userAuthenticationService.impl;

import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.TokenRefreshException;
import com.JK.SIMS.models.UM_models.RefreshToken;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.repository.UserManagement_repo.RefreshTokenRepository;
import com.JK.SIMS.repository.UserManagement_repo.UserRepository;
import com.JK.SIMS.service.userAuthenticationService.RefreshTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class RefreshTokenServiceImpl implements RefreshTokenService {

    private static final int MAX_ACTIVE_TOKENS_PER_USER = 5;
    @Value("${jwt.refresh.expiration}") // 7 days in milliseconds
    private Long refreshTokenDurationMs;

    private final RefreshTokenRepository refreshTokenRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public RefreshToken createRefreshToken(String username, String ipAddress, String userAgent) {
        Users user = findUserByUsername(username); // might throw ResourceNotFoundException
        // Limit active tokens per user (prevent token hoarding)
        limitActiveTokens(user);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setToken(generateSecureToken());
        refreshToken.setUser(user);
        refreshToken.setIpAddress(ipAddress);
        refreshToken.setUserAgent(userAgent);
        refreshToken.setExpiryDate(Instant.now().plusMillis(refreshTokenDurationMs));

        log.info("Created refresh token for user: {} from IP: {}", user.getUsername(), ipAddress);
        return refreshTokenRepository.save(refreshToken);
    }

    @Override
    @Transactional
    public RefreshToken verifyExpiration(String token) {
        RefreshToken refreshToken = findByToken(token);
        if (refreshToken.isRevoked()) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token has been revoked. Please login again.");
        }

        if (refreshToken.getExpiryDate().isBefore(Instant.now())) {
            refreshTokenRepository.delete(refreshToken);
            throw new TokenRefreshException("Refresh token has expired. Please login again.");
        }
        return refreshToken;
    }

    /**
     * Generates a cryptographically secure random token
     * More secure than UUID.randomUUID()
     */
    private String generateSecureToken() {
        SecureRandom secureRandom = new SecureRandom();
        byte[] tokenBytes = new byte[32]; // 256 bits
        secureRandom.nextBytes(tokenBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
    }

    private void limitActiveTokens(Users user) {
        List<RefreshToken> activeTokens = refreshTokenRepository
                .findActiveTokensByUser(user, Instant.now());

        if (activeTokens.size() >= MAX_ACTIVE_TOKENS_PER_USER) {
            RefreshToken oldest = activeTokens.getFirst();
            oldest.setRevoked(true);
            refreshTokenRepository.save(oldest);
            log.info("Revoked oldest token for user {} (limit reached)", user.getUsername());
        }
    }

    @Override
    @Transactional
    public void revokeToken(String token) {
        refreshTokenRepository.findByToken(token).ifPresent(rt -> {
            rt.setRevoked(true);
            refreshTokenRepository.save(rt);
            log.info("Revoked refresh token for user: {}", rt.getUser().getUsername());
        });
    }

    /**
     * Revokes all refresh tokens for a specific user (logout from all devices)
     */
    @Override
    @Transactional
    public void revokeAllUserTokens(Users user) {
        refreshTokenRepository.revokeAllUserTokens(user);
        log.info("Revoked all tokens for user: {}", user.getUsername());
    }

    /**
     * Rotates refresh token - deletes old one and creates new one
     * This is a security best practice (Refresh Token Rotation)
     */
    @Override
    @Transactional
    public RefreshToken rotateRefreshToken(RefreshToken oldToken, String ipAddress, String userAgent) {
        // Check if already revoked (prevent double-use)
        if (oldToken.isRevoked()) {
            log.warn("Attempted to rotate already revoked token - possible token theft!");
            throw new TokenRefreshException("Token has already been used");
        }

        // Mark as revoked first (prevents concurrent use)
        oldToken.setRevoked(true);
        refreshTokenRepository.save(oldToken);

        log.info("Rotated refresh token for user: {}", oldToken.getUser().getUsername());
        return createRefreshToken(oldToken.getUser().getUsername(), ipAddress, userAgent);
    }

    private Users findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username));
    }

    @Transactional(readOnly = true)
    public RefreshToken findByToken(String token) {
        return refreshTokenRepository.findByToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Refresh token not found with token: " + token));
    }

    /**
     * Scheduled cleanup of expired refresh tokens
     * Runs every day at 3 AM
     */
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanupExpiredTokens() {
        try {
            Instant now = Instant.now();
            refreshTokenRepository.deleteByExpiryDateBefore(now);
            log.info("Cleaned up expired refresh tokens at {}", now);
        } catch (Exception e) {
            log.error("Error during refresh token cleanup: {}", e.getMessage(), e);
        }
    }
}
