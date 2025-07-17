package com.JK.SIMS.service.confirmTokenService;

import com.JK.SIMS.models.IC_models.incoming.IncomingStock;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockStatus;
import com.JK.SIMS.models.IC_models.incoming.token.ConfirmationToken;
import com.JK.SIMS.models.IC_models.incoming.token.ConfirmationTokenStatus;
import com.JK.SIMS.repository.confirmationTokenRepo.ConfirmationTokenRepository;
import com.JK.SIMS.service.GlobalServiceHelper;
import com.JK.SIMS.service.incomingStock_service.IncomingStockHelper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@AllArgsConstructor
public class ConfirmationTokenService {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmationTokenService.class);

    private final ConfirmationTokenRepository tokenRepository;
    private final IncomingStockHelper incomingStockHelper;
    private final Clock clock;

    @Transactional
    public ConfirmationToken createConfirmationToken(IncomingStock order){
        String token = generateConfirmationToken();
        ConfirmationToken confirmationToken = new ConfirmationToken(
                token,
                GlobalServiceHelper.now(clock),
                ConfirmationTokenStatus.PENDING,
                GlobalServiceHelper.now(clock).plusDays(1),
                order);
        return tokenRepository.save(confirmationToken);
    }

    @Transactional
    public void expireTokens() {
        List<ConfirmationToken> expiredTokens = tokenRepository.findAllByExpiresAtBeforeAndClickedAtIsNull(LocalDateTime.now());
        for (ConfirmationToken token : expiredTokens) {
            // Set the purchase order status to the FAILED and save it
            IncomingStock order = token.getOrder();
            order.setStatus(IncomingStockStatus.FAILED);

            incomingStockHelper.saveIncomingStock(order);
            tokenRepository.delete(token);
            logger.info("Deleted {} Expired Confirmation Token", expiredTokens.size());
        }
    }

    public ConfirmationToken getConfirmationToken(String token) {
        return tokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("Invalid confirmation token"));
    }

    public void saveConfirmationToken(ConfirmationToken confirmationToken){
        tokenRepository.save(confirmationToken);
        logger.info("Token saved successfully: {}", confirmationToken.getToken());
    }

    private String generateConfirmationToken(){
        return UUID.randomUUID().toString();
    }
}
