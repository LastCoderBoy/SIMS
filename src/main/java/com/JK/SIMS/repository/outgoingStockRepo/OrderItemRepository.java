package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
