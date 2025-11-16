package com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventorySearchService;


import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.totalItemsService.filterLogic.InventorySpecification;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
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
            return inventoryQueryService.getAllLowStockProducts(sortBy, sortDirection, page, size);
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

            Specification<InventoryControlData> spec = Specification
                    .where(InventorySpecification.hasLowStock())
                    .and(InventorySpecification.hasProductCategory(category));

            return icRepository.findAll(spec, pageable);
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

    @Transactional(readOnly = true)
    public Page<InventoryControlData> searchAll(String text, String sortBy, String sortDirection, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
                // Search by SKU, Location, ID, Name, Category.
                return icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
            }
            log.info("SearchService-searchAll(): No search text provided. Retrieving first page with default size.");
            return inventoryQueryService.getAllInventoryProducts(sortBy, sortDirection, page, size);
        } catch (DataAccessException e) {
            log.error("SearchService-searchAll(): Database error: {}", e.getMessage(), e);
            throw new DatabaseException("Internal Database error", e);
        } catch (Exception e) {
            log.error("SearchService-searchAll(): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<InventoryControlData> filterAll(String filterBy, String sortBy, String sortDirection, int page, int size) {
        try {

            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            // Handle filtering
            Page<InventoryControlData> resultPage;
            if (filterBy != null && !filterBy.trim().isEmpty()) {
                String[] filterParts = filterBy.trim().split(":");
                if (filterParts.length == 2) {
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1];

                    resultPage = switch (field) {
                        case "status" -> {
                            InventoryDataStatus status = InventoryDataStatus.valueOf(value.toUpperCase());
                            yield icRepository.findByStatus(status, pageable);
                        }
                        case "stock" -> icRepository.findByStockLevel(Integer.parseInt(value), pageable);
                        default -> icRepository.findAll(pageable);
                    };
                } else {
                    boolean isStatusType = GlobalServiceHelper.isInEnum(filterBy.trim().toUpperCase(), InventoryDataStatus.class);
                    Specification<InventoryControlData> specification;
                    if(isStatusType){
                        InventoryDataStatus statusValue = InventoryDataStatus.valueOf(filterBy.trim().toUpperCase());
                        specification = Specification.where(InventorySpecification.hasStatus(statusValue));
                    }else {
                        // If not Category Type, the IllegalArgumentException will be thrown
                        ProductCategories categoryValue = ProductCategories.valueOf(filterBy.trim().toUpperCase());
                        specification = Specification.where(InventorySpecification.hasProductCategory(categoryValue));
                    }
                    resultPage = icRepository.findAll(specification, pageable);
                }
            } else {
                // Retrieve all if no filter is provided
                resultPage = icRepository.findAll(pageable);
            }

            return resultPage;
        } catch (IllegalArgumentException iae) {
            log.error("SearchService-filterAll() : Invalid filter parameter provided: {}", iae.getMessage());
            throw new ValidationException("Invalid filter parameter provided, please check your request: " + iae.getMessage());
        } catch (DataAccessException da) {
            log.error("SearchService-filterAll(): Database error while filtering by '{}': {}",
                    filterBy, da.getMessage(), da);
            throw new DatabaseException("Internal Database error", da.getCause());
        } catch (Exception e) {
            log.error("SearchService-filterAll(): Internal error while filtering by '{}': {})", filterBy, e.getMessage(), e);
            throw new ServiceException("Internal Service Error", e);
        }
    }
}
