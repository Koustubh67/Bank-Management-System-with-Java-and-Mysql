package com.koustubh.bank.domain;

public enum LoanStatus {
    /** Waiting for a loan officer's decision. */
    APPLIED,
    /** Approved, disbursed, and being repaid by EMIs. */
    ACTIVE,
    REJECTED,
    /** Every EMI has been paid. */
    CLOSED
}
