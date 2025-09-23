package com.JK.SIMS.service.InventoryServices.poService.searchLogic;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderResponseDto;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.poService.PurchaseOrderServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
public class IcPoSearchStrategy implements PoStrategy {
    private static final Logger logger = LoggerFactory.getLogger(IcPoSearchStrategy.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderServiceHelper poServiceHelper;

    public IcPoSearchStrategy(PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderServiceHelper poServiceHelper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poServiceHelper = poServiceHelper;
    }

    @Override
    public PaginatedResponse<PurchaseOrderResponseDto> searchInPos(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable(text);
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                logger.info("IcPo (searchProduct): Search text provided. Searching for orders with text '{}'", text);
                Pageable pageable = PageRequest.of(page, size, Sort.by("product.name"));
                Page<PurchaseOrder> searchEntityResponse = purchaseOrderRepository.searchInPendingOrders(text.trim().toLowerCase(), pageable);
                return poServiceHelper.transformToPaginatedDtoResponse(searchEntityResponse);
            }
            return new PaginatedResponse<>();
        } catch (DataAccessException dae) {
            logger.error("IcPo (searchProduct): Database error while searching products", dae);
            throw new DatabaseException("IcPo (searchProduct): Error occurred while searching products");
        } catch (Exception e) {
            logger.error("IcPo (searchProduct): Unexpected error while searching products", e);
            throw new ServiceException("IcPo (searchProduct): Error occurred while searching products");
        }
    }
}
