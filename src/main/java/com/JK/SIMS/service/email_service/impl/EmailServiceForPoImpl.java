package com.JK.SIMS.service.email_service.impl;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationTokenStatus;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailServiceForPo;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
public class EmailServiceForPoImpl implements EmailServiceForPo {

    private final ConfirmationTokenService confirmationTokenService;
    private final InventoryQueryService inventoryQueryService;
    private final InventoryControlService inventoryControlService;
    private final ProductManagementService productManagementService;

    private final PurchaseOrderRepository purchaseOrderRepository;

    // Method for the Email Confirmation
    @Override
    @Transactional
    public ApiResponse<String> confirmPurchaseOrder(String token, LocalDate expectedArrivalDate) {
        ConfirmationToken confirmationToken = confirmationTokenService.validateConfirmationToken(token);
        if (confirmationToken == null) {
            return new ApiResponse<>(false, "Email link is expired or already processed.");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                // Update expected arrival date from supplier
                order.setExpectedArrivalDate(expectedArrivalDate);

                // Update inventory status if needed
                handleInventoryStatusUpdates(order.getProduct());

                // Set PO status
                order.setStatus(PurchaseOrderStatus.DELIVERY_IN_PROCESS);
                order.setUpdatedBy("Supplier via Confirmation Link");
                purchaseOrderRepository.save(order);

                // Update token
                confirmationTokenService.updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CONFIRMED);

                log.info("OM (confirmPurchaseOrder): Order confirmed by supplier. PO Number: {}", order.getPONumber());
                return new ApiResponse<>(true, "Order " + order.getPONumber() + " confirmed successfully with expected arrival: " + expectedArrivalDate);
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                log.warn("Race condition detected when confirming order. Order ID: {}", order.getId());
                return new ApiResponse<>(false, "This order has already been processed by someone else.");
            }
        } else {
            log.warn("OM (confirmPurchaseOrder): Order ID {} is not in AWAITING_APPROVAL status. Current status: {}", order.getId(), order.getStatus());
            return new ApiResponse<>(false, "Order already confirmed or cancelled.");
        }
    }


    private void handleInventoryStatusUpdates(ProductsForPM orderedProduct) {
        Optional<InventoryControlData> inventoryProductOpt =
                inventoryQueryService.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, inventoryProductOpt);
        } else if (orderedProduct.getStatus() == ProductStatus.ACTIVE) {
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handlePlanningStatusUpdate(ProductsForPM orderedProduct, Optional<InventoryControlData> inventoryProductOpt) {
        // Update the product status from PLANNING to ON_ORDER
        orderedProduct.setStatus(ProductStatus.ON_ORDER);
        productManagementService.saveProduct(orderedProduct);

        if (inventoryProductOpt.isEmpty()) {
            // Product not in inventory, add it
            inventoryControlService.addProduct(orderedProduct, true);
        } else {
            // Else update the existing inventory status to INCOMING
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handleActiveStatusUpdate(Optional<InventoryControlData> inventoryDataOpt) {
        if(inventoryDataOpt.isPresent()) {
            InventoryControlData inventoryControlData = inventoryDataOpt.get();
            if (inventoryControlData.getStatus() != InventoryDataStatus.INCOMING) {
                inventoryControlData.setStatus(InventoryDataStatus.INCOMING);
                inventoryControlService.saveInventoryProduct(inventoryControlData);
            }
        }
        // Active status products will always be present in the Inventory
    }

    // Method for the Email Cancellation
    @Override
    @Transactional
    public String cancelPurchaseOrder(String token) {
        ConfirmationToken confirmationToken = confirmationTokenService.validateConfirmationToken(token);
        if (confirmationToken == null) {
            return buildConfirmationPage("Email link is expired or already processed.", "alert-danger");
        }

        PurchaseOrder order = confirmationToken.getOrder();
        if (order.getStatus() == PurchaseOrderStatus.AWAITING_APPROVAL) {
            try {
                order.setStatus(PurchaseOrderStatus.FAILED);
                order.setUpdatedBy("Supplier via Email Link.");
                purchaseOrderRepository.save(order);

                confirmationTokenService.updateConfirmationToken(confirmationToken, ConfirmationTokenStatus.CANCELLED);

                // Change the status from PLANNING -> ACTIVE
//                pmService.updateIncomingProductStatusInPm(order.getProduct());

                log.info("IS (cancelPurchaseOrder): SalesOrder cancelled by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("SalesOrder " + order.getPONumber() + " has been successfully cancelled!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                log.warn("Race condition detected when cancelling order. SalesOrder ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            log.warn("IS (cancelPurchaseOrder): SalesOrder ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
            return buildConfirmationPage("SalesOrder already confirmed or cancelled.", "alert-danger");
        }
    }

    // Method to build an HTML response page for the supplier after clicking a link
    public String buildConfirmationPage(String message, String alertClass) {
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
