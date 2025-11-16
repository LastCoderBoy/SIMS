package com.JK.SIMS.controller.product_management;

import com.JK.SIMS.config.security.utils.TokenUtils;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.BatchProductRequest;
import com.JK.SIMS.models.PM_models.dtos.BatchProductResponse;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.coyote.BadRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;

import static com.JK.SIMS.service.generalUtils.EntityConstants.DEFAULT_SORT_DIRECTION;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {
    
    private final ProductManagementService pmService;

    @GetMapping
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> getAllProducts(
            @RequestParam(defaultValue = "productID") String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        log.info("PM: getAllProducts() calling...");
        PaginatedResponse<ProductManagementResponse> products = pmService.getAllProducts(sortBy, sortDirection, page, size);
        return ResponseEntity.ok(products);
    }

    @PostMapping
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<ProductManagementResponse>> addProduct(
            @RequestBody @Valid ProductManagementRequest newProduct){
        if (newProduct == null) {
            log.error("PM: addProduct() Request for Product cannot be null");
            throw new ValidationException("Request for Product cannot be null");
        }
        //Only the Admins and Managers can add new products.
        log.info("PM: addProduct() calling for product: {}", newProduct.getName());
        ProductManagementResponse response = pmService.addProduct(newProduct);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new ApiResponse<>(true, "Product added successfully", response));
    }

    // Batch product creation
    @PostMapping("/batch")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<BatchProductResponse>> addProductsBatch(
            @RequestBody @Valid BatchProductRequest batchRequest) {
        if (batchRequest == null || batchRequest.getProducts() == null ||
                batchRequest.getProducts().isEmpty()) {
            throw new ValidationException("Batch request must contain at least one product");
        }
        if (batchRequest.getProducts().size() > 100) {
            throw new ValidationException("Cannot add more than 100 products at once");
        }

        log.info("PM: addProductsBatch() calling for {} products", batchRequest.getProducts().size());
        BatchProductResponse response = pmService.addProductsBatch(batchRequest.getProducts());

        HttpStatus status = response.getFailureCount() > 0
                ? HttpStatus.MULTI_STATUS  // 207: Some succeeded, some failed
                : HttpStatus.CREATED;       // 201: All succeeded

        return ResponseEntity.status(status).body(new ApiResponse<>(
                        response.getFailureCount() == 0,
                        String.format("Added %d/%d products successfully",
                                response.getSuccessCount(),
                                response.getTotalRequested()),
                        response
                ));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@securityUtils.hasAccess()")
    public ResponseEntity<ApiResponse<Void>> updateProduct(
            @PathVariable String id,
            @RequestBody ProductManagementRequest productRequest) throws BadRequestException {
        if (id == null || productRequest == null || id.trim().isEmpty()) {
            throw new BadRequestException("Product ID or New product cannot be null");
        }
        log.info("PM: updateProduct() calling...");
        ApiResponse<Void> response = pmService.updateProduct(id.toUpperCase(), productRequest);
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
            return ResponseEntity.ok(response);
        }
        throw new AccessDeniedException("PM: deleteProduct() Invalid Token provided.");
    }

    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "productID") String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String sortDirection,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        log.info("PM: searchProduct() calling...");
        PaginatedResponse<ProductManagementResponse> result =
                pmService.searchProduct(text, sortBy, sortDirection, page, size);
        log.info("PM (searchProduct): Returning {} paginated data", result.getContent().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/filter")
    public ResponseEntity<PaginatedResponse<ProductManagementResponse>> filterProducts(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "productID") String sortBy,
            @RequestParam(defaultValue = DEFAULT_SORT_DIRECTION) String direction,
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
