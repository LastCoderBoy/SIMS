package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.exception.InsufficientStockException;
import com.JK.SIMS.exception.InventoryException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.service.InventoryServices.inventoryDashboardService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import com.JK.SIMS.service.generalUtils.SalesOrderServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Slf4j
@RequiredArgsConstructor
public abstract class OrderProcessor {
    protected final Clock clock;

    protected final SalesOrderServiceHelper salesOrderServiceHelper;
    protected final StockManagementLogic stockManagementLogic;
    protected final StockMovementService stockMovementService;

    @Transactional
    public SalesOrder processOrder(SalesOrder salesOrder, Map<String, Integer> approvedQuantities, String approvedPerson){
        log.info("SO: Processing order with reference: {}", salesOrder.getOrderReference());
        try {
            if (salesOrder.isFinalized()) {
                throw new ResourceNotFoundException("OrderProcessor processOrder(): SalesOrder is finalized. Cannot process the following order: " + salesOrder.getOrderReference());
            }
            salesOrder.setConfirmedBy(approvedPerson);
            salesOrder.setLastUpdate(GlobalServiceHelper.now(clock));

            // Convert reservations to actual stock deductions
            for (OrderItem item : salesOrder.getItems()) {
                String productId = item.getProduct().getProductID();
                Integer approvedQty = approvedQuantities.get(productId);
                if (approvedQty == null) {
                    log.warn("Skipping item {} - no approved quantity provided", item.getProduct().getProductID());
                    continue;
                }
                if(approvedQty < 0){
                    log.error("OrderProcessor processOrder(): Negative approved quantity for item: {}", productId);
                    throw new InventoryException("Cannot approve negative stock for item: " + productId);
                }
                if(approvedQty > item.getQuantity()){
                    log.error("OrderProcessor processOrder(): More stock than the order quantity for item: {}", productId);
                    throw new InventoryException("Cannot approve more stock than the order quantity for item: " + productId);
                }

                // Out the approved quantity from the inventory
                stockManagementLogic.fulfillReservation(item.getProduct().getProductID(), approvedQty);

                // Update the status after fulfillment
                salesOrderServiceHelper.updateOrderItemFulfillStatus(item, approvedQty);

                // set the fulfilled field
                item.setApprovedQuantity(item.getApprovedQuantity() + approvedQty);

                // Track the record
                stockMovementService.logMovement(
                        item.getProduct(), StockMovementType.OUT, approvedQty,
                        salesOrder.getOrderReference(), StockMovementReferenceType.SALES_ORDER, approvedPerson
                );
            }

            salesOrderServiceHelper.updateSoStatusBasedOnItemQuantity(salesOrder);
            log.info("OrderProcessor processOrder(): Returning updated SalesOrder: {}", salesOrder.getOrderReference());
            return salesOrder;
        } catch (InsufficientStockException e) {
            log.error("OrderProcessor processOrder(): Insufficient stock - {}", e.getMessage());
            throw e;
        } catch (InventoryException e) {
            log.error("OrderProcessor processOrder(): Inventory error - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("OrderProcessor processOrder(): Error processing order - {}", e.getMessage());
            throw new ServiceException("Failed to process order", e);
        }
    }
}
