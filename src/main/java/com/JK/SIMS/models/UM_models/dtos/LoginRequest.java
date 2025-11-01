package com.JK.SIMS.models.UM_models.dtos;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LoginRequest {
    @NotNull(message = "Username or Email cannot be null")
    private String login;

    @NotNull(message = "Password cannot be null")
    private String password;
}
