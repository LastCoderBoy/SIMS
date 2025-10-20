package com.JK.SIMS.service.confirmTokenService;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationTokenStatus;
import com.JK.SIMS.repository.confirmationTokenRepo.ConfirmationTokenRepository;
import com.JK.SIMS.service.utilities.PurchaseOrderServiceHelper;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import lombok.AllArgsConstructor;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ConfirmationTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmationTokenService.class);

    private final ConfirmationTokenRepository tokenRepository;
    private final PurchaseOrderServiceHelper poServiceHelper;
    private final Clock clock;

    @Transactional
    public ConfirmationToken createConfirmationToken(PurchaseOrder order){
        String token = generateConfirmationToken();
        ConfirmationToken confirmationToken = new ConfirmationToken(
                token,
                GlobalServiceHelper.now(clock),
                ConfirmationTokenStatus.PENDING,
                GlobalServiceHelper.now(clock).plusDays(1),
                order);
        return tokenRepository.save(confirmationToken);
    }

    // Deletes the expired tokens from the database
    @Transactional
    public void expireTokens() {
        List<ConfirmationToken> expiredTokens = tokenRepository.findAllByExpiresAtBeforeAndClickedAtIsNull(LocalDateTime.now());
        for (ConfirmationToken token : expiredTokens) {
            // Set the purchase order status to the FAILED and save it
            PurchaseOrder order = token.getOrder();
            order.setStatus(PurchaseOrderStatus.FAILED);

            poServiceHelper.saveIncomingStock(order);
            tokenRepository.delete(token);
            logger.info("Deleted {} Expired Confirmation Token", expiredTokens.size());
        }
    }

    @Nullable
    public ConfirmationToken validateConfirmationToken(String token) {
        ConfirmationToken confirmationToken = getConfirmationToken(token);
        if (confirmationToken.getClickedAt() != null ||
                confirmationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return confirmationToken;
    }

    @Transactional(readOnly = true)
    public ConfirmationToken getConfirmationToken(String token) {
        return tokenRepository.findByToken(token).orElse(null);
    }

//    @Transactional
//    public void saveConfirmationToken(ConfirmationToken confirmationToken){
//        tokenRepository.save(confirmationToken);
//        logger.info("Token saved successfully: {}", confirmationToken.getToken());
//    }


    @Transactional
    public void updateConfirmationToken(ConfirmationToken token, ConfirmationTokenStatus status) {
        token.setClickedAt(GlobalServiceHelper.now(clock));
        token.setStatus(status);
        logger.info("Token saved successfully: {}", token);
        tokenRepository.save(token);
    }
    

    public Map<String, String> getConfirmationStatus(String token) {
        ConfirmationToken confirmationToken = tokenRepository.findByToken(token).orElse(null);
        if (confirmationToken == null) {
            return buildStatusMap("Invalid token.", "alert-danger");
        }
        switch (confirmationToken.getStatus()) {
            case CONFIRMED:
                return buildStatusMap(
                        "Order " + confirmationToken.getOrder().getPONumber() + " confirmed successfully.",
                        "alert-success");
            case CANCELLED:
                return buildStatusMap(
                        "Order " + confirmationToken.getOrder().getPONumber() + " cancelled successfully.",
                        "alert-warning");
            case PENDING:
                if (confirmationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
                    return buildStatusMap("Token expired.", "alert-danger");
                }
                return buildStatusMap("Token pending confirmation.", "alert-info");
            default:
                return buildStatusMap("Unknown status.", "alert-danger");
        }
    }

    private Map<String, String> buildStatusMap(String message, String alertClass) {
        Map<String, String> data = new HashMap<>();
        data.put("message", message);
        data.put("alertClass", alertClass);
        return data;
    }


    private String generateConfirmationToken(){
        return UUID.randomUUID().toString();
    }
}
