package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;


/**
 * Centralized Query Service for Inventory Controller read operations
 * Purpose:
 * - Provides reusable read-only methods for Inventory queries
 * - Prevents code duplication across services
 * - Follows Single Responsibility Principle
 *
 * @author LastCoderBoy
 * @since 2025-01-12
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InventoryQueryService {

    private final GlobalServiceHelper globalServiceHelper;
    private final IC_repository icRepository;

    @Transactional(readOnly = true)
    public InventoryControlData getInventoryDataBySku(String sku) {
        return icRepository.findBySKU(sku)
                .orElseThrow(() -> new ResourceNotFoundException("IC (updateProduct): No product with SKU " + sku + " found"));
    }

    @Transactional(readOnly = true)
    public Optional<InventoryControlData> getInventoryProductByProductId(String productId) {
        return icRepository.findByPmProduct_ProductID(productId);
    }

    @Transactional(readOnly = true)
    public Page<InventoryControlData> getAllPagedLowStockItems (String sortBy, String sortDirection, int page, int size) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
            return icRepository.getLowStockItems(pageable);
        }catch (DataAccessException da){
            log.error("getAllPagedLowStockItems(): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve products due to database error", da);
        }catch (Exception e){
            log.error("getAllPagedLowStockItems(): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error, please contact the administration", e);
        }
    }

    @Transactional(readOnly = true)
    public List<InventoryControlData> getAllLowStockProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            return icRepository.getLowStockItems(sort);
        } catch (DataAccessException da) {
            log.error("getAllLowStockProducts(): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            log.error("getAllLowStockProducts(): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("Internal Service Error", e);
        }
    }
}
