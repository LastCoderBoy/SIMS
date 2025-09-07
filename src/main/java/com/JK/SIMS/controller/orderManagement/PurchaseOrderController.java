package com.JK.SIMS.controller.orderManagement;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.purchaseOrder.PurchaseOrderRequestDto;
import com.JK.SIMS.service.utilities.TokenUtils;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;


public class PurchaseOrderController {
//
//    @PostMapping
//    public ResponseEntity<?> createPurchaseOrder(@Valid @RequestBody PurchaseOrderRequestDto stockRequest,
//                                                 @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
//        logger.info("IS: createPurchaseOrder() calling...");
//        if(SecurityUtils.hasAccess()) {
//            if(token != null && !token.trim().isEmpty()) {
//                String jwtToken = TokenUtils.extractToken(token);
//                purchaseOrderService.createPurchaseOrder(stockRequest, jwtToken);
//
//                return new ResponseEntity<>(
//                        new ApiResponse(true, stockRequest.getProductId() + " is ordered successfully"),
//                        HttpStatus.CREATED);
//            }
//            throw new InvalidTokenException("IS: createPurchaseOrder() Invalid Token provided.");
//        }
//        throw new AccessDeniedException("IS createPurchaseOrder(): You cannot perform the following operation.");
//    }
//
//    @PutMapping("{id}/cancel-order")
//    public ResponseEntity<?> cancelIncomingStockInternal(@PathVariable Long id, @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
//        if(SecurityUtils.hasAccess()) {
//            if(token != null && !token.trim().isEmpty()) {
//                String jwtToken = TokenUtils.extractToken(token);
//                ApiResponse response = purchaseOrderService.cancelIncomingStockInternal(id, jwtToken);
//                return ResponseEntity.ok(response);
//            }
//            throw new InvalidTokenException("IS: cancelIncomingStockInternal() Invalid Token provided.");
//        }
//        throw new AccessDeniedException("IS: cancelIncomingStockInternal() You cannot perform the following operation.");
//    }
}
