package com.koustubh.bank.config;

import com.koustubh.bank.repository.AdminUserRepository;
import com.koustubh.bank.service.CardSecurityService;
import com.koustubh.bank.service.UpiService;
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
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.Locale;

/**
 * Two separate logins:
 *  - ATM customers sign in at /atm/login with card number + PIN
 *  - Bank staff sign in at /admin/login with username + password
 *  - JavaPay UPI users sign in at /upi/login with UPI ID + 6-digit UPI PIN
 * Everything else (home page, account opening, CSS) is public.
 */
@Configuration
public class SecurityConfig {

    @Bean
    @Order(1)
    SecurityFilterChain adminChain(HttpSecurity http, AdminUserRepository adminUsers, PasswordEncoder encoder) throws Exception {
        UserDetailsService admins = username -> adminUsers.findByUsername(username)
                .map(a -> User.withUsername(a.getUsername()).password(a.getPasswordHash()).roles("ADMIN").build())
                .orElseThrow(() -> new UsernameNotFoundException(username));
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(admins);
        provider.setPasswordEncoder(encoder);

        http.securityMatcher("/admin/**")
                .securityContext(ctx -> ctx.securityContextRepository(sessionRepository("ADMIN_SECURITY_CONTEXT")))
                .authenticationManager(new ProviderManager(provider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/login").permitAll()
                        .anyRequest().hasRole("ADMIN"))
                .formLogin(form -> form
                        .loginPage("/admin/login")
                        .defaultSuccessUrl("/admin", true)
                        .failureUrl("/admin/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login?logout"));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain atmChain(HttpSecurity http, CardSecurityService cardSecurity) throws Exception {
        AuthenticationProvider cardPinProvider = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                String cardNumber = authentication.getName().replaceAll("\\s", "");
                cardSecurity.verifyLogin(cardNumber, String.valueOf(authentication.getCredentials()));
                return UsernamePasswordAuthenticationToken.authenticated(cardNumber, null,
                        AuthorityUtils.createAuthorityList("ROLE_CUSTOMER"));
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };

        http.securityMatcher("/atm/**")
                .securityContext(ctx -> ctx.securityContextRepository(sessionRepository("ATM_SECURITY_CONTEXT")))
                .authenticationManager(new ProviderManager(cardPinProvider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/atm/login").permitAll()
                        .anyRequest().hasRole("CUSTOMER"))
                .formLogin(form -> form
                        .loginPage("/atm/login")
                        .usernameParameter("cardNumber")
                        .passwordParameter("pin")
                        .defaultSuccessUrl("/atm", true)
                        .failureUrl("/atm/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/atm/logout")
                        .logoutSuccessUrl("/atm/login?logout"));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain upiChain(HttpSecurity http, UpiService upi) throws Exception {
        AuthenticationProvider upiPinProvider = new AuthenticationProvider() {
            @Override
            public Authentication authenticate(Authentication authentication) {
                String vpa = authentication.getName().trim().toLowerCase(Locale.ROOT);
                upi.verifyLogin(vpa, String.valueOf(authentication.getCredentials()));
                return UsernamePasswordAuthenticationToken.authenticated(vpa, null,
                        AuthorityUtils.createAuthorityList("ROLE_UPI"));
            }

            @Override
            public boolean supports(Class<?> authentication) {
                return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
            }
        };

        http.securityMatcher("/upi/**")
                .securityContext(ctx -> ctx.securityContextRepository(sessionRepository("UPI_SECURITY_CONTEXT")))
                .authenticationManager(new ProviderManager(upiPinProvider))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/upi/login", "/upi/register").permitAll()
                        .anyRequest().hasRole("UPI"))
                .formLogin(form -> form
                        .loginPage("/upi/login")
                        .usernameParameter("vpa")
                        .passwordParameter("pin")
                        .defaultSuccessUrl("/upi", true)
                        .failureUrl("/upi/login?error"))
                .logout(logout -> logout
                        .logoutUrl("/upi/logout")
                        .logoutSuccessUrl("/upi/login?logout"));
        return http.build();
    }

    /** Staff, ATM and UPI logins are stored under different session keys, so one never counts as the other. */
    private static HttpSessionSecurityContextRepository sessionRepository(String key) {
        HttpSessionSecurityContextRepository repository = new HttpSessionSecurityContextRepository();
        repository.setSpringSecurityContextKey(key);
        return repository;
    }

    @Bean
    @Order(4)
    SecurityFilterChain publicChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
