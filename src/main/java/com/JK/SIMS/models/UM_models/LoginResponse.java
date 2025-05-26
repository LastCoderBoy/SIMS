package com.JK.SIMS.models.UM_models;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String role;

    public LoginResponse(String token, String role) {
        this.token = token;
        this.role = role;
    }
}
