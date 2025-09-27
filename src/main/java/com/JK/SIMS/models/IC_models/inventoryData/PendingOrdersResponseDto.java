package com.JK.SIMS.models.IC_models.inventoryData;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemResponseDto;
import com.JK.SIMS.service.InventoryServices.soService.SalesOrderServiceHelper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

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
    private BigDecimal totalAmount; // For SO: totalAmount, for PO: null or calculated value
    private String customerOrSupplierName; // For SO: customerName, for PO: supplierName
    private Integer totalOrderedQuantity;
}
