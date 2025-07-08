package com.JK.SIMS.models.IC_models.incoming;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.UM_models.Users;
import com.JK.SIMS.models.supplier.Supplier;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "incoming_stock")
public class IncomingStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "PO_Number", unique = true, nullable = false)
    private String PONumber; // PO Invoice Number, will be [PO-supplierName-ID]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false)
    private Integer orderedQuantity; // Quantity initially ordered
    private Integer receivedQuantity = 0; // Quantity actually received so far (defaults to 0)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private IncomingStockStatus status; // PENDING, RECEIVED, PARTIALLY_RECEIVED, CANCELLED, FAILED

    @Column(nullable = false)
    private LocalDate orderDate;
    private LocalDate expectedArrivalDate;
    private LocalDate actualArrivalDate; // When the stock was actually received

    @Column(columnDefinition = "TEXT")
    private String notes; // Any additional notes about the incoming shipment

    @ManyToOne
    @JoinColumn(name = "supplier_name")
    private Supplier supplier; // The supplier from whom the stock is coming

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private String updatedBy; // User who last updated this record
}