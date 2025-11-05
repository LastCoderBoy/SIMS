package com.JK.SIMS.repository.ProductManagement_repo;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PM_repository extends JpaRepository<ProductsForPM, String> {
    @Query(value = "SELECT pm.productID  FROM ProductsForPM pm ORDER BY CAST(SUBSTRING(pm.productID, 4) AS INTEGER) DESC Limit 1")
    Optional<String> getLastId();

    @Query("SELECT pm FROM ProductsForPM pm WHERE " +
            "LOWER(pm.category) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.productID) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.location) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.status) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.name) LIKE LOWER(CONCAT('%', :text, '%'))")
    Page<ProductsForPM> searchProducts(String text, Pageable pageable);


    @Query("SELECT p FROM ProductsForPM p WHERE " +
            "LOWER(p.location) LIKE %:filter% OR " +
            "LOWER(p.category) LIKE %:filter% OR " +
            "LOWER(p.status) LIKE %:filter%")
    Page<ProductsForPM> findByGeneralFilter(@Param("filter") String filter, Pageable pageable);

    Page<ProductsForPM> findByCategory(ProductCategories category, Pageable pageable);

    Page<ProductsForPM> findByLocation(String value, Pageable pageable);

    @Query("SELECT p FROM ProductsForPM p WHERE p.price <= :price")
    Page<ProductsForPM> findByPriceLevel(int price, Pageable pageable);

    Page<ProductsForPM> findByStatus(ProductStatus status, Pageable pageable);

    @Query("""
        SELECT new com.JK.SIMS.models.PM_models.dtos.ReportProductMetrics(
            COUNT(CASE WHEN pm.status IN :activeStatuses THEN 1 END),
            COUNT(CASE WHEN pm.status IN :inactiveStatuses THEN 1 END)
        )
        FROM ProductsForPM pm
    """)
    ReportProductMetrics countProductMetricsByStatus(
            @Param("activeStatuses") List<ProductStatus> activeStatuses,
            @Param("inactiveStatuses") List<ProductStatus> inactiveStatuses
    );
}
