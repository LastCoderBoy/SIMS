package com.JK.SIMS.service.InventoryControl_service;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryData;
import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import com.JK.SIMS.models.PM_models.ProductCategories;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.utilities.GlobalServiceHelper;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

import static com.JK.SIMS.service.utilities.ExcelReporterHelper.*;

@Service
public class LowStockService {
    private static final Logger logger = LoggerFactory.getLogger(LowStockService.class);

    private static final String DEFAULT_SORT_BY = "pmProduct.name";
    private static final String DEFAULT_SORT_DIRECTION = "asc";

    private final InventoryServiceHelper inventoryServiceHelper;
    private final GlobalServiceHelper globalServiceHelper;
    private final IC_repository icRepository;
    @Autowired
    public LowStockService(InventoryServiceHelper inventoryServiceHelper, GlobalServiceHelper globalServiceHelper, IC_repository icRepository) {
        this.inventoryServiceHelper = inventoryServiceHelper;
        this.globalServiceHelper = globalServiceHelper;
        this.icRepository = icRepository;
    }
    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryDataDto> getAllPaginatedLowStockRecords(String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get the data
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryData> inventoryPage = icRepository.getLowStockItems(pageable);
            return inventoryServiceHelper.transformToPaginatedDTOResponse(inventoryPage);
        }catch (DataAccessException da){
            logger.error("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products due to database error", da);
        }catch (Exception e){
            logger.error("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products: {}", e.getMessage(), e);
            throw new RuntimeException("LowStockService (getAllPaginatedLowStockRecords): Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryDataDto> searchInLowStockProducts(String text, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by(DEFAULT_SORT_BY).ascending());
                Page<InventoryData> inventoryData =
                        icRepository.searchInLowStockProducts(inputText.get().trim().toLowerCase(), pageable);

                logger.info("LowStockService (searchProduct): {} products retrieved.", inventoryData.getContent().size());
                return inventoryServiceHelper.transformToPaginatedDTOResponse(inventoryData) ;
            }
            logger.info("LowStockService (searchProduct): No search text provided. Retrieving first page with default size.");
            return getAllPaginatedLowStockRecords(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION, page,size);
        } catch (DataAccessException e) {
            logger.error("LowStockService (searchProduct): Database error: {}", e.getMessage(), e);
            throw new DatabaseException("LowStockService (searchProduct): Database error", e);
        } catch (Exception e) {
            logger.error("LowStockService (searchProduct): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("LowStockService (searchProduct): Failed to retrieve products", e);
        }
    }

    public PaginatedResponse<InventoryDataDto> filterLowStockProducts(ProductCategories category, String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);

            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            Page<InventoryData> resultPage = icRepository.getLowStockItemsByCategory(category, pageable);

            logger.info("TotalItems (filterProducts): {} products retrieved.", resultPage.getContent().size());
            return inventoryServiceHelper.transformToPaginatedDTOResponse(resultPage);
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
        List<InventoryDataDto> allLowStockProducts = getAllLowStockProducts(sortBy, sortDirection);
        createHeaderRowForInventoryDto(sheet);
        populateDataRowsForInventoryDto(sheet, allLowStockProducts);
        logger.info("TotalItems (generateReport): {} products retrieved.", allLowStockProducts.size());
        writeWorkbookToResponse(response, workbook);
    }

    public List<InventoryDataDto> getAllLowStockProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            List<InventoryData> inventoryList = icRepository.getLowStockItems(sort);

            // Convert to DTOs
            return inventoryList.stream().map(inventoryServiceHelper::convertToDTO).toList();
        } catch (DataAccessException da) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products due to database error: {}", da.getMessage(), da);
            throw new DatabaseException("TotalItems (getAllInventoryData): Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            logger.error("TotalItems (getAllInventoryData): Failed to retrieve products: {}", e.getMessage(), e);
            throw new ServiceException("TotalItems (getAllInventoryData): Failed to retrieve products", e);
        }
    }
}
