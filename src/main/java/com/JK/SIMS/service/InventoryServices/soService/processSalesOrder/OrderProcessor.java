package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.exceptionHandler.InsufficientStockException;
import com.JK.SIMS.exceptionHandler.InventoryException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemStatus;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Map;

@Slf4j
public abstract class OrderProcessor {
    protected final Clock clock;

    protected final SalesOrderServiceHelper salesOrderServiceHelper;
    protected final StockManagementLogic stockManagementLogic;
    protected final StockMovementService stockMovementService;
    protected final SalesOrderRepository salesOrderRepository;
    @Autowired
    public OrderProcessor(Clock clock, SalesOrderServiceHelper salesOrderServiceHelper, StockManagementLogic stockManagementLogic, StockMovementService stockMovementService, SalesOrderRepository salesOrderRepository) {
        this.clock = clock;
        this.salesOrderServiceHelper = salesOrderServiceHelper;
        this.stockManagementLogic = stockManagementLogic;
        this.stockMovementService = stockMovementService;
        this.salesOrderRepository = salesOrderRepository;
    }

    @Transactional
    public ApiResponse<String> processOrder(Long orderId, Map<String, Integer> approvedQuantities, String approvedPerson){
        log.info("SO: Processing order with ID: {}", orderId);
        try {
            SalesOrder salesOrder = getSalesOrderById(orderId);
            if (salesOrder.isFinalized()) {
                throw new ResourceNotFoundException("OrderProcessor processOrder(): SalesOrder is finalized. Cannot process the following order: " + salesOrder.getOrderReference());
            }
            salesOrder.setConfirmedBy(approvedPerson);
            salesOrder.setLastUpdate(GlobalServiceHelper.now(clock));

            boolean partialApproval = false;
            boolean missingItem = false;

            // Convert reservations to actual stock deductions
            for (OrderItem item : salesOrder.getItems()) {
                String productId = item.getProduct().getProductID();
                Integer approvedQty = approvedQuantities.get(productId);
                if (approvedQty == null || approvedQty <= 0) {
                    log.warn("Skipping item {} - no approved quantity provided", item.getProduct().getProductID());
                    missingItem = true;
                    continue;
                }
                if(approvedQty < item.getQuantity()){
                    partialApproval = true;
                }
                if(approvedQty > item.getQuantity()){
                    throw new InventoryException("Cannot approve more stock than the order quantity for item: " + productId);
                }
                // Update the status and save the order
                updateOrderItemStatus(item, partialApproval);
                stockManagementLogic.fulfillReservation(item.getProduct().getProductID(), approvedQty);
                stockMovementService.logMovement(
                        item.getProduct(), StockMovementType.OUT, approvedQty,
                        salesOrder.getOrderReference(), StockMovementReferenceType.SALES_ORDER, approvedPerson
                );
            }

            salesOrderServiceHelper.updateSalesOrderStatus(salesOrder, missingItem);
            salesOrderRepository.save(salesOrder);
            log.info("OrderProcessor processOrder(): SalesOrder {} processed successfully", orderId);
            return new ApiResponse<>(true, "SalesOrder processed successfully");
        } catch (InsufficientStockException e) {
            log.error("OrderProcessor processOrder(): Insufficient stock - {}", e.getMessage());
            throw e;
        } catch (InventoryException e) {
            throw e;
        } catch (Exception e) {
            log.error("OrderProcessor processOrder(): Error processing order - {}", e.getMessage());
            throw new ServiceException("Failed to process order", e);
        }
    }

    protected void updateOrderItemStatus(OrderItem orderItem, boolean partialApproval) {
        if (partialApproval) {
            orderItem.setStatus(OrderItemStatus.PARTIALLY_APPROVED);
            log.info("OrderProcessor updateOrderStatus(): Updated status of OrderItem {} - productID: {} to PARTIALLY_APPROVED", orderItem.getId(), orderItem.getProduct().getProductID());
        } else{
            orderItem.setStatus(OrderItemStatus.APPROVED);
            log.info("OrderProcessor updateOrderStatus(): Updated status of OrderItem {} - productID: {} to APPROVED", orderItem.getId(), orderItem.getProduct().getProductID());
        }
    }


    protected SalesOrder getSalesOrderById(Long orderId) {
        return salesOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("SalesOrder with {} not found"));
    }
}
