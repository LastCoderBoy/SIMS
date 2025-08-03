package com.JK.SIMS.service.utilities;

import com.JK.SIMS.models.IC_models.inventoryData.InventoryDataDto;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

public class ExcelReporterHelper {
    private static final Logger logger = LoggerFactory.getLogger(ExcelReporterHelper.class);

    public static void writeWorkbookToResponse(HttpServletResponse response, XSSFWorkbook workbook) {
        try (ServletOutputStream outputStream = response.getOutputStream()) {
            workbook.write(outputStream);
            logger.info("ExcelReporterHelper: Report is downloaded with {} sheet(s)", workbook.getNumberOfSheets());
            workbook.close();
        } catch (IOException e) {
            logger.error("ExcelReporterHelper: Error writing Excel file", e);
        }
    }

    public static void createHeaderRowForInventoryDto(XSSFSheet sheet) {
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

    public static void populateDataRowsForInventoryDto(XSSFSheet sheet, List<InventoryDataDto> allProducts) {
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
}
