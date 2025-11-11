package com.JK.SIMS.service.InventoryServices.inventoryQueryService;

import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryQueryService {

    private final IC_repository icRepository;

    @Transactional(readOnly = true)
    public InventoryControlData getInventoryDataBySku(String sku) throws BadRequestException {
        return icRepository.findBySKU(sku)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "IC (updateProduct): No product with SKU " + sku + " found"));
    }

    @Transactional(readOnly = true)
    public Optional<InventoryControlData> getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId);
    }
}
