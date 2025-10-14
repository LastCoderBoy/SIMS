package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.salesOrder.SalesOrder;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.IC_models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.OrderItemRequestDto;
import com.JK.SIMS.models.IC_models.salesOrder.orderItem.dtos.OrderItemResponseDto;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@Slf4j
public class SalesOrderServiceHelper {

    public SalesOrderResponseDto convertToSalesOrderResponseDto(SalesOrder salesOrder) {
        try {
            List<OrderItemResponseDto> itemDtos = salesOrder.getItems().stream()
                    .map(this::convertToOrderItemResponseDto)
                    .collect(Collectors.toList());

            // Calculate total amount and total items
            BigDecimal totalAmount = calculateTotalAmount(salesOrder.getItems());

            return new SalesOrderResponseDto(
                    salesOrder.getId(),
                    salesOrder.getOrderReference(),
                    salesOrder.getDestination(),
                    salesOrder.getCustomerName(),
                    salesOrder.getStatus(),
                    salesOrder.getOrderDate(),
                    salesOrder.getEstimatedDeliveryDate(),
                    salesOrder.getDeliveryDate(),
                    salesOrder.getLastUpdate(),
                    totalAmount,
                    itemDtos
            );
        } catch (Exception e) {
            log.error("OS (convertToSalesOrderResponseDto): Error converting salesOrder {} - {}",
                    salesOrder.getId(), e.getMessage());
            throw new ServiceException("Failed to convert salesOrder to response DTO", e);
        }
    }

    private BigDecimal calculateTotalAmount(List<OrderItem> items) {
        return items.stream().map(OrderItem::getOrderPrice).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Integer totalSalesOrderQuantity(List<OrderItemResponseDto> orderedItems){
        return orderedItems.stream().mapToInt(OrderItemResponseDto::getQuantity).sum();
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
                    item.getProduct().getPrice(), // Unit price
                    item.getOrderPrice() // Total price for this line item
            );
        } catch (Exception e) {
            log.error("SoHelper convertToOrderItemResponseDto(): Error converting order item {} - {}",
                    item.getId(), e.getMessage());
            throw new ServiceException("Failed to convert order item to response DTO");
        }
    }

    public PaginatedResponse<SummarySalesOrderView> transformToSummarySalesOrderView(Page<SalesOrder> salesOrderPage){
         Page<SummarySalesOrderView> test = salesOrderPage.map(this::convertToSummarySalesOrderView);
         return new PaginatedResponse<>(test);
    }

    private SummarySalesOrderView convertToSummarySalesOrderView(SalesOrder order){
        return new SummarySalesOrderView(order);
    }

    public void validateSalesOrderItems(List<OrderItemRequestDto> requestedOrderItems) {
        // Check for duplicate products in the same order
        Set<String> productIds = new HashSet<>();
        for (OrderItemRequestDto item : requestedOrderItems) {
            if (!productIds.add(item.getProductId())) {
                throw new ValidationException("OM-SO validateSoRequestForCreate(): Duplicate product found in order: " + item.getProductId());
            }
        }

        log.debug("OM-SO validateSoRequestForCreate(): SalesOrder request validation passed");
    }

    public void validateSoRequestForUpdate(SalesOrderRequestDto salesOrderRequestDto) {
        if (salesOrderRequestDto == null) {
            log.debug("OM-SO validateSoRequestForUpdate(): SalesOrder request is null. Nothing to validate.");
            throw new ValidationException("OM-SO validateSoRequestForUpdate(): SalesOrder request cannot be null.");
        }
    }
}
