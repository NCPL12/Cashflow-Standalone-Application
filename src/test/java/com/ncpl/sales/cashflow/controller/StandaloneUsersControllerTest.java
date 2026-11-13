package com.ncpl.sales.cashflow.controller;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;
import com.ncpl.sales.security.User;
import com.ncpl.sales.security.UserRepo;
import com.ncpl.sales.security.UserService;

class StandaloneUsersControllerTest {
    private UserRepo users;
    private UserService service;
    private StandaloneUsersController controller;
    private RedirectAttributesModelMap flash;

    @BeforeEach void setup() {
        users = mock(UserRepo.class);
        service = mock(UserService.class);
        controller = new StandaloneUsersController(users, service);
        flash = new RedirectAttributesModelMap();
    }

    @Test void createsFinanceUserThroughPasswordEncodingService() {
        when(users.findAll()).thenReturn(Collections.emptyList());
        controller.create(" finance.person ", " Finance Person ", "SafePass123!", "CASHFLOW_USER", flash);
        verify(service).saveUser(argThat(user -> "finance.person".equals(user.getUsername())
            && "Finance Person".equals(user.getName()) && "CASHFLOW_USER".equals(user.getRole()) && user.isEnabled()));
        assertTrue(flash.getFlashAttributes().containsKey("message"));
    }

    @Test void rejectsCaseInsensitiveDuplicateAndInvalidRole() {
        User existing = user(1L, "Finance.Person", "CASHFLOW_USER");
        when(users.findAll()).thenReturn(Collections.singletonList(existing));
        controller.create("finance.person", "Person", "SafePass123!", "CASHFLOW_USER", flash);
        assertTrue(flash.getFlashAttributes().containsKey("error"));
        controller.create("another", "Person", "SafePass123!", "SUPER ADMIN", flash);
        verify(service, never()).saveUser(any());
    }

    @Test void administratorCannotDisableOwnAccount() {
        User administrator = user(1L, "admin", "ADMIN");
        when(users.findById(1L)).thenReturn(Optional.of(administrator));
        controller.setEnabled(1L, false, new UsernamePasswordAuthenticationToken("admin", ""), flash);
        assertTrue(administrator.isEnabled());
        verify(users, never()).save(any());
        assertTrue(flash.getFlashAttributes().get("error").toString().contains("own account"));
    }

    @Test void cannotDisableLastEnabledAdministrator() {
        User administrator = user(1L, "admin", "ADMIN");
        User finance = user(2L, "finance", "CASHFLOW_USER");
        when(users.findById(1L)).thenReturn(Optional.of(administrator));
        when(service.setUserEnabled(1L, false)).thenThrow(new IllegalArgumentException("Keep at least one enabled administrator."));
        controller.setEnabled(1L, false, new UsernamePasswordAuthenticationToken("another-admin", ""), flash);
        verify(users, never()).save(any());
        assertTrue(flash.getFlashAttributes().get("error").toString().contains("at least one"));
    }

    @Test void administratorCanDisableFinanceUser() {
        User finance = user(2L, "finance", "CASHFLOW_USER");
        when(users.findById(2L)).thenReturn(Optional.of(finance));
        when(service.setUserEnabled(2L, false)).thenAnswer(invocation -> { finance.setEnabled(false); return finance; });
        controller.setEnabled(2L, false, new UsernamePasswordAuthenticationToken("admin", ""), flash);
        assertFalse(finance.isEnabled());
        verify(service).setUserEnabled(2L, false);
    }

    @Test void passwordResetRejectsShortPasswords() {
        controller.resetPassword(2L, "short", flash);
        verify(service, never()).changePassword(any(), any());
        assertTrue(flash.getFlashAttributes().containsKey("error"));
    }

    @Test void userViewNeverExposesPasswordHash() {
        User administrator = user(1L, "admin", "ADMIN");
        administrator.setPassword("secret-hash");
        when(users.findAll()).thenReturn(Collections.singletonList(administrator));
        ExtendedModelMap model = new ExtendedModelMap();
        assertEquals("cashflow/users", controller.page(model));
        assertFalse(Arrays.stream(StandaloneUsersController.UserView.class.getMethods())
            .anyMatch(method -> method.getName().equals("getPassword")));
    }

    private static User user(Long id, String username, String role) {
        User user = new User(); user.setId(id); user.setUsername(username); user.setName(username);
        user.setRole(role); user.setEnabled(true); return user;
    }
}
