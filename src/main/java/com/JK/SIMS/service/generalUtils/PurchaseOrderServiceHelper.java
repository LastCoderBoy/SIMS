package com.JK.SIMS.service.generalUtils;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.dtos.views.SummaryPurchaseOrderView;
import com.JK.SIMS.models.PaginatedResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;


@Component
@RequiredArgsConstructor
public class PurchaseOrderServiceHelper {

    public SummaryPurchaseOrderView convertToSummaryView(PurchaseOrder order){
        return new SummaryPurchaseOrderView(order);
    }

    public PaginatedResponse<SummaryPurchaseOrderView> transformToPaginatedSummaryView(Page<PurchaseOrder> poEntityPage){
        Page<SummaryPurchaseOrderView> paginatedResponse = poEntityPage.map(this::convertToSummaryView);
        return new PaginatedResponse<>(paginatedResponse);
    }

    public void validateOrderId(Long orderId) {
        if (orderId == null) {
            throw new IllegalArgumentException("PO (validateOrderId): PurchaseOrder ID cannot be null");
        }
        if (orderId < 1) {
            throw new IllegalArgumentException("PO (validateOrderId): PurchaseOrder ID must be greater than zero");
        }
    }

    public static BigDecimal calculateTotalPrice(PurchaseOrder purchaseOrder){
        return purchaseOrder.getProduct().getPrice().multiply(new BigDecimal(purchaseOrder.getOrderedQuantity()));
    }
}