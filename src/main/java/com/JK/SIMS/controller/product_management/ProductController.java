package com.JK.SIMS.controller.product_management;

import com.JK.SIMS.config.security.utils.TokenUtils;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    
    private final ProductManagementService pmService;

    @GetMapping
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("PM: getAllProducts() calling...");
        PaginatedResponse<ProductManagementResponse> products = pmService.getAllProducts(page, size);
        return ResponseEntity.ok(products);
    }

    @PostMapping
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<Void>> addProduct(@RequestBody ProductsForPM newProduct) throws AccessDeniedException {
        if (newProduct == null) {
            throw new ValidationException("PM: Product data cannot be null");
        }
        //Only the Admins and Managers can add new products.
        log.info("PM: addProduct() calling...");
        ApiResponse<Void> response = pmService.addProduct(newProduct);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<Void>> updateProduct(@PathVariable String id, @RequestBody ProductsForPM newProduct) throws BadRequestException, AccessDeniedException {
        if (id == null || newProduct == null || id.trim().isEmpty()) {
            throw new BadRequestException("PM (updateProduct): Product ID or New product cannot be null");
        }
        log.info("PM: updateProduct() calling...");
        ApiResponse<Void> response = pmService.updateProduct(id.toUpperCase(), newProduct);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<Void>> deleteProduct(@PathVariable String id,
                                           @RequestHeader("Authorization") String token) throws BadRequestException, AccessDeniedException {
        if (id == null || id.trim().isEmpty()) {
            throw new ValidationException("PM: Product ID cannot be null");
        }
        if(token != null && !token.trim().isEmpty()) {
            String jwtToken = TokenUtils.extractToken(token);
            log.info("PM: deleteProduct() calling...");
            ApiResponse<Void> response = pmService.deleteProduct(id, jwtToken);
            return new ResponseEntity<>(response, HttpStatus.NO_CONTENT);
        }
        throw new AccessDeniedException("PM: deleteProduct() Invalid Token provided.");
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        log.info("PM: searchProduct() calling...");
        PaginatedResponse<ProductManagementResponse> result = pmService.searchProduct(text, page, size);
        log.info("PM (searchProduct): Returning {} paginated data", result.getContent().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/filter")
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> filterProducts(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "productID") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<ProductManagementResponse> result = pmService.filterProducts(filter, sortBy, direction, page, size);
        log.info("PM (filterProducts): Returning {} paginated data", result.getContent().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/report")
    public void generatePMReport(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=product.xlsx";
        response.setHeader(headerKey, headerValue);
        log.info("PM: generatePMReport() calling...");
        pmService.generatePMReport(response);
    }

}
