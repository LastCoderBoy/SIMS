package com.JK.SIMS.models.PM_models.dtos;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BatchProductRequest {
    private List<ProductManagementRequest> products;
}
