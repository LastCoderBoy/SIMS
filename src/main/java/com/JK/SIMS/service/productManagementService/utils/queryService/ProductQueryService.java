package com.JK.SIMS.service.productManagementService.utils.queryService;


import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Shared query service for product-related read operations
 * Purpose: Break circular dependencies between ProductManagementService and other services
 * Contains ONLY read operations - no business logic or state changes
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class ProductQueryService {

    private final PM_repository pmRepository;

    /**
     * Find product by ID - throws exception if not found
     * Used by: SalesOrderService, PurchaseOrderService, InventoryService
     */
    @Transactional(readOnly = true)
    public ProductsForPM findById(String productId) {
        return pmRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found with ID: " + productId));
    }

    @Transactional(readOnly = true)
    public ProductsForPM isProductFinalized(String productId) throws ResourceNotFoundException, ValidationException {
        ProductsForPM product = findById(productId);
        if (product.isInInvalidStatus()) {
            throw new ValidationException("Product is not for sale and cannot be ordered. Please update the status in the PM section first.");
        }
        return product;
    }


    /**
     * Helper method for Report & Analytics section
     * @return ReportProductMetrics object containing counts of active and inactive products
     */
    @Transactional(readOnly = true)
    public ReportProductMetrics countTotalActiveInactiveProducts(){
        try {
            return pmRepository.countProductMetricsByStatus(
                    ProductStatus.getActiveStatuses(),
                    ProductStatus.getInactiveStatuses()
            );
        } catch (DataAccessException e) {
            log.error("PM (totalProductsByStatus): Failed to retrieve product metrics due to database error: {}", e.getMessage());
            throw new DatabaseException("PM (totalProductsByStatus): Failed to retrieve product metrics", e);
        } catch (Exception e) {
            log.error("PM (totalProductsByStatus): Failed to retrieve product metrics: {}", e.getMessage());
            throw new ServiceException("Internal Service Error occurred", e);
        }
    }
}
