package com.JK.SIMS.controller.product_management;

import com.JK.SIMS.models.PM_models.ProductResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.service.PM_service.ProductsForPMService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.List;

/**
 *  Where every successful user can access to this endpoint.
 */

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {

    private final ProductsForPMService pmService;
    @Autowired
    public ProductController(ProductsForPMService pmService) {
        this.pmService = pmService;
    }

    @GetMapping
    public ResponseEntity<ProductResponse> getAllProducts() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean hasAdminAccess = auth.getAuthorities().stream()
                .anyMatch(r ->
                        r.getAuthority().equals("ROLE_ADMIN") ||
                        r.getAuthority().equals("ROLE_MANAGER"));

        List<ProductsForPM> products = pmService.getAllProducts();
        ProductResponse response = new ProductResponse(products, hasAdminAccess);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    public ResponseEntity<String> addProduct(@RequestBody ProductsForPM newProduct){
        return pmService.addProduct(newProduct);
    }

    @PutMapping("/{id}")
    public ResponseEntity<String> updateProduct(@PathVariable String id, @RequestBody ProductsForPM newProduct){
        return pmService.updateProduct(id, newProduct);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteProduct(@PathVariable String id){
        return pmService.deleteProduct(id);
    }

    @GetMapping("/search")
    public ResponseEntity<List<ProductsForPM>> searchProduct(@RequestParam String text){
        return pmService.searchProduct(text);
    }

    @GetMapping("/report")
    public void generatePMReport(HttpServletResponse response) throws IOException {
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String headerKey = "Content-Disposition";
        String headerValue = "attachment; filename=product.xlsx";
        response.setHeader(headerKey, headerValue);

        List<ProductsForPM> allProducts = pmService.getAllProducts();
        pmService.generatePMReport(response, allProducts);
    }

    @GetMapping("/filter")
    public ResponseEntity<List<ProductsForPM>> filterProducts(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String status){
        return pmService.filterProducts(category, sortBy, status);
    }

}
