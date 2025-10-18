package com.JK.SIMS.service.utilities.purchaseOrderSearchLogic;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
public class IcPoSearchStrategy implements PoSearchStrategy {
    private final PurchaseOrderRepository purchaseOrderRepository;

    public IcPoSearchStrategy(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> searchInPos(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            log.info("IcPo (searchProduct): Search text provided. Searching for orders with text '{}'", text);
            Pageable pageable = PageRequest.of(page, size, sort);
            return purchaseOrderRepository.searchInPendingOrders(text.trim().toLowerCase(), pageable);
        } catch (DataAccessException dae) {
            log.error("IcPo (searchProduct): Database error while searching products", dae);
            throw new DatabaseException("IcPo (searchProduct): Error occurred while searching products");
        } catch (Exception e) {
            log.error("IcPo (searchProduct): Unexpected error while searching products", e);
            throw new ServiceException("IcPo (searchProduct): Error occurred while searching products");
        }
    }
}
