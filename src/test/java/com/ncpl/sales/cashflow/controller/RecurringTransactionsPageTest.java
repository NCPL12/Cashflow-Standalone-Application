package com.ncpl.sales.cashflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
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
class RecurringTransactionsPageTest {
    private static final String API = "/cashflow-analyzer/api/cashflow/recurring-transactions";
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;

    @Test
    void hidesUnusedPageAndPreservesExistingRecurringDataApi() throws Exception {
        mvc.perform(get("/cashflow-analyzer/overview"))
            .andExpect(status().isOk())
            .andExpect(content().string(not(containsString("recurringTransactionsModal"))))
            .andExpect(content().string(not(containsString("recurring-transactions.js"))));
        mvc.perform(get("/cashflow-analyzer/recurring-transactions"))
            .andExpect(status().isNotFound());
        mvc.perform(get("/cashflow-analyzer/js/recurring-transactions.js"))
            .andExpect(status().isOk()).andExpect(content().string(containsString("page disabled: not in use")));
        mvc.perform(get(API)).andExpect(status().isOk()).andExpect(jsonPath("$.count").value(0));
        String rent = "{\"name\":\"Office Rent\",\"description\":\"\",\"category\":\"RENT\","
            + "\"type\":\"MONTHLY\",\"dayOfMonth\":1,\"amount\":\"25000\",\"nextDueDate\":\"2026-12-01\",\"notes\":\"\"}";
        mvc.perform(post(API).contentType("application/json").content(rent)).andExpect(status().isForbidden());
        String created = mvc.perform(post(API).with(csrf()).contentType("application/json").content(rent))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.success").value(true))
            .andReturn().getResponse().getContentAsString();
        long id = mapper.readTree(created).get("id").asLong();
        mvc.perform(get(API)).andExpect(status().isOk())
            .andExpect(jsonPath("$.transactions[0].name").value("Office Rent"))
            .andExpect(jsonPath("$.transactions[0].amount").value(25000))
            .andExpect(jsonPath("$.totalMonthly").value(25000));
        mvc.perform(put(API + "/" + id).with(csrf()).contentType("application/json").content(rent.replace("25000", "26000")))
            .andExpect(status().isOk()).andExpect(jsonPath("$.transaction.amount").value(26000));
        mvc.perform(get(API)).andExpect(jsonPath("$.totalMonthly").value(26000));
        mvc.perform(delete(API + "/" + id).with(csrf())).andExpect(status().isOk());
        mvc.perform(get(API)).andExpect(jsonPath("$.count").value(0)).andExpect(jsonPath("$.totalMonthly").value(0));
    }
}