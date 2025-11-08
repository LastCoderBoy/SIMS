package com.JK.SIMS.repository.salesOrderRepo;

import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

    @Query("""
        SELECT COALESCE(SUM(oi.quantity * oi.product.price), 0)
        FROM OrderItem oi
        JOIN oi.salesOrder so
        WHERE so.orderDate BETWEEN :startDate AND :endDate
        AND so.status IN ('DELIVERED', 'COMPLETED')
    """)
    BigDecimal calculateTotalRevenue(@Param("startDate") LocalDateTime startDate,
                                     @Param("endDate") LocalDateTime endDate);
}
