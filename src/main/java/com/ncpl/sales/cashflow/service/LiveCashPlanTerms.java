package com.ncpl.sales.cashflow.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ncpl.sales.cashflow.entity.InvoiceEntity;
import com.ncpl.sales.service.TallySyncCoordinator;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.math.*;
import java.nio.file.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.*;

/** Reads payment terms for Finance Cash Plan only; never changes invoices or Tally. */
@Service
public class LiveCashPlanTerms {
    private final TallyLiveService tally;
    private final ObjectMapper mapper;
    @Value("${cashflow.finance-plan.payment-terms-cache:data/cash-plan-payment-term-cache.json}")
    private String cacheFile="data/cash-plan-payment-term-cache.json";
    private Cache last;
    public LiveCashPlanTerms(TallyLiveService tally,ObjectMapper mapper){this.tally=tally;this.mapper=mapper;}

    public synchronized Result resolve(List<InvoiceEntity> snapshot,List<CashPlanPaymentTerms.Rule> configured){
        List<InvoiceEntity> eligible=new ArrayList<>();
        for(InvoiceEntity b:snapshot)if(b.getInvoiceType()==InvoiceEntity.InvoiceType.INFLOW&&!RetentionLedgerClassifier.isRetentionRecord(b)&&b.getInvoiceDate()!=null&&b.getInvoiceValue()!=null&&"TALLY".equals(b.getSource()))eligible.add(b);
        if(eligible.isEmpty())return new Result(Collections.emptyList(),"NO_RECEIVABLES",null,null,0);
        Cache previous=load();boolean acquired=false;
        try{
            acquired=TallySyncCoordinator.tryAcquire();
            if(!acquired)throw new IllegalStateException("Tally is busy with another synchronization. Refresh after it completes.");
            String company=tally.cashPlanCompanyName();
            Cache next=new Cache();next.server=tally.getServerUrl();next.company=company;next.snapshot=signature(eligible);
            if(previous!=null&&Objects.equals(previous.server,next.server)&&Objects.equals(previous.company,company))next.entries.putAll(previous.entries);
            LocalDate from=eligible.stream().map(InvoiceEntity::getInvoiceDate).min(LocalDate::compareTo).get();
            LocalDate to=eligible.stream().map(InvoiceEntity::getInvoiceDate).max(LocalDate::compareTo).get();
            List<TallyLiveService.CashPlanVoucher> headers=tally.cashPlanHeaders(company,from.minusDays(2),to.plusDays(2));
            if(headers.isEmpty())throw new IllegalStateException("Tally returned no sales-voucher metadata for the current outstanding snapshot.");
            Map<String,List<TallyLiveService.CashPlanVoucher>> index=new HashMap<>();
            for(TallyLiveService.CashPlanVoucher v:headers){add(index,v.party,v.number,v);if(!norm(v.reference).isEmpty()&&!norm(v.reference).equals(norm(v.number)))add(index,v.party,v.reference,v);}
            Map<String,Integer> invoiceUses=new HashMap<>();
            for(InvoiceEntity bill:eligible)for(TallyLiveService.CashPlanVoucher v:index.getOrDefault(key(bill.getCustomerName(),bill.getInvoiceNumber()),Collections.emptyList()))if(v.masterId!=null&&v.date!=null&&Math.abs(ChronoUnit.DAYS.between(v.date,bill.getInvoiceDate()))<=2)invoiceUses.merge(v.masterId.trim(),1,Integer::sum);
            Map<String,InvoiceEntity> selected=new LinkedHashMap<>();
            Map<String,TallyLiveService.CashPlanVoucher> selectedHeaders=new LinkedHashMap<>();
            List<CashPlanPaymentTerms.Rule> rules=new ArrayList<>();
            for(InvoiceEntity bill:eligible){
                List<TallyLiveService.CashPlanVoucher> matches=new ArrayList<>();
                for(TallyLiveService.CashPlanVoucher v:index.getOrDefault(key(bill.getCustomerName(),bill.getInvoiceNumber()),Collections.emptyList()))if(v.date!=null&&Math.abs(ChronoUnit.DAYS.between(v.date,bill.getInvoiceDate()))<=2)matches.add(v);
                if(matches.isEmpty())continue;
                if(matches.size()!=1){if(matches.stream().anyMatch(v->candidate(v.narration)))rules.add(review(bill,"Multiple vouchers match these payment terms."));continue;}
                TallyLiveService.CashPlanVoucher v=matches.get(0);
                if(!candidate(v.narration))continue;
                if(percent(v.narration)==null){rules.add(review(bill,"Narration payment terms are ambiguous or do not state basic percentage plus full GST."));continue;}
                if(v.masterId==null||!v.masterId.trim().matches("[0-9]+")){rules.add(review(bill,"Voucher identity is missing."));continue;}
                String id=v.masterId.trim();
                if(invoiceUses.getOrDefault(id,0)>1){rules.add(review(bill,"One voucher has multiple outstanding bill references; allocation review is required."));continue;}
                Entry old=next.entries.get(id);
                if(old!=null&&Objects.equals(old.version,version(v))&&old.rule!=null&&old.rule.reviewReason==null&&System.currentTimeMillis()-old.readAt<300000&&old.rule.matches(bill))rules.add(withDate(old.rule,configured));
                else {selected.put(id,bill);selectedHeaders.put(id,v);}
            }
            List<String> ids=new ArrayList<>(selected.keySet());
            for(int start=0;start<ids.size();start+=5){
                List<String> batch=ids.subList(start,Math.min(start+5,ids.size()));
                Map<String,List<TallyLiveService.CashPlanVoucher>> details=new HashMap<>();
                for(TallyLiveService.CashPlanVoucher v:tally.cashPlanAmounts(company,batch))if(v.masterId!=null)details.computeIfAbsent(v.masterId.trim(),x->new ArrayList<>()).add(v);
                for(String id:batch){
                    InvoiceEntity bill=selected.get(id);TallyLiveService.CashPlanVoucher header=selectedHeaders.get(id);
                    List<TallyLiveService.CashPlanVoucher> found=details.getOrDefault(id,Collections.emptyList());
                    CashPlanPaymentTerms.Rule rule;
                    if(found.size()!=1||!Objects.equals(version(found.get(0)),version(header)))rule=review(bill,"Voucher changed during refresh or ledger details are unavailable. Refresh again.");
                    else rule=derive(bill,found.get(0),configured);
                    Entry entry=new Entry();entry.version=version(header);entry.rule=rule;entry.readAt=System.currentTimeMillis();next.entries.put(id,entry);rules.add(withDate(rule,configured));
                }
            }
            next.active=rules;next.checkedAt=Instant.now().toString();last=next;
            String message=null;
            try{save(next);}catch(Exception e){message="Payment terms were read from Tally but the local terms cache could not be saved.";}
            return new Result(rules,"TALLY",next.checkedAt,message,headers.size());
        }catch(Exception e){
            if(previous!=null&&Objects.equals(previous.server,tally.getServerUrl())&&(norm(tally.cashPlanConfiguredCompany()).equals(norm(previous.company)) || (norm(tally.cashPlanConfiguredCompany()).isEmpty()&&Objects.equals(previous.snapshot,signature(eligible)))))
                return new Result(previous.active,"CACHED",previous.checkedAt,"Using last successful payment terms; new Tally edits have NOT been verified: "+e.getMessage(),0);
            return new Result(configured,"CONFIG_ONLY",null,"Automatic payment terms could not be checked. Only configured rules are applied; other invoices may still include deferred amounts: "+e.getMessage(),0);
        }finally{if(acquired)TallySyncCoordinator.release();}
    }

