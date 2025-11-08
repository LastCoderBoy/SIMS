package com.JK.SIMS.controller.reportAnalyticsController;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.reportAnalyticsMetrics.DashboardMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.TimeRange;
import com.JK.SIMS.models.reportAnalyticsMetrics.financial.FinancialOverviewMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.orderOverview.OrderSummaryMetrics;
import com.JK.SIMS.service.reportAnalytics.ReportAnalyticsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/analytics")
public class ReportAnalyticsController {

    private final ReportAnalyticsService reportAnalyticsService;

    /**
     * Get main dashboard with all key metrics
     * Accessible by: Admin, Manager
     */
    @GetMapping("/dashboard")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<DashboardMetrics>> getMainDashboard() {
        log.info("RA-Controller: Main dashboard requested");

        DashboardMetrics metrics = reportAnalyticsService.getMainDashboardMetrics();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Main dashboard retrieved successfully", metrics)
        );
    }

    /**
     * Get detailed inventory health metrics
     * Accessible by authorized users
     */
    @GetMapping("/inventory-health")
    public ResponseEntity<ApiResponse<InventoryReportMetrics>> getInventoryHealth() {
        log.info("RA-Controller: Inventory health requested");

        InventoryReportMetrics metrics = reportAnalyticsService.getInventoryHealth();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Inventory health retrieved successfully", metrics)
        );
    }

    /**
     * Get financial overview by predefined time range
     * Accessible by: Admin, Manager
     */
    @GetMapping("/financial-overview")
    @PreAuthorize("hasAnyAuthority('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<ApiResponse<FinancialOverviewMetrics>> getFinancialOverview(
            @RequestParam(required = false, defaultValue = "MONTHLY") TimeRange range,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        log.info("RA-Controller: Financial overview requested with range: {}", range);

        FinancialOverviewMetrics metrics;

        // If custom range, use provided dates
        if (range == TimeRange.CUSTOM) {
            metrics = reportAnalyticsService.getFinancialOverview(startDate, endDate);
        } else {
            // Use predefined range
            metrics = reportAnalyticsService.getFinancialOverview(range);
        }

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Financial overview retrieved successfully", metrics)
        );
    }

    /**
     * Get order summary (both sales and purchase orders)
     * Accessible by: Admin, Manager
     */
    @GetMapping("/order-summary")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<OrderSummaryMetrics>> getOrderSummary() {
        log.info("RA-Controller: Order summary requested");
        OrderSummaryMetrics orderSummaryMetrics = reportAnalyticsService.getOrderSummary();

        return ResponseEntity.ok(
                new ApiResponse<>(true, "Order summary retrieved successfully", orderSummaryMetrics)
        );
    }
}
