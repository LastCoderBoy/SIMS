package com.JK.SIMS.service.utilities.purchaseOrderSearchLogic;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component("omPoSearchStrategy") // Must match field name
@Slf4j
public class OmPoSearchStrategy implements PoSearchStrategy {
    private final PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    public OmPoSearchStrategy(PurchaseOrderRepository purchaseOrderRepository) {
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PurchaseOrder> searchInPos(String text, int page, int size, String sortBy, String sortDirection) {
        try {
            Sort sort = sortDirection.equalsIgnoreCase("desc")
                    ? Sort.by(sortBy).descending()
                    : Sort.by(sortBy).ascending();
            Pageable pageable = PageRequest.of(page, size, sort);
            log.info("OmPo searchInPos(): search text: {} is provided", text);
            return purchaseOrderRepository.searchOrders(text.trim().toLowerCase(), pageable);
        } catch (DataAccessException dae) {
            log.error("OmPo (searchProduct): Database error while searching products", dae);
            throw new DatabaseException("Error occurred while searching products");
        } catch (Exception e) {
            log.error("OmPo (searchProduct): Unexpected error while searching products", e);
            throw new ServiceException("Internal Service occurred, please contact the administration");
        }
    }
}
