package com.JK.SIMS.service.InventoryServices.totalItemsService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ResourceNotFoundException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.ApiResponse;
import com.JK.SIMS.models.PM_models.ProductStatus;
import com.JK.SIMS.models.PM_models.ProductsForPM;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlRequest;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventorySearchService.InventorySearchService;
import com.JK.SIMS.service.InventoryServices.inventoryDashboardService.stockManagement.StockManagementLogic;
import com.JK.SIMS.service.productManagementService.ProductManagementService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.InventoryServiceHelper.validateUpdateRequest;
import static com.JK.SIMS.service.generalUtils.ExcelReporterHelper.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class TotalItemsService {
    // =========== Helpers & Utilities ===========
    private final InventoryServiceHelper inventoryServiceHelper;

    // =========== Components ===========
    private final StockManagementLogic stockManagementLogic;

    // =========== Services ===========
    private final ProductManagementService productManagementService;
    private final InventorySearchService inventorySearchService;
    private final InventoryQueryService inventoryQueryService;

    // =========== Repositories ===========
    private final IC_repository icRepository; // We are working with the Inventory Products so that's why we need this repository


    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> getAllPaginatedInventoryResponse(String sortBy, String sortDirection,
                                                                                        int page, int size) {
        Page<InventoryControlData> allInventoryProducts =
                inventoryQueryService.getAllInventoryProducts(sortBy, sortDirection, page, size);
        log.info("getAllPaginatedInventoryResponse(): {} products retrieved.", allInventoryProducts.getContent().size());
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(allInventoryProducts);
    }

    // Only currentStock and minLevel can be updated in the IC section
    @Transactional
    public ApiResponse<Void> updateProduct(String sku, InventoryControlRequest inventoryControlRequest) {
        try {
            // Validate input parameters
            validateUpdateRequest(inventoryControlRequest);

            // Find and validate the existing product
            InventoryControlData existingProduct = inventoryQueryService.getInventoryDataBySku(sku); // might throw ResourceNotFoundException

            // Update stock levels
            stockManagementLogic.updateInventoryStockLevels(
                    existingProduct,
                    Optional.ofNullable(inventoryControlRequest.getCurrentStock()),
                    Optional.ofNullable(inventoryControlRequest.getMinLevel())
            );

            log.info("IcTotalItems (updateProduct): Product with SKU {} updated successfully", sku);
            return new ApiResponse<>(true, sku + " is updated successfully");

        } catch (DataAccessException da) {
            log.error("IcTotalItems (updateProduct): Database error while updating SKU {}: {}",
                    sku, da.getMessage());
            throw new DatabaseException("Internal Database Error", da);
        } catch (ResourceNotFoundException | ValidationException e) {
            throw e;
        } catch (Exception ex) {
            log.error("IcTotalItems (updateProduct): Unexpected error while updating SKU {}: {}",
                    sku, ex.getMessage(), ex);
            throw new ServiceException("Internal Service error", ex);
        }
    }

    // Search by SKU, Location, ID, Name, Category.
    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> searchProduct(String text, String sortBy, String sortDirection,
                                                                     int page, int size) {
        Page<InventoryControlData> inventorySearchResponse =
                inventorySearchService.searchAll(text, sortBy, sortDirection, page, size);
        log.info("TotalItems (searchProduct): {} products retrieved.", inventorySearchResponse.getContent().size());
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(inventorySearchResponse) ;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> filterProducts(String filterBy, String sortBy, String sortDirection,
                                                                      int page, int size) {
        Page<InventoryControlData> inventoryFilterResponse =
                inventorySearchService.filterAll(filterBy, sortBy, sortDirection, page, size);

        log.info("TotalItems (filterProducts): {} products retrieved.", inventoryFilterResponse.getContent().size());
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(inventoryFilterResponse);
    }

    /**
     * Deletes a product from the inventory control system and archives it in the Product Management system.
     *
     * @param sku The Stock Keeping Unit identifier of the product to delete
     * @return ApiResponse containing success status and confirmation message
     * @throws ResourceNotFoundException if product is not found in IC or PM system
     * @throws DatabaseException   if database operation fails
     * @throws ServiceException    if any other error occurs during deletion
     */
    @Transactional
    public ApiResponse<Void> deleteProduct(String sku){
        try{
            InventoryControlData product = inventoryQueryService.getInventoryDataBySku(sku);
            ProductsForPM productInPM = product.getPmProduct();

            if(productInPM.getStatus().equals(ProductStatus.ACTIVE) || productInPM.getStatus().equals(ProductStatus.PLANNING)
                    || productInPM.getStatus().equals(ProductStatus.ON_ORDER) ) {
                productInPM.setStatus(ProductStatus.ARCHIVED);
                productManagementService.saveProduct(productInPM);
            }
            icRepository.delete(product);
            log.info("TotalItems deleteProduct(): Product {} is deleted successfully.", sku);
            return new ApiResponse<>(true, "Product " + sku + " is deleted successfully.");

        }catch (DataAccessException de){
            log.error("TotalItems deleteProduct(): Database error occurred.", de);
            throw new DatabaseException("Internal Database error occurred.", de);
        }catch (ResourceNotFoundException re){
            throw re;
        }catch (Exception e){
            log.error("TotalItems deleteProduct(): Unexpected error occurred.", e);
            throw new ServiceException("Internal Service Error", e);
        }
    }

    public void generateTotalItemsReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("All Inventory Products");
        createHeaderRowForInventoryDto(sheet);

        // Retrieve all inventory products & transform to DTO List
        List<InventoryControlResponse> allProducts = inventoryServiceHelper
                .convertToInventoryResponseList(
                        inventoryQueryService.getAllInventoryProducts(sortBy, sortDirection)
                );

        populateDataRowsForInventoryDto(sheet, allProducts);
        log.info("TotalItems (generateTotalItemsReport): {} products retrieved.", allProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

}
