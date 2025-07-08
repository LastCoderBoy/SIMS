package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockRequest;
import com.JK.SIMS.service.IC_service.IncomingStockService;
import com.JK.SIMS.service.TokenUtils;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/products/inventory/incoming-stock")
public class IncomingStockController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockController.class);
    private final IncomingStockService incomingStockService;
    @Autowired
    public IncomingStockController(IncomingStockService incomingStockService) {
        this.incomingStockService = incomingStockService;
    }

    @PostMapping
    public ResponseEntity<?> createPurchaseOrder(@Valid @RequestBody IncomingStockRequest stockRequest,
                                                 @RequestHeader("Authorization") String token) throws AccessDeniedException, BadRequestException {
        logger.info("IC: createPurchaseOrder() calling...");
        if(SecurityUtils.hasAccess()){
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                incomingStockService.createPurchaseOrder(stockRequest, jwtToken);

                return new ResponseEntity<>(
                        new ApiResponse(true, stockRequest.getProductId() + "is ordered successfully"),
                        HttpStatus.CREATED);
            }
            throw new InvalidTokenException("IC: createPurchaseOrder() Invalid Token provided.");
        }
        throw new AccessDeniedException("IC: createPurchaseOrder() No access for the current user.");
    }

    // TODO: Update incoming stock order manually

    // TODO: GET : Get all incoming stock records.

    // TODO: GET /?status=PENDING: Filter records by status.

    // TODO: GET /api/incoming-stock?startDate=2025-07-01&endDate=2025-07-31: Filter by order date range.
}
