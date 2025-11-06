package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.DashboardMetrics;
import com.JK.SIMS.service.InventoryServices.damageLossService.DamageLossService;
import com.JK.SIMS.service.InventoryServices.poService.POServiceInInventory;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.orderManagementService.salesOrderService.SalesOrderService;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import com.JK.SIMS.service.reportAnalytics.InventoryHealthService;
import com.JK.SIMS.service.reportAnalytics.ReportAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class ReportAnalyticsServiceImpl implements ReportAnalyticsService {

    private final ProductManagementService pmService;
    private final InventoryHealthService inventoryHealthService;
    private final SalesOrderService salesOrderService;
    private final POServiceInInventory poServiceInInventory;
    private final PurchaseOrderService purchaseOrderService;
    private final DamageLossService damageLossService;

    @Override
    public DashboardMetrics getDashboardMetrics() {
        ReportProductMetrics totalActiveInactiveProducts = pmService.countTotalActiveInactiveProducts();
        BigDecimal inventoryStockValue = inventoryHealthService.getInventoryStockValue();
        Long inProgressSalesOrders = salesOrderService.countInProgressSalesOrders();
        Long totalValidPoSize = poServiceInInventory.getTotalValidPoSize();
        Long totalDamagedProducts = damageLossService.countTotalDamagedProducts();

        return DashboardMetrics.builder()
                .totalActiveInactivatedProducts(totalActiveInactiveProducts)
                .totalInventoryStockValue(inventoryStockValue)
                .totalInProgressSalesOrders(inProgressSalesOrders)
                .totalValidPurchaseOrders(totalValidPoSize)
                .totalDamagedProducts(totalDamagedProducts)
                .build();
    }
}
