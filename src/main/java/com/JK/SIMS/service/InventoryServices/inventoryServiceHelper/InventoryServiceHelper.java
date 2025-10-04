package com.JK.SIMS.service.InventoryServices.inventoryServiceHelper;

import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.soService.SalesOrderServiceHelper;
import com.JK.SIMS.service.email_service.LowStockScheduler;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Component
public class InventoryServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(InventoryServiceHelper.class);

    private final IC_repository icRepository;
    private final LowStockScheduler lowStockAlert;
    private final SalesOrderServiceHelper salesOrderServiceHelper;
    @Autowired
    public InventoryServiceHelper(IC_repository icRepository, LowStockScheduler lowStockAlert, SalesOrderServiceHelper salesOrderServiceHelper) {
        this.icRepository = icRepository;
        this.lowStockAlert = lowStockAlert;
        this.salesOrderServiceHelper = salesOrderServiceHelper;
    }


    public static void validateUpdateRequest(InventoryData newInventoryData) {
        List<String> errors = new ArrayList<>();
        Integer newCurrentStock = newInventoryData.getCurrentStock();
        Integer newMinLevel = newInventoryData.getMinLevel();

        if (newCurrentStock == null && newMinLevel == null) {
            errors.add("At least one of currentStock or minLevel must be provided");
        }

        if (newCurrentStock != null &&
                newCurrentStock <= 0) {
            errors.add("Current stock must be greater than 0");
        }

        if (newMinLevel != null &&
                newMinLevel <= 0) {
            errors.add("Minimum stock level must be greater than 0");
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(errors);
        }
    }

    @Transactional(readOnly = true)
    public InventoryData getInventoryDataBySku(String sku) throws BadRequestException {
        return icRepository.findBySKU(sku)
                .orElseThrow(() -> new BadRequestException(
                        "IC (updateProduct): No product with SKU " + sku + " found"));
    }

    @Transactional(readOnly = true)
    public Optional<InventoryData> getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId);
    }

    public void updateInventoryStatus(InventoryData product) {
        if(product.getStatus() != InventoryDataStatus.INVALID) {
            if (product.getCurrentStock() <= product.getMinLevel()) {
                product.setStatus(InventoryDataStatus.LOW_STOCK);
                lowStockAlert.sendDailyLowStockAlert();
            } else {
                product.setStatus(InventoryDataStatus.IN_STOCK);
            }
        }
    }

//    public void fillWithPurchaseOrders(List<PendingOrdersResponseDto> combinedPendingOrders,
//                                        List<PurchaseOrderResponseDto> pendingPurchaseOrders){
//        for(PurchaseOrderResponseDto po : pendingPurchaseOrders){
//            PendingOrdersResponseDto pendingOrder = new PendingOrdersResponseDto(
//                    po.getId(),
//                    po.getPoNumber(),
//                    po.getProductName(),
//                    po.getProductCategory().toString(),
//                    StockMovementReferenceType.PURCHASE_ORDER.toString(),
//                    po.getStatus().toString(),
//                    po.getOrderDate().atStartOfDay(),
//                    po.getExpectedArrivalDate().atStartOfDay(),
//                    po.getSupplierName(),
//                    po.getOrderedQuantity()
//            );
//            combinedPendingOrders.add(pendingOrder);
//        }
//    }

    public void fillWithPurchaseOrders(List<PendingOrdersResponseDto> combinedPendingOrders,
                                       List<SummaryPurchaseOrderView> pendingPurchaseOrders){
        for(SummaryPurchaseOrderView po : pendingPurchaseOrders){
            PendingOrdersResponseDto pendingOrder = new PendingOrdersResponseDto(
                    po.getId(),
                    po.getPoNumber(),
                    po.getProductName(),
                    po.getProductCategory().toString(),
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

    public void fillWithSalesOrders(List<PendingOrdersResponseDto> combinedPendingOrders, List<SalesOrderResponseDto> pendingSalesOrders){
        pendingSalesOrders.forEach(so ->
                combinedPendingOrders.add(new PendingOrdersResponseDto(
                        so.getId(),
                        so.getOrderReference(),
                        null,
                        null,
                        StockMovementReferenceType.SALES_ORDER.toString(),
                        so.getStatus().toString(),
                        so.getOrderDate(),
                        so.getEstimatedDeliveryDate(),
                        so.getCustomerName(),
                        salesOrderServiceHelper.totalSalesOrderQuantity(so.getItems())
                ))
        );
    }

    public PaginatedResponse<InventoryDataDto> transformToPaginatedInventoryDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDto> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream()
                                                        .map(this::convertToInventoryDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());
        logger.info("TotalItems (getInventoryDataDTOList): {} products retrieved.", inventoryPage.getContent().size());
        return dtoResponse;
    }

    public InventoryDataDto convertToInventoryDTO(InventoryData inventoryData) {
        InventoryDataDto dto = new InventoryDataDto();
        // Set product fields
        dto.setProductID(inventoryData.getPmProduct().getProductID());
        dto.setProductName(inventoryData.getPmProduct().getName());
        dto.setCategory(inventoryData.getPmProduct().getCategory());
        dto.setPrice(inventoryData.getPmProduct().getPrice());
        dto.setProductStatus(inventoryData.getPmProduct().getStatus());

        // Set inventory fields
        dto.setSKU(inventoryData.getSKU());
        dto.setLocation(inventoryData.getLocation());
        dto.setCurrentStock(inventoryData.getCurrentStock());
        dto.setMinLevel(inventoryData.getMinLevel());
        dto.setReservedStock(inventoryData.getReservedStock());
        dto.setInventoryStatus(inventoryData.getStatus());
        dto.setLastUpdate(inventoryData.getLastUpdate().toString());

        return dto;
    }
}
