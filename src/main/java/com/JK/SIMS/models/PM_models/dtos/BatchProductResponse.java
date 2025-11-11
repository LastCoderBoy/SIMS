package com.JK.SIMS.models.PM_models.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class BatchProductResponse {
    private int totalRequested;
    private int successCount;
    private int failureCount;
    private List<String> successfulProductIds;
    private List<ProductError> errors;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    public static class ProductError {
        private int index;
        private ProductManagementRequest product;
        private String errorMessage;
    }
}
