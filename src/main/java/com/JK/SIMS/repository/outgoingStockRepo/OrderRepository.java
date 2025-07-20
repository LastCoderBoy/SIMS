package com.JK.SIMS.repository.outgoingStockRepo;

import com.JK.SIMS.models.IC_models.outgoing.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {

    Optional<Order> findTopByOrderByIdDesc();
}
