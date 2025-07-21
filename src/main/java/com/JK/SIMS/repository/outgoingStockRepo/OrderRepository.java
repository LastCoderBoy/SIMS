package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.outgoing.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findTopByOrderByIdDesc();


    @Query(value = "SELECT * FROM orders WHERE order_reference LIKE CONCAT(:pattern, '%') ORDER BY order_reference DESC LIMIT 1",
            nativeQuery = true)
    Optional<Order> findLatestOrderByReferencePattern(@Param("pattern") String pattern);

}
