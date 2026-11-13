package com.ncpl.sales.cashflow.service;

import com.ncpl.sales.cashflow.model.Invoice;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ExcelImportFormatTest {
    @TempDir
    Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"xls", "xlsx"})
    void importsBothFormatsThroughAllReaders(String extension) throws Exception {
        byte[] bytes;
        try (Workbook workbook = "xls".equals(extension) ? new HSSFWorkbook() : new XSSFWorkbook();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            CellStyle dateStyle = workbook.createCellStyle();
            dateStyle.setDataFormat(workbook.createDataFormat().getFormat("yyyy-mm-dd"));
            for (String name : new String[]{"Inflow", "Outflow"}) {
                Sheet sheet = workbook.createSheet(name);
                Row header = sheet.createRow(0);
                String[] columns = {"Customer", "Invoice", "Invoice Date", "Due Date", "Amount"};
                for (int i = 0; i < columns.length; i++) header.createCell(i).setCellValue(columns[i]);
                Row row = sheet.createRow(1);
                row.createCell(0).setCellValue("Example " + name);
                row.createCell(1).setCellValue(name + "-1");
                row.createCell(2).setCellValue(java.sql.Date.valueOf("2026-01-01"));
                row.createCell(3).setCellValue(java.sql.Date.valueOf("2026-01-31"));
                row.getCell(2).setCellStyle(dateStyle);
                row.getCell(3).setCellStyle(dateStyle);
                row.createCell(4).setCellValue(1250.50);
            }
            workbook.write(output);
            bytes = output.toByteArray();
        }
        // Uploads are persisted with an .xlsx suffix, even when their content is .xls.
        Path stored = directory.resolve("upload.xlsx");
        Files.write(stored, bytes);
        List<Invoice> invoices = new ExcelReaderService().readExcelFile(stored.toString());
        assertEquals(2, invoices.size());
        assertEquals(Invoice.InvoiceType.INFLOW, invoices.get(0).getType());
        assertEquals(Invoice.InvoiceType.OUTFLOW, invoices.get(1).getType());
        assertEquals(1250.50, invoices.get(0).getInvoiceValue());

        MockMultipartFile file = new MockMultipartFile("file", "invoices." + extension,
                "application/octet-stream", bytes);
        AgingAnalysisService.AgingAnalysisDTO aging = new AgingAnalysisService().analyzeAging(file);
        assertEquals(1, aging.getDetails().size());
        assertEquals("Inflow-1", aging.getDetails().get(0).getInvoice());
        assertEquals(1250.50, aging.getDetails().get(0).getAmount().doubleValue());
        java.util.List<com.ncpl.sales.cashflow.entity.InvoiceEntity> entities =
                new CustomerVendorAnalysisService().parseToEntities(file);
        assertEquals(2, entities.size());
        assertEquals("Inflow-1", entities.get(0).getInvoiceNumber());
        assertEquals("Outflow-1", entities.get(1).getInvoiceNumber());
        assertEquals(1250.50, entities.get(1).getInvoiceValue().doubleValue());
    }
}