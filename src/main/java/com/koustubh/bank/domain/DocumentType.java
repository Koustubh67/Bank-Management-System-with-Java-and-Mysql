package com.koustubh.bank.domain;

public enum DocumentType {
    PAN("PAN card"),
    AADHAAR("Aadhaar card");

    private final String label;

    DocumentType(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }
}
