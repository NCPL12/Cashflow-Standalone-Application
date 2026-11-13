package com.ncpl.sales.cashflow.config;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.context.SecurityContextPersistenceFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import com.ncpl.sales.security.LoginSuccessHandler;
import com.ncpl.sales.security.UserService;
import com.ncpl.sales.security.UserStatusFilter;
@Configuration @Order(1)
@ConditionalOnProperty(name = "cashflow.standalone", havingValue = "false", matchIfMissing = true)
public class CashflowSecurityConfig extends WebSecurityConfigurerAdapter {
    @Override protected void configure(HttpSecurity http) throws Exception {
        http.requestCache().requestCache(new NullRequestCache())
            .and().antMatcher("/cashflow-analyzer/**")
            .authorizeRequests()
            .antMatchers("/cashflow-analyzer/js/**", "/cashflow-analyzer/css/**", "/cashflow-analyzer/images/**").permitAll()
            .anyRequest().hasAnyAuthority("ADMIN", "SUPER ADMIN")
            .and().csrf()
            .and().exceptionHandling().authenticationEntryPoint((request,response,error) ->
                response.sendRedirect(request.getContextPath() + "/login"));
    }

    /** Isolates the shared Finance installation from the ERP routes and APIs. */
    @Configuration
    @Order(0)
    @ConditionalOnProperty(name = "cashflow.standalone", havingValue = "true")
    public static class StandaloneSecurityConfig extends WebSecurityConfigurerAdapter {
        @Autowired private UserService users;
        @Autowired private LoginSuccessHandler loginSuccessHandler;
        @Autowired private UserStatusFilter userStatusFilter;

        @Override protected void configure(AuthenticationManagerBuilder auth) throws Exception {
            auth.userDetailsService(users).passwordEncoder(new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder());
        }

        @Override protected void configure(HttpSecurity http) throws Exception {
            http.addFilterAfter(userStatusFilter, SecurityContextPersistenceFilter.class);
            http.requestCache().requestCache(new NullRequestCache())
                .and().authorizeRequests()
                .antMatchers("/login", "/login/**", "/cashflow-health", "/error", "/favicon.ico",
                    "/cashflow-analyzer/js/**", "/cashflow-analyzer/css/**", "/cashflow-analyzer/images/**",
                    "/css/**", "/js/**", "/images/**", "/resources/**").permitAll()
                .antMatchers("/cashflow-analyzer/users", "/cashflow-analyzer/users/**", "/api/users", "/api/users/**")
                    .hasAnyAuthority("ADMIN", "SUPER ADMIN")
                .antMatchers("/", "/cashflow-analyzer", "/cashflow-analyzer/**", "/logout")
                    .hasAnyAuthority("ADMIN", "SUPER ADMIN", "CASHFLOW_USER")
                .anyRequest().denyAll()
                .and().formLogin().loginPage("/login").successHandler(loginSuccessHandler)
                    .failureUrl("/login?error").permitAll()
                .and().logout().logoutUrl("/logout").logoutSuccessUrl("/login?logout").permitAll()
                .and().csrf();
        }
    }
}
