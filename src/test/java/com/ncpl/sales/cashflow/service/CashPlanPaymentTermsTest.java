package com.ncpl.sales.cashflow.service;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncpl.sales.cashflow.entity.*;import com.ncpl.sales.cashflow.repository.*;
import org.junit.jupiter.api.Test;import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;import static org.mockito.Mockito.*;import static org.mockito.ArgumentMatchers.*;
import java.math.BigDecimal;import java.time.*;import java.util.*;import java.nio.file.*;
class CashPlanPaymentTermsTest {
 BigDecimal n(String v){return new BigDecimal(v);}
 CashPlanPaymentTerms terms=new CashPlanPaymentTerms(new ObjectMapper());
 CashPlanPaymentTerms.Rule rule(){CashPlanPaymentTerms.Rule r=new CashPlanPaymentTerms.Rule();r.invoiceNumber="BLR2026361";r.customerName="KALPATARU PROJECTS INTERNATIONAL LTD - WB";r.invoiceDate="2026-09-21";r.expectedOutstanding=n("2053955.00");r.basicAmount=n("1740640");r.gstAmount=n("313315.20");r.rounding=n("-0.20");r.payableBasicPercent=n("85");r.classification="TESTING_AND_COMMISSIONING";r.approvedBy="Finance";r.validate();return r;}
 InvoiceEntity bill(){InvoiceEntity b=new InvoiceEntity(rule().customerName,"BLR2026361",LocalDate.of(2026,9,21),LocalDate.of(2026,11,5),n("2053955.00"),InvoiceEntity.InvoiceType.INFLOW,"TALLY");b.setRetention(false);return b;}
 CashPlanPaymentTerms.Allocation apply(InvoiceEntity b){return terms.allocation(b,Collections.singletonList(b),Collections.singletonList(rule()));}
 @Test void exactApprovedInvoiceSplitsBasicOnlyWithoutChangingStoredAmount(){InvoiceEntity b=bill();CashPlanPaymentTerms.Allocation a=apply(b);assertTrue(a.applied);assertEquals(n("1792859.00"),a.normal);assertEquals(n("261096.00"),a.held);assertEquals(b.getInvoiceValue(),a.normal.add(a.held));assertEquals(n("2053955.00"),b.getInvoiceValue());assertNull(a.expectedDate);}
 @Test void otherCustomerIsNotChanged(){InvoiceEntity b=bill();b.setCustomerName("Other customer");assertFalse(apply(b).applied);assertEquals(b.getInvoiceValue(),apply(b).normal);}
 @Test void otherReferenceAndDateAreNotChanged(){InvoiceEntity b=bill();b.setInvoiceNumber("BLR2026362");assertFalse(apply(b).applied);b=bill();b.setInvoiceDate(LocalDate.of(2026,9,22));assertFalse(apply(b).applied);}
 @Test void changedOutstandingNeedsReview(){InvoiceEntity b=bill();b.setInvoiceValue(n("1000000"));assertTrue(apply(b).review);assertEquals(BigDecimal.ZERO,apply(b).normal);}
 @Test void separateRetentionCannotBeAllocatedTwice(){InvoiceEntity b=bill();InvoiceEntity other=bill();other.setRetention(true);assertTrue(terms.allocation(b,Arrays.asList(b,other),Collections.singletonList(rule())).review);}
 @Test void retainedAndOutflowRecordsAreNotNormalCollections(){InvoiceEntity b=bill();b.setRetention(true);assertTrue(apply(b).review);b=bill();b.setInvoiceType(InvoiceEntity.InvoiceType.OUTFLOW);assertTrue(apply(b).review);}
 @Test void spreadsheetSourceCannotGetTallyRule(){InvoiceEntity b=bill();b.setSource("EXCEL");assertFalse(apply(b).applied);}
 @Test void invalidTotalsOrMissingApprovalFailValidation(){CashPlanPaymentTerms.Rule r=rule();r.gstAmount=n("1");assertThrows(IllegalArgumentException.class,r::validate);r=rule();r.approvedBy="";assertThrows(IllegalArgumentException.class,r::validate);}
 @Test void duplicateRulesCannotDoubleApply(@TempDir Path folder)throws Exception{CashPlanPaymentTerms.Rule r=rule();Path path=folder.resolve("rules.json");new ObjectMapper().writeValue(path.toFile(),Arrays.asList(r,r));org.springframework.test.util.ReflectionTestUtils.setField(terms,"rulesFile",path.toString());assertThrows(IllegalArgumentException.class,terms::rules);}
 FinanceCashPlanService plan(InvoiceEntity bill,CashPlanPaymentTerms.Rule rule){InvoiceRepository invoices=mock(InvoiceRepository.class);when(invoices.findBySource("TALLY")).thenReturn(Collections.singletonList(bill));CashPlanPaymentTerms rules=spy(terms);doReturn(Collections.singletonList(rule)).when(rules).rules();return new FinanceCashPlanService(invoices,mock(FinanceCashPlanEntryRepository.class),null,null,rules);}
 Map first(Map m){return (Map)((List)m.get("periods")).get(0);}
 @Test void normalCashPlanAmountReflectsAgreedTermsWithoutASeparateMappingStep(){InvoiceEntity b=bill();Map m=plan(b,rule()).buildPlan(YearMonth.now(),3,false);assertEquals(n("1792859.00"),first(m).get("totalCashIn"));assertEquals(n("261096.00"),m.get("commissioningAwaitingDate"));assertEquals(1,m.get("paymentTermsAppliedCount"));assertEquals(n("2053955.00"),b.getInvoiceValue());}
 @Test void expectedCommissioningCollectionIsAddedExactlyOnceInItsMonth(){CashPlanPaymentTerms.Rule r=rule();r.expectedCollectionDate=YearMonth.now().plusMonths(1).atDay(15).toString();Map m=plan(bill(),r).buildPlan(YearMonth.now(),3,false);List<Map> periods=(List)m.get("periods");assertEquals(n("1792859.00"),periods.get(0).get("totalCashIn"));assertEquals(n("261096.00"),periods.get(1).get("commissioningCashIn"));assertEquals(n("261096.00"),periods.get(1).get("totalCashIn"));assertEquals(BigDecimal.ZERO,m.get("commissioningAwaitingDate"));}
 @Test void retentionPlanIsIndependentOfCommissioningRules(){Map m=plan(bill(),rule()).buildPlan(YearMonth.now(),3,true);assertEquals(BigDecimal.ZERO,first(m).get("totalCashIn"));assertEquals(0,m.get("paymentTermsAppliedCount"));}
 @Test void noSilentForecastForChangedPaymentAllocations(){InvoiceEntity b=bill();b.setInvoiceValue(n("1000000"));Map m=plan(b,rule()).buildPlan(YearMonth.now(),3,false);assertEquals(BigDecimal.ZERO,first(m).get("totalCashIn"));assertEquals(n("1000000"),m.get("paymentTermsReviewBalance"));assertEquals(1,m.get("paymentTermsReviewCount"));}
}
