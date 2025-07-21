package com.JK.SIMS.service.InventoryControl_service.outgoingStockService;

import com.JK.SIMS.exceptionHandler.*;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.outgoing.*;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.outgoingStockRepo.OrderItemRepository;
import com.JK.SIMS.repository.outgoingStockRepo.OrderRepository;
import com.JK.SIMS.service.InventoryControl_service.InventoryControlService;
import com.JK.SIMS.service.productManagement_service.ProductManagementService;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
@AllArgsConstructor
public class OutgoingStockService {
    private static final Logger logger = LoggerFactory.getLogger(OutgoingStockService.class);

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductManagementService pmService;
    private final InventoryControlService icService;

    @Transactional
    public ApiResponse createOrder(OrderRequestDto orderRequestDto) {
        try {
            validateOrderRequest(orderRequestDto);

            String orderReference = generateOrderReference(LocalDate.now());
            Order order = createOrderEntity(orderRequestDto, orderReference);


            // Process each item: Validate, Decrement Stock, Add to Order
            for (OrderItemDto itemDto : orderRequestDto.getItems()) {
                ProductsForPM product = pmService.findProductById(itemDto.getProductId()); // Throws ResourceNotFound if not found

                // Validate Product Status
                if (product.getStatus() != ProductStatus.ACTIVE) {
                    throw new ValidationException("Product '" + product.getName() + "' (ID: " + product.getProductID() + ") is not active and cannot be ordered.");
                }

                // Get Inventory Data with Pessimistic Lock
                InventoryData inventory = icService.getInventoryProductByProductIdWithLock(product.getProductID());


                if (inventory.getCurrentStock() < itemDto.getQuantity()) {
                    throw new InsufficientStockException("Not enough stock for product '" + product.getName() + "' (ID: " + product.getProductID() + "). Available: " + inventory.getCurrentStock() + ", Ordered: " + itemDto.getQuantity());
                }

                // Decrement Stock level
                int updatedStockLevel = inventory.getCurrentStock() - itemDto.getQuantity();
                icService.updateStockLevels( // will save the inventory changes
                        inventory,
                        Optional.of(updatedStockLevel),
                        Optional.empty());

                // Create and Add OrderItem to Order
                OrderItem orderItem = createOrderItem(product, itemDto.getQuantity());
                order.addOrderItem(orderItem); // Sets the bidirectional link
            }

            orderRepository.save(order);
            logger.info("OS (createOrder): Order created successfully with reference ID: {}", orderReference);
            return new ApiResponse(true, "Order created successfully and it is under PROCESSING status");
        }catch (DataIntegrityViolationException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("Order reference collision detected, retrying...");
            }
            throw e;
        }
        catch (ValidationException ve) {
            logger.error("OS (createOrder): Validation error - {}", ve.getMessage());
            throw ve; // global exception will handle it
        } catch (InsufficientStockException ie){
            logger.error("OS (createOrder): Insufficient exception, requesting more than available");
            throw ie;
        } catch (ResourceNotFoundException rnfe) {
            logger.error("OS (createOrder): Product not found - {}", rnfe.getMessage());
            throw rnfe; // global exception will handle it
        } catch (DataAccessException dae) {
            logger.error("OS (createOrder): Database error - {}", dae.getMessage());
            throw new DatabaseException("OS (createOrder): Failed to save the order to the database.", dae);
        } catch (Exception e) {
            logger.error("OS (createOrder): Unexpected error - {}", e.getMessage());
            throw new ServiceException("OS (createOrder): An unexpected error occurred while creating the order.", e);
        }
    }

    private void validateOrderRequest(OrderRequestDto orderRequestDto) {
        if (orderRequestDto == null) {
            throw new ValidationException("OS (validateOrderRequest): Order request cannot be null.");
        }

        if (orderRequestDto.getItems() == null || orderRequestDto.getItems().isEmpty()) {
            throw new ValidationException("OS (validateOrderRequest): Order items list cannot be empty.");
        }

        if (orderRequestDto.getDestination() == null || orderRequestDto.getDestination().trim().isEmpty()) {
            throw new ValidationException("OS (validateOrderRequest): Destination cannot be empty.");
        }

        // Check for duplicate products in the same order
        Set<String> productIds = new HashSet<>();
        for (OrderItemDto item : orderRequestDto.getItems()) {
            if (!productIds.add(item.getProductId())) {
                throw new ValidationException("OS (validateOrderRequest): Duplicate product found in order: " + item.getProductId());
            }
        }

        logger.debug("OS (validateOrderRequest): Order request validation passed");
    }

    private Order createOrderEntity(OrderRequestDto orderRequestDto, String orderReference) {
        Order order = new Order();
        order.setOrderReference(orderReference);
        order.setDestination(orderRequestDto.getDestination().trim());
        order.setStatus(OrderStatus.PROCESSING);

        logger.debug("OS (createOrderEntity): Created order entity with reference {}", orderReference);
        return order;
    }

    private OrderItem createOrderItem(ProductsForPM product, Integer orderedQuantity) {
        BigDecimal unitPriceAtOrder = product.getPrice();
        BigDecimal lineItemPrice = unitPriceAtOrder.multiply(BigDecimal.valueOf(orderedQuantity));

        return new OrderItem(orderedQuantity, product, lineItemPrice);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public synchronized String generateOrderReference(LocalDate now) {
        try {
            // Get the latest order reference for today with pessimistic lock
            Optional<Order> lastOrderOpt = orderRepository.findLatestOrderByReferencePattern(
                    "ORD-" + now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")) + "%");

            DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            String todayPrefix = "ORD-" + now.format(dateFormatter) + "-";

            if (lastOrderOpt.isPresent()) {
                Order lastOrder = lastOrderOpt.get();
                String lastOrderReference = lastOrder.getOrderReference();

                // Validate that the reference belongs to today
                if (lastOrderReference.startsWith(todayPrefix)) {
                    String[] splitReference = lastOrderReference.split("-");

                    if (splitReference.length >= 5) {
                        try {
                            int lastOrderNumber = Integer.parseInt(splitReference[4]);
                            int nextOrderNumber = lastOrderNumber + 1;
                            String paddedOrderNumber = String.format("%03d", nextOrderNumber);
                            return todayPrefix + paddedOrderNumber;

                        } catch (NumberFormatException e) {
                            logger.error("OS (generateOrderReference): Invalid order number format in reference: {}", lastOrderReference);
                            // Fall through to generate first order of the day
                        }
                    }
                }
            }

            // First order of the day or fallback case
            return todayPrefix + "001";

        } catch (Exception e) {
            logger.error("OS (generateOrderReference): Error generating order reference - {}", e.getMessage());
            throw new ServiceException("Failed to generate unique order reference", e);
        }
    }
}
