package com.JK.SIMS.service.stockMovementService;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.stockMovements.StockMovement;
import com.JK.SIMS.models.stockMovements.StockMovementType;
import com.JK.SIMS.models.stockMovements.StockMovementReferenceType;
import com.JK.SIMS.repository.stockMovement.StockMovementRepository;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class StockMovementService {
    private static final Logger logger = LoggerFactory.getLogger(StockMovementService.class);
    private final StockMovementRepository stockMovementRepository;

    @Autowired
    public StockMovementService(StockMovementRepository stockMovementRepository) {
        this.stockMovementRepository = stockMovementRepository;
    }

    @Transactional
    public void logMovement(ProductsForPM product, StockMovementType type, Integer quantity,
                            String referenceId, StockMovementReferenceType referenceType, String createdBy) {
        logger.info("Logging stock movement: productId={}, type={}, quantity={}, referenceId={}, referenceType={}",
                product.getProductID(), type, quantity, referenceId, referenceType);
        StockMovement movement = new StockMovement(product, quantity, type, referenceId, referenceType, createdBy);
        stockMovementRepository.save(movement);
    }
}
