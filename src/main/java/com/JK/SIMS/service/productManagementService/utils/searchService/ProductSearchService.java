package com.JK.SIMS.service.productManagementService.utils.searchService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import com.JK.SIMS.service.generalUtils.GlobalServiceHelper;
import com.JK.SIMS.service.productManagementService.utils.queryService.ProductQueryService;
import com.JK.SIMS.service.productManagementService.utils.searchService.specification.ProductSpecification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@Slf4j
@RequiredArgsConstructor
public class ProductSearchService {
    private final ProductQueryService productQueryService;
    private final GlobalServiceHelper globalServiceHelper;

    private final PM_repository pmRepository;

    @Transactional(readOnly = true)
    public Page<ProductsForPM> searchProduct(String text, String sortBy, String sortDirection, int page, int size) {
        try {
            if (text != null && !text.trim().isEmpty()) {
                Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);
                return pmRepository.searchProducts(text.trim().toLowerCase(), pageable);
            }
            log.info("SearchService-searchProduct(): No search text provided. Retrieving first page with default size.");
            return productQueryService.getAllProducts(sortBy, sortDirection, page, size);
        }catch (DataAccessException da) {
            log.error("SearchService-searchProduct(): Database error: {}", da.getMessage());
            throw new DatabaseException("Internal Database error", da);
        } catch (Exception e) {
            log.error("SearchService-searchProduct(): Failed to retrieve products: {}", e.getMessage());
            throw new ServiceException("Internal Service Error", e);
        }
    }

    @Transactional(readOnly = true)
    public Page<ProductsForPM> filterProducts(String filter, String sortBy, String sortDirection, int page, int size) {
        try {
            Pageable pageable = globalServiceHelper.preparePageable(page, size, sortBy, sortDirection);

            if (filter == null || filter.trim().isEmpty()) {
                return pmRepository.findAll(pageable);
            }

            Specification<ProductsForPM> spec;
            String[] filterParts = filter.split(":");

            if (filterParts.length == 2) {
                String field = filterParts[0].toLowerCase();
                String value = filterParts[1];

                spec = switch (field) {
                    case "category" -> {
                        ProductCategories category = ProductCategories.valueOf(value.toUpperCase());
                        yield ProductSpecification.hasCategory(category);
                    }
                    case "location" -> ProductSpecification.hasLocation(value);
                    case "price" -> ProductSpecification.hasPriceLessThanOrEqual(new BigDecimal(value));
                    case "status" -> {
                        ProductStatus status = ProductStatus.valueOf(value.toUpperCase());
                        yield ProductSpecification.hasStatus(status);
                    }
                    default -> null;
                };
            } else {
                // General filter across multiple fields
                spec = ProductSpecification.generalFilter(filter.trim());
            }

            return spec != null ? pmRepository.findAll(spec, pageable) : pmRepository.findAll(pageable);

        } catch (IllegalArgumentException iae) {
            log.error("PM (filterProducts): Invalid filter value: {}", iae.getMessage());
            throw new ValidationException("PM (filterProducts): Invalid filter value");
        } catch (DataAccessException da) {
            log.error("PM (filterProducts): Database error: {}", da.getMessage());
            throw new DatabaseException("PM (filterProducts): Database error", da);
        } catch (Exception e) {
            log.error("PM (filterProducts): Failed to filter products: {}", e.getMessage());
            throw new ServiceException("PM (filterProducts): Failed to filter products", e);
        }
    }
}
