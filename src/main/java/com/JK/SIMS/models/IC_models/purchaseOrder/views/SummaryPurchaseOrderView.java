package com.JK.SIMS.models.IC_models.purchaseOrder.views;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SummaryPurchaseOrderView {
    private Long id;
    private String poNumber;
    private PurchaseOrderStatus status;
    private LocalDate orderDate;
    private LocalDate expectedArrivalDate;
    private LocalDate actualArrivalDate;
    private Integer orderedQuantity;
    private Integer receivedQuantity;
    private String productName;

    public SummaryPurchaseOrderView(PurchaseOrder order){
        this.id = order.getId();
        this.poNumber = order.getPONumber();
        this.status = order.getStatus();
        this.orderDate = order.getOrderDate();
        this.expectedArrivalDate = order.getExpectedArrivalDate();
        this.actualArrivalDate = order.getActualArrivalDate();
        this.orderedQuantity = order.getOrderedQuantity();
        this.receivedQuantity = order.getReceivedQuantity();
        this.productName = order.getProduct() != null ? order.getProduct().getName() : "N/A";
    }
}
