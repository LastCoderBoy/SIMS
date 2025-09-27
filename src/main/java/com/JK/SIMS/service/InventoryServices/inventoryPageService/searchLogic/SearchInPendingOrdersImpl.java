package com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic;

import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.poService.PoServiceInIc;
import com.JK.SIMS.service.InventoryServices.poService.searchLogic.PoStrategy;
import com.JK.SIMS.service.InventoryServices.soService.SoServiceInIc;
import com.JK.SIMS.service.InventoryServices.soService.searchLogic.SoStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageImpl;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SearchInPendingOrdersImpl implements PendingOrdersSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(SearchInPendingOrdersImpl.class);

    private final PoStrategy poStrategy;
    private final SoStrategy soStrategy;
    private final InventoryServiceHelper inventoryServiceHelper;

    public SearchInPendingOrdersImpl(@Qualifier("icPoSearchStrategy") PoStrategy poStrategy,
                                     @Qualifier("icSoSearchStrategy") SoStrategy soStrategy,
                                     InventoryServiceHelper inventoryServiceHelper) {
        this.poStrategy = poStrategy;
        this.soStrategy = soStrategy;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Override
    public PaginatedResponse<PendingOrdersResponseDto> searchInPendingOrders(String text, int page, int size) {
        // Search in Sales Orders
        PaginatedResponse<SalesOrderResponseDto> pendingSalesOrdersResult =
                soStrategy.searchInSo(text, page, size);

        // Search in Purchase Orders
        PaginatedResponse<PurchaseOrderResponseDto> pendingPurchaseOrdersResult =
                poStrategy.searchInPos(text, page, size);

        // Combine the results and return
        List<PendingOrdersResponseDto> combinedResults = new ArrayList<>();
        inventoryServiceHelper.fillWithSalesOrders(combinedResults, pendingSalesOrdersResult.getContent());
        inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, pendingPurchaseOrdersResult.getContent());
        logger.info("IcPendingOrders: searchInPendingOrders() returning {} results", combinedResults.size());
        return new PaginatedResponse<>(new PageImpl<>(combinedResults));
    }
}
