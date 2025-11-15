package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventorySearchService;


import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

// Will be having Search and Filter business logics
// used by: LowStock, TotalItems services
@Service
@Slf4j
@RequiredArgsConstructor
public class InventorySearchService {

    private final GlobalServiceHelper globalServiceHelper;
    private final InventoryQueryService inventoryQueryService;
    private final IC_repository icRepository;

    @Transactional(readOnly = true)
    public Page<InventoryControlData> searchInLowStockProducts(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
                return icRepository.searchInLowStockProducts(inputText.get().trim().toLowerCase(), pageable);
            }
            log.info("LowStockService (searchProduct): No search text provided. Retrieving first page with default size.");
            return inventoryQueryService.getAllPagedLowStockItems(sortBy, sortDirection, page, size);
        } catch (DataAccessException e) {
            log.error("LowStockService (searchProduct): Database error: {}", e.getMessage(), e);
            throw new DatabaseException("Database error", e);
        } catch (Exception e) {
            log.error("LowStockService (searchProduct): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("Failed to retrieve products, internal service error.", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<InventoryControlData> filterLowStockProducts(ProductCategories category, String sortBy,
                                                                              String sortDirection, int page, int size) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            return icRepository.getLowStockItemsByCategory(category, pageable);
        } catch (IllegalArgumentException iae) {
            log.error("filterLowStockProducts(): Invalid filter values: {}", iae.getMessage());
            throw new ValidationException("Invalid filter values, please check your request: " + iae.getMessage());
        } catch (DataAccessException da) {
            log.error("filterLowStockProducts(): Database error: {}", da.getMessage(), da);
            throw new DatabaseException("Internal Database error", da.getCause());
        } catch (Exception e) {
            log.error("filterLowStockProducts(): Unexpected error occurred: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error", e);
        }
    }
}
