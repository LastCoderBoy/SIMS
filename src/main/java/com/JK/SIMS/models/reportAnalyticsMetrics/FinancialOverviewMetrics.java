package com.JK.SIMS.models.reportAnalyticsMetrics;

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
    private BigDecimal totalRevenue;
    private BigDecimal avgOrderValue;
    private BigDecimal lossValue;
    private BigDecimal profitMargin;        // Percentage
    private BigDecimal netProfit;

    // Period information
    private LocalDate periodStart;
    private LocalDate periodEnd;

    // Calculated fields
    public BigDecimal getGrossProfit() {
        return totalRevenue.subtract(lossValue);
    }

    public BigDecimal getLossPercentage() {
        return totalRevenue.compareTo(BigDecimal.ZERO) > 0
                ? lossValue.divide(totalRevenue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                : BigDecimal.ZERO;
    }
}
