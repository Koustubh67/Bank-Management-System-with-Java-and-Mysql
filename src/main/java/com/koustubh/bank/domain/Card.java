package com.koustubh.bank.domain;

import jakarta.persistence.*;

@Entity
public class Card {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String cardNumber;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    private Account account;

    /** BCrypt hash; the plain PIN is never stored. */
    private String pinHash;

    private int failedAttempts;

    private boolean blocked;

    protected Card() {
    }

    public Card(String cardNumber, Account account, String pinHash) {
        this.cardNumber = cardNumber;
        this.account = account;
        this.pinHash = pinHash;
    }

    /** Records a wrong PIN and blocks the card once the limit is reached. */
    public void registerFailedAttempt(int maxAttempts) {
        failedAttempts++;
        if (failedAttempts >= maxAttempts) {
            blocked = true;
        }
    }

    public void resetFailedAttempts() {
        failedAttempts = 0;
    }

    public void unblock() {
        blocked = false;
        failedAttempts = 0;
    }

    public void changePin(String newPinHash) {
        this.pinHash = newPinHash;
    }

    public Long getId() { return id; }
    public String getCardNumber() { return cardNumber; }
    public Account getAccount() { return account; }
    public String getPinHash() { return pinHash; }
    public int getFailedAttempts() { return failedAttempts; }
    public boolean isBlocked() { return blocked; }

    public String getMaskedNumber() {
        return "XXXX-XXXX-XXXX-" + cardNumber.substring(12);
    }
}
