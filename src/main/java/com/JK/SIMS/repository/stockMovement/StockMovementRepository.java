package com.JK.SIMS.repository.stockMovement;

import com.JK.SIMS.models.stockMovements.StockMovement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StockMovementRepository extends JpaRepository<StockMovement, Long> {
}
