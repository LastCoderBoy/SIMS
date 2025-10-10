package com.JK.SIMS.service.email_service.impl;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.IC_models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.IC_models.purchaseOrder.confirmationToken.ConfirmationTokenStatus;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.repository.PurchaseOrder_repo.PurchaseOrderRepository;
import com.JK.SIMS.service.InventoryServices.inventoryPageService.InventoryControlService;
import com.JK.SIMS.service.InventoryServices.inventoryServiceHelper.InventoryServiceHelper;
import com.JK.SIMS.service.confirmTokenService.ConfirmationTokenService;
import com.JK.SIMS.service.email_service.EmailServiceForPo;
import com.JK.SIMS.service.productManagementService.PMServiceHelper;
import jakarta.persistence.OptimisticLockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class EmailServiceForPoImpl implements EmailServiceForPo {

    private static final Logger logger = LoggerFactory.getLogger(EmailServiceForPoImpl.class);

    private final ConfirmationTokenService confirmationTokenService;
    private final InventoryServiceHelper inventoryServiceHelper;
    private final InventoryControlService inventoryControlService;
    private final PMServiceHelper pmServiceHelper;

    private final PurchaseOrderRepository purchaseOrderRepository;
    @Autowired
    public EmailServiceForPoImpl(ConfirmationTokenService confirmationTokenService, InventoryServiceHelper inventoryServiceHelper, InventoryControlService inventoryControlService, PMServiceHelper pmServiceHelper, PurchaseOrderRepository purchaseOrderRepository) {
        this.confirmationTokenService = confirmationTokenService;
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.inventoryControlService = inventoryControlService;
        this.pmServiceHelper = pmServiceHelper;
        this.purchaseOrderRepository = purchaseOrderRepository;
    }

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

                logger.info("OM (confirmPurchaseOrder): Order confirmed by supplier. PO Number: {}", order.getPONumber());
                return new ApiResponse<>(true, "Order " + order.getPONumber() + " confirmed successfully with expected arrival: " + expectedArrivalDate);
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when confirming order. Order ID: {}", order.getId());
                return new ApiResponse<>(false, "This order has already been processed by someone else.");
            }
        } else {
            logger.warn("OM (confirmPurchaseOrder): Order ID {} is not in AWAITING_APPROVAL status. Current status: {}", order.getId(), order.getStatus());
            return new ApiResponse<>(false, "Order already confirmed or cancelled.");
        }
    }


    private void handleInventoryStatusUpdates(ProductsForPM orderedProduct) {
        Optional<InventoryData> inventoryProductOpt =
                inventoryServiceHelper.getInventoryProductByProductId(orderedProduct.getProductID());

        if (orderedProduct.getStatus() == ProductStatus.PLANNING) {
            handlePlanningStatusUpdate(orderedProduct, inventoryProductOpt);
        } else if (orderedProduct.getStatus() == ProductStatus.ACTIVE) {
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handlePlanningStatusUpdate(ProductsForPM orderedProduct, Optional<InventoryData> inventoryProductOpt) {
        // Update the product status from PLANNING to ON_ORDER
        orderedProduct.setStatus(ProductStatus.ON_ORDER);
        pmServiceHelper.saveProduct(orderedProduct);

        if (inventoryProductOpt.isEmpty()) {
            // Product not in inventory, add it
            inventoryControlService.addProduct(orderedProduct, true);
        } else {
            // Else update the existing inventory status to INCOMING
            handleActiveStatusUpdate(inventoryProductOpt);
        }
    }

    private void handleActiveStatusUpdate(Optional<InventoryData> inventoryDataOpt) {
        if(inventoryDataOpt.isPresent()) {
            InventoryData inventoryData = inventoryDataOpt.get();
            if (inventoryData.getStatus() != InventoryDataStatus.INCOMING) {
                inventoryData.setStatus(InventoryDataStatus.INCOMING);
                inventoryControlService.saveInventoryProduct(inventoryData);
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

                logger.info("IS (cancelPurchaseOrder): SalesOrder cancelled by supplier. PO Number: {}", order.getPONumber());
                return buildConfirmationPage("SalesOrder " + order.getPONumber() + " has been successfully cancelled!", "alert-success");
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                logger.warn("Race condition detected when cancelling order. SalesOrder ID: {}", order.getId());
                return buildConfirmationPage("This order has already been processed by someone else.", "alert-danger");
            }
        } else {
            logger.warn("IS (cancelPurchaseOrder): SalesOrder ID {} is not in AWAITING_SUPPLIER_CONFIRMATION status. Current status: {}", order.getId(), order.getStatus());
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
