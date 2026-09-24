package com.koustubh.bank.domain;

public enum StaffRole {
    /** Branch manager: everything, including adding and disabling staff. */
    ADMIN("Branch manager"),
    /** Bank officer: reviews applications, handles insurance requests and customer accounts. */
    OFFICER("Bank officer");

    private final String label;

    StaffRole(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
