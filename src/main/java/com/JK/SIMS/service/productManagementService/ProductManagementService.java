package com.JK.SIMS.service.productManagementService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.BatchProductResponse;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;

import java.util.List;

public interface ProductManagementService {
    PaginatedResponse<ProductManagementResponse> getAllProducts(String sortBy, String sortDirection, int page, int size);
    ProductManagementResponse addProduct(ProductManagementRequest newProduct);
    BatchProductResponse addProductsBatch(List<ProductManagementRequest> products);
    ApiResponse<Void> deleteProduct(String id, String jwtToken) throws BadRequestException;
    ApiResponse<Void> updateProduct(String productId, ProductManagementRequest updateProductRequest);
    PaginatedResponse<ProductManagementResponse> searchProduct(String text, String sortBy, String sortDirection, int page, int size);
    PaginatedResponse<ProductManagementResponse> filterProducts(String filter, String sortBy, String direction, int page, int size);
    void generatePMReport(HttpServletResponse response);
    void saveProduct(ProductsForPM product);
}
