package com.JK.SIMS.repository.PM_repo;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PM_models.ProductStatus;
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
            "LOWER(pm.name) LIKE LOWER(CONCAT('%', :text, '%'))")
    Optional<List<ProductsForPM>> searchByProductIDAndCategoryAndNameAndLocation (String text);


    @Query("SELECT pm FROM ProductsForPM pm WHERE " +
            "(:category IS NULL OR pm.category = :category) AND " +
            "(:status IS NULL OR pm.status = :status)")
    List<ProductsForPM> findByFilters(
            @Param("category") ProductCategories category,
            @Param("status") ProductStatus status
    );


    // This one is Sort By Category
    public List<ProductsForPM> findAllByCategory(ProductCategories category);

    // This one is Sort By Status
    public List<ProductsForPM> findAllByStatus(ProductStatus status);
}
