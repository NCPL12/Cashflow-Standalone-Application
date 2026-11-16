package com.ncpl.sales.cashflow.service;
import com.ncpl.sales.cashflow.entity.InvoiceEntity;
import com.ncpl.sales.cashflow.repository.InvoiceRepository;
import com.ncpl.sales.cashflow.controller.CashFlowApiController;
import com.ncpl.sales.cashflow.controller.ProjectedCashflowController;
import org.junit.jupiter.api.Test;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import java.io.ByteArrayInputStream;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class RetentionSeparationTest {
 InvoiceEntity bill(long id,String party,String value,InvoiceEntity.InvoiceType type){InvoiceEntity b=new InvoiceEntity();b.setId(id);b.setCustomerName(party);b.setInvoiceNumber("B"+id);b.setInvoiceValue(new BigDecimal(value));b.setInvoiceType(type);b.setInvoiceDate(LocalDate.now().minusDays(2));b.setDueDate(LocalDate.now().plusDays(3));b.setExpectedCashDate(LocalDate.now().plusDays(2));return b;}
 List<InvoiceEntity> sample(){return Arrays.asList(bill(1,"Customer","1000",InvoiceEntity.InvoiceType.INFLOW),bill(2,"Retention-Customer","250",InvoiceEntity.InvoiceType.INFLOW),bill(3,"Vendor","600",InvoiceEntity.InvoiceType.OUTFLOW),bill(4,"Retainage Vendor","100",InvoiceEntity.InvoiceType.OUTFLOW));}
 CashFlowApiController api(InvoiceRepository repo){return new CashFlowApiController(null,null,null,null,null,null,null,new ExcelExportService(),null,null,null,repo,null);}
 @Test void normalAndRetentionTablesKeepInclusiveTileMetadataSeparate(){InvoiceRepository repo=mock(InvoiceRepository.class);when(repo.findBySource("TALLY")).thenReturn(sample());Map normal=(Map)api(repo).getTallyOutstanding("receivables",false).getBody();Map retention=(Map)api(repo).getTallyOutstanding("receivables",true).getBody();assertEquals(1000d,normal.get("total"));assertEquals(250d,retention.get("total"));assertEquals(1250d,normal.get("allTotal"));assertEquals(2,normal.get("allCount"));assertEquals(600d,((Map)api(repo).getTallyOutstanding("payables",false).getBody()).get("total"));assertEquals(100d,((Map)api(repo).getTallyOutstanding("payables",true).getBody()).get("total"));}
 @Test void outstandingExportsContainOnlyTheSelectedView()throws Exception{InvoiceRepository repo=mock(InvoiceRepository.class);when(repo.findBySource("TALLY")).thenReturn(sample());for(boolean retained:new boolean[]{false,true}){byte[] bytes=(byte[])api(repo).exportTallyOutstanding("receivables","all","all","all","",retained).getBody();try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(bytes))){assertEquals(3,book.getSheetAt(0).getLastRowNum());assertEquals(retained?250d:1000d,book.getSheetAt(0).getRow(3).getCell(4).getNumericCellValue());assertEquals(retained?"Retention Amount":"Outstanding Amount",book.getSheetAt(0).getRow(2).getCell(4).getStringCellValue());}}}
 @Test void plannedRetentionNeverLeaksIntoNormalCashflow(){ProjectedCashflowService s=new ProjectedCashflowService();LocalDate d=LocalDate.now();Map normal=s.project(sample(),d,d.plusDays(7),"daily",BigDecimal.TEN,d,d);Map retained=s.project(sample(),d,d.plusDays(7),"daily",BigDecimal.TEN,d,d,true);assertEquals(new BigDecimal("1000.00"),normal.get("totalReceivables"));assertEquals(new BigDecimal("600.00"),normal.get("totalPayables"));assertEquals(new BigDecimal("250.00"),retained.get("totalReceivables"));assertEquals(new BigDecimal("100.00"),retained.get("totalPayables"));assertNull(retained.get("closingBalance"));}
 @Test void forecastExportCannotMixIdsFromTheOtherView()throws Exception{InvoiceRepository repo=mock(InvoiceRepository.class);when(repo.findBySource("TALLY")).thenReturn(sample());ProjectedCashflowController c=new ProjectedCashflowController(repo,null,new ProjectedCashflowService());for(boolean retained:new boolean[]{false,true}){try(XSSFWorkbook book=new XSSFWorkbook(new ByteArrayInputStream(c.export(Arrays.asList(1L,2L,3L,4L),retained).getBody()))){assertEquals(2,book.getSheetAt(0).getLastRowNum());assertEquals(retained?250d:1000d,book.getSheetAt(0).getRow(1).getCell(6).getNumericCellValue());}}}
}
