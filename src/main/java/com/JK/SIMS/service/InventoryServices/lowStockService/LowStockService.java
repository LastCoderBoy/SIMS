package com.JK.SIMS.service.InventoryServices.lowStockService;

import com.JK.SIMS.exception.DatabaseException;
import com.JK.SIMS.exception.ServiceException;
import com.JK.SIMS.exception.ValidationException;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.models.inventoryData.InventoryControlData;
import com.JK.SIMS.models.inventoryData.dtos.InventoryControlResponse;
import com.JK.SIMS.repository.InventoryControl_repo.IC_repository;
import com.JK.SIMS.service.InventoryServices.inventoryUtils.InventoryServiceHelper;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.utilities.EntityConstants.DEFAULT_SORT_DIRECTION;
import static com.JK.SIMS.service.utilities.ExcelReporterHelper.*;

@Service
@Slf4j
@RequiredArgsConstructor
public class LowStockService {
    private static final String DEFAULT_SORT_BY = "pmProduct.name";

    private final InventoryServiceHelper inventoryServiceHelper;
    private final GlobalServiceHelper globalServiceHelper;

    // =========== Repository ===========
    private final IC_repository icRepository;

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> getAllPaginatedLowStockRecords(String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get the data
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryControlData> inventoryPage = icRepository.getLowStockItems(pageable);
            return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(inventoryPage);
        }catch (DataAccessException da){
            log.error("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products due to database error", da);
        }catch (Exception e){
            log.error("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products: {}", e.getMessage(), e);
            throw new RuntimeException("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryControlResponse> searchInLowStockProducts(String text, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_BY).descending());
                Page<InventoryControlData> inventoryData =
                        icRepository.searchInLowStockProducts(inputText.get().trim().toLowerCase(), pageable);

                log.info("LowStockService (searchProduct): {} products retrieved.", inventoryData.getContent().size());
                return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(inventoryData) ;
            }
            log.info("LowStockService (searchProduct): No search text provided. Retrieving first page with default size.");
            return getAllPaginatedLowStockRecords(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION, page,size);
        } catch (DataAccessException e) {
            log.error("LowStockService (searchProduct): Database error: {}", e.getMessage(), e);
            throw new DatabaseException("LowStockService (searchProduct): Database error", e);
        } catch (Exception e) {
            log.error("LowStockService (searchProduct): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("LowStockService (searchProduct): Failed to retrieve products", e);
        }
    }

    public PaginatedResponse<InventoryControlResponse> filterLowStockProducts(ProductCategories category, String sortBy,
                                                                              String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);

            log.info("SortBy: {}, SortDirection: {}", sortBy, sortDirection);

            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<InventoryControlData> resultPage = icRepository.getLowStockItemsByCategory(category, pageable);

            log.info("TotalItems (filterProducts): {} products retrieved.", resultPage.getContent().size());
            return inventoryServiceHelper.transformToPaginatedInventoryDTOResponse(resultPage);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("TotalItems (filterProducts): Invalid filterBy value: " + iae.getMessage());
        } catch (DataAccessException da) {
            throw new DatabaseException("TotalItems (filterProducts): Database error", da.getCause());
        } catch (Exception e) {
            throw new ServiceException("TotalItems (filterProducts): Internal error", e);
        }
    }

    public void generateLowStockReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("Low Stock Products");
        List<InventoryControlResponse> allLowStockProducts = getAllLowStockProducts(sortBy, sortDirection);
        createHeaderRowForInventoryDto(sheet);
        populateDataRowsForInventoryDto(sheet, allLowStockProducts);
        log.info("TotalItems (generateTotalItemsReport): {} products retrieved.", allLowStockProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

    public List<InventoryControlResponse> getAllLowStockProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            List<InventoryControlData> inventoryList = icRepository.getLowStockItems(sort);

            // Convert to DTOs
            return inventoryList.stream().map(inventoryServiceHelper::convertToInventoryDTO).toList();
        } catch (DataAccessException da) {
            log.error("TotalItems (getAllInventoryData): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("TotalItems (getAllInventoryData): Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            log.error("TotalItems (getAllInventoryData): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("TotalItems (getAllInventoryData): Failed to retrieve products", e);
        }
    }
}
