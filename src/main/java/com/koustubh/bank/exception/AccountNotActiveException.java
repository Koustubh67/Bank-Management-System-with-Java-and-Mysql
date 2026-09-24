package com.koustubh.bank.exception;

import com.koustubh.bank.domain.AccountStatus;

public class AccountNotActiveException extends BankException {
    public AccountNotActiveException(String accountNumber, AccountStatus status) {
        super(switch (status) {
            case PENDING -> "Account is awaiting approval by the bank";
            case DECLINED -> "This account application was declined";
            default -> "Account " + accountNumber + " is frozen. Please contact your branch";
        });
    }
}
