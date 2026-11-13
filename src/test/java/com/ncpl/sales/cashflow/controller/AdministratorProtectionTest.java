package com.ncpl.sales.cashflow.controller;

import com.ncpl.sales.security.User;
import com.ncpl.sales.security.UserRepo;
import com.ncpl.sales.security.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:administrator-guard-test;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
    "cashflow.tally.sync.enabled=false",
    "CASHFLOW_BOOTSTRAP_ADMIN_USERNAME=guard-admin",
    "CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD=TestOnly-Admin-123!",
    "CASHFLOW_RESET_ADMIN=false"
})
@AutoConfigureMockMvc
@WithMockUser(username = "guard-admin", authorities = "ADMIN")
class AdministratorProtectionTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepo users;
    @Autowired UserService service;
    private User administrator;
    private User finance;

    @BeforeEach
    void createIsolatedAccounts() {
        users.deleteAll();
        administrator = create("guard-admin", "ADMIN", true);
        create("disabled-admin", "SUPER ADMIN", false);
        finance = create("guard-finance", "CASHFLOW_USER", true);
    }

    @ParameterizedTest
    @CsvSource({"ADMIN,delete", "ADMIN,disable", "ADMIN,demote",
                "SUPER ADMIN,delete", "SUPER ADMIN,disable", "SUPER ADMIN,demote"})
    void legacyApiCannotRemoveTheLastEnabledAdministrator(String role, String action) throws Exception {
        administrator.setRole(role);
        administrator = users.saveAndFlush(administrator);
        String passwordHash = administrator.getPassword();
        mvc.perform(change(administrator, action)).andExpect(status().isBadRequest())
            .andExpect(content().string(containsString("Keep at least one enabled administrator.")));
        User retained = users.findById(administrator.getId()).get();
        assertTrue(retained.isEnabled());
        assertEquals(role, retained.getRole());
        assertEquals("guard-admin", retained.getName());
        assertEquals(passwordHash, retained.getPassword());
        assertEquals(0, users.countOtherEnabledAdministrators(retained.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"delete", "disable", "demote"})
    void legacyApiAllowsRemovalWhenAnotherEnabledAdministratorRemains(String action) throws Exception {
        User backup = create("backup-admin", "SUPER ADMIN", true);
        mvc.perform(change(administrator, action)).andExpect(status().isOk());
        if ("delete".equals(action)) {
            assertFalse(users.existsById(administrator.getId()));
        } else {
            User changed = users.findById(administrator.getId()).get();
            if ("disable".equals(action)) assertFalse(changed.isEnabled());
            else assertEquals("CASHFLOW_USER", changed.getRole());
        }
        assertTrue(users.findById(backup.getId()).get().isEnabled());
        assertEquals(1, enabledAdministrators());
    }

    @Test
    void lastAdministratorCanEditProfileAndSwitchBetweenAdministratorRoles() throws Exception {
        String passwordHash = administrator.getPassword();
        administrator.setRole("SUPER ADMIN");
        mvc.perform(change(administrator, "profile")).andExpect(status().isOk());
        User changed = users.findById(administrator.getId()).get();
        assertEquals("Updated name", changed.getName());
        assertEquals("SUPER ADMIN", changed.getRole());
        assertEquals(passwordHash, changed.getPassword());
        assertTrue(changed.isEnabled());
    }

    @Test
    void ordinaryUsersCanStillBeDisabledAndDeleted() throws Exception {
        mvc.perform(change(finance, "disable")).andExpect(status().isOk());
        assertFalse(users.findById(finance.getId()).get().isEnabled());
        mvc.perform(change(finance, "delete")).andExpect(status().isOk());
        assertFalse(users.existsById(finance.getId()));
        assertTrue(users.findById(administrator.getId()).get().isEnabled());
    }

    @Test
    void newerScreenUsesSharedServiceAndStillPreventsSelfDisable() throws Exception {
        mvc.perform(post("/cashflow-analyzer/users/" + administrator.getId() + "/enabled")
                .param("enabled", "false").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attribute("error", "You cannot disable your own account."));
        User backup = create("backup-admin", "SUPER ADMIN", true);
        mvc.perform(post("/cashflow-analyzer/users/" + backup.getId() + "/enabled")
                .param("enabled", "false").with(csrf()))
            .andExpect(status().is3xxRedirection())
            .andExpect(flash().attributeExists("message"));
        assertFalse(users.findById(backup.getId()).get().isEnabled());
        assertEquals(1, enabledAdministrators());
    }

    @Test
    void legacyMutationsStillRequireCsrfAndAdministratorAuthorization() throws Exception {
        mvc.perform(delete("/api/users/" + administrator.getId())).andExpect(status().isForbidden());
        mvc.perform(change(administrator, "delete").with(user("guard-finance")
                .authorities(new SimpleGrantedAuthority("CASHFLOW_USER"))))
            .andExpect(status().isForbidden());
        assertTrue(users.existsById(administrator.getId()));
    }

    @ParameterizedTest
    @CsvSource({"delete,disable", "disable,demote", "demote,delete"})
    void concurrentRemovalsKeepOneEnabledAdministrator(String firstAction, String secondAction) throws Exception {
        User backup = create("backup-admin", "SUPER ADMIN", true);
        ExecutorService workers = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Boolean> first = workers.submit(() -> removeAfterStart(administrator, firstAction, ready, start));
            Future<Boolean> second = workers.submit(() -> removeAfterStart(backup, secondAction, ready, start));
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
            assertEquals(1, enabledAdministrators());
        } finally {
            start.countDown();
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private boolean removeAfterStart(User target, String action, CountDownLatch ready, CountDownLatch start) throws Exception {
        ready.countDown();
        assertTrue(start.await(10, TimeUnit.SECONDS));
        try {
            if ("delete".equals(action)) service.deleteUser(target.getId());
            else if ("disable".equals(action)) service.setUserEnabled(target.getId(), false);
            else {
                target.setRole("CASHFLOW_USER");
                service.updateUser(target);
            }
            return true;
        } catch (IllegalArgumentException ex) {
            assertEquals("Keep at least one enabled administrator.", ex.getMessage());
            return false;
        }
    }

    private MockHttpServletRequestBuilder change(User target, String action) {
        if ("delete".equals(action)) return delete("/api/users/" + target.getId()).with(csrf());
        return put("/api/users/" + target.getId()).with(csrf())
            .param("username", target.getUsername())
            .param("role", "demote".equals(action) ? "CASHFLOW_USER" : target.getRole())
            .param("enabled", "disable".equals(action) ? "false" : Boolean.toString(target.isEnabled()))
            .param("number", "").param("emailId", target.getEmailId()).param("name", "Updated name");
    }

    private User create(String username, String role, boolean enabled) {
        User user = new User();
        user.setUsername(username);
        user.setName(username);
        user.setPassword("TestOnly-Admin-123!");
        user.setRole(role);
        user.setEnabled(enabled);
        user.setEmailId(username + "@example.test");
        return service.saveUser(user);
    }

    private long enabledAdministrators() {
        return users.findAll().stream().filter(user -> user.isEnabled()
            && ("ADMIN".equals(user.getRole()) || "SUPER ADMIN".equals(user.getRole()))).count();
    }
}