package com.koustubh.bank.config;

import com.koustubh.bank.domain.AdminUser;
import com.koustubh.bank.domain.StaffRole;
import com.koustubh.bank.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;

/** Creates the first bank-staff login on startup if it does not exist yet. */
@Component
public class DataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final AdminUserRepository adminUsers;
    private final PasswordEncoder passwordEncoder;
    private final BankProperties properties;
    private final Clock clock;

    public DataSeeder(AdminUserRepository adminUsers, PasswordEncoder passwordEncoder, BankProperties properties,
                      Clock clock) {
        this.adminUsers = adminUsers;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public void run(ApplicationArguments args) {
        String username = properties.admin().username();
        if (adminUsers.findByUsername(username).isEmpty()) {
            adminUsers.save(new AdminUser(username, "Branch Manager", StaffRole.ADMIN,
                    passwordEncoder.encode(properties.admin().password()), false, LocalDateTime.now(clock)));
            log.info("Created admin user '{}'", username);
        }
    }
}
