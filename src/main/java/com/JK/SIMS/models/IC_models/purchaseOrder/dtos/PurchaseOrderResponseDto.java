package com.JK.SIMS.models.IC_models.purchaseOrder.dtos;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.PurchaseOrderViews;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.fasterxml.jackson.annotation.JsonView;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PurchaseOrderResponseDto {
    private Long id;
    private String poNumber;
    private PurchaseOrderStatus status;
    private LocalDate orderDate;
    private LocalDate expectedArrivalDate;
    private LocalDate actualArrivalDate;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private String productName;
    private ProductCategories productCategory;
    private BigDecimal totalPrice;
    private String supplierName;
    private String orderedBy;
    private String updatedBy;

    public PurchaseOrderResponseDto(PurchaseOrder purchaseOrder){
        this.id = purchaseOrder.getId();
        this.poNumber = purchaseOrder.getPONumber();
        this.status = purchaseOrder.getStatus();
        this.orderDate = purchaseOrder.getOrderDate();
        this.expectedArrivalDate = purchaseOrder.getExpectedArrivalDate();
        this.actualArrivalDate = purchaseOrder.getActualArrivalDate();
        this.orderedQuantity = purchaseOrder.getOrderedQuantity();
        this.receivedQuantity = purchaseOrder.getReceivedQuantity();
        this.productName = purchaseOrder.getProduct() != null ? purchaseOrder.getProduct().getName() : "N/A";
        this.productCategory = purchaseOrder.getProduct() != null && purchaseOrder.getProduct().getCategory() != null
                ? purchaseOrder.getProduct().getCategory()
                : null;
        this.totalPrice = calculateTotalPrice(purchaseOrder);
        this.supplierName = purchaseOrder.getSupplier() != null ? purchaseOrder.getSupplier().getName() : "N/A";
        this.orderedBy = purchaseOrder.getOrderedBy();
        this.updatedBy = purchaseOrder.getUpdatedBy();
    }

    public BigDecimal calculateTotalPrice(PurchaseOrder purchaseOrder){
        return purchaseOrder.getProduct().getPrice().multiply(new BigDecimal(purchaseOrder.getOrderedQuantity()));
    }
}
