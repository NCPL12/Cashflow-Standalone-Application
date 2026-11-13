package com.ncpl.sales.cashflow.config;

import com.ncpl.sales.security.User;
import com.ncpl.sales.security.UserRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the initial local administrator once, using the installer's credentials. */
@Component
@ConditionalOnProperty(name = "cashflow.standalone", havingValue = "true")
public class StandaloneAdministratorBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(StandaloneAdministratorBootstrap.class);
    private final UserRepo users;
    private final Environment environment;
    private volatile boolean ready;

    public StandaloneAdministratorBootstrap(UserRepo users, Environment environment) {
        this.users = users;
        this.environment = environment;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments arguments) {
        boolean reset = environment.getProperty("CASHFLOW_RESET_ADMIN", Boolean.class, false);
        if (users.count() != 0 && !reset) { ready = true; return; }
        String username = environment.getProperty("CASHFLOW_BOOTSTRAP_ADMIN_USERNAME", "").trim();
        String password = environment.getProperty("CASHFLOW_BOOTSTRAP_ADMIN_PASSWORD", "");
        if (username.isEmpty() || password.length() < 8) {
            throw new IllegalStateException("First-time setup is required. Run Install On This Laptop.cmd and create an administrator password of at least 8 characters.");
        }
        User admin = reset ? users.findUserByUserName(username) : null;
        if (admin == null) admin = new User();
        admin.setUsername(username);
        admin.setPassword(new BCryptPasswordEncoder().encode(password));
        admin.setRole("ADMIN");
        admin.setEnabled(true);
        admin.setName("Finance administrator");
        users.saveAndFlush(admin);
        ready = true;
        log.info("Standalone administrator created. Credentials are stored as a password hash.");
    }

    public boolean isReady() { return ready; }
}
