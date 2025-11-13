package com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderFilterLogic;

import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderSearchService.purchaseOrderFilterLogic.filterSpecification.PurchaseOrderSpecification;
import jakarta.annotation.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.annotation.Transactional;

public abstract class AbstractPoFilterStrategy implements PoFilterStrategy {

    protected final Logger logger = LoggerFactory.getLogger(getClass());
    protected final PurchaseOrderServiceHelper poServiceHelper;
    protected final PurchaseOrderRepository purchaseOrderRepository;

    protected AbstractPoFilterStrategy(PurchaseOrderServiceHelper poServiceHelper, PurchaseOrderRepository purchaseOrderRepository) {
        this.poServiceHelper = poServiceHelper;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    /**
     * Defines the base specification for the filter strategy (e.g., pending or all).
     * @return Specification or null if no base filter applies.
     */
    protected @Nullable abstract Specification<PurchaseOrder> baseSpecType();

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> filterPurchaseOrders(ProductCategories category, PurchaseOrderStatus status, Pageable pageable) {
        try {
            Specification<PurchaseOrder> spec = Specification.where(baseSpecType());

            if (status != null) {
                spec = spec.and(PurchaseOrderSpecification.hasStatus(status));
            }
            if (category != null) {
                spec = spec.and(PurchaseOrderSpecification.hasProductCategory(category));
            }

            Page<PurchaseOrder> filterResult = purchaseOrderRepository.findAll(spec, pageable);
            return poServiceHelper.transformToPaginatedSummaryView(filterResult);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("Invalid parameters: " + iae.getMessage());
        } catch (Exception e) {
            logger.error("{}: Error filtering orders - {}", getClass().getSimpleName(), e.getMessage());
            throw new ServiceException("Failed to filter orders", e);
        }
    }
}
