package com.JK.SIMS.models.IC_models.damage_loss;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "DamageLosses")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DamageLoss {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @ManyToOne
    @JoinColumn(name = "productID", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false)
    private Integer quantityLost;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LossReason reason;    // will be dropdown for fast and easy options

    @Column(nullable = false)
    private LocalDateTime lossDate; //if lossDate is not provided, by default will be same as createdAt column

    @Column(length = 100)
    private String recordedBy;  // Username of the person logging the loss

    @Column
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime updatedAt; //Only Admins can update
}
