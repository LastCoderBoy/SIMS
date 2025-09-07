package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long> {

    Optional<SalesOrder> findTopByOrderByIdDesc();


    @Query(value = "SELECT * FROM sales_order WHERE order_reference LIKE CONCAT(:pattern, '%') ORDER BY order_reference DESC LIMIT 1",
            nativeQuery = true)
    Optional<SalesOrder> findLatestOrderByReferencePattern(@Param("pattern") String pattern);

    Page<SalesOrder> findByStatus(SalesOrderStatus status, Pageable pageable);

    @Query("""
    SELECT DISTINCT o FROM SalesOrder o
    JOIN o.items i
    JOIN i.product p
    WHERE (:status IS NULL OR o.status = :status)
      AND (LOWER(p.name) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(p.category) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.destination) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.orderReference) LIKE LOWER(CONCAT('%', :text, '%')))
""")
    Page<SalesOrder> searchOutgoingStock(@Param("text") String text, @Param("status") SalesOrderStatus status, Pageable pageable);

    @Query(value = "SELECT COUNT(*) FROM sales_order WHERE status IN ('PROCESSING', 'SHIPPED')", nativeQuery = true)
    Long getOutgoingValidStockSize();

    Page<SalesOrder> findAllWaitingSalesOrders(Pageable pageable, Sort sort);

}
