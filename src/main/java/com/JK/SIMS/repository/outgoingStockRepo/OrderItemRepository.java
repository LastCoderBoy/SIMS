package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.outgoing.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
}
