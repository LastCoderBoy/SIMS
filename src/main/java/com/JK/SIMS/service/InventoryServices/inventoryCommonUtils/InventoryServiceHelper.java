package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils;

import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlRequest;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.models.inventoryData.dtos.PendingOrdersResponseInIC;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class InventoryServiceHelper {
    public static void validateUpdateRequest(InventoryControlRequest inventoryControlRequest) {
        List<String> errors = new ArrayList<>();
        Integer newCurrentStock = inventoryControlRequest.getCurrentStock();
        Integer newMinLevel = inventoryControlRequest.getMinLevel();

        if (newCurrentStock == null && newMinLevel == null) {
            errors.add("At least one of currentStock or minLevel must be provided");
        }
        if (newCurrentStock != null && newCurrentStock <= 0) {
            errors.add("Current stock must be greater than 0");
        }
        if (newMinLevel != null && newMinLevel <= 0) {
            errors.add("Minimum stock level must be greater than 0");
        }
        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    public void fillWithPurchaseOrders(List<PendingOrdersResponseInIC> combinedPendingOrders,
                                       List<PurchaseOrder> pendingPurchaseOrders){
        for(PurchaseOrder po : pendingPurchaseOrders){
            PendingOrdersResponseInIC pendingOrder = new PendingOrdersResponseInIC(
                    po.getId(),
                    po.getPONumber(),
                    StockMovementReferenceType.PURCHASE_ORDER.toString(),
                    po.getStatus().toString(),
                    po.getOrderDate() != null ? po.getOrderDate().atStartOfDay() : null,
                    po.getExpectedArrivalDate() != null ? po.getExpectedArrivalDate().atStartOfDay() : null,
                    po.getSupplier().getName(),
                    po.getOrderedQuantity()
            );
            combinedPendingOrders.add(pendingOrder);
        }
    }

    public void fillWithPurchaseOrderView(List<PendingOrdersResponseInIC> combinedPendingOrders,
                                       List<SummaryPurchaseOrderView> pendingPurchaseOrders){
        for(SummaryPurchaseOrderView po : pendingPurchaseOrders){
            PendingOrdersResponseInIC pendingOrder = new PendingOrdersResponseInIC(
                    po.getId(),
                    po.getPoNumber(),
                    StockMovementReferenceType.PURCHASE_ORDER.toString(),
                    po.getStatus().toString(),
                    po.getOrderDate() != null ? po.getOrderDate().atStartOfDay() : null,
                    po.getExpectedArrivalDate() != null ? po.getExpectedArrivalDate().atStartOfDay() : null,
                    po.getSupplierName(),
                    po.getOrderedQuantity()
            );
            combinedPendingOrders.add(pendingOrder);
        }
    }

    public void fillWithSalesOrders(List<PendingOrdersResponseInIC> combinedPendingOrders,
                                    List<SalesOrder> pendingSalesOrders){
        pendingSalesOrders.forEach(so ->
                combinedPendingOrders.add(new PendingOrdersResponseInIC(
                        so.getId(),
                        so.getOrderReference(),
                        StockMovementReferenceType.SALES_ORDER.toString(),
                        so.getStatus().toString(),
                        so.getOrderDate(),
                        so.getEstimatedDeliveryDate(),
                        so.getCustomerName(),
                        so.getItems().stream().mapToInt(OrderItem::getQuantity).sum()
                ))
        );
    }

    public void fillWithSalesOrderView(List<PendingOrdersResponseInIC> combinedPendingOrders,
                                    List<SummarySalesOrderView> pendingSalesOrders){
        pendingSalesOrders.forEach(so ->
                combinedPendingOrders.add(new PendingOrdersResponseInIC(
                        so.getId(),
                        so.getOrderReference(),
                        StockMovementReferenceType.SALES_ORDER.toString(),
                        so.getStatus().toString(),
                        so.getOrderDate(),
                        so.getEstimatedDeliveryDate(),
                        so.getCustomerName(),
                        so.getTotalOrderedQuantity()
                ))
        );
    }

    public PaginatedResponse<InventoryControlResponse> transformToPaginatedInventoryResponse(Page<InventoryControlData> inventoryPage) {
        return PaginatedResponse.<InventoryControlResponse>builder()
                .content(inventoryPage.getContent().stream()
                        .map(this::convertToInventoryDTO)
                        .toList())
                .totalPages(inventoryPage.getTotalPages())
                .totalElements(inventoryPage.getTotalElements())
                .build();
    }

    public InventoryControlResponse convertToInventoryDTO(InventoryControlData inventoryControlData) {
        return InventoryControlResponse.builder()
                // Product fields
                .productID(inventoryControlData.getPmProduct().getProductID())
                .productName(inventoryControlData.getPmProduct().getName())
                .category(inventoryControlData.getPmProduct().getCategory())
                .price(inventoryControlData.getPmProduct().getPrice())
                .productStatus(inventoryControlData.getPmProduct().getStatus())
                // Inventory fields
                .SKU(inventoryControlData.getSKU())
                .location(inventoryControlData.getLocation())
                .currentStock(inventoryControlData.getCurrentStock())
                .minLevel(inventoryControlData.getMinLevel())
                .reservedStock(inventoryControlData.getReservedStock())
                .inventoryStatus(inventoryControlData.getStatus())
                .lastUpdate(inventoryControlData.getLastUpdate().toString())
                .build();
    }

}
