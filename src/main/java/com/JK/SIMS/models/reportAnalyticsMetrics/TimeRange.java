package com.JK.SIMS.models.reportAnalyticsMetrics;

import lombok.Getter;

import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

@Getter
public enum TimeRange {
    MONTHLY("Monthly", "Current month"),
    YEARLY("Yearly", "Current year"),
    ALL_TIME("All Time", "Since beginning"),
    CUSTOM("Custom", "Custom date range");

    private final String displayName;
    private final String description;

    TimeRange(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    /**
     * Calculate the start date based on time range
     */
    public LocalDate getStartDate() {
        LocalDate today = LocalDate.now();

        return switch (this) {
            case MONTHLY -> today.withDayOfMonth(1);
            case YEARLY -> today.withDayOfYear(1);
            case ALL_TIME -> LocalDate.of(2024, 1, 1);
            case CUSTOM -> null; // Must be provided by user
        };
    }

    /**
     * Calculate end date based on time range
     */
    public LocalDate getEndDate() {
        LocalDate today = LocalDate.now();

        return switch (this) {
            case MONTHLY -> today.with(TemporalAdjusters.lastDayOfMonth());
            case YEARLY -> today.with(TemporalAdjusters.lastDayOfYear());
            case ALL_TIME -> today;
            case CUSTOM -> null; // Must be provided by user
        };
    }
}
