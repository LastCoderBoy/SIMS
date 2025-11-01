package com.JK.SIMS.models.UM_models.dtos;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL) // Don't include null fields
public class TokenResponse {
    private String accessToken;

    // Refresh token should NOT be in response when using cookies
    // For backward compatibility with non-cookie approach
    private String refreshToken;

    private String tokenType;
    private Long expiresIn; // in seconds
    private String username;
    private String role;

    public TokenResponse(String accessToken, String tokenType, Long expiresIn, String username, String role) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.username = username;
        this.role = role;
    }
}
