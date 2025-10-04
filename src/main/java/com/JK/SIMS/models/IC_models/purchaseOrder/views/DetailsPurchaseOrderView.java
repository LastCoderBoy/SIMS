package com.JK.SIMS.models.IC_models.purchaseOrder.views;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.service.helperServices.PurchaseOrderServiceHelper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailsPurchaseOrderView{
    // Existing fields
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
    private String supplierName;

    // New fields
    private BigDecimal totalPrice;
    private String orderedBy;
    private String updatedBy;

    public DetailsPurchaseOrderView (PurchaseOrder order){
        this.id = order.getId();
        this.poNumber = order.getPONumber();
        this.status = order.getStatus();
        this.orderDate = order.getOrderDate();
        this.expectedArrivalDate = order.getExpectedArrivalDate();
        this.actualArrivalDate = order.getActualArrivalDate();
        this.orderedQuantity = order.getOrderedQuantity();
        this.receivedQuantity = order.getReceivedQuantity();
        this.productName = order.getProduct() != null ? order.getProduct().getName() : "N/A";
        this.productCategory = order.getProduct() != null && order.getProduct().getCategory() != null
                ? order.getProduct().getCategory()
                : null;
        this.supplierName = order.getSupplier() != null ? order.getSupplier().getName() : "N/A";
        this.totalPrice = PurchaseOrderServiceHelper.calculateTotalPrice(order);
        this.orderedBy = order.getOrderedBy();
        this.updatedBy = order.getUpdatedBy();
    }
}
