package com.JK.SIMS.repository.InventoryControl_repo;

import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.inventoryData.dtos.InventoryMetrics;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.reportAnalyticsMetrics.inventoryHealth.InventoryReportMetrics;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface IC_repository extends JpaRepository<InventoryControlData, String>, JpaSpecificationExecutor<InventoryControlData> {

    @Modifying // Tells Spring this is a modifying query (not a SELECT)
    @Transactional // Ensures the delete operation is atomic (either all or none)
    @Query("DELETE FROM InventoryControlData ic WHERE ic.pmProduct.productID = :productId")
    void deleteByProduct_ProductID(@Param("productId") String productId);

    @Query("""
        SELECT new com.JK.SIMS.models.inventoryData.dtos.InventoryMetrics(
            COUNT(*),
            COUNT(CASE WHEN i.currentStock <= i.minLevel AND i.status != 'INVALID' THEN 1 ELSE NULL END)
        )
        FROM InventoryControlData i
    """)
    InventoryMetrics getInventoryMetrics();


    @Query("SELECT ic FROM InventoryControlData ic WHERE " +
            "LOWER(ic.SKU) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.location) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.productID) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.category) LIKE CONCAT('%', :text, '%')")
    Page<InventoryControlData> searchProducts(String text, Pageable pageable);

    @Query("SELECT ic FROM InventoryControlData ic WHERE ic.status != 'INVALID' AND  ic.currentStock <= ic.minLevel AND " +
            "LOWER(ic.SKU) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.location) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.productID) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.name) LIKE CONCAT('%', :text, '%') OR " +
            "LOWER(ic.pmProduct.category) LIKE CONCAT('%', :text, '%')")
    Page<InventoryControlData> searchInLowStockProducts(@Param("text") String text, Pageable pageable);



    Page<InventoryControlData> findByStatus(InventoryDataStatus status, Pageable pageable);

    @Query("SELECT i FROM InventoryControlData i WHERE i.currentStock <= :level")
    Page<InventoryControlData> findByStockLevel(@Param("level") Integer level, Pageable pageable);

    Optional<InventoryControlData> findBySKU(String sku);

    Optional<InventoryControlData> findByPmProduct_ProductID(String productId);

    void deleteBySKU(String sku);

    @Query("SELECT i FROM InventoryControlData i WHERE i.status != 'INVALID' AND  i.currentStock <= i.minLevel")
    List<InventoryControlData> getLowStockItems();

    @Query("SELECT i FROM InventoryControlData i WHERE i.status != 'INVALID' AND  i.currentStock <= i.minLevel")
    List<InventoryControlData> getLowStockItems(Sort sort);

    @Query("SELECT i FROM InventoryControlData i WHERE i.status != 'INVALID' AND  i.currentStock <= i.minLevel")
    Page<InventoryControlData> getLowStockItems(Pageable pageable);

    @Query("SELECT i FROM InventoryControlData i " +
            "JOIN i.pmProduct p " +
            "WHERE i.status != 'INVALID' AND i.currentStock <= i.minLevel " +
            "AND (:category IS NULL OR p.category = :category)")
    Page<InventoryControlData> getLowStockItemsByCategory(@Param("category") ProductCategories category, Pageable pageable);


    // Find InventoryControlData by product ID with a pessimistic write lock
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM InventoryControlData i WHERE i.pmProduct.productID = :productId")
    InventoryControlData findByProductIdWithLock(@Param("productId") String productId);


    // ******* Report & Analytics related methods *******

    @Query("""
    SELECT new com.JK.SIMS.models.reportAnalyticsMetrics.InventoryReportMetrics(
        CAST(COALESCE(SUM(ic.currentStock * pm.price), 0.0) AS BigDecimal),
        CAST(COALESCE(SUM(ic.currentStock), 0) AS long),
        CAST(COALESCE(SUM(ic.reservedStock), 0) AS long),
        CAST(COALESCE(SUM(CASE WHEN ic.currentStock > ic.reservedStock
            THEN ic.currentStock - ic.reservedStock
            ELSE 0 END), 0) AS long),
        COUNT(CASE WHEN ic.currentStock = 0 THEN 1 END),
        COUNT(CASE WHEN ic.currentStock <= ic.minLevel AND ic.currentStock > 0 AND ic.status!='INVALID' THEN 1 END),
        COUNT(CASE WHEN ic.currentStock > ic.minLevel THEN 1 END)
        )
    FROM InventoryControlData ic
    JOIN ic.pmProduct pm
    WHERE ic.status != 'INVALID'
""")
    InventoryReportMetrics getInventoryReportMetrics();
}
