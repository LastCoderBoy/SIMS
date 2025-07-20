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
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

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
            if (orderRequestDto == null || orderRequestDto.getItems() == null || orderRequestDto.getItems().isEmpty()) {
                throw new ValidationException("OS (createOrder): The input request or items list cannot be empty.");
            }

            String orderReference = generateOrderReference(LocalDate.now());

            // Create the object
            Order order = new Order();
            order.setOrderReference(orderReference);
            order.setDestination(orderRequestDto.getDestination());
            order.setStatus(OrderStatus.PROCESSING);

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
                BigDecimal unitPriceAtOrder = product.getPrice();
                BigDecimal lineItemPrice = unitPriceAtOrder.multiply(BigDecimal.valueOf(itemDto.getQuantity())); // Total for this item line
                OrderItem orderItem = new OrderItem(itemDto.getQuantity(), product, lineItemPrice);

                order.addOrderItem(orderItem); // Sets the bidirectional link
            }

            orderRepository.save(order);
            logger.info("OS (createOrder): Order created successfully with reference ID: {}", orderReference);
            return new ApiResponse(true, "Order created successfully and it is under PROCESSING status");
        } catch (ValidationException ve) {
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

    private String generateOrderReference(LocalDate now) {
        Optional<Order> lastOrderOpt = orderRepository.findTopByOrderByIdDesc();

        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

        if (lastOrderOpt.isPresent()) {
            Order lastOrder = lastOrderOpt.get();
            String lastOrderReference = lastOrder.getOrderReference(); // e.g. ORD-2024-07-20-001
            String[] splitReference = lastOrderReference.split("-");

            // Safely reconstruct the date
            String dateStr = splitReference[1] + "-" + splitReference[2] + "-" + splitReference[3];
            LocalDate lastOrderDate = LocalDate.parse(dateStr, dateFormatter);

            if (now.isEqual(lastOrderDate)) {
                int orderNumber = Integer.parseInt(splitReference[4]) + 1;
                String paddedOrderNumber = String.format("%03d", orderNumber);
                return "ORD-" + now.format(dateFormatter) + "-" + paddedOrderNumber;
            }
        }

        // First order of the day
        return "ORD-" + now.format(dateFormatter) + "-001";
    }
}
