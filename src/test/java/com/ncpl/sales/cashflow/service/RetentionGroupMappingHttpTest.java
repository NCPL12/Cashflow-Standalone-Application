package com.ncpl.sales.cashflow.service;
import com.sun.net.httpserver.HttpServer;
import com.ncpl.sales.cashflow.model.Invoice;
import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;import java.math.BigDecimal;
import org.junit.jupiter.api.Test;import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
class RetentionGroupMappingHttpTest {
 @Test void groupMembersAndUnallocatedBalancesReconcileWithoutNameGuessing() throws Exception {
  HttpServer http=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  http.createContext("/",ex->{String req=new String(read(ex.getRequestBody()),StandardCharsets.UTF_8);String body="";
   assertTrue(req.contains("<TALLYREQUEST>Export</TALLYREQUEST>"));
   if(req.contains("<TYPE>Group</TYPE>"))body="<GROUP NAME=\"Retention Amounts\"><PARENT>Sundry Debtors</PARENT></GROUP><GROUP NAME=\"Project A\"><PARENT>Retention Amounts</PARENT></GROUP><GROUP NAME=\"Sundry Debtors\"><PARENT>Current Assets</PARENT></GROUP>";
   if(req.contains("<TYPE>Ledger</TYPE>"))body="<LEDGER NAME=\"Against Handing Over\"><PARENT>Project A</PARENT><CLOSINGBALANCE>-500</CLOSINGBALANCE></LEDGER><LEDGER NAME=\"Retention in name only\"><PARENT>Sundry Debtors</PARENT><CLOSINGBALANCE>-300</CLOSINGBALANCE></LEDGER><LEDGER NAME=\"Credit adjusted project\"><PARENT>Retention Amounts</PARENT><CLOSINGBALANCE>-100</CLOSINGBALANCE></LEDGER><LEDGER NAME=\"Installation\"><PARENT>Retention Amounts</PARENT><CLOSINGBALANCE>-200</CLOSINGBALANCE></LEDGER>";
   if(req.contains("<TYPE>Bill</TYPE>"))body="<BILL NAME=\"B1\"><PARENT>Against Handing Over</PARENT><BILLDATE>20261101</BILLDATE><CLOSINGBALANCE>-150</CLOSINGBALANCE></BILL><BILL NAME=\"C1\"><PARENT>Credit adjusted project</PARENT><BILLDATE>20261101</BILLDATE><CLOSINGBALANCE>-120</CLOSINGBALANCE></BILL><BILL NAME=\"B2\"><PARENT>Retention in name only</PARENT><BILLDATE>20261101</BILLDATE><CLOSINGBALANCE>-300</CLOSINGBALANCE></BILL>";
   byte[] out=("<ENVELOPE><STATUS>1</STATUS><COLLECTION>"+body+"</COLLECTION></ENVELOPE>").getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(200,out.length);ex.getResponseBody().write(out);ex.close();});http.start();
  try{TallyLiveService client=new TallyLiveService();ReflectionTestUtils.setField(client,"serverUrl","http://127.0.0.1:"+http.getAddress().getPort());List<Invoice> data=client.fetchOutstandingBills();
   double retained=data.stream().filter(RetentionLedgerClassifier::isRetentionRecord).mapToDouble(Invoice::getInvoiceValue).sum();assertEquals(800d,retained);
   assertEquals(300d,data.stream().filter(i->!RetentionLedgerClassifier.isRetentionRecord(i)).mapToDouble(Invoice::getInvoiceValue).sum());
   List<Invoice> residual=new ArrayList<>();for(Invoice i:data)if(Boolean.TRUE.equals(i.getRetentionBalanceOnly()))residual.add(i);assertEquals(3,residual.size());for(Invoice i:residual){assertNull(i.getInvoiceDate());assertNull(i.getDueDate());assertTrue(i.getInvoiceNumber().startsWith("Unallocated retention balance"));}
   assertEquals(530d,residual.stream().mapToDouble(Invoice::getInvoiceValue).sum());
  }finally{http.stop(0);}
 }
 static byte[] read(java.io.InputStream in)throws java.io.IOException{java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();byte[] b=new byte[1024];int n;while((n=in.read(b))!=-1)out.write(b,0,n);return out.toByteArray();}
}
