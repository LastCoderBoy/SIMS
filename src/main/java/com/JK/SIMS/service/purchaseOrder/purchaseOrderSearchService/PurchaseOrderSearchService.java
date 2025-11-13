package com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderQueryService.PurchaseOrderQueryService;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderFilterLogic.PoFilterStrategy;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderSearchLogic.PoSearchStrategy;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class PurchaseOrderSearchService {
    private final GlobalServiceHelper globalServiceHelper;
    private final PurchaseOrderServiceHelper poServiceHelper;
    private final PurchaseOrderQueryService queryService;
    // Search & Filters
    private final PoSearchStrategy icPoSearchStrategy;
    private final PoSearchStrategy omPoSearchStrategy;
    private final PoFilterStrategy filterWaitingPurchaseOrders;
    private final PoFilterStrategy filterPurchaseOrders;

    // IC context - search pending only
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchPending(
            String text, int page, int size, String sortBy, String sortDirection) {
        globalServiceHelper.validatePaginationParameters(page, size);

        if (text == null || text.trim().isEmpty()) {
            // Delegate to query service
            return queryService.getAllPendingPurchaseOrders(page, size, sortBy, sortDirection);
        }

        Page<PurchaseOrder> result = icPoSearchStrategy.searchInPos(text, page, size, sortBy, sortDirection);
        return poServiceHelper.transformToPaginatedSummaryView(result);
    }

    // IC context - filter pending only
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterPending(PurchaseOrderStatus status, ProductCategories category,
                                                                     String sortBy, String sortDirection, int page, int size) {
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc")
                ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));

        return filterWaitingPurchaseOrders.filterPurchaseOrders(category, status, pageable);
    }

    // OM context - search all
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchAll(String text, int page, int size, String sortBy, String sortDirection) {
        globalServiceHelper.validatePaginationParameters(page, size);

        if (text == null || text.trim().isEmpty()) {
            return queryService.getAllPurchaseOrders(page, size, sortBy, sortDirection);
        }

        Page<PurchaseOrder> result = omPoSearchStrategy.searchInPos(text, page, size, sortBy, sortDirection);
        return poServiceHelper.transformToPaginatedSummaryView(result);
    }

    // OM context - filter all
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterAll(ProductCategories category, PurchaseOrderStatus status, String sortBy,
                                                                 String sortDirection, int page, int size) {
        Sort sort = sortDirection.equalsIgnoreCase("desc")
                ? Sort.by(sortBy).descending()
                : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);

        return filterPurchaseOrders.filterPurchaseOrders(category, status, pageable);
    }
}
