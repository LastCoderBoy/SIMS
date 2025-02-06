package com.JK.SIMS.repository.PM_repo;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PM_repository extends JpaRepository<ProductsForPM, String> {
    @Query(value = "SELECT pm.productID  FROM ProductsForPM pm ORDER BY CAST(SUBSTRING(pm.productID, 4) AS INTEGER) DESC Limit 1")
    public Optional<String> getLastId();
}
