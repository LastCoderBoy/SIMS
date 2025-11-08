package com.JK.SIMS.service.reportAnalytics;

import com.JK.SIMS.models.reportAnalyticsMetrics.TimeRange;
import com.JK.SIMS.models.reportAnalyticsMetrics.financial.FinancialOverviewMetrics;

import java.time.LocalDate;

public interface FinancialOverviewService {
    FinancialOverviewMetrics getFinancialOverview(LocalDate startDate, LocalDate endDate);
    FinancialOverviewMetrics getFinancialOverview(TimeRange timeRange);
}
