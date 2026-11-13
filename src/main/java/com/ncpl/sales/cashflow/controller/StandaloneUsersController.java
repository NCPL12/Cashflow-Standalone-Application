package com.ncpl.sales.cashflow.controller;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.ncpl.sales.security.User;
import com.ncpl.sales.security.UserRepo;
import com.ncpl.sales.security.UserService;

/** Local account administration for the Finance host; passwords never enter the view model. */
@Controller
@ConditionalOnProperty(name = "cashflow.standalone", havingValue = "true")
@PreAuthorize("hasAnyAuthority('ADMIN','SUPER ADMIN')")
public class StandaloneUsersController {
    private static final String PAGE = "redirect:/cashflow-analyzer/users";
    private final UserRepo users;
    private final UserService userService;

    public StandaloneUsersController(UserRepo users, UserService userService) {
        this.users = users;
        this.userService = userService;
    }

    @GetMapping("/cashflow-analyzer/users")
    public String page(Model model) {
        model.addAttribute("users", users.findAll().stream()
            .sorted(Comparator.comparing(User::getUsername, String.CASE_INSENSITIVE_ORDER))
            .map(UserView::new).collect(Collectors.toList()));
        return "cashflow/users";
    }

    @PostMapping("/cashflow-analyzer/users")
    public synchronized String create(@RequestParam String username, @RequestParam String name,
            @RequestParam String password, @RequestParam String role, RedirectAttributes flash) {
        try {
            String cleanUsername = username.trim();
            if (!cleanUsername.matches("[A-Za-z0-9._-]{3,64}")) {
                throw new IllegalArgumentException("Username must be 3-64 letters, numbers, dots, underscores or hyphens.");
            }
            String cleanName = name.trim();
            if (cleanName.isEmpty() || cleanName.length() > 100) {
                throw new IllegalArgumentException("Enter a name of 1-100 characters.");
            }
            validatePassword(password);
            if (!"ADMIN".equals(role) && !"CASHFLOW_USER".equals(role)) {
                throw new IllegalArgumentException("Choose Administrator or Finance user.");
            }
            if (users.findAll().stream().anyMatch(user -> cleanUsername.equalsIgnoreCase(user.getUsername()))) {
                throw new IllegalArgumentException("That username is already in use.");
            }
            User user = new User();
            user.setUsername(cleanUsername);
            user.setName(cleanName);
            user.setPassword(password);
            user.setRole(role);
            user.setEnabled(true);
            userService.saveUser(user);
            flash.addFlashAttribute("message", "User " + cleanUsername + " created. They can now sign in from another computer.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/cashflow-analyzer/users/{id}/enabled")
    public synchronized String setEnabled(@PathVariable Long id, @RequestParam boolean enabled,
            Authentication authentication, RedirectAttributes flash) {
        try {
            User user = users.findById(id).orElseThrow(() -> new IllegalArgumentException("User not found."));
            if (!enabled && user.getUsername().equals(authentication.getName())) {
                throw new IllegalArgumentException("You cannot disable your own account.");
            }
            user = userService.setUserEnabled(id, enabled);
            flash.addFlashAttribute("message", "User " + user.getUsername() + (enabled ? " enabled." : " disabled."));
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return PAGE;
    }

    @PostMapping("/cashflow-analyzer/users/{id}/password")
    public synchronized String resetPassword(@PathVariable Long id, @RequestParam String password,
            RedirectAttributes flash) {
        try {
            validatePassword(password);
            if (!users.existsById(id)) throw new IllegalArgumentException("User not found.");
            if (!userService.changePassword(id, password)) throw new IllegalArgumentException("User not found.");
            flash.addFlashAttribute("message", "Password updated. Give the new password to that user securely.");
        } catch (IllegalArgumentException ex) {
            flash.addFlashAttribute("error", ex.getMessage());
        }
        return PAGE;
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 8 || password.length() > 128 || password.trim().isEmpty()) {
            throw new IllegalArgumentException("Password must be 8-128 characters.");
        }
    }

public static final class UserView {
        private final Long id;
        private final String username;
        private final String name;
        private final String role;
        private final boolean enabled;
        UserView(User user) {
            id = user.getId(); username = user.getUsername(); name = user.getName();
            role = user.getRole(); enabled = user.isEnabled();
        }
        public Long getId() { return id; }
        public String getUsername() { return username; }
        public String getName() { return name; }
        public String getRole() { return role; }
        public boolean isEnabled() { return enabled; }
    }
}
