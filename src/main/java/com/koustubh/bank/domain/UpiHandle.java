package com.koustubh.bank.domain;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A UPI ID such as priya.3311@javabank, linked to one account and protected by a 6-digit UPI PIN. */
@Entity
public class UpiHandle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String vpa;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    /** BCrypt hash of the UPI PIN. */
    private String pinHash;

    private int failedAttempts;

    private boolean blocked;

    private LocalDateTime createdAt;

    protected UpiHandle() {
    }

    public UpiHandle(String vpa, Account account, String pinHash, LocalDateTime createdAt) {
        this.vpa = vpa;
        this.account = account;
        this.pinHash = pinHash;
        this.createdAt = createdAt;
    }

    public void registerFailedAttempt(int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            blocked = true;
        }
    }

    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    /** Setting a new PIN with the debit card also lifts a lock, as in real UPI apps. */
    public void resetPin(String newPinHash) {
        pinHash = newPinHash;
        unblock();
    }

    public void unblock() {
        blocked = false;
        failedAttempts = 0;
    }

    public Long getId() { return id; }
    public String getVpa() { return vpa; }
    public Account getAccount() { return account; }
    public String getPinHash() { return pinHash; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isBlocked() { return blocked; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
