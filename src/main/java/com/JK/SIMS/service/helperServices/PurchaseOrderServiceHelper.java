package com.JK.SIMS.service.helperServices;

import com.JK.SIMS.exceptionHandler.ResourceNotFoundException;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.dtos.PurchaseOrderResponseDto;
import com.JK.SIMS.models.IC_models.purchaseOrder.views.SummaryPurchaseOrderView;
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


    // Method to build an HTML response page for the supplier after clicking a link
    public static String buildConfirmationPage(String message, String alertClass) {
        return "<html>"
                + "<head><title>Purchase Order Confirmation</title>"
                + "<style>"
                + "body { font-family: Arial, sans-serif; text-align: center; padding: 50px; }"
                + ".alert { padding: 20px; margin: 20px auto; border-radius: 5px; max-width: 500px; }"
                + ".alert-success { background-color: #d4edda; color: #155724; border: 1px solid #c3e6cb; }"
                + ".alert-danger { background-color: #f8d7da; color: #721c24; border: 1px solid #f5c6cb; }"
                + "</style>"
                + "</head>"
                + "<body>"
                + "<div class='alert " + alertClass + "'>"
                + "<h2>SIMS Inventory System</h2>"
                + "<p>" + message + "</p>"
                + "<p>You can close this window.</p>"
                + "</div>"
                + "</body>"
                + "</html>";
    }
}