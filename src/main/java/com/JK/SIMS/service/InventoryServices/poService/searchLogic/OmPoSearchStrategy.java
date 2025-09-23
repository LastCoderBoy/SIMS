package com.JK.SIMS.service.InventoryServices.poService.searchLogic;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.poService.PurchaseOrderServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

@Component
public class OmPoSearchStrategy implements PoStrategy {
    private static final Logger logger = LoggerFactory.getLogger(OmPoSearchStrategy.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderServiceHelper poServiceHelper;
    @Autowired
    public OmPoSearchStrategy(PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderServiceHelper poServiceHelper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poServiceHelper = poServiceHelper;
    }

    @Override
    public PaginatedResponse<PurchaseOrderResponseDto> searchInPos(String text, int page, int size) {
        if (text == null || text.isEmpty()) {
            logger.warn("OmPo (searchProduct): Search text is null or empty");
            throw new ValidationException("IcPo (searchProduct): Search text cannot be empty");
        }
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<PurchaseOrder> searchEntityResponse = purchaseOrderRepository.searchOrders(text.trim().toLowerCase(), pageable);
            return poServiceHelper.transformToPaginatedDtoResponse(searchEntityResponse);
        } catch (DataAccessException dae) {
            logger.error("OmPo (searchProduct): Database error while searching products", dae);
            throw new DatabaseException("OmPo (searchProduct): Error occurred while searching products");
        } catch (Exception e) {
            logger.error("OmPo (searchProduct): Unexpected error while searching products", e);
            throw new ServiceException("OmPo (searchProduct): Error occurred while searching products");
        }
    }
}
