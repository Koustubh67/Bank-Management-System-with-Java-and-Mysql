package com.koustubh.bank.domain;

public enum AccountStatus {
    /** Opened by the customer, waiting for a bank officer to approve it. */
    PENDING,
    ACTIVE,
    /** Blocked by a bank officer; no transactions or ATM logins allowed. */
    FROZEN,
    /** Application rejected by a bank officer during review. */
    DECLINED
}
