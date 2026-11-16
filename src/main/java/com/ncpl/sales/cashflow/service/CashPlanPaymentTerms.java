package com.ncpl.sales.cashflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncpl.sales.cashflow.entity.InvoiceEntity;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.io.*;
import java.nio.file.*;
import java.math.*;
import java.time.LocalDate;
import java.util.*;

/** Finance-approved allocations used ONLY by the monthly Finance Cash Plan. */
@Service
public class CashPlanPaymentTerms {
    private final ObjectMapper mapper;
    private LiveCashPlanTerms live;
    @org.springframework.beans.factory.annotation.Autowired
    public void setLiveTerms(LiveCashPlanTerms live){this.live=live;}
    public LiveCashPlanTerms.Result resolve(List<InvoiceEntity> snapshot){List<Rule> configured=rules();return live==null?new LiveCashPlanTerms.Result(configured,"CONFIG",null,null,0):live.resolve(snapshot,configured);}

    @Value("${cashflow.finance-plan.payment-terms-file:config/cash-plan-payment-terms.json}")
    private String rulesFile="config/cash-plan-payment-terms.json";
    public CashPlanPaymentTerms(ObjectMapper mapper){this.mapper=mapper;}

    public List<Rule> rules(){
        try {
            Path path=Paths.get(rulesFile);
            InputStream stream=Files.exists(path)?Files.newInputStream(path):getClass().getResourceAsStream("/cash-plan-payment-terms.json");
            if(stream==null)throw new IllegalArgumentException("Cash plan payment-term rules are missing.");
            Rule[] values;
            try(InputStream input=stream){values=mapper.readValue(input,Rule[].class);}
            if(values==null)throw new IllegalArgumentException("Cash plan payment-term rules must be an array.");
            Set<String> keys=new HashSet<>();
            for(Rule rule:values){
                rule.validate();
                String key=key(rule.customerName,rule.invoiceNumber,LocalDate.parse(rule.invoiceDate));
                if(!keys.add(key))throw new IllegalArgumentException("Duplicate cash plan payment-term rule for "+rule.invoiceNumber);
            }
            return Arrays.asList(values);
        }catch(IOException e){throw new IllegalArgumentException("Cannot read cash plan payment-term rules: "+e.getMessage(),e);}
    }

    public Allocation allocation(InvoiceEntity bill,List<InvoiceEntity> snapshot,List<Rule> rules){
        for(Rule rule:rules){
            if(!rule.matches(bill))continue;
            if(rule.reviewReason!=null)return Allocation.review(bill.getInvoiceValue());
            boolean duplicate=snapshot.stream().anyMatch(other->other!=bill && eq(other.getInvoiceNumber(),bill.getInvoiceNumber()) && Objects.equals(other.getInvoiceDate(),bill.getInvoiceDate()) && RetentionLedgerClassifier.isRetentionRecord(other));
            if(bill.getInvoiceType()!=InvoiceEntity.InvoiceType.INFLOW || RetentionLedgerClassifier.isRetentionRecord(bill) || bill.getInvoiceValue()==null || rule.expectedOutstanding.compareTo(bill.getInvoiceValue())!=0 || duplicate)
                return Allocation.review(bill.getInvoiceValue());
            BigDecimal held=rule.held();
            return new Allocation(true,false,bill.getInvoiceValue().subtract(held),held,rule.expectedCollectionDate==null?null:LocalDate.parse(rule.expectedCollectionDate));
        }
        return new Allocation(false,false,bill.getInvoiceValue(),BigDecimal.ZERO,null);
    }

    private static String key(String party,String number,LocalDate date){return normalize(party)+"|"+normalize(number)+"|"+date;}
    private static String normalize(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
    private static boolean eq(String a,String b){return normalize(a).equals(normalize(b));}

    public static class Rule {
        public String invoiceNumber,customerName,invoiceDate,classification,expectedCollectionDate,approvedBy,narration,reviewReason,source;
        public BigDecimal expectedOutstanding,basicAmount,gstAmount,rounding,payableBasicPercent;
        public void validate(){
            if(normalize(invoiceNumber).isEmpty()||normalize(customerName).isEmpty()||invoiceDate==null||normalize(approvedBy).isEmpty()||!("TESTING_AND_COMMISSIONING".equals(classification)||"DEFERRED_PAYMENT_TERMS".equals(classification)))throw new IllegalArgumentException("Cash plan rules need an invoice, customer, date, Finance approval and milestone classification.");
            LocalDate.parse(invoiceDate);if(expectedCollectionDate!=null)LocalDate.parse(expectedCollectionDate);
            if(reviewReason!=null)return;
            if(expectedOutstanding==null||basicAmount==null||gstAmount==null||rounding==null||payableBasicPercent==null||basicAmount.signum()<=0||gstAmount.signum()<0||expectedOutstanding.signum()<=0||payableBasicPercent.signum()<=0||payableBasicPercent.compareTo(new BigDecimal("100"))>=0)throw new IllegalArgumentException("Invalid cash plan payment-term amounts for "+invoiceNumber);
            if(basicAmount.add(gstAmount).add(rounding).compareTo(expectedOutstanding)!=0||rounding.abs().compareTo(BigDecimal.ONE)>0||held().compareTo(expectedOutstanding)>0)throw new IllegalArgumentException("Cash plan payment-term values do not reconcile for "+invoiceNumber);
        }
        public boolean matches(InvoiceEntity b){return "TALLY".equals(b.getSource()) && key(customerName,invoiceNumber,LocalDate.parse(invoiceDate)).equals(key(b.getCustomerName(),b.getInvoiceNumber(),b.getInvoiceDate()));}
        public BigDecimal held(){return basicAmount.multiply(new BigDecimal("100").subtract(payableBasicPercent)).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP);}
    }
    public static class Allocation {
        public final boolean applied,review;public final BigDecimal normal,held;public final LocalDate expectedDate;
        Allocation(boolean applied,boolean review,BigDecimal normal,BigDecimal held,LocalDate expectedDate){this.applied=applied;this.review=review;this.normal=normal;this.held=held;this.expectedDate=expectedDate;}
        static Allocation review(BigDecimal amount){return new Allocation(false,true,BigDecimal.ZERO,amount==null?BigDecimal.ZERO:amount,null);}
    }
}
