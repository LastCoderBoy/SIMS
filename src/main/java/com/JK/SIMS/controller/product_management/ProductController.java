package com.JK.SIMS.controller.product_management;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductManagementDTO;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.service.PM_service.ProductManagementService;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.AccessDeniedException;
import java.util.List;

/**
 *  Where every successful user can access to this endpoint.
 */

@RestController
@RequestMapping("/api/v1/products") // Endpoint requires Authentication.
public class ProductController {

    private static final Logger logger = LoggerFactory.getLogger(ProductController.class);
    private final ProductManagementService pmService;
    @Autowired
    public ProductController(ProductManagementService pmService) {
        this.pmService = pmService;
    }

    @GetMapping
    public ResponseEntity<?> getAllProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        logger.info("PM: getAllProducts() calling...");
        PaginatedResponse<ProductManagementDTO> products = pmService.getAllProducts(page, size);
        return ResponseEntity.ok(
                (products.getContent().isEmpty()) ? new ApiResponse(true, "No products found.") : products
        );
    }

    @PostMapping
    public ResponseEntity<?> addProduct(@RequestBody ProductsForPM newProduct) throws AccessDeniedException {
        if (newProduct == null) {
            throw new ValidationException("PM: Product data cannot be null");
        }
        //Only the Admins and Managers can add new products.
        logger.info("PM: addProduct() calling...");
        return ResponseEntity.ok(pmService.addProduct(newProduct, SecurityUtils.hasAccess()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable String id, @RequestBody ProductsForPM newProduct) throws BadRequestException {
        if (id == null || newProduct == null || id.trim().isEmpty()) {
            throw new BadRequestException("PM (updateProduct): Product ID or New product cannot be null");
        }
        logger.info("PM: updateProduct() calling...");
        ApiResponse response = pmService.updateProduct(id.toUpperCase(), newProduct);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable String id) throws BadRequestException {
        if(id == null || id.trim().isEmpty()){
            throw new ValidationException("PM: Product ID cannot be null");
        }
        logger.info("PM: deleteProduct() calling...");
        return pmService.deleteProduct(id);
    }

    @GetMapping("/search")
    public ResponseEntity<?> searchProduct(
            @RequestParam(required = false) String text,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size){
        logger.info("PM: searchProduct() calling...");
        PaginatedResponse<ProductManagementDTO> result = pmService.searchProduct(text, page, size);
        logger.info("PM (searchProduct): Returning {} paginated data", result.getContent().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/filter")
    public ResponseEntity<?> filterProducts(
            @RequestParam(required = false) String filter,
            @RequestParam(defaultValue = "productID") String sortBy,
            @RequestParam(defaultValue = "asc") String direction,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<ProductManagementDTO> result = pmService.filterProducts(filter, sortBy, direction, page, size);
        logger.info("PM (filterProducts): Returning {} paginated data", result.getContent().size());
        return ResponseEntity.ok(result);
    }

    @GetMapping("/report")
    public void generatePMReport(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=product.xlsx";
        response.setHeader(headerKey, headerValue);
        logger.info("PM: generatePMReport() calling...");

        List<ProductManagementDTO> allProducts = pmService.getAllProducts();
        pmService.generatePMReport(response, allProducts);
    }

}
