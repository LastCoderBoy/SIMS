package com.JK.SIMS.service.utilities;

import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.PO_repo.PurchaseOrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;

@Component
@AllArgsConstructor
public class PurchaseOrderServiceHelper {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderServiceHelper.class);
    private final PurchaseOrderRepository purchaseOrderRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveIncomingStock(PurchaseOrder order) {
        purchaseOrderRepository.save(order);
        logger.info("PurchaseOrderServiceHelper: Successfully saved/updated product with PO Number: {}",
                order.getPONumber());
    }

    public PaginatedResponse<PurchaseOrderResponseDto> transformToPaginatedDtoResponse(Page<PurchaseOrder> poEntityPage){
        Page<PurchaseOrderResponseDto> paginatedResponse = poEntityPage.map(this::convertToDto);
        return new PaginatedResponse<>(paginatedResponse);
    }

    public PurchaseOrderResponseDto convertToDto(PurchaseOrder order){
        return new PurchaseOrderResponseDto(order);
    }

    public SummaryPurchaseOrderView convertToSummaryView(PurchaseOrder order){
        return new SummaryPurchaseOrderView(order);
    }

    public PaginatedResponse<SummaryPurchaseOrderView> transformToPaginatedSummaryView(Page<PurchaseOrder> poEntityPage){
        Page<SummaryPurchaseOrderView> paginatedResponse = poEntityPage.map(this::convertToSummaryView);
        return new PaginatedResponse<>(paginatedResponse);
    }

    @Transactional(readOnly = true)
    public PurchaseOrder getPurchaseOrderById(Long orderId) {
        return purchaseOrderRepository.findById(orderId)
                .orElseThrow(() -> new ResourceNotFoundException("PurchaseOrderServiceHelper (getPurchaseOrderById): No incoming stock order found for ID: " + orderId));
    }

    public void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("PO (validateOrderId): PurchaseOrder ID cannot be null");
        }
    }

    public static BigDecimal calculateTotalPrice(PurchaseOrder purchaseOrder){
        return purchaseOrder.getProduct().getPrice().multiply(new BigDecimal(purchaseOrder.getOrderedQuantity()));
    }
}