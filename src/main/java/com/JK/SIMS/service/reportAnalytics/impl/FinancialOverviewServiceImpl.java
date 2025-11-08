package com.JK.SIMS.service.reportAnalytics.impl;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.reportAnalyticsMetrics.financial.FinancialOverviewMetrics;
import com.JK.SIMS.models.reportAnalyticsMetrics.TimeRange;
import com.JK.SIMS.repository.damageLossRepo.DamageLossRepository;
import com.JK.SIMS.repository.salesOrderRepo.OrderItemRepository;
import com.JK.SIMS.repository.salesOrderRepo.SalesOrderRepository;
import com.JK.SIMS.service.reportAnalytics.FinancialOverviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
@Slf4j
@RequiredArgsConstructor
public class FinancialOverviewServiceImpl implements FinancialOverviewService {

    private final OrderItemRepository orderItemRepository;
    private final SalesOrderRepository salesOrderRepository;
    private final DamageLossRepository damageLossRepository;

    @Override
    @Transactional(readOnly = true)
    public FinancialOverviewMetrics getFinancialOverview(LocalDate startDate, LocalDate endDate) {
        try {
            if (startDate == null || endDate == null) {
                throw new ValidationException("Start date and end date are required for custom range");
            }
            if (startDate.isAfter(endDate)) {
                throw new ValidationException("Start date must be before or equal to end date");
            }

            log.info("RA-FO (getFinancialOverview): Fetching custom financial overview from {} to {}",
                    startDate, endDate);

            return calculateFinancialMetrics(startDate, endDate, TimeRange.CUSTOM);
        } catch (ValidationException e) {
            log.error("RA-FO (getFinancialOverview) Custom Type: Validation error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("RA-FO (getFinancialOverview): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch financial overview", e);
        } catch (Exception e) {
            log.error("RA-FO (getFinancialOverview): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to fetch financial overview", e);
        }

    }

    @Override
    @Transactional(readOnly = true)
    public FinancialOverviewMetrics getFinancialOverview(TimeRange timeRange) {
        try{
            if(timeRange == null){
                throw new ValidationException("Time range cannot be null");
            }
            if(timeRange.equals(TimeRange.CUSTOM)){
                throw new ValidationException("Please provide start and end dates");
            }

            LocalDate startDate = timeRange.getStartDate();
            LocalDate endDate = timeRange.getEndDate();
            log.info("RA-FO (getFinancialOverview): Fetching {} financial overview from {} to {}",
                    timeRange.getDisplayName(), startDate, endDate);

            return calculateFinancialMetrics(startDate, endDate, timeRange);
        } catch (ValidationException e) {
            log.error("RA-FO (getFinancialOverview): Validation error - {}", e.getMessage());
            throw e;
        } catch (DataAccessException e) {
            log.error("RA-FO (getFinancialOverview): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to fetch financial overview", e);
        } catch (Exception e) {
            log.error("RA-FO (getFinancialOverview): Unexpected error - {}", e.getMessage(), e);
            throw new ServiceException("Failed to fetch financial overview", e);
        }
    }

    private FinancialOverviewMetrics calculateFinancialMetrics(LocalDate startDate,
                                                               LocalDate endDate,
                                                               TimeRange timeRange) {
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);

        // Fetch financial data
        BigDecimal totalRevenue = orderItemRepository.calculateTotalRevenue(start, end);
        Long totalCompletedSalesOrders = countCompletedSalesOrders(start, end);
        BigDecimal avgOrderValue = (totalCompletedSalesOrders == 0)
                ? BigDecimal.valueOf(0)
                : totalRevenue.divide(BigDecimal.valueOf(totalCompletedSalesOrders), 2, java.math.RoundingMode.HALF_UP);
        BigDecimal lossValue = damageLossRepository.sumLossValueBetween(start, end);

        // Calculate Profit Margins
        // Since we don't store the Purchase Price of the Product,
        // I just set the estimated cost percentage to 30% of the total revenue
        BigDecimal costPercentage = BigDecimal.valueOf(0.30);
        BigDecimal estimatedCost = totalRevenue.multiply(costPercentage);
        BigDecimal grossProfitBeforeLoss = totalRevenue.subtract(estimatedCost);
        BigDecimal netProfitAfterLoss = grossProfitBeforeLoss.subtract(lossValue);

        // Calculate profit margin percentage
        BigDecimal profitMargin = totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? netProfitAfterLoss.divide(totalRevenue, 4, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;

        long daysInPeriod = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        return FinancialOverviewMetrics.builder()
                .totalRevenue(totalRevenue)
                .avgOrderValue(avgOrderValue)
                .lossValue(lossValue)
                .profitMargin(profitMargin)
                .netProfit(netProfitAfterLoss)
                .periodStart(startDate)
                .periodEnd(endDate)
                .timeRange(timeRange)
                .daysInPeriod(daysInPeriod)
                .build();
    }

    private Long countCompletedSalesOrders(LocalDateTime startDate, LocalDateTime endDate) {
        try{
            return salesOrderRepository.countCompletedSalesOrdersBetween(startDate, endDate);
        } catch (DataAccessException e) {
            log.error("RA-FO (countCompletedSalesOrders): Database error - {}", e.getMessage(), e);
            throw new DatabaseException("Failed to count completed sales orders", e);
        }
    }
}
