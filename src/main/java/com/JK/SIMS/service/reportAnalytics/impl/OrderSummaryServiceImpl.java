package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.OrderSummaryMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.PurchaseOrderSummary;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.SalesOrderSummary;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.reportAnalytics.OrderSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class OrderSummaryServiceImpl implements OrderSummaryService {

    private final SalesOrderRepository salesOrderRepository;
    private final PurchaseOrderRepository purchaseOrderRepository;

    @Override
    @Transactional(readOnly = true)
    public OrderSummaryMetrics getOrderSummaryMetrics() {
        try{
            log.info("RA-OS (getOrderSummary): Fetching order summary");

            return OrderSummaryMetrics.builder()
                    .salesOrderSummary(getSalesOrderSummary())
                    .purchaseOrderSummary(getPurchaseOrderSummary())
                    .build();
        } catch (DataAccessException e) {
            log.error("RA-OS (getOrderSummary): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch order summary", e);
        } catch (Exception e) {
            log.error("RA-OS (getOrderSummary): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to fetch order summary", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public SalesOrderSummary getSalesOrderSummary() {
        try{
            return salesOrderRepository.getSalesOrderSummaryMetrics();
        } catch (DataAccessException da){
            log.error("RA-OS (getSalesOrderSummary): Failed to retrieve metrics due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve metrics due to database error", da);
        } catch (Exception e) {
            log.error("RA-OS (getSalesOrderSummary): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred", e);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public PurchaseOrderSummary getPurchaseOrderSummary(){
        try {
            return purchaseOrderRepository.getPurchaseOrderSummaryMetrics();
        } catch (DataAccessException da){
            log.error("RA-PO (getPurchaseOrderSummary): Failed to retrieve metrics due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve metrics due to database error", da);
        } catch (Exception e) {
            log.error("RA-PO (getPurchaseOrderSummary): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error occurred", e);
        }
    }
}
