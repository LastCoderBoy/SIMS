package com.JK.SIMS.service.InventoryServices.lowStockService;

import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.InventoryServiceHelper;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventoryQueryService.InventoryQueryService;
import com.JK.SIMS.service.InventoryServices.inventoryCommonUtils.inventorySearchService.InventorySearchService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static com.JK.SIMS.service.generalUtils.ExcelReporterHelper.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LowStockService {
    private final InventoryServiceHelper inventoryServiceHelper;

    private final InventoryQueryService inventoryQueryService;
    private final InventorySearchService inventorySearchService;

    // =========== Repository ===========
    private final IC_repository icRepository;

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> getAllPaginatedLowStockRecords(String sortBy, String sortDirection, int page, int size) {
        Page<InventoryControlData> pagedLowStockItems = inventoryQueryService.getAllPagedLowStockItems(sortBy, sortDirection, page, size);
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(pagedLowStockItems);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> searchInLowStockProducts(String text, int page, int size, String sortBy, String sortDirection) {
        Page<InventoryControlData> pagedResponse =
                inventorySearchService.searchInLowStockProducts(text, page, size, sortBy, sortDirection); // if no text provided, will retrieve all products in page format
        log.info("LowStockService (searchProduct): {} products retrieved.", pagedResponse.getContent().size());
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(pagedResponse) ;
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> filterLowStockProducts(ProductCategories category, String sortBy,
                                                                              String sortDirection, int page, int size) {
        Page<InventoryControlData> pagedFilterResponse = inventorySearchService.filterLowStockProducts(category, sortBy, sortDirection, page, size);
        log.info("TotalItems (filterProducts): {} products retrieved.", pagedFilterResponse.getContent().size());
        return inventoryServiceHelper.transformToPaginatedInventoryResponse(pagedFilterResponse);
    }

    public void generateLowStockReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Low Stock Products");
        List<InventoryControlResponse> allLowStockProducts = getAllLowStockProducts(sortBy, sortDirection);
        createHeaderRowForInventoryDto(sheet);
        populateDataRowsForInventoryDto(sheet, allLowStockProducts);
        log.info("generateLowStockReport(): {} products retrieved.", allLowStockProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

    // Helper method for internal use
    private List<InventoryControlResponse> getAllLowStockProducts(String sortBy, String sortDirection) {
        List<InventoryControlData> lowStockList = inventoryQueryService.getAllLowStockProducts(sortBy, sortDirection);
        return lowStockList.stream().map(inventoryServiceHelper::convertToInventoryDTO).toList();
    }
}
