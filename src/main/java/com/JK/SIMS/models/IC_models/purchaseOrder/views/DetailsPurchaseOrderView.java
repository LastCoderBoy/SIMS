package com.JK.SIMS.models.IC_models.purchaseOrder.views;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.service.helperServices.PurchaseOrderServiceHelper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DetailsPurchaseOrderView extends SummaryPurchaseOrderView{
    private ProductCategories productCategory;
    private BigDecimal totalPrice;
    private String supplierName;
    private String orderedBy;
    private String updatedBy;

    public DetailsPurchaseOrderView (PurchaseOrder purchaseOrder){
        super(purchaseOrder.getId(), purchaseOrder.getPONumber(), purchaseOrder.getStatus(),
                purchaseOrder.getOrderDate(), purchaseOrder.getExpectedArrivalDate(), purchaseOrder.getActualArrivalDate(),
                purchaseOrder.getOrderedQuantity(), purchaseOrder.getReceivedQuantity(),
                purchaseOrder.getProduct() != null ? purchaseOrder.getProduct().getName() : "N/A"
        );
        this.productCategory = purchaseOrder.getProduct() != null && purchaseOrder.getProduct().getCategory() != null
                ? purchaseOrder.getProduct().getCategory()
                : null;
        this.totalPrice = PurchaseOrderServiceHelper.calculateTotalPrice(purchaseOrder);
        this.supplierName = purchaseOrder.getSupplier() != null ? purchaseOrder.getSupplier().getName() : "N/A";
        this.orderedBy = purchaseOrder.getOrderedBy();
        this.updatedBy = purchaseOrder.getUpdatedBy();
    }
}
