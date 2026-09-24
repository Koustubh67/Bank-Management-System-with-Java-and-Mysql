package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A bank staff login. New staff get a temporary password and must change it when they first log in. */
@Entity
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String username;
    private String passwordHash;
    private String fullName;

    @Enumerated(EnumType.STRING)
    private StaffRole role;

    private boolean active;
    private boolean mustChangePassword;
    private LocalDateTime createdAt;

    protected AdminUser() {
    }

    public AdminUser(String username, String fullName, StaffRole role, String passwordHash, boolean temporaryPassword,
                     LocalDateTime createdAt) {
        this.username = username;
        this.fullName = fullName;
        this.role = role;
        this.passwordHash = passwordHash;
        this.mustChangePassword = temporaryPassword;
        this.active = true;
        this.createdAt = createdAt;
    }

    public void changePassword(String newHash) {
        passwordHash = newHash;
        mustChangePassword = false;
    }

    /** Admin reset: a new temporary password that must be changed at the next login. */
    public void resetPassword(String temporaryHash) {
        passwordHash = temporaryHash;
        mustChangePassword = true;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public String getUsername() { return username; }
    public String getPasswordHash() { return passwordHash; }
    public String getFullName() { return fullName; }
    public StaffRole getRole() { return role; }
    public boolean isActive() { return active; }
    public boolean isMustChangePassword() { return mustChangePassword; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
