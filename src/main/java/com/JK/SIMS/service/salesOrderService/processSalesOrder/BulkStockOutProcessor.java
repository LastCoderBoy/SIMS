package com.JK.SIMS.service.salesOrderService.processSalesOrder;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.salesOrder.BulkShipStockRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.ShipStockRequestDto;
import com.JK.SIMS.repository.outgoingStockRepo.SalesOrderRepository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.salesOrderService.SoServiceInIc;
import com.JK.SIMS.service.stockMovementService.StockMovementService;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Component;

import java.util.List;

public class BulkStockOutProcessor extends OrderProcessor implements StockOutProcessor {
    public BulkStockOutProcessor(InventoryControlService icService, SoServiceInIc soServiceInIc, StockMovementService stockMovementService, SalesOrderRepository salesOrderRepository) {
        super(icService, soServiceInIc, stockMovementService, salesOrderRepository);
    }

    @Override
    @Transactional
    public ApiResponse processStockOut(BulkShipStockRequestDto bulkSoRequestDto, String username) {
        List<ShipStockRequestDto> shipStockRequestDtoList = bulkSoRequestDto.getBulkSoRequestDtos();
        for(ShipStockRequestDto stockRequestDto : shipStockRequestDtoList){
            processOrder(stockRequestDto.getOrderId(), stockRequestDto.getItemQuantities(), username);
        }
        return new ApiResponse(true, "Stock out processed successfully");
    }
}
