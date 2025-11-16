package com.JK.SIMS.controller.inventoryControllers;

import com.JK.SIMS.config.security.utils.SecurityUtils;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.damage_loss.DamageLoss;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossPageResponse;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossRequest;
import com.JK.SIMS.models.damage_loss.dtos.DamageLossResponse;
import com.JK.SIMS.service.InventoryServices.damageLossService.DamageLossService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_DIRECTION;
import static com.JK.SIMS.service.generalUtils.GlobalServiceHelper.validateAndExtractToken;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/products/inventory/damage-loss")
public class DamageLossController {

    private final DamageLossService damageLossService;

    @GetMapping
    public ResponseEntity<DamageLossPageResponse> getDamageLossDashboardData(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        DamageLossPageResponse pageResponse = damageLossService.getDamageLossDashboardData(page, size);
        return ResponseEntity.ok(pageResponse);
    }

    @PostMapping
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<?> addDamageLoss(@RequestBody DamageLossRequest dtoRequest,
                                           @RequestHeader("Authorization") String token){
        String jwtToken = validateAndExtractToken(token);
        damageLossService.addDamageLoss(dtoRequest, jwtToken);
        log.info("DL (addDamageLoss): {} sku report is created successfully.", dtoRequest.sku());
        return new ResponseEntity<>(
                new ApiResponse<>(true, dtoRequest.sku() + " sku report is created successfully"),
                HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<DamageLoss>> updateDamageLossProduct(@PathVariable Integer id,
                                                     @RequestBody DamageLossRequest request) throws BadRequestException{
        ApiResponse<DamageLoss> response = damageLossService.updateDamageLossProduct(id, request);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<String>> deleteDamageLossReport(@PathVariable Integer id) {
        ApiResponse<String> response = damageLossService.deleteDamageLossReport(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<DamageLossResponse>> searchProduct(@RequestParam(required = false) String text,
                                                                               @RequestParam(defaultValue = "0") int page,
                                                                               @RequestParam(defaultValue = "10") int size){
        log.info("DL: searchProduct() calling...");
        PaginatedResponse<DamageLossResponse> dtoResponse = damageLossService.searchProduct(text, page, size);
        return ResponseEntity.ok(dtoResponse);
    }

    @GetMapping("/filter")
    public ResponseEntity<PaginatedResponse<DamageLossResponse>> filterProducts(@RequestParam String reason,
                                                                                @RequestParam(defaultValue = "icProduct.pmProduct.name") String sortBy,
                                                                                @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
                                                                                @RequestParam(defaultValue = "0") int page,
                                                                                @RequestParam(defaultValue = "10") int size) {
        log.info("DL: filterProducts() calling with page {} and size {}...", page, size);
        PaginatedResponse<DamageLossResponse> filteredDTOs =
                damageLossService.filterProducts(reason, sortBy, sortDirection, page, size);
        return ResponseEntity.ok(filteredDTOs);
    }
}
