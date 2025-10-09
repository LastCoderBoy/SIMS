package com.JK.SIMS.service.purchaseOrderSearchLogic;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Component
public class IcPoSearchStrategy implements PoSearchStrategy {
    private static final Logger logger = LoggerFactory.getLogger(IcPoSearchStrategy.class);

    private final PurchaseOrderRepository purchaseOrderRepository;
    private final PurchaseOrderServiceHelper poServiceHelper;

    public IcPoSearchStrategy(PurchaseOrderRepository purchaseOrderRepository, PurchaseOrderServiceHelper poServiceHelper) {
        this.purchaseOrderRepository = purchaseOrderRepository;
        this.poServiceHelper = poServiceHelper;
    }

    @Override
    @Transactional(readOnly = true)
    public PaginatedResponse<SummaryPurchaseOrderView> searchInPos(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Optional<String> inputText = Optional.ofNullable(text);
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                logger.info("IcPo (searchProduct): Search text provided. Searching for orders with text '{}'", text);
                Pageable pageable = PageRequest.of(page, size, sort);
                Page<PurchaseOrder> searchEntityResponse =
                        purchaseOrderRepository.searchInPendingOrders(text.trim().toLowerCase(), pageable);
                return poServiceHelper.transformToPaginatedSummaryView(searchEntityResponse);
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
