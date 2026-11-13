package com.ncpl.sales.cashflow.controller;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:recurring-page-test;DB_CLOSE_DELAY=-1",
    "cashflow.tally.sync.enabled=false",
    "CASHFLOW_BOOTSTRAP_ADMIN_USERNAME=recurring-test-admin",
    "CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD=Test-Only-Password-123",
    "CASHFLOW_RESET_ADMIN=false"
})
@AutoConfigureMockMvc
@WithMockUser(username = "recurring-test-admin", authorities = "ADMIN")
class UnusedModulesRoutingTest {
    @Autowired MockMvc mvc;

    @Test
    void disabledPagesAreNoLongerRoutable() throws Exception {
        for (String route : Arrays.asList("analytics", "due-date-tracker", "customer-vendor", "top-performers",
                "debtors-creditors", "mode-selection", "recurring-transactions")) {
            mvc.perform(get("/cashflow-analyzer/" + route)).andExpect(status().isNotFound());
        }
    }

    @Test
    void activeSidebarLinksRemainRoutableAndPipelineControlsAreAbsent() throws Exception {
        Set<String> expected = new HashSet<>(Arrays.asList("overview", "receivables", "payables", "advances",
            "cost-centres", "reconciliation", "aging", "finance-cash-plan", "cashflow-forecast"));
        for (String page : Arrays.asList("overview", "finance-cash-plan", "cashflow-forecast")) {
            String html = mvc.perform(get("/cashflow-analyzer/" + page)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
            Matcher navigation = Pattern.compile("<nav class=\"(?:finance|forecast)-nav\"[\\s\\S]*?</nav>").matcher(html);
            assertTrue(navigation.find(), "Sidebar missing from " + page);
            Matcher links = Pattern.compile("href=\"/cashflow-analyzer/([^\"#?]+)\"").matcher(navigation.group());
            Set<String> actual = new HashSet<>();
            while (links.find()) actual.add(links.group(1));
            assertEquals(expected, actual, "Unexpected sidebar routes on " + page);
        }
        for (String route : expected) {
            mvc.perform(get("/cashflow-analyzer/" + route)).andExpect(status().isOk());
        }
        mvc.perform(get("/cashflow-analyzer/cashflow-forecast"))
            .andExpect(content().string(not(containsString("id=\"pipelineViewBtn\""))))
            .andExpect(content().string(not(containsString("id=\"pipelineSummary\""))))
            .andExpect(content().string(not(containsString("id=\"pipelineDetailPanel\""))))
            .andExpect(content().string(containsString("id=\"weeklyCashflowChart\"")))
            .andExpect(content().string(containsString("id=\"exportForecastInvoices\"")))
            .andExpect(content().string(containsString("cashflow-forecast.js?v=20261112-unused-modules")));
    }
}
