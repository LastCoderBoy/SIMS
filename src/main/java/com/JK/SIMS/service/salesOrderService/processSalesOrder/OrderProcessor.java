package com.JK.SIMS.service.salesOrderService.processSalesOrder;

import com.JK.SIMS.exceptionHandler.InsufficientStockException;
import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.salesOrderService.SoServiceInIc;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

public abstract class OrderProcessor {
    private static Logger logger = LoggerFactory.getLogger(OrderProcessor.class);

    protected final InventoryControlService icService;
    protected final SoServiceInIc soServiceInIc;
    protected final StockMovementService stockMovementService;

    protected final SalesOrderRepository salesOrderRepository;

    public OrderProcessor(InventoryControlService icService, SoServiceInIc soServiceInIc, StockMovementService stockMovementService, SalesOrderRepository salesOrderRepository) {
        this.icService = icService;
        this.soServiceInIc = soServiceInIc;
        this.stockMovementService = stockMovementService;
        this.salesOrderRepository = salesOrderRepository;
    }

    @Transactional
    public ApiResponse processOrder(Long orderId, Map<Long, Integer> approvedQuantities, String approvedPerson){
        logger.info("SO: Processing order with ID: {}", orderId);
        try {
            SalesOrder salesOrder = soServiceInIc.getSalesOrderById(orderId);
            validateOrder(salesOrder);
            salesOrder.setConfirmedBy(approvedPerson);

            boolean partialApproval = false;

            // Convert reservations to actual stock deductions
            for (OrderItem item : salesOrder.getItems()) {
                Integer approvedQty = approvedQuantities.get(item.getId());
                if (approvedQty == null || approvedQty <= 0) {
                    throw new IllegalArgumentException("No approved quantity provided for item: " + item.getId());
                }
                if(approvedQty < item.getQuantity()){
                    partialApproval = true;
                }
                icService.fulfillReservation(item.getProduct().getProductID(), approvedQty);
                stockMovementService.logMovement(
                        item.getProduct(), StockMovementType.OUT, approvedQty,
                        salesOrder.getOrderReference(), StockMovementReferenceType.SALES_ORDER, approvedPerson
                );
            }

            // Update the status and save the order
            updateOrderStatus(salesOrder, partialApproval);
            salesOrderRepository.save(salesOrder);

            logger.info("OS (processOrderedProduct): SalesOrder {} processed successfully", orderId);
            return new ApiResponse(true, "SalesOrder processed successfully");
        } catch (InsufficientStockException e) {
            logger.error("OS (processOrderedProduct): Insufficient stock - {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            logger.error("OS (processOrderedProduct): Error processing order - {}", e.getMessage());
            throw new ServiceException("Failed to process order", e);
        }
    }

    protected void validateOrder(SalesOrder salesOrder) {
        if (salesOrder == null) {
            throw new ResourceNotFoundException("SalesOrder not found");
        }
        if (salesOrder.isFinalized()) {
            throw new ResourceNotFoundException("SalesOrder is finalized. Cannot process the following order: " + salesOrder.getOrderReference());
        }
    }

    protected void updateOrderStatus(SalesOrder salesOrder, boolean partialApproval) {
        if (partialApproval) {
            salesOrder.setStatus(SalesOrderStatus.PARTIALLY_APPROVED);
            logger.info("OS (updateOrderStatus): Updated status of SalesOrder {} to PARTIALLY_APPROVED", salesOrder.getOrderReference());
        } else{
            salesOrder.setStatus(SalesOrderStatus.APPROVED);
            logger.info("OS (updateOrderStatus): Updated status of SalesOrder {} to APPROVED", salesOrder.getOrderReference());
        }
    }
}
