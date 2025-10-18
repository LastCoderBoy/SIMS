package com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.inventoryData.PendingOrdersResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.soService.searchLogic.SoStrategy;
import com.JK.SIMS.service.purchaseOrderSearchLogic.PoSearchStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
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

    // Search method which is used for the pending SO and PO orders
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseDto> searchInPendingOrders(String text, int page, int size) {
        try {
            // Search in Sales Orders
            Page<SalesOrder> salesOrderPage = soStrategy.searchInSo(text, page, size);

            // Search in Purchase Orders
            String defaultSortByForPo = "product.name";
            Page<PurchaseOrder> purchaseOrderPage =
                    poSearchStrategy.searchInPos(text, page, size, defaultSortByForPo, "asc");

            // Combine the results
            List<PendingOrdersResponseDto> combinedResults = new ArrayList<>();
            inventoryServiceHelper.fillWithSalesOrders(combinedResults, salesOrderPage.getContent());
            inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, purchaseOrderPage.getContent());

            // Return with correct pagination metadata
            long totalResults = salesOrderPage.getTotalElements() + purchaseOrderPage.getTotalElements();
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
