package com.koustubh.bank.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.security.SecureRandom;
import java.time.Clock;

@Configuration
@EnableConfigurationProperties(BankProperties.class)
public class AppConfig {

    /** All timestamps and the "today" used for daily limits come from this clock, in the bank's time zone. */
    @Bean
    Clock clock(BankProperties properties) {
        return Clock.system(properties.zone());
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    SecureRandom secureRandom() {
        return new SecureRandom();
    }
}
