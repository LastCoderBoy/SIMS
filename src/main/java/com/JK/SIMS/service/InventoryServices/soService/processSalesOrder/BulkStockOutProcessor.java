package com.JK.SIMS.service.InventoryServices.soService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.BulkShipStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.ShipStockRequestDto;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.StockManagementLogic;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import jakarta.transaction.Transactional;
import org.slf4j.*;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BulkStockOutProcessor extends OrderProcessor implements StockOutProcessor {
    private static final Logger logger = LoggerFactory.getLogger(BulkStockOutProcessor.class);

    public BulkStockOutProcessor(StockManagementLogic stockManagementLogic, StockMovementService stockMovementService, SalesOrderRepository salesOrderRepository) {
        super(stockManagementLogic, stockMovementService, salesOrderRepository);
    }

    @Override
    @Transactional
    public ApiResponse processStockOut(BulkShipStockRequestDto bulkSoRequestDto, String username) {
        List<ShipStockRequestDto> shipStockRequestDtoList = bulkSoRequestDto.getBulkSoRequestDtos();
        for(ShipStockRequestDto stockRequestDto : shipStockRequestDtoList){
            ApiResponse response = processOrder(stockRequestDto.getOrderId(), stockRequestDto.getItemQuantities(), username);
            logger.info("SO: Processing order with ID: {} - Response: {}", stockRequestDto.getOrderId(), response.getMessage());
        }
        return new ApiResponse(true, "Stock out processed successfully");
    }
}
