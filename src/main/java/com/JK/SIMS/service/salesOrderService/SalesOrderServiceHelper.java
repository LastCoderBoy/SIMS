package com.JK.SIMS.service.salesOrderService;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItemResponseDto;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class SalesOrderServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(SalesOrderServiceHelper.class);

    public SalesOrderResponseDto convertToOrderResponseDto(SalesOrder salesOrder) {
        try {
            List<OrderItemResponseDto> itemDtos = salesOrder.getItems().stream()
                    .map(this::convertToOrderItemResponseDto)
                    .collect(Collectors.toList());

            // Calculate total amount and total items
            BigDecimal totalAmount = salesOrder.getItems().stream()
                    .map(OrderItem::getOrderPrice)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            return new SalesOrderResponseDto(
                    salesOrder.getId(),
                    salesOrder.getOrderReference(),
                    salesOrder.getDestination(),
                    salesOrder.getCustomerName(),
                    salesOrder.getStatus(),
                    salesOrder.getOrderDate(),
                    salesOrder.getLastUpdate(),
                    totalAmount,
                    itemDtos
            );
        } catch (Exception e) {
            logger.error("OS (convertToOrderResponseDto): Error converting salesOrder {} - {}",
                    salesOrder.getId(), e.getMessage());
            throw new ServiceException("Failed to convert salesOrder to response DTO", e);
        }
    }

    private OrderItemResponseDto convertToOrderItemResponseDto(OrderItem item) {
        try {
            ProductsForPM product = item.getProduct();

            return new OrderItemResponseDto(
                    item.getId(),
                    product.getProductID(),
                    product.getName(),
                    product.getCategory(),
                    item.getQuantity(),
                    item.getOrderPrice().divide(BigDecimal.valueOf(item.getQuantity()), 2, RoundingMode.HALF_UP), // Unit price
                    item.getOrderPrice() // Total price for this line item
            );
        } catch (Exception e) {
            logger.error("OS (convertToOrderItemResponseDto): Error converting order item {} - {}",
                    item.getId(), e.getMessage());
            throw new ServiceException("Failed to convert order item to response DTO", e);
        }
    }
}
