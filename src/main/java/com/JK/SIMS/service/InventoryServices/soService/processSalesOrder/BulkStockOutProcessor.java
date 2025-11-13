package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.exception.InventoryException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Map;

@Component
@Slf4j
public class BulkStockOutProcessor extends OrderProcessor implements StockOutProcessor {

    public BulkStockOutProcessor(Clock clock, SalesOrderServiceHelper salesOrderServiceHelper, StockManagementLogic stockManagementLogic,
                                 StockMovementService stockMovementService) {
        super(clock, salesOrderServiceHelper, stockManagementLogic, stockMovementService);
    }

    @Override
    @Transactional
    public SalesOrder processStockOut(SalesOrder salesOrder, Map<String, Integer> approvedQuantities, String username) {
        try {
            SalesOrder updatedSalesOrder = processOrder(salesOrder, approvedQuantities, username);
            log.info("SO-processStockOut(): Processing order with reference: {} is complete!", updatedSalesOrder.getOrderReference());
            return updatedSalesOrder;
        } catch (InventoryException ie){
            throw ie;
        } catch (Exception e) {
            log.error("SO-processStockOut(): Error processing order - {}", e.getMessage());
            throw new ServiceException("Failed to process order");
        }
    }
}
