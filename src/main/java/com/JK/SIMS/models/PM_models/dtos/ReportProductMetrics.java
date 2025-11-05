package com.JK.SIMS.models.PM_models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class ReportProductMetrics {
    private Long totalActiveProducts;
    private Long totalInactiveProducts;
}
