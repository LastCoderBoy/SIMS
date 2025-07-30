package com.JK.SIMS.service.InventoryControl_service.totalItemsService;

import com.JK.SIMS.exceptionHandler.DatabaseException;
import com.JK.SIMS.exceptionHandler.ServiceException;
import com.JK.SIMS.exceptionHandler.ValidationException;
import com.JK.SIMS.models.IC_models.InventoryData;
import com.JK.SIMS.models.IC_models.InventoryDataDto;
import com.JK.SIMS.models.IC_models.InventoryDataStatus;
import com.JK.SIMS.models.PM_models.ProductManagementDTO;
import com.JK.SIMS.models.PaginatedResponse;
import com.JK.SIMS.repository.IC_repo.IC_repository;
import com.JK.SIMS.service.GlobalServiceHelper;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFRow;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class TotalItemsService {
    private static final Logger logger = LoggerFactory.getLogger(TotalItemsService.class);
    private static final String DEFAULT_SORT_BY = "pmProduct.name";
    private static final String DEFAULT_SORT_DIRECTION = "asc";

    private final GlobalServiceHelper globalServiceHelper;
    private final IC_repository icRepository;
    @Autowired
    public TotalItemsService(GlobalServiceHelper globalServiceHelper, IC_repository icRepository) {
        this.globalServiceHelper = globalServiceHelper;
        this.icRepository = icRepository;
    }

    public PaginatedResponse<InventoryDataDto> getPaginatedInventoryDto(String sortBy, String sortDirection, int page, int size) {
        try{
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get the data
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);
            Page<InventoryData> inventoryPage = icRepository.findAll(pageable);
            return transformToPaginatedDTOResponse(inventoryPage);
        } catch (DataAccessException da){
            throw new DatabaseException("TotalItems (getInventoryDataDTOList): Failed to retrieve products due to database error", da);
        } catch (Exception e){
            throw new ServiceException("TotalItems (getInventoryDataDTOList): Failed to retrieve products", e);
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<InventoryDataDto> searchProduct(String text, int page, int size) {
        try {
            Optional<String> inputText = Optional.ofNullable((text));
            if (inputText.isPresent() && !inputText.get().trim().isEmpty()) {
                Pageable pageable = PageRequest.of(page, size, Sort.by("pmProduct.name").ascending());
                Page<InventoryData> inventoryData = icRepository.searchProducts(inputText.get().trim().toLowerCase(), pageable);
                logger.info("TotalItems (searchProduct): {} products retrieved.", inventoryData.getContent().size());
                return transformToPaginatedDTOResponse(inventoryData) ;
            }
            logger.info("TotalItems (searchProduct): No search text provided. Retrieving first page with default size.");
            return getPaginatedInventoryDto(DEFAULT_SORT_BY, DEFAULT_SORT_DIRECTION, page,size);
        } catch (DataAccessException e) {
            throw new DatabaseException("TotalItems (searchProduct): Database error", e);
        } catch (Exception e) {
            throw new ServiceException("TotalItems (searchProduct): Failed to retrieve products", e);
        }
    }



    public PaginatedResponse<InventoryDataDto> filterProducts(String filterBy, String sortBy, String sortDirection, int page, int size) {
        try {
            globalServiceHelper.validatePaginationParameters(page, size);

            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create sort
            Sort sort = Sort.by(direction, sortBy);
            Pageable pageable = PageRequest.of(page, size, sort);

            // Handle filtering
            Page<InventoryData> resultPage;
            if (filterBy != null && !filterBy.trim().isEmpty()) {
                String[] filterParts = filterBy.trim().split(":");
                if (filterParts.length == 2) {
                    String field = filterParts[0].toLowerCase();
                    String value = filterParts[1];

                    resultPage = switch (field) {
                        case "status" -> {
                            InventoryDataStatus status = InventoryDataStatus.valueOf(value.toUpperCase());
                            yield icRepository.findByStatus(status, pageable);
                        }
                        case "stock" -> icRepository.findByStockLevel(Integer.parseInt(value), pageable);
                        default -> icRepository.findAll(pageable);
                    };
                } else {
                    // Use as a general search term
                    resultPage = icRepository.findByGeneralFilter(filterBy.trim().toLowerCase(), pageable);
                }
            } else {
                resultPage = icRepository.findAll(pageable);
            }

            logger.info("TotalItems (filterProducts): {} products retrieved.", resultPage.getContent().size());
            return transformToPaginatedDTOResponse(resultPage);
        } catch (IllegalArgumentException iae) {
            throw new ValidationException("TotalItems (filterProducts): Invalid filterBy value: " + iae.getMessage());
        } catch (DataAccessException da) {
            logger.error("TotalItems (filterProducts): Database error while filtering by '{}': {}",
                    filterBy, da.getMessage(), da);
            throw new DatabaseException("TotalItems (filterProducts): Database error", da.getCause());
        } catch (Exception e) {
            logger.error("TotalItems (filterProducts): Internal error while filtering by '{}': {})", filterBy, e.getMessage(), e);
            throw new ServiceException("TotalItems (filterProducts): Internal error", e);
        }
    }

    // Helper methods

    private PaginatedResponse<InventoryDataDto> transformToPaginatedDTOResponse(Page<InventoryData> inventoryPage){
        PaginatedResponse<InventoryDataDto> dtoResponse = new PaginatedResponse<>();
        dtoResponse.setContent(inventoryPage.getContent().stream().map(this::convertToDTO).toList());
        dtoResponse.setTotalPages(inventoryPage.getTotalPages());
        dtoResponse.setTotalElements(inventoryPage.getTotalElements());
        logger.info("TotalItems (getInventoryDataDTOList): {} products retrieved.", inventoryPage.getContent().size());
        return dtoResponse;
    }

    private InventoryDataDto convertToDTO(InventoryData currentProduct) {
        InventoryDataDto inventoryDataDTO = new InventoryDataDto();
        inventoryDataDTO.setInventoryData(currentProduct);
        inventoryDataDTO.setProductName(currentProduct.getPmProduct().getName());
        inventoryDataDTO.setCategory(currentProduct.getPmProduct().getCategory());
        return inventoryDataDTO;
    }

    public List<InventoryDataDto> getAllInventoryProducts(String sortBy, String sortDirection) {
        try {
            // Parse sort direction
            Sort.Direction direction = sortDirection.equalsIgnoreCase("desc") ?
                    Sort.Direction.DESC : Sort.Direction.ASC;

            // Create the sort and get all data
            Sort sort = Sort.by(direction, sortBy);
            List<InventoryData> inventoryList = icRepository.findAll(sort);

            // Convert to DTOs
            return inventoryList.stream().map(this::convertToDTO).toList();
        } catch (DataAccessException da) {
            throw new DatabaseException("TotalItems (getAllInventoryData): Failed to retrieve products due to database error", da);
        } catch (Exception e) {
            throw new ServiceException("TotalItems (getAllInventoryData): Failed to retrieve products", e);
        }
    }

    public void generateReport(HttpServletResponse response, String sortBy, String sortDirection) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        XSSFSheet sheet = workbook.createSheet("All Inventory Products");
        List<InventoryDataDto> allProducts = getAllInventoryProducts(sortBy, sortDirection);
        createHeaderRow(sheet);
        populateDataRows(sheet, allProducts);
        writeWorkbookToResponse(response, workbook);
    }

    private void createHeaderRow(XSSFSheet sheet) {
        XSSFRow row = sheet.createRow(0);
        row.createCell(0).setCellValue("SKU");
        row.createCell(1).setCellValue("Product ID");
        row.createCell(2).setCellValue("Name");
        row.createCell(3).setCellValue("Category");
        row.createCell(4).setCellValue("Location");
        row.createCell(5).setCellValue("Price");
        row.createCell(6).setCellValue("Product Status");
        row.createCell(7).setCellValue("Current Stock");
        row.createCell(8).setCellValue("Minimum Stock");
        row.createCell(9).setCellValue("Inventory Status");
    }

    private void populateDataRows(XSSFSheet sheet, List<InventoryDataDto> allProducts) {
        int dataRowIndex = 1;
        for (InventoryDataDto ic : allProducts) {
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(ic.getInventoryData().getSKU());
            rowForData.createCell(1).setCellValue(ic.getInventoryData().getPmProduct().getProductID());
            rowForData.createCell(2).setCellValue(ic.getProductName());
            rowForData.createCell(3).setCellValue(ic.getCategory().toString());
            rowForData.createCell(4).setCellValue(ic.getInventoryData().getLocation() != null ? ic.getInventoryData().getLocation() : "");
            rowForData.createCell(5).setCellValue(ic.getInventoryData().getPmProduct().getPrice().doubleValue());
            rowForData.createCell(6).setCellValue(ic.getInventoryData().getPmProduct().getStatus().toString());
            rowForData.createCell(7).setCellValue(ic.getInventoryData().getCurrentStock());
            rowForData.createCell(8).setCellValue(ic.getInventoryData().getMinLevel());
            rowForData.createCell(9).setCellValue(ic.getInventoryData().getStatus().toString());
            dataRowIndex++;
        }
    }

    private void writeWorkbookToResponse(HttpServletResponse response, XSSFWorkbook workbook) {
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            logger.info("TotalItems (generateReport): Total Items report is downloaded with {} data size", workbook.getNumberOfSheets());
            workbook.close();
        } catch (IOException e) {
            logger.error("PM (generateReport): Error writing Excel file", e);
        }
    }

}
