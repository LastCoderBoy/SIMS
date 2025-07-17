package com.JK.SIMS.models.IC_models.incoming.token;

import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Data
@NoArgsConstructor
public class ConfirmationToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String token;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime clickedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private ConfirmationTokenStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
            name = "incoming_stock_id",
            nullable = false)
    private IncomingStock order;

    public ConfirmationToken(String token, LocalDateTime createdAt, ConfirmationTokenStatus status, LocalDateTime expiresAt, IncomingStock order) {
        this.token = token;
        this.createdAt = createdAt;
        this.status = status;
        this.expiresAt = expiresAt;
        this.order = order;
    }
}
