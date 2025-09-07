package com.JK.SIMS.models.IC_models.purchaseOrder.token;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

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

    @CreationTimestamp
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
            name = "purchase_order_id",
            nullable = false)
    private PurchaseOrder order;

    public ConfirmationToken(String token, LocalDateTime createdAt, ConfirmationTokenStatus status, LocalDateTime expiresAt, PurchaseOrder order) {
        this.token = token;
        this.createdAt = createdAt;
        this.status = status;
        this.expiresAt = expiresAt;
        this.order = order;
    }
}
