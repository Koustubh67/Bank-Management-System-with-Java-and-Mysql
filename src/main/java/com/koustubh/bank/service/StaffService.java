package com.koustubh.bank.service;

import com.koustubh.bank.domain.AdminUser;
import com.koustubh.bank.domain.StaffRole;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.repository.AdminUserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;

/**
 * Staff accounts. The branch manager (ADMIN) adds officers; each new officer gets a one-time temporary
 * password, logs in on the Bank staff tab, and must choose their own password before doing anything else.
 */
@Service
public class StaffService {

    public record Created(AdminUser user, String temporaryPassword) {
    }

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    private final AdminUserRepository staff;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom random;
    private final Clock clock;

    public StaffService(AdminUserRepository staff, PasswordEncoder passwordEncoder, SecureRandom random, Clock clock) {
        this.staff = staff;
        this.passwordEncoder = passwordEncoder;
        this.random = random;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<AdminUser> all() {
        return staff.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public AdminUser get(String username) {
        return staff.findByUsername(username).orElseThrow(() -> new NotFoundException("Staff member not found"));
    }

    @Transactional
    public Created create(String username, String fullName, StaffRole role) {
        String clean = username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
        if (!clean.matches("[a-z][a-z0-9.]{2,29}")) {
            throw new InvalidRequestException("Username must be 3-30 characters: lowercase letters, numbers or dots, starting with a letter");
        }
        if (fullName == null || fullName.isBlank() || fullName.trim().length() > 80) {
            throw new InvalidRequestException("Enter the staff member's full name");
        }
        if (role == null) {
            throw new InvalidRequestException("Choose a role");
        }
        if (staff.existsByUsername(clean)) {
            throw new InvalidRequestException("Username '" + clean + "' is already taken");
        }
        String temporary = temporaryPassword();
        AdminUser user = staff.save(new AdminUser(clean, fullName.trim(), role, passwordEncoder.encode(temporary), true,
                LocalDateTime.now(clock)));
        return new Created(user, temporary);
    }

    /** Sets a new temporary password; the staff member must change it at the next login. */
    @Transactional
    public String resetPassword(Long id, String actingUsername) {
        AdminUser user = byId(id);
        if (user.getUsername().equals(actingUsername)) {
            throw new InvalidRequestException("Use \"Change password\" to change your own password");
        }
        String temporary = temporaryPassword();
        user.resetPassword(passwordEncoder.encode(temporary));
        return temporary;
    }

    @Transactional
    public void setActive(Long id, boolean active, String actingUsername) {
        AdminUser user = byId(id);
        if (user.getUsername().equals(actingUsername)) {
            throw new InvalidRequestException("You can't disable your own login");
        }
        user.setActive(active);
    }

    @Transactional
    public void changeOwnPassword(String username, String current, String newPassword) {
        AdminUser user = get(username);
        if (current == null || !passwordEncoder.matches(current, user.getPasswordHash())) {
            throw new InvalidRequestException("Current password is incorrect");
        }
        CustomerLoginService.requireStrong(newPassword);
        if (passwordEncoder.matches(newPassword, user.getPasswordHash())) {
            throw new InvalidRequestException("New password must be different from the current one");
        }
        user.changePassword(passwordEncoder.encode(newPassword));
    }

    private AdminUser byId(Long id) {
        return staff.findById(id).orElseThrow(() -> new NotFoundException("Staff member not found"));
    }

    private String temporaryPassword() {
        StringBuilder sb = new StringBuilder("Jb");
        for (int i = 0; i < 8; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.append(random.nextInt(10)).toString();
    }
}
