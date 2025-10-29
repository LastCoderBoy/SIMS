package com.JK.SIMS.service.InventoryServices.inventoryPageService.searchLogic;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.inventoryData.dtos.PendingOrdersResponseInIC;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.utilities.purchaseOrderSearchLogic.PoSearchStrategy;
import com.JK.SIMS.service.utilities.salesOrderSearchLogic.SoSearchStrategy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

import static com.JK.SIMS.service.utilities.EntityConstants.*;

@Component
@Slf4j
public class SearchInPendingOrdersImpl implements PendingOrdersSearchStrategy {
    private final PoSearchStrategy poSearchStrategy;
    private final SoSearchStrategy soSearchStrategy;
    private final InventoryServiceHelper inventoryServiceHelper;

    public SearchInPendingOrdersImpl(@Qualifier("icPoSearchStrategy") PoSearchStrategy poSearchStrategy,
                                     @Qualifier("icSoSearchStrategy") SoSearchStrategy soSearchStrategy,
                                     InventoryServiceHelper inventoryServiceHelper) {
        this.poSearchStrategy = poSearchStrategy;
        this.soSearchStrategy = soSearchStrategy;
        this.inventoryServiceHelper = inventoryServiceHelper;
    }

    // Search method which is used for the pending SO and PO orders
    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<PendingOrdersResponseInIC> searchInPendingOrders(String text, int page, int size) {
        try {
            // Search in Sales Orders
            Page<SalesOrder> salesOrderPage = soSearchStrategy.searchInSo(text, page, size, DEFAULT_SORT_BY_FOR_SO, DEFAULT_SORT_DIRECTION);

            // Search in Purchase Orders
            Page<PurchaseOrder> purchaseOrderPage =
                    poSearchStrategy.searchInPos(text, page, size, DEFAULT_SORT_BY_FOR_PO, DEFAULT_SORT_DIRECTION);

            // Combine the results
            List<PendingOrdersResponseInIC> combinedResults = new ArrayList<>();
            inventoryServiceHelper.fillWithSalesOrders(combinedResults, salesOrderPage.getContent());
            inventoryServiceHelper.fillWithPurchaseOrders(combinedResults, purchaseOrderPage.getContent());

            // Return with correct pagination metadata
            long totalResults = salesOrderPage.getTotalElements() + purchaseOrderPage.getTotalElements();
            log.info("IcPendingOrders: searchInPendingOrders() returning {} results", combinedResults.size());
            return new PaginatedResponse<>(
                    new PageImpl<>(
                            combinedResults,
                            PageRequest.of(page, size),
                            totalResults
                    )
            );
        } catch (Exception e) {
            log.error("IC (searchInPendingOrders): Error searching pending orders", e);
            throw new ServiceException("Failed to search pending orders", e);
        }
    }

}
