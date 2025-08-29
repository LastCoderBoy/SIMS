package com.JK.SIMS.service.purchaseOrderService;

import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.repository.IC_repo.PurchaseOrderRepository;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;

@Service
@AllArgsConstructor
public class PurchaseOrderHelper {

    private static final Logger logger = LoggerFactory.getLogger(PurchaseOrderHelper.class);
    private final PurchaseOrderRepository incomingStockRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public void saveIncomingStock(PurchaseOrder order) {
        incomingStockRepository.save(order);
        logger.info("PurchaseOrderHelper: Successfully saved/updated product with PO Number: {}",
                order.getPONumber());
    }

    // Helper method to build a simple HTML response page for the supplier after clicking a link
    public static String buildConfirmationPage(String message, String alertClass) {
        return "<html>"
                + "<head><title>SalesOrder Confirmation</title>"
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