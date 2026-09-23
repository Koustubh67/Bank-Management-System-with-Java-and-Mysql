package com.koustubh.bank.exception;

import com.koustubh.bank.domain.AccountStatus;

public class AccountNotActiveException extends BankException {
    public AccountNotActiveException(String accountNumber, AccountStatus status) {
        super(status == AccountStatus.PENDING
                ? "Account is awaiting approval by the bank"
                : "Account " + accountNumber + " is frozen. Please contact your branch");
    }
}
