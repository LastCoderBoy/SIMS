package com.JK.SIMS.repository.SalesOrder_Repo;

import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SalesOrderRepository extends JpaRepository<SalesOrder, Long>, JpaSpecificationExecutor<SalesOrder> {

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

    @Query(value = "SELECT COUNT(*) FROM sales_order WHERE status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_SHIPPED')", nativeQuery = true)
    Long getOutgoingValidStockSize();

    @Query("SELECT so FROM SalesOrder so WHERE so.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_SHIPPED')")
    Page<SalesOrder> findAllWaitingSalesOrders(Pageable pageable);

    @Query("SELECT so FROM SalesOrder so WHERE so.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_SHIPPED') " +
            "AND so.estimatedDeliveryDate < FUNCTION('DATE_ADD', CURRENT_DATE, 2)")
    Page<SalesOrder> findAllUrgentSalesOrders(Pageable pageable);

    @Query(value = "SELECT SUM(DATEDIFF(estimated_delivery_date, order_date)) " +
            "FROM sales_order WHERE status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_SHIPPED')",
            nativeQuery = true)
    long calculateTotalDeliveryDate();

    @Query("""
    SELECT DISTINCT o FROM SalesOrder o
    JOIN o.items i
    JOIN i.product p
    WHERE o.status IN ('PARTIALLY_APPROVED', 'PENDING', 'PARTIALLY_SHIPPED')
      AND (
        LOWER(p.name) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(p.category) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.destination) LIKE LOWER(CONCAT('%', :text, '%'))
       OR LOWER(o.orderReference) LIKE LOWER(CONCAT('%', :text, '%'))
       )
""")
    Page<SalesOrder> searchInOutgoingSalesOrders(String text, Pageable pageable);
}
