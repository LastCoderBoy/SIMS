package com.JK.SIMS.models.UM_models;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;

@Data
@Entity
@Table(name = "BlacklistedTokens")
@NoArgsConstructor
public class BlacklistedToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token", unique = true, nullable = false)
    private String token;
    private Date blacklistedAt;

    public BlacklistedToken(String token, Date blacklistedAt) {
        this.token = token;
        this.blacklistedAt = blacklistedAt;
    }
}
