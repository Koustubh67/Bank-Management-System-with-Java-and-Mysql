package com.koustubh.bank.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

public class TransferForm {

    @NotBlank
    @Pattern(regexp = "\\d{12}", message = "must be a 12-digit account number")
    private String toAccountNumber;

    @NotNull
    private BigDecimal amount;

    public String getToAccountNumber() { return toAccountNumber; }
    public void setToAccountNumber(String toAccountNumber) { this.toAccountNumber = toAccountNumber == null ? null : toAccountNumber.trim(); }
    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }
}
