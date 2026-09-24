package com.koustubh.bank.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.math.BigDecimal;
import java.time.ZoneId;

@ConfigurationProperties(prefix = "bank")
public record BankProperties(ZoneId zone, Atm atm, Upi upi, Admin admin) {

    public record Atm(BigDecimal dailyWithdrawalLimit, int maxPinAttempts, BigDecimal maxDeposit) {
    }

    public record Upi(BigDecimal perTransactionLimit, BigDecimal dailyLimit, int maxPinAttempts) {
    }

    public record Admin(String username, String password) {
    }
}
