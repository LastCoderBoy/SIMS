package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.processSalesOrderDtos.ProcessSalesOrderRequestDto;
import com.JK.SIMS.repository.SalesOrder_Repo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import com.JK.SIMS.service.utilities.SalesOrderServiceHelper;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Clock;

@Component
@Slf4j
public class BulkStockOutProcessor extends OrderProcessor implements StockOutProcessor {

    public BulkStockOutProcessor(Clock clock, SalesOrderServiceHelper salesOrderServiceHelper, StockManagementLogic stockManagementLogic,
                                 StockMovementService stockMovementService, SalesOrderRepository salesOrderRepository) {
        super(clock, salesOrderServiceHelper, stockManagementLogic, stockMovementService, salesOrderRepository);
    }

    @Override
    @Transactional
    public ApiResponse<String> processStockOut(ProcessSalesOrderRequestDto processSoRequestDto, String username) {
        ApiResponse<String> response = processOrder(
                processSoRequestDto.getOrderId(),
                processSoRequestDto.getItemQuantities(),
                username);
        log.info("SO: Processing order with ID: {} - Response: {}", processSoRequestDto.getOrderId(), response.getMessage());
        return new ApiResponse<>(true, "Stock out processed successfully");
    }
}
