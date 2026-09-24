package com.koustubh.bank.config;

import com.koustubh.bank.domain.StaffRole;
import com.koustubh.bank.repository.AdminUserRepository;
import com.koustubh.bank.service.CustomerLoginService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;

/**
 * Exactly two logins, both on the /login page:
 *  - Customers: Customer ID + password. This one login protects the dashboard, passbook, profile, ATM and UPI.
 *    The ATM then asks for the card PIN and UPI asks for the UPI PIN, like a real bank.
 *  - Bank staff: username + password, for the admin panel.
 * Only the home page, account opening, application tracking and the login page itself are public.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain staffChain(HttpSecurity http, AdminUserRepository adminUsers, PasswordEncoder encoder) throws Exception {
        UserDetailsService admins = username -> adminUsers.findByUsername(username)
                .map(a -> User.withUsername(a.getUsername()).password(a.getPasswordHash())
                        .roles(a.getRole() == StaffRole.ADMIN ? new String[]{"STAFF", "ADMIN"} : new String[]{"STAFF"})
                        .disabled(!a.isActive())
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(admins);
        provider.setPasswordEncoder(encoder);

        http.securityMatcher("/admin/**")
                .securityContext(ctx -> ctx.securityContextRepository(sessionRepository("STAFF_SECURITY_CONTEXT")))
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .requestMatchers("/admin/staff/**").hasRole("ADMIN")
                        .anyRequest().hasRole("STAFF"))
                .formLogin(form -> form
                        .loginPage("/login?as=staff")
                        .loginProcessingUrl("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/login?as=staff&error"))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .addLogoutHandler(clearBrowserCache())
                        .logoutSuccessUrl("/login?as=staff&logout"));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain customerChain(HttpSecurity http, CustomerLoginService customerLogin) throws Exception {
        AuthenticationProvider customerProvider = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                String customerId = customerLogin.verifyLogin(authentication.getName(),
                        String.valueOf(authentication.getCredentials()));
                return UsernamePasswordAuthenticationToken.authenticated(customerId, null,
                        AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };

        http.securityMatcher("/customer/**", "/atm/**", "/upi/**")
                .securityContext(ctx -> ctx.securityContextRepository(sessionRepository("CUSTOMER_SECURITY_CONTEXT")))
                .authenticationManager(new ProviderManager(customerProvider))
                .authorizeHttpRequests(auth -> auth.anyRequest().hasRole("CUSTOMER"))
                .formLogin(form -> form
                        .loginPage("/login")
                        .loginProcessingUrl("/customer/login")
                        .usernameParameter("customerId")
                        .passwordParameter("password")
                        // After "Invest now" on the public Invest page, go back to that fund instead of the dashboard
                        .defaultSuccessUrl("/customer", false)
                        .failureUrl("/login?error")
                        .permitAll())
                .logout(logout -> logout
                        .logoutUrl("/customer/logout")
                        .addLogoutHandler(clearBrowserCache())
                        .logoutSuccessUrl("/login?logout"));
        return http.build();
    }

    /**
     * After logout the browser is told to drop its cache, so pressing or swiping "back" can't show a cached
     * dashboard or profile. (Every signed-in page is also sent with Cache-Control: no-store, and the pages reload
     * themselves if the browser restores them from its back-forward cache.)
     */
    private static HeaderWriterLogoutHandler clearBrowserCache() {
        return new HeaderWriterLogoutHandler(new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.CACHE));
    }

    /** Staff and customer logins are stored under different session keys, so one never counts as the other. */
    private static HttpSessionSecurityContextRepository sessionRepository(String key) {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey(key);
        return repository;
    }

    @Bean
    @Order(3)
    SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
