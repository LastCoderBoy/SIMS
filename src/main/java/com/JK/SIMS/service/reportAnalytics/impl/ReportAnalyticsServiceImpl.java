package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.DashboardMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.TimeRange;
import com.JK.SIMS.models.reportAnalyticsMetrics.financial.FinancialOverviewMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.OrderSummaryMetrics;
import com.JK.SIMS.service.InventoryServices.damageLossService.DamageLossService;
import com.JK.SIMS.service.InventoryServices.damageLossService.damageLossQueryService.DamageLossQueryService;
import com.JK.SIMS.service.InventoryServices.poService.POServiceInInventory;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import com.JK.SIMS.service.productManagementService.queryService.ProductQueryService;
import com.JK.SIMS.service.reportAnalytics.FinancialOverviewService;
import com.JK.SIMS.service.reportAnalytics.InventoryHealthService;
import com.JK.SIMS.service.reportAnalytics.OrderSummaryService;
import com.JK.SIMS.service.reportAnalytics.ReportAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportAnalyticsServiceImpl implements ReportAnalyticsService {

    private final ProductQueryService productQueryService;
    private final SalesOrderService salesOrderService;
    private final POServiceInInventory poServiceInInventory;
    private final DamageLossQueryService damageLossQueryService;

    // =========== Report Analytics Services ===========
    private final InventoryHealthService inventoryHealthService;
    private final OrderSummaryService orderSummaryService;
    private final FinancialOverviewService financialOverviewService;

    @Override
    @Transactional(readOnly = true)
    public DashboardMetrics getMainDashboardMetrics() {
        ReportProductMetrics totalActiveInactiveProducts = productQueryService.countTotalActiveInactiveProducts();
        BigDecimal inventoryStockValue = inventoryHealthService.calculateInventoryStockValueAtRetail();
        Long inProgressSalesOrders = salesOrderService.countInProgressSalesOrders();
        Long totalValidPoSize = poServiceInInventory.getTotalValidPoSize();
        Long totalDamagedProducts = damageLossQueryService.countTotalDamagedProducts();

        return DashboardMetrics.builder()
                .totalActiveProducts(totalActiveInactiveProducts.getTotalActiveProducts())
                .totalInactiveProducts(totalActiveInactiveProducts.getTotalInactiveProducts())
                .totalInventoryStockValue(inventoryStockValue)
                .totalInProgressSalesOrders(inProgressSalesOrders)
                .totalValidPurchaseOrders(totalValidPoSize)
                .totalDamagedProducts(totalDamagedProducts)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public InventoryReportMetrics getInventoryHealth() {
        log.info("RA (getInventoryHealth): Delegating to InventoryHealthService");
        return inventoryHealthService.getInventoryHealth();
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialOverviewMetrics getFinancialOverview(TimeRange timeRange) {
        log.info("RA (getFinancialOverview): Delegating to FinancialOverviewService with TimeRange");
        return financialOverviewService.getFinancialOverview(timeRange);
    }

    @Override
    @Transactional(readOnly = true)
    public FinancialOverviewMetrics getFinancialOverview(LocalDate startDate, LocalDate endDate) {
        log.info("RA (getFinancialOverview): Delegating to FinancialOverviewService");
        return financialOverviewService.getFinancialOverview(startDate, endDate);
    }

    @Override
    @Transactional(readOnly = true)
    public OrderSummaryMetrics getOrderSummary() {
        log.info("RA (getOrderSummary): Delegating to OrderSummaryService");
        return orderSummaryService.getOrderSummaryMetrics();
    }
}
