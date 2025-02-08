package com.JK.SIMS.repository.PM_repo;

import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.ProductCategories;
import com.JK.SIMS.models.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Repository;

import javax.swing.text.html.Option;
import java.util.List;
import java.util.Optional;

@Repository
public interface PM_repository extends JpaRepository<ProductsForPM, String> {
    @Query(value = "SELECT pm.productID  FROM ProductsForPM pm ORDER BY CAST(SUBSTRING(pm.productID, 4) AS INTEGER) DESC Limit 1")
    public Optional<String> getLastId();

    @Query("SELECT pm FROM ProductsForPM pm WHERE " +
            "LOWER(pm.category) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.productID) LIKE LOWER(CONCAT('%', :text, '%')) OR " +
            "LOWER(pm.name) LIKE LOWER(CONCAT('%', :text, '%'))")
    public Optional<List<ProductsForPM>> searchByProductIDAndCategoryAndName (String text);

    // This one is Sort By Category
    public List<ProductsForPM> findAllByCategory(ProductCategories category);

    // This one is Sort By Status
    public List<ProductsForPM> findAllByStatus(ProductStatus status);
}
