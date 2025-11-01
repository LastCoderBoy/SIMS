package com.JK.SIMS.models.UM_models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "refresh_tokens")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 500)
    private String token;

    @Column(nullable = false)
    private Instant expiryDate;

    @CreationTimestamp
    @Column(nullable = false)
    private Instant createdAt;

    @Column
    private String ipAddress;

    @Column
    private String userAgent;

    @Column(nullable = false)
    private boolean revoked = false; // manually killed tokens even if they haven't expired yet.

    // ***** Relationship detail *****
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private Users user;
}
