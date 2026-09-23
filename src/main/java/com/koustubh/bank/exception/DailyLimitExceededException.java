package com.koustubh.bank.exception;

import java.math.BigDecimal;

public class DailyLimitExceededException extends BankException {
    public DailyLimitExceededException(BigDecimal remaining) {
        super("Daily withdrawal limit exceeded. You can withdraw up to Rs " + remaining.toPlainString() + " more today");
    }
}
