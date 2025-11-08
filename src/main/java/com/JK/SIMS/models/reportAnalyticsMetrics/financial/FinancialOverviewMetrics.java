package com.JK.SIMS.models.reportAnalyticsMetrics.financial;

import com.JK.SIMS.models.reportAnalyticsMetrics.TimeRange;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FinancialOverviewMetrics {
    private BigDecimal totalRevenue;        // of OrderItems repo
    private BigDecimal avgOrderValue;       // of Sales Order
    private BigDecimal lossValue;
    private BigDecimal profitMargin;        // Percentage
    private BigDecimal netProfit;

    // Period information
    private LocalDate periodStart;
    private LocalDate periodEnd;
    private TimeRange timeRange;
    private Long daysInPeriod;

    public FinancialOverviewMetrics(BigDecimal totalRevenue, BigDecimal avgOrderValue, BigDecimal lossValue,
                                    BigDecimal profitMargin, BigDecimal netProfit) {
        this.totalRevenue = totalRevenue;
        this.avgOrderValue = avgOrderValue;
        this.lossValue = lossValue;
        this.profitMargin = profitMargin;
        this.netProfit = netProfit;
    }

    public BigDecimal getLossPercentage() {
        return totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? lossValue.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
    }

    public BigDecimal getAvgRevenuePerDay() {
        if (daysInPeriod == null || daysInPeriod == 0) {
            return BigDecimal.ZERO;
        }
        return totalRevenue.divide(BigDecimal.valueOf(daysInPeriod), 2, java.math.RoundingMode.HALF_UP);
    }
}
