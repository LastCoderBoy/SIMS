package com.JK.SIMS.models.IC_models.purchaseOrder;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.supplier.Supplier;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Objects;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "purchase_order")
public class PurchaseOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "po_number", unique = true, nullable = false)
    private String PONumber; // PO Invoice Number will be [PO-supplierID-UUID]

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private ProductsForPM product;

    @Column(nullable = false, name = "ordered_quantity")
    private Integer orderedQuantity; // Quantity initially ordered

    @Column(name = "received_quantity")
    private Integer receivedQuantity = 0; // Quantity actually received so far (defaults to 0)

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PurchaseOrderStatus status; // DELIVERY_IN_PROCESS, RECEIVED, PARTIALLY_RECEIVED, CANCELLED, FAILED

    @Column(nullable = false, name = "order_date")
    private LocalDate orderDate;

    @Column(name = "expected_arrival_date")
    private LocalDate expectedArrivalDate;

    @Column(name = "actual_arrival_date")
    private LocalDate actualArrivalDate; // When the stock was actually received

    @Column(columnDefinition = "TEXT")
    private String notes; // Any additional notes about the incoming shipment

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "supplier_id")
    private Supplier supplier; // The supplier from whom the stock is coming

    @UpdateTimestamp // Automatically will be set
    @Column(nullable = false, name = "last_updated")
    private LocalDateTime lastUpdated;

    @Column(nullable = false, name = "ordered_by")
    private String orderedBy;

    @Column(name = "updated_by")
    private String updatedBy;

    @Version
    private Integer version; // Used to handle the Optimistic Locking

    // Business constructor for creating new purchase orders
    public PurchaseOrder(ProductsForPM product,
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
        this.orderDate = Objects.requireNonNull(orderDate, "SalesOrder date cannot be null");
        this.lastUpdated = Objects.requireNonNull(lastUpdated, "Last updated cannot be null");
        this.orderedBy = Objects.requireNonNull(orderedBy, "Updated by cannot be null");

        // Set defaults
        this.receivedQuantity = 0;
        this.status = PurchaseOrderStatus.AWAITING_APPROVAL;
        this.actualArrivalDate = null;
        this.updatedBy = null;
    }

    // Convenience constructor with common defaults
    public PurchaseOrder(ProductsForPM product,
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
        return this.status == PurchaseOrderStatus.RECEIVED ||
                this.status == PurchaseOrderStatus.CANCELLED ||
                this.status == PurchaseOrderStatus.FAILED;
    }
}