package com.JK.SIMS.models.IC_models.incoming;

import com.JK.SIMS.models.PM_models.ProductCategories;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class IncomingStockResponse {
    private Long id;
    private String poNumber;
    private IncomingStockStatus status;
    private LocalDate orderDate;
    private LocalDate expectedArrivalDate;
    private LocalDate actualArrivalDate;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private String productName;
    private ProductCategories productCategory;
    private String supplierName;
    private String orderedBy;
    private String receivedBy;

    public IncomingStockResponse(IncomingStock incomingStock){
        this.id = incomingStock.getId();
        this.poNumber = incomingStock.getPONumber();
        this.status = incomingStock.getStatus();
        this.orderDate = incomingStock.getOrderDate();
        this.expectedArrivalDate = incomingStock.getExpectedArrivalDate();
        this.actualArrivalDate = incomingStock.getActualArrivalDate();
        this.orderedQuantity = incomingStock.getOrderedQuantity();
        this.receivedQuantity = incomingStock.getReceivedQuantity();
        this.productName = incomingStock.getProduct() != null ? incomingStock.getProduct().getName() : "N/A";
        this.productCategory = incomingStock.getProduct() != null && incomingStock.getProduct().getCategory() != null
                ? incomingStock.getProduct().getCategory()
                : null;
        this.supplierName = incomingStock.getSupplier() != null ? incomingStock.getSupplier().getName() : "N/A";
        this.orderedBy = incomingStock.getOrderedBy();
        this.receivedBy = incomingStock.getUpdatedBy();
    }
}
