package com.ncpl.sales.cashflow.service;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class StandalonePipelineSourceTest {
 @Test void standaloneDoesNotQueryErpSchemaOrTally() throws Exception {
  JdbcTemplate jdbc=mock(JdbcTemplate.class); TallyLiveService tally=mock(TallyLiveService.class);
  InvoicePipelineService service=new InvoicePipelineService(jdbc,tally);
  ReflectionTestUtils.setField(service,"standalone",true);
  Map<String,Object> result=service.load(LocalDate.of(2026,9,1),LocalDate.of(2026,9,30));
  assertEquals(false,result.get("available")); assertEquals(0,result.get("completeCount"));
  assertTrue(result.get("note").toString().contains("integrated ERP"));
  verifyZeroInteractions(jdbc,tally);
 }
}
