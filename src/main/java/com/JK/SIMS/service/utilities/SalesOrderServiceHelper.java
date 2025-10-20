package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.salesOrder.SalesOrder;
import com.JK.SIMS.models.salesOrder.SalesOrderStatus;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderRequestDto;
import com.JK.SIMS.models.salesOrder.dtos.SalesOrderResponseDto;
import com.JK.SIMS.models.salesOrder.dtos.views.SummarySalesOrderView;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItem;
import com.JK.SIMS.models.salesOrder.orderItem.OrderItemStatus;
import com.JK.SIMS.models.salesOrder.orderItem.dtos.OrderItemRequestDto;
import com.JK.SIMS.models.salesOrder.orderItem.dtos.OrderItemResponseDto;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
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

    public BigDecimal calculateTotalAmount(List<OrderItem> items) {
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
                    item.getStatus(),
                    item.getQuantity(),
                    item.getApprovedQuantity(),
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

    public void updateOrderItemStatus(OrderItem orderItem, int approvedQuantity) {
        if ( approvedQuantity < orderItem.getQuantity()) {
            orderItem.setStatus(OrderItemStatus.PARTIALLY_APPROVED);
            log.info("OrderProcessor updateOrderStatus(): Updated status of OrderItem {} - productID: {} to PARTIALLY_APPROVED", orderItem.getId(), orderItem.getProduct().getProductID());
        } else{
            orderItem.setStatus(OrderItemStatus.APPROVED);
            log.info("OrderProcessor updateOrderStatus(): Updated status of OrderItem {} - productID: {} to APPROVED", orderItem.getId(), orderItem.getProduct().getProductID());
        }
    }

    // Keep in Pending if all items are not approved yet
    public void updateSalesOrderStatus(SalesOrder salesOrder) {
        boolean allApproved = allItemsFulfilled(salesOrder);
        boolean anyApproved = salesOrder.getItems().stream()
                .anyMatch(item -> item.getStatus() == OrderItemStatus.APPROVED);

        if (allApproved) {
            salesOrder.setStatus(SalesOrderStatus.APPROVED);
        } else if (anyApproved) {
            salesOrder.setStatus(SalesOrderStatus.PARTIALLY_APPROVED);
        } else {
            salesOrder.setStatus(SalesOrderStatus.PENDING);
        }
    }


    public boolean allItemsFulfilled(SalesOrder salesOrder){
        return salesOrder.getItems().stream()
                .allMatch(item -> Objects.equals(item.getQuantity(), item.getApprovedQuantity()));
    }
}
