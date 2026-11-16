package com.ncpl.sales.cashflow.controller;

import com.ncpl.sales.cashflow.dto.OverviewAnalysisDTO;
import com.ncpl.sales.cashflow.entity.InvoiceEntity;
import com.ncpl.sales.cashflow.model.Invoice;
import com.ncpl.sales.cashflow.repository.InvoiceRepository;
import com.ncpl.sales.cashflow.service.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RetentionLegacyViewsTest {
    private final InvoiceRepository repo=mock(InvoiceRepository.class);
    private final SessionDataService session=mock(SessionDataService.class);
    private final AgingAnalysisService aging=mock(AgingAnalysisService.class);
    private final CustomerVendorAnalysisService parties=mock(CustomerVendorAnalysisService.class);
    private final CashFlowAnalysisService overview=mock(CashFlowAnalysisService.class);
    private final CashFlowApiController api=new CashFlowApiController(null,overview,null,aging,parties,session,null,new ExcelExportService(),null,null,null,repo,null);
    private InvoiceEntity bill(String party,int value,InvoiceEntity.InvoiceType type){
        InvoiceEntity b=new InvoiceEntity();b.setCustomerName(party);b.setInvoiceNumber(party);b.setInvoiceDate(LocalDate.now().minusDays(20));b.setDueDate(LocalDate.now().minusDays(10));b.setInvoiceValue(BigDecimal.valueOf(value));b.setInvoiceType(type);return b;
    }
    private void data(){when(session.isTallyMode()).thenReturn(true);when(repo.findBySource("TALLY")).thenReturn(Arrays.asList(bill("Customer",1000,InvoiceEntity.InvoiceType.INFLOW),bill("Retention-Customer",250,InvoiceEntity.InvoiceType.INFLOW),bill("Vendor",600,InvoiceEntity.InvoiceType.OUTFLOW),bill("Retainage Vendor",100,InvoiceEntity.InvoiceType.OUTFLOW)));}
    private void normal(List<Invoice> items){assertEquals(2,items.size());assertTrue(items.stream().noneMatch(i->RetentionLedgerClassifier.isRetention(i.getCustomerName())));}
    @Test void agingTableBypassesInclusiveCachedData(){data();AgingAnalysisService.AgingAnalysisDTO stale=mock(AgingAnalysisService.AgingAnalysisDTO.class),fresh=mock(AgingAnalysisService.AgingAnalysisDTO.class);when(session.getAgingAnalysisData()).thenReturn(stale);when(aging.analyzeAging(anyList())).thenReturn(fresh);assertSame(fresh,api.getCachedAgingAnalysis(null,null,null,null).getBody());ArgumentCaptor<List> captor=ArgumentCaptor.forClass(List.class);verify(aging).analyzeAging(captor.capture());normal(captor.getValue());}
    @Test void agingExcelRecomputesNormalRowsInsteadOfInclusiveCache()throws Exception{data();when(session.getAgingAnalysisData()).thenReturn(mock(AgingAnalysisService.AgingAnalysisDTO.class));AgingAnalysisService.AgingAnalysisDTO fresh=mock(AgingAnalysisService.AgingAnalysisDTO.class);when(aging.analyzeAging(anyList())).thenReturn(fresh);when(aging.generateDetailedReport(fresh)).thenReturn(new byte[]{1,2});assertEquals(200,api.downloadAgingReport(null,null,null,null).getStatusCodeValue());ArgumentCaptor<List> captor=ArgumentCaptor.forClass(List.class);verify(aging).analyzeAging(captor.capture());normal(captor.getValue());verify(aging).generateDetailedReport(fresh);}
    @Test void debtorCreditorDetailsExcludeBothRetentionDirections(){data();Map body=(Map)api.getDebtorsCreditors(null,null).getBody();List<Map> debtors=(List<Map>)body.get("highestDebtors"),creditors=(List<Map>)body.get("highestCreditors");assertEquals(1,debtors.size());assertEquals("Customer",debtors.get(0).get("name"));assertEquals(1000d,debtors.get(0).get("amount"));assertEquals(1,creditors.size());assertEquals("Vendor",creditors.get(0).get("name"));assertEquals(600d,creditors.get(0).get("amount"));}
    @Test void customerVendorDetailsBypassInclusiveCache(){data();CustomerVendorAnalysisService.CustomerVendorAnalysisDTO stale=mock(CustomerVendorAnalysisService.CustomerVendorAnalysisDTO.class),fresh=mock(CustomerVendorAnalysisService.CustomerVendorAnalysisDTO.class);when(session.getCustomerVendorData()).thenReturn(stale);when(parties.analyze(anyList())).thenReturn(fresh);assertSame(fresh,api.getCachedCustomerVendor(null,null).getBody());ArgumentCaptor<List> captor=ArgumentCaptor.forClass(List.class);verify(parties).analyze(captor.capture());normal(captor.getValue());}
    @Test void overviewAnalysisStillReceivesAllInvoices(){data();OverviewAnalysisDTO inclusive=new OverviewAnalysisDTO();when(overview.analyzeForOverview(anyList())).thenReturn(inclusive);assertSame(inclusive,api.getCachedOverview(null,null,null,null).getBody());ArgumentCaptor<List> captor=ArgumentCaptor.forClass(List.class);verify(overview).analyzeForOverview(captor.capture());assertEquals(4,captor.getValue().size());}
}
