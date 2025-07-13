package com.JK.SIMS.models.IC_models.incoming;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.service.GlobalServiceHelper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "incoming_stock")
public class IncomingStock {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", unique = true, nullable = false)
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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier; // The supplier from whom the stock is coming

    @Column(nullable = false)
    private LocalDateTime lastUpdated;

    @Column(nullable = false)
    private String orderedBy;

    @Column
    private String updatedBy;

    // Business constructor for creating new purchase orders
    public IncomingStock(ProductsForPM product,
                         Supplier supplier,
                         Integer orderedQuantity,
                         LocalDate expectedArrivalDate,
                         String notes,
                         String poNumber,
                         LocalDate orderDate,
                         LocalDateTime lastUpdated,
                         String orderedBy) {
        this.product = Objects.requireNonNull(product, "Product cannot be null");
        this.supplier = Objects.requireNonNull(supplier, "Supplier cannot be null");
        this.orderedQuantity = Objects.requireNonNull(orderedQuantity, "Ordered quantity cannot be null");
        this.expectedArrivalDate = expectedArrivalDate;
        this.notes = notes != null ? notes : "";
        this.PONumber = Objects.requireNonNull(poNumber, "PO Number cannot be null");
        this.orderDate = Objects.requireNonNull(orderDate, "Order date cannot be null");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "Last updated cannot be null");
        this.orderedBy = Objects.requireNonNull(orderedBy, "Updated by cannot be null");

        // Set defaults
        this.receivedQuantity = 0;
        this.status = IncomingStockStatus.PENDING;
        this.actualArrivalDate = null;
        this.updatedBy = null;
    }

    // Convenience constructor with common defaults
    public IncomingStock(ProductsForPM product,
                         Supplier supplier,
                         Integer orderedQuantity,
                         LocalDate expectedArrivalDate,
                         String notes,
                         String poNumber,
                         String orderedBy,
                         Clock clock) {
        this(product, supplier, orderedQuantity, expectedArrivalDate, notes, poNumber,
                LocalDate.from(GlobalServiceHelper.now(clock)),    //  Calculate orderDate
                GlobalServiceHelper.now(clock),                    //  Calculate lastUpdated
                orderedBy);                                        //  Pass through orderedBy
    }

    public boolean isFinalized() {
        return this.status == IncomingStockStatus.RECEIVED ||
                this.status == IncomingStockStatus.CANCELLED ||
                this.status == IncomingStockStatus.FAILED;
    }
}