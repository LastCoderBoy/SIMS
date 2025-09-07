package com.JK.SIMS.service.purchaseOrderService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.repository.PO_repo.purchaseOrderSpec.PurchaseOrderSpecification;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.purchaseOrderService.searchLogic.PoStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;



@Service
public class PoService {
    private static final Logger logger = LoggerFactory.getLogger(PoService.class);
    private final PurchaseOrderRepository purchaseOrderRepository;

    private final PurchaseOrderServiceHelper poServiceHelper;
    private final PoStrategy poStrategy;

    public PoService(PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderServiceHelper poServiceHelper,
                     @Qualifier("icPoSearchStrategy") PoStrategy poStrategy) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poServiceHelper = poServiceHelper;
        this.poStrategy = poStrategy;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> getAllIncomingStockRecords(int page, int size) {
        try {
            Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
            Page<PurchaseOrder> entityResponse = purchaseOrderRepository.findAll(pageable);
            PaginatedResponse<PurchaseOrderResponseDto> dtoResponse =
                    poServiceHelper.transformToPaginatedDtoResponse(entityResponse);
            logger.info("PO (getAllIncomingStock): Returning {} paginated data", dtoResponse.getContent().size());
            return dtoResponse;
        }catch (DataAccessException da){
            throw new DatabaseException("PO (getAllIncomingStock): Database error", da);
        }catch (Exception e){
            logger.error("PO (getAllIncomingStock): Service error occurred: {}", e.getMessage(), e);
            throw new ServiceException("PO (getAllIncomingStock): Service error occurred", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> searchInPendingProduct(String text, int page, int size) {
        return poStrategy.searchInPos(text, page, size);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<PurchaseOrderResponseDto> filterIncomingStock(PurchaseOrderStatus status, ProductCategories category,
                                                                           String sortBy, String sortDirection, int page, int size){
        // Parse sort direction
        Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        // Create sort
        Sort sort = Sort.by(direction, sortBy);
        Pageable pageable = PageRequest.of(page, size, sort);

        Specification<PurchaseOrder> spec = Specification
                .where(PurchaseOrderSpecification.hasStatus(status))
                .and(PurchaseOrderSpecification.hasProductCategory(category));

        Page<PurchaseOrder> filterResult = purchaseOrderRepository.findAll(spec, pageable);
        return poServiceHelper.transformToPaginatedDtoResponse(filterResult);
    }
}
