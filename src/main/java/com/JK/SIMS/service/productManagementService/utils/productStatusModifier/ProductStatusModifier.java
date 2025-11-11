package com.JK.SIMS.service.productManagementService.utils.productStatusModifier;

import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.ProductManagement_repo.PM_repository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
@Slf4j
@RequiredArgsConstructor
public class ProductStatusModifier {

    private final PM_repository pmRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public void updateIncomingProductStatusInPm(ProductsForPM orderedProduct) {
        if (orderedProduct.getStatus() == ProductStatus.ON_ORDER) {
            orderedProduct.setStatus(ProductStatus.ACTIVE);
            pmRepository.save(orderedProduct);
        }
    }
}
