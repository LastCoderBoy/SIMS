package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockRequestDto;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockResponseDto;
import com.JK.SIMS.models.IC_models.incoming.IncomingStockStatus;
import com.JK.SIMS.models.IC_models.incoming.ReceiveStockRequestDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.TokenUtils;
import com.JK.SIMS.service.incomingStock_service.IncomingStockService;
import jakarta.validation.Valid;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/priority/inventory/incoming-stock")
public class IncomingStockController {

    private static final Logger logger = LoggerFactory.getLogger(IncomingStockController.class);
    private final IncomingStockService incomingStockService;
    @Autowired
    public IncomingStockController(IncomingStockService incomingStockService) {
        this.incomingStockService = incomingStockService;
    }

    @GetMapping
    public ResponseEntity<?> getAllIncomingStockRecords(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        PaginatedResponse<IncomingStockResponseDto> paginatedStockResponse =
                incomingStockService.getAllIncomingStockRecords(page, size);
        return ResponseEntity.ok(paginatedStockResponse);
    }

    @PostMapping
    public ResponseEntity<?> createPurchaseOrder(@Valid @RequestBody IncomingStockRequestDto stockRequest,
                                                 @RequestHeader("Authorization") String token) throws BadRequestException {
        logger.info("IS: createPurchaseOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            incomingStockService.createPurchaseOrder(stockRequest, jwtToken);

            return new ResponseEntity<>(
                    new ApiResponse(true, stockRequest.getProductId() + "is ordered successfully"),
                    HttpStatus.CREATED);
        }
        throw new InvalidTokenException("IS: createPurchaseOrder() Invalid Token provided.");
    }

    @PutMapping("/{id}/receive")
    public ResponseEntity<?> receiveIncomingStockOrder(@Valid @RequestBody ReceiveStockRequestDto receiveRequest,
                                                      @PathVariable Long id,
                                                      @RequestHeader("Authorization") String token) throws BadRequestException {
        logger.info("IS: receiveIncomingStockOrder() calling...");
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse response =  incomingStockService.receiveIncomingStock(id, receiveRequest, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.OK);
        }
        throw new InvalidTokenException("IS: receiveIncomingStockOrder() Invalid Token provided.");
    }

    @PutMapping("{id}/cancel-order")
    public ResponseEntity<?> cancelIncomingStockInternal(@PathVariable Long id, @RequestHeader("Authorization") String token) throws BadRequestException {
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            ApiResponse response = incomingStockService.cancelIncomingStockInternal(id, jwtToken);
            return ResponseEntity.ok(response);
        }
        throw new InvalidTokenException("IS: cancelIncomingStockInternal() Invalid Token provided.");
    }


    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("IS: searchProduct() calling with text: {}", text);
        PaginatedResponse<IncomingStockResponseDto> dtoResponse = incomingStockService.searchProduct(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterStock(
            @RequestParam(required = false) IncomingStockStatus status,
            @RequestParam(required = false) ProductCategories category,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<IncomingStockResponseDto> filterResponse = incomingStockService.filterIncomingStock(status, category, page, size);
        logger.info("IS filterStock(): Returning {} paginated data", filterResponse.getContent().size());
        return ResponseEntity.ok(filterResponse);
    }

}
