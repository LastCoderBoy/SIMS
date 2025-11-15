package com.JK.SIMS.service.productManagementService.excelReporter;

import com.JK.SIMS.models.PM_models.dtos.ProductManagementResponse;
import com.JK.SIMS.service.generalUtils.ExcelReporterHelper;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFRow;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.util.List;

public class ExcelReporterForPM {

    public static void createHeaderRow(XSSFSheet sheet) {
        // Create a bold font style
        XSSFWorkbook workbook = sheet.getWorkbook();
        CellStyle headerStyle = ExcelReporterHelper.createBoldHeaderStyle(workbook);

        // Create the header row
        XSSFRow row = sheet.createRow(0);

        // Create cells with the bold style
        createHeaderCell(row, 0, "Product ID", headerStyle);
        createHeaderCell(row, 1, "Category", headerStyle);
        createHeaderCell(row, 2, "Name", headerStyle);
        createHeaderCell(row, 3, "Location", headerStyle);
        createHeaderCell(row, 4, "Price", headerStyle);
        createHeaderCell(row, 5, "Status", headerStyle);
    }

    private static void createHeaderCell(XSSFRow row, int column, String value, CellStyle style) {
        Cell cell = row.createCell(column);
        cell.setCellValue(value);
        cell.setCellStyle(style);
    }

    public static void populateDataRows(XSSFSheet sheet, List<ProductManagementResponse> allProducts) {
        int dataRowIndex = 1;
        for (ProductManagementResponse pm : allProducts) {
            XSSFRow rowForData = sheet.createRow(dataRowIndex);
            rowForData.createCell(0).setCellValue(pm.getProductID());
            rowForData.createCell(1).setCellValue(pm.getCategory().toString());
            rowForData.createCell(2).setCellValue(pm.getName());
            rowForData.createCell(3).setCellValue(pm.getLocation() != null ? pm.getLocation() : "");
            rowForData.createCell(4).setCellValue(pm.getPrice().doubleValue());
            rowForData.createCell(5).setCellValue(pm.getStatus().toString());
            dataRowIndex++;
        }
    }
}
