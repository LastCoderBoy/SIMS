package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.InventoryData;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IC_repository extends JpaRepository<InventoryData, String> {

    @Modifying // Tells Spring this is a modifying query (not a SELECT)
    @Transactional // Ensures the delete operation is atomic (either all or none)
    @Query("DELETE FROM InventoryData ic WHERE ic.product.productID = :productId")
    void deleteByProduct_ProductID(@Param("productId") String productId);
}
