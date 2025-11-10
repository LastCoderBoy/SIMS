package com.JK.SIMS.service.productManagementService;

import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementRequest;
import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.models.PaginatedResponse;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.coyote.BadRequestException;

public interface ProductManagementService {
    PaginatedResponse<ProductManagementResponse> getAllProducts(int page, int size);
    ProductManagementResponse addProduct(ProductManagementRequest newProduct);
    ApiResponse<Void> deleteProduct(String id, String jwtToken) throws BadRequestException;
    ApiResponse<Void> updateProduct(String productId, ProductManagementRequest updateProductRequest);
    PaginatedResponse<ProductManagementResponse> searchProduct(String text, int page, int size);
    PaginatedResponse<ProductManagementResponse> filterProducts(String filter, String sortBy, String direction, int page, int size);
    void generatePMReport(HttpServletResponse response);

    void updateIncomingProductStatusInPm(ProductsForPM orderedProduct);
    void saveProduct(ProductsForPM product);
}
