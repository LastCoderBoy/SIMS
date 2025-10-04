package com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.purchaseOrderSearchLogic.PoSearchStrategy;
import com.JK.SIMS.service.InventoryServices.soService.searchLogic.SoStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
public class SearchInPendingOrdersImpl implements PendingOrdersSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(SearchInPendingOrdersImpl.class);

    private final PoSearchStrategy poSearchStrategy;
    private final SoStrategy soStrategy;
    private final InventoryServiceHelper inventoryServiceHelper;

    public SearchInPendingOrdersImpl(@Qualifier("icPoSearchStrategy") PoSearchStrategy poSearchStrategy,
                                     @Qualifier("icSoSearchStrategy") SoStrategy soStrategy,
                                     InventoryServiceHelper inventoryServiceHelper) {
        this.poSearchStrategy = poSearchStrategy;
        this.soStrategy = soStrategy;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseDto> searchInPendingOrders(String text, int page, int size) {
        try {
            // Search in Sales Orders
            PaginatedResponse<SalesOrderResponseDto> pendingSalesOrdersResult =
                    soStrategy.searchInSo(text, page, size);

            // Search in Purchase Orders
            String defaultSortByForPo = "product.name";
            PaginatedResponse<SummaryPurchaseOrderView> pendingPurchaseOrdersResult =
                    poSearchStrategy.searchInPos(text, page, size, defaultSortByForPo, "asc");

            // Combine the results
            List<PendingOrdersResponseDto> combinedResults = new ArrayList<>();
            inventoryServiceHelper.fillWithSalesOrders(combinedResults, pendingSalesOrdersResult.getContent());
            inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, pendingPurchaseOrdersResult.getContent());

            // Return with correct pagination metadata
            long totalResults = pendingSalesOrdersResult.getTotalElements() + pendingPurchaseOrdersResult.getTotalElements();
            logger.info("IcPendingOrders: searchInPendingOrders() returning {} results", combinedResults.size());
            return new PaginatedResponse<>(
                    new PageImpl<>(
                            combinedResults,
                            PageRequest.of(page, size),
                            totalResults
                    )
            );
        } catch (Exception e) {
            logger.error("IC (searchInPendingOrders): Error searching pending orders", e);
            throw new ServiceException("Failed to search pending orders", e);
        }
    }

}
