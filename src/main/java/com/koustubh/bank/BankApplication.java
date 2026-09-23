package com.koustubh.bank;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.security.autoconfigure.UserDetailsServiceAutoConfiguration;

// Logins are handled in SecurityConfig, so Spring's default in-memory user is not needed.
@SpringBootApplication(exclude = UserDetailsServiceAutoConfiguration.class)
public class BankApplication {

	public static void main(String[] args) {
		SpringApplication.run(BankApplication.class, args);
	}

}
