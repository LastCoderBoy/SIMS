package com.JK.SIMS.models.IC_models.inventoryData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendingOrdersResponseDto {

    private Long id;
    private String orderReference; // For SO: orderReference, for PO: poNumber
    private String productName;
    private String productCategory;
    private String type; // "SALES_ORDER" or "PURCHASE_ORDER"
    private String status;
    private LocalDateTime orderDate;
    private LocalDateTime estimatedDate; // For SO: estimatedDeliveryDate, for PO: expectedArrivalDate
    private String customerOrSupplierName; // For SO: customerName, for PO: supplierName
    private Integer totalOrderedQuantity;
}
