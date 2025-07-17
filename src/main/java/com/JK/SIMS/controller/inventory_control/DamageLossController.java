package com.JK.SIMS.controller.inventory_control;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.InvalidTokenException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossDTO;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossDTORequest;
import com.JK.SIMS.models.IC_models.damage_loss.DamageLossPageResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.InventoryControl_service.DamageLossService;
import com.JK.SIMS.service.TokenUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@Slf4j
@RestController
@RequestMapping("/api/v1/products/inventory/damage-loss")
public class DamageLossController {

    private static final Logger logger = LoggerFactory.getLogger(DamageLossController.class);
    private final DamageLossService damageLossService;

    public DamageLossController(DamageLossService damageLossService) {
        this.damageLossService = damageLossService;
    }

    @GetMapping
    public ResponseEntity<?> getDamageLossDashboardData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        DamageLossPageResponse pageResponse = damageLossService.getDamageLossDashboardData(page, size);
        return ResponseEntity.ok(pageResponse);
    }

    @PostMapping
    public ResponseEntity<?> addDamageLoss(
            @RequestBody DamageLossDTORequest dtoRequest,
            @RequestHeader("Authorization") String token) throws AccessDeniedException {
        // Only the Admin or Managers can add Damage/Loss report
        if(SecurityUtils.hasAccess()){
            if(token != null && !token.trim().isEmpty()) {
                String jwtToken = TokenUtils.extractToken(token);
                damageLossService.addDamageLoss(dtoRequest, jwtToken);
                logger.info("DL (addDamageLoss): {} sku report is created successfully.", dtoRequest.sku());
                return new ResponseEntity<>(
                        new ApiResponse(true, dtoRequest.sku() + " sku report is created successfully"),
                        HttpStatus.CREATED);
            }
            throw new InvalidTokenException("DL (addDamageLoss): Invalid Token provided.");
        }
        throw new AccessDeniedException("DL (addDamageLoss): No access for the current user.");
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateDamageLossProduct(@PathVariable Integer id, @RequestBody DamageLossDTORequest request) throws BadRequestException, AccessDeniedException {
        if(SecurityUtils.hasAccess()) {
            ApiResponse response = damageLossService.updateDamageLossProduct(id, request);
            return ResponseEntity.ok(response);
        }
        throw new AccessDeniedException("DL (update): You cannot perform the following operation.");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteDamageLossReport(@PathVariable Integer id) throws AccessDeniedException {
        if (SecurityUtils.hasAccess()) {
            ApiResponse response = damageLossService.deleteDamageLossReport(id);
            return ResponseEntity.ok(response);
        }
        throw new AccessDeniedException("DL (delete): No access for the current user.");
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("DL: searchProduct() calling...");
        PaginatedResponse<DamageLossDTO> dtoResponse = damageLossService.searchProduct(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam String reason,
            @RequestParam(defaultValue = "icProduct.pmProduct.name") String sortBy,
            @RequestParam(defaultValue = "asc") String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("IC: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<DamageLossDTO> filteredDTOs = damageLossService.filterProducts(reason, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filteredDTOs);
    }
}
