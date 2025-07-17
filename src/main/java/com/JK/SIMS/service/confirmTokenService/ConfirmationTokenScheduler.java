package com.JK.SIMS.service.confirmTokenService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConfirmationTokenScheduler {

    private static final Logger logger = LoggerFactory.getLogger(ConfirmationTokenScheduler.class);

    @Autowired
    private ConfirmationTokenService tokenService;

    @Scheduled(cron = "0 0 0 * * *")
    public void expireOldTokens() {
        logger.info("Check expired old Confirmation Tokens.");
        tokenService.expireTokens();
    }
}
