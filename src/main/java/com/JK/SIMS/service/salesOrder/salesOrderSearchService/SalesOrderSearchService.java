package com.JK.SIMS.service.salesOrder.salesOrderSearchService;

import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.service.salesOrder.salesOrderQueryService.SalesOrderQueryService;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderFilterLogic.SoFilterStrategy;
import com.JK.SIMS.service.salesOrder.salesOrderSearchService.salesOrderSearchLogic.SoSearchStrategy;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import com.JK.SIMS.service.generalUtils.SalesOrderServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class SalesOrderSearchService {
    private final GlobalServiceHelper globalServiceHelper;
    private final SalesOrderQueryService salesOrderQueryService;
    private final SalesOrderServiceHelper salesOrderServiceHelper;

    private final SoSearchStrategy icSoSearchStrategy;
    private final SoSearchStrategy omSoSearchStrategy;
    private final SoFilterStrategy filterWaitingSalesOrders;
    private final SoFilterStrategy filterSalesOrdersInOm;

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> searchPending(String text, int page, int size, String sortBy, String sortDir){
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if(text == null || text.trim().isEmpty()){
                log.warn("IC-SO (searchInWaitingSalesOrders): Search text is null or empty, returning all waiting orders.");
                return salesOrderQueryService.getAllOutgoingSalesOrders(page, size, "id", "asc");
            }
            Page<SalesOrder> salesOrderPage = icSoSearchStrategy.searchInSo(text, page, size, sortBy, sortDir);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException ie) {
            log.error("IC-SO (searchInWaitingSalesOrders): Invalid pagination parameters: {}", ie.getMessage());
            throw ie;
        } catch (Exception e) {
            log.error("IC-SO (searchInWaitingSalesOrders): Error searching orders - {}", e.getMessage());
            throw new ServiceException("Failed to search orders", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterPending(SalesOrderStatus statusValue, String optionDateValue, LocalDate startDate,
                                                                  LocalDate endDate, int page, int size, String sortBy, String sortDirection) {
        try{
            // Validate and prepare the pageable
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            // Filter the orders
            Page<SalesOrder> salesOrderPage =
                    filterWaitingSalesOrders.filterSalesOrders(statusValue, optionDateValue, startDate, endDate, pageable);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException e) {
            log.error("IC-SO filterSalesOrders(): Invalid filter parameters: {}", e.getMessage(), e);
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("IC-SO filterSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> searchAll(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            if(text == null || text.trim().isEmpty()){
                log.info("OM-SO searchInSalesOrders(): No search text provided, returning all orders");
                return salesOrderQueryService.getAllSummarySalesOrders(sortBy, sortDirection, page, size);
            }
            Page<SalesOrder> salesOrderPage = omSoSearchStrategy.searchInSo(text, page, size, sortBy, sortDirection);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (Exception e) {
            log.error("OM-SO searchInSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<SummarySalesOrderView> filterAll(SalesOrderStatus statusValue, String optionDateValue,
                                                              LocalDate startDate, LocalDate endDate,
                                                              int page, int size, String sortBy, String sortDirection){
        try{
            // Validate and prepare the pageable
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            // Filter the orders
            Page<SalesOrder> salesOrderPage =
                    filterSalesOrdersInOm.filterSalesOrders(statusValue, optionDateValue, startDate, endDate, pageable);
            return salesOrderServiceHelper.transformToSummarySalesOrderView(salesOrderPage);
        } catch (IllegalArgumentException e) {
            log.error("OM-SO filterSalesOrders(): Invalid filter parameters: {}", e.getMessage(), e);
            throw new ValidationException("Invalid filter parameters: " + e.getMessage());
        } catch (Exception e) {
            log.error("OM-SO filterSalesOrders(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred: ", e);
        }
    }

}
