package com.JK.SIMS.service.userAuthenticationService;

import com.JK.SIMS.models.UM_models.RefreshToken;
import com.JK.SIMS.models.UM_models.Users;

public interface RefreshTokenService {
    RefreshToken createRefreshToken(String username, String ipAddress, String userAgent);
    RefreshToken verifyExpiration(String token);
    void revokeToken(String token);
    RefreshToken findByToken(String token);
    RefreshToken rotateRefreshToken(RefreshToken oldToken, String ipAddress, String userAgent);
    void revokeAllUserTokens(Users user);
    // delete expired refresh tokens
}
