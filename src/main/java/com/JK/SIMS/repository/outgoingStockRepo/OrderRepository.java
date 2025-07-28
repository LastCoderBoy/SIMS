package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.outgoing.Order;
import com.JK.SIMS.models.IC_models.outgoing.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findTopByOrderByIdDesc();


    @Query(value = "SELECT * FROM orders WHERE order_reference LIKE CONCAT(:pattern, '%') ORDER BY order_reference DESC LIMIT 1",
            nativeQuery = true)
    Optional<Order> findLatestOrderByReferencePattern(@Param("pattern") String pattern);

    Page<Order> findByStatus(OrderStatus status, Pageable pageable);

    @Query("""
    SELECT DISTINCT o FROM Order o
    JOIN o.items i
    JOIN i.product p
    WHERE (:status IS NULL OR o.status = :status)
      AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(p.category) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.destination) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.orderReference) LIKE LOWER(CONCAT('%', :text, '%')))
""")
    Page<Order> searchOutgoingStock(@Param("text") String text, @Param("status") OrderStatus status, Pageable pageable);
}
