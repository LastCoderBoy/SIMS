package com.JK.SIMS.controller.product_management;

import com.JK.SIMS.config.SecurityUtils;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
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
    public ResponseEntity<?> getAllProducts() {
        logger.info("PM: getAllProducts() calling...");
        List<ProductsForPM> products = pmService.getAllProducts();
        return ResponseEntity.ok(
                (products.isEmpty()) ? new ApiResponse(false, "No products found.") : products
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
        if(id == null || newProduct == null || id.trim().isEmpty()){
            throw new ValidationException("PM (updateProduct): Product ID or New product cannot be null");
        }
        logger.info("PM: updateProduct() calling...");
        return pmService.updateProduct(id.toUpperCase(), newProduct);
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
    public ResponseEntity<?> searchProduct(@RequestParam String text){
        logger.info("PM: searchProduct() calling...");
        return pmService.searchProduct(text);
    }

    @GetMapping("/filter")
    public ResponseEntity<List<ProductsForPM>> filterProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String status){
        logger.info("PM: filterProducts() calling...");
        return pmService.filterProducts(category, sortBy, status);
    }

    @GetMapping("/report")
    public void generatePMReport(HttpServletResponse response) {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=product.xlsx";
        response.setHeader(headerKey, headerValue);

        List<ProductsForPM> allProducts = pmService.getAllProducts();
        logger.info("PM: generatePMReport() calling...");
        pmService.generatePMReport(response, allProducts);
    }

}
