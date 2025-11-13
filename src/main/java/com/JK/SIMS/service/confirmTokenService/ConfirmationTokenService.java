package com.JK.SIMS.service.confirmTokenService;

import com.JK.SIMS.models.purchaseOrder.PurchaseOrder;
import com.JK.SIMS.models.purchaseOrder.PurchaseOrderStatus;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationToken;
import com.JK.SIMS.models.purchaseOrder.confirmationToken.ConfirmationTokenStatus;
import com.JK.SIMS.repository.confirmationTokenRepo.ConfirmationTokenRepository;
import com.JK.SIMS.service.orderManagementService.purchaseOrderService.PurchaseOrderService;
import com.JK.SIMS.service.purchaseOrder.purchaseOrderQueryService.PurchaseOrderQueryService;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.JK.SIMS.service.utilities.GlobalServiceHelper.generateToken;

@Service
@Slf4j
@RequiredArgsConstructor
public class ConfirmationTokenService {
    private final Clock clock;
    private final PurchaseOrderQueryService purchaseOrderQueryService;
    private final ConfirmationTokenRepository tokenRepository;

    @Transactional
    public ConfirmationToken createConfirmationToken(PurchaseOrder order){
        String token = generateToken();
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

            purchaseOrderQueryService.save(order);
            tokenRepository.delete(token);
            log.info("Deleted {} Expired Confirmation Token", expiredTokens.size());
        }
    }

    @Nullable
    @Transactional(readOnly = true)
    public ConfirmationToken validateConfirmationToken(String token) {
        ConfirmationToken confirmationToken = getConfirmationToken(token);
        if (confirmationToken.getClickedAt() != null ||
                confirmationToken.getExpiresAt().isBefore(LocalDateTime.now())) {
            return null;
        }
        return confirmationToken;
    }


    private ConfirmationToken getConfirmationToken(String token) {
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
        log.info("Token saved successfully: {}", token);
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
}