    private void add(Map<String,List<TallyLiveService.CashPlanVoucher>> index,String party,String number,TallyLiveService.CashPlanVoucher v){index.computeIfAbsent(key(party,number),x->new ArrayList<>()).add(v);}
    static boolean candidate(String text){String t=norm(text);return t.contains("%")&&(t.contains("BASIC")||t.contains("PAYMENT")||t.contains("COMMISSION")||t.contains("RETENTION")||t.contains("T&C"));}
    static BigDecimal percent(String text){
        if(text==null||!Pattern.compile("\\bbasic\\b",Pattern.CASE_INSENSITIVE).matcher(text).find()||!Pattern.compile("(?:100\\s*%\\s*GST|GST\\s*(?:full|100\\s*%)|full\\s*GST)",Pattern.CASE_INSENSITIVE).matcher(text).find())return null;
        String rest=text.replaceAll("(?i)100\\s*%\\s*GST","full GST").replaceAll("(?i)GST\\s*100\\s*%","GST full");
        Set<BigDecimal> values=new HashSet<>();Matcher m=Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*%").matcher(rest);
        while(m.find())values.add(new BigDecimal(m.group(1)).stripTrailingZeros());
        if(values.size()!=1)return null;BigDecimal value=values.iterator().next();return value.signum()>0&&value.compareTo(new BigDecimal("100"))<0?value:null;
    }
    static CashPlanPaymentTerms.Rule derive(InvoiceEntity bill,TallyLiveService.CashPlanVoucher v,List<CashPlanPaymentTerms.Rule> configured){
        if(!norm(v.party).equals(norm(bill.getCustomerName()))||v.date==null||Math.abs(ChronoUnit.DAYS.between(v.date,bill.getInvoiceDate()))>2)return review(bill,"Voucher identity does not match the outstanding invoice.");
        BigDecimal pct=percent(v.narration);if(pct==null)return review(bill,"Payment terms are ambiguous.");
        BigDecimal basic=BigDecimal.ZERO,gst=BigDecimal.ZERO,rounding=BigDecimal.ZERO,total=null;int partyLines=0;
        for(TallyLiveService.LedgerLine l:v.ledgers){
            String name=norm(l.name());BigDecimal amount=BigDecimal.valueOf(l.amount());
            if(name.equals(norm(bill.getCustomerName()))){partyLines++;if(amount.signum()>=0)return review(bill,"Expected a sales receivable party entry.");total=amount.abs();}
            else if(name.contains("ROUND")){if(amount.abs().compareTo(BigDecimal.ONE)>0)return review(bill,"Nonstandard rounding adjustment.");rounding=rounding.add(amount);}
            else if(name.contains("IGST")||name.contains("CGST")||name.contains("SGST")||name.contains("UTGST")){if(amount.signum()<0)return review(bill,"GST adjustment needs allocation review.");gst=gst.add(amount);}
            else if((name.startsWith("SUPPLY")||name.startsWith("SALES"))&&amount.signum()>0)basic=basic.add(amount);
            else if(amount.signum()!=0)return review(bill,"Additional ledger charges/deductions need payment-term review.");
        }
        if(partyLines!=1||total==null||basic.signum()<=0||basic.add(gst).add(rounding).compareTo(total)!=0)return review(bill,"Basic, GST and party total do not reconcile.");
        CashPlanPaymentTerms.Rule r=new CashPlanPaymentTerms.Rule();r.customerName=bill.getCustomerName();r.invoiceNumber=bill.getInvoiceNumber();r.invoiceDate=bill.getInvoiceDate().toString();r.expectedOutstanding=total;r.basicAmount=basic;r.gstAmount=gst;r.rounding=rounding;r.payableBasicPercent=pct;r.classification="DEFERRED_PAYMENT_TERMS";r.approvedBy="Read from Tally narration";r.narration=v.narration;r.source="TALLY";
        for(CashPlanPaymentTerms.Rule known:configured)if(norm(known.customerName).equals(norm(r.customerName))&&norm(known.narration).equals(norm(r.narration)))r.classification=known.classification;
        try{r.validate();}catch(IllegalArgumentException e){return review(bill,e.getMessage());}return r;
    }
    static CashPlanPaymentTerms.Rule review(InvoiceEntity b,String reason){CashPlanPaymentTerms.Rule r=new CashPlanPaymentTerms.Rule();r.customerName=b.getCustomerName();r.invoiceNumber=b.getInvoiceNumber();r.invoiceDate=b.getInvoiceDate().toString();r.classification="DEFERRED_PAYMENT_TERMS";r.approvedBy="Tally terms require review";r.reviewReason=reason;r.source="TALLY";return r;}
    private CashPlanPaymentTerms.Rule withDate(CashPlanPaymentTerms.Rule rule,List<CashPlanPaymentTerms.Rule> configured){
        CashPlanPaymentTerms.Rule result=mapper.convertValue(rule,CashPlanPaymentTerms.Rule.class);
        for(CashPlanPaymentTerms.Rule known:configured)if(norm(known.customerName).equals(norm(rule.customerName))&&norm(known.invoiceNumber).equals(norm(rule.invoiceNumber))&&Objects.equals(known.invoiceDate,rule.invoiceDate)&&sameMoney(known.expectedOutstanding,rule.expectedOutstanding)&&sameMoney(known.payableBasicPercent,rule.payableBasicPercent))result.expectedCollectionDate=known.expectedCollectionDate;
        return result;
    }
    private static boolean sameMoney(BigDecimal a,BigDecimal b){return a!=null&&b!=null&&a.compareTo(b)==0;}
    private Cache load(){if(last!=null)return last;try{Path p=Paths.get(cacheFile);if(Files.exists(p))last=mapper.readValue(p.toFile(),Cache.class);}catch(Exception ignored){}return last;}
    private void save(Cache cache)throws Exception{Path p=Paths.get(cacheFile).toAbsolutePath();Files.createDirectories(p.getParent());Path tmp=Files.createTempFile(p.getParent(),"cashplan-terms-",".json");try{mapper.writeValue(tmp.toFile(),cache);try{Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE);}catch(AtomicMoveNotSupportedException e){Files.move(tmp,p,StandardCopyOption.REPLACE_EXISTING);}}finally{Files.deleteIfExists(tmp);}}
    private static String norm(String s){return s==null?"":s.trim().replaceAll("\\s+"," ").toUpperCase(Locale.ROOT);}
    private static String key(String party,String number){return norm(party)+"|"+norm(number);}
    private static String version(TallyLiveService.CashPlanVoucher v){return norm(v.masterId)+"|"+norm(v.alterId)+"|"+v.date+"|"+norm(v.number)+"|"+norm(v.party)+"|"+norm(v.narration);}
    private static String signature(List<InvoiceEntity> bills){List<String> keys=new ArrayList<>();for(InvoiceEntity b:bills)keys.add(key(b.getCustomerName(),b.getInvoiceNumber())+"|"+b.getInvoiceDate()+"|"+b.getInvoiceValue().stripTrailingZeros().toPlainString());Collections.sort(keys);return String.join(";",keys);}
    public static class Entry{public String version;public CashPlanPaymentTerms.Rule rule;public long readAt;}
    public static class Cache{public String server,company,snapshot,checkedAt;public Map<String,Entry> entries=new LinkedHashMap<>();public List<CashPlanPaymentTerms.Rule> active=new ArrayList<>();}
    public static class Result{
        public final List<CashPlanPaymentTerms.Rule> rules;public final String source,checkedAt,warning;public final int checkedVouchers;
        Result(List<CashPlanPaymentTerms.Rule> rules,String source,String checkedAt,String warning,int checkedVouchers){this.rules=rules;this.source=source;this.checkedAt=checkedAt;this.warning=warning;this.checkedVouchers=checkedVouchers;}
    }
}
