package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.InventoryMetrics;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface IC_repository extends JpaRepository<InventoryData, String> {

    @Modifying // Tells Spring this is a modifying query (not a SELECT)
    @Transactional // Ensures the delete operation is atomic (either all or none)
    @Query("DELETE FROM InventoryData ic WHERE ic.product.productID = :productId")
    void deleteByProduct_ProductID(@Param("productId") String productId);

    @Query("""
        SELECT new com.JK.SIMS.models.IC_models.InventoryMetrics(
            COUNT(*),
            COUNT(CASE WHEN i.currentStock <= i.minLevel THEN 1 END),
            COUNT(CASE WHEN i.status = 'INCOMING' THEN 1 END),
            COUNT(CASE WHEN i.status = 'OUTGOING' THEN 1 END),
            COUNT(CASE WHEN i.status = 'DAMAGE_LOSS' THEN 1 END)
        )
        FROM InventoryData i
    """)
    InventoryMetrics getInventoryMetrics();


    @Query("SELECT ic FROM InventoryData ic WHERE " +
            "LOWER(ic.SKU) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.location) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.status) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.product.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.product.category) LIKE CONCAT('%', :text, '%')"
    )
    List<InventoryData> searchProducts(String text);


    Page<InventoryData> findByStatus(InventoryDataStatus status, Pageable pageable);

    Page<InventoryData> findByLocationContainingIgnoreCase(String location, Pageable pageable);

    @Query("SELECT i FROM InventoryData i WHERE i.currentStock <= :level")
    Page<InventoryData> findByStockLevel(@Param("level") Integer level, Pageable pageable);

    @Query("SELECT i FROM InventoryData i WHERE " +
            "LOWER(i.SKU) LIKE %:term% OR " +
            "LOWER(i.location) LIKE %:term% OR " +
            "LOWER(i.status) LIKE %:term% OR " +
            "LOWER(i.product.name) LIKE %:term%")
    Page<InventoryData> findByGeneralSearch(@Param("term") String searchTerm, Pageable pageable);

}
