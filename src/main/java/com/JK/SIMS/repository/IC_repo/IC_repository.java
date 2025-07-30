package com.JK.SIMS.repository.IC_repo;

import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.InventoryMetrics;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IC_repository extends JpaRepository<InventoryData, String> {

    @Modifying // Tells Spring this is a modifying query (not a SELECT)
    @Transactional // Ensures the delete operation is atomic (either all or none)
    @Query("DELETE FROM InventoryData ic WHERE ic.pmProduct.productID = :productId")
    void deleteByProduct_ProductID(@Param("productId") String productId);

    @Query("""
        SELECT new com.JK.SIMS.models.IC_models.InventoryMetrics(
            COUNT(*),
            COUNT(CASE WHEN i.currentStock <= i.minLevel AND i.status != 'INVALID' THEN 1 ELSE NULL END)
        )
        FROM InventoryData i
    """)
    InventoryMetrics getInventoryMetrics();


    @Query("SELECT ic FROM InventoryData ic WHERE " +
            "LOWER(ic.SKU) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.location) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.productID) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.category) LIKE CONCAT('%', :text, '%')"
    )
    Page<InventoryData> searchProducts(String text, Pageable pageable);


    Page<InventoryData> findByStatus(InventoryDataStatus status, Pageable pageable);

    @Query("SELECT i FROM InventoryData i WHERE i.currentStock <= :level")
    Page<InventoryData> findByStockLevel(@Param("level") Integer level, Pageable pageable);


    @Query("SELECT i FROM InventoryData i WHERE " +
            "LOWER(i.status) LIKE LOWER(CONCAT('%', :term, '%')) OR " +
            "LOWER(i.pmProduct.category) LIKE LOWER(CONCAT('%', :term, '%'))")
    Page<InventoryData> findByGeneralFilter(@Param("term") String filterBy, Pageable pageable);


    Optional<InventoryData> findBySKU(String sku);

    Optional<InventoryData> findByPmProduct_ProductID(String productId);

    void deleteBySKU(String sku);

    @Query("SELECT i FROM InventoryData i WHERE i.status != 'INVALID' AND  i.currentStock <= i.minLevel")
    List<InventoryData> getLowStockItems();

    // Find InventoryData by product ID with a pessimistic write lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryData i WHERE i.pmProduct.productID = :productId")
    InventoryData findByProductIdWithLock(@Param("productId") String productId);

}
