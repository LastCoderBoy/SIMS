package com.JK.SIMS.service.utilities;

import com.JK.SIMS.models.inventoryData.InventoryDataDto;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
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
        // Create a bold font style
        XSSFWorkbook workbook = sheet.getWorkbook();
        Font headerFont = workbook.createFont();
        headerFont.setBold(true);

        // Create a cell style with the bold font
        CellStyle headerStyle = workbook.createCellStyle();
        headerStyle.setFont(headerFont);

        // Create the header row
        XSSFRow row = sheet.createRow(0);

        // Create cells with the bold style
        createHeaderCell(row, 0, "Product ID", headerStyle);
        createHeaderCell(row, 1, "SKU", headerStyle);
        createHeaderCell(row, 2, "Name", headerStyle);
        createHeaderCell(row, 3, "Category", headerStyle);
        createHeaderCell(row, 4, "Location", headerStyle);
        createHeaderCell(row, 5, "Price", headerStyle);
        createHeaderCell(row, 6, "Product Status", headerStyle);
        createHeaderCell(row, 7, "Current Stock", headerStyle);
        createHeaderCell(row, 8, "Minimum Stock", headerStyle);
        createHeaderCell(row, 9, "Inventory Status", headerStyle);
    }

    private static void createHeaderCell(XSSFRow row, int column, String value, org.apache.poi.ss.usermodel.CellStyle style) {
        org.apache.poi.ss.usermodel.Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void populateDataRowsForInventoryDto(XSSFSheet sheet, List<InventoryDataDto> allProducts) {
        int dataRowIndex = 1;
        for (InventoryDataDto ic : allProducts) {
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(ic.getProductID());
            rowForData.createCell(1).setCellValue(ic.getSKU());
            rowForData.createCell(2).setCellValue(ic.getProductName());
            rowForData.createCell(3).setCellValue(ic.getCategory().toString());
            rowForData.createCell(4).setCellValue(ic.getLocation() != null ? ic.getLocation() : "");
            rowForData.createCell(5).setCellValue(ic.getPrice().doubleValue());
            rowForData.createCell(6).setCellValue(ic.getProductStatus().toString());
            rowForData.createCell(7).setCellValue(ic.getCurrentStock());
            rowForData.createCell(8).setCellValue(ic.getMinLevel());
            rowForData.createCell(9).setCellValue(ic.getInventoryStatus().toString());
            dataRowIndex++;
        }
    }
}
