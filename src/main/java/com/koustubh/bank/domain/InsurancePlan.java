package com.koustubh.bank.domain;

import java.util.List;

/** Demo insurance plans with the cover amounts a customer can choose. Premiums come from WealthService. */
public enum InsurancePlan {
    TERM_LIFE("Term Life", "Protect your family's future", List.of(2_500_000L, 5_000_000L, 10_000_000L), "Nominee name"),
    HEALTH("Health", "Cashless hospitalisation for you", List.of(500_000L, 1_000_000L, 2_500_000L), "Nominee name"),
    MOTOR("Motor", "Car insurance, comprehensive cover", List.of(300_000L, 600_000L, 1_000_000L), "Vehicle number"),
    TRAVEL("Travel", "Multi-trip cover for a year", List.of(500_000L, 1_000_000L), null);

    private final String label;
    private final String tagline;
    private final List<Long> covers;
    /** What the customer must enter for this plan (nominee or vehicle), or null. */
    private final String detailsLabel;

    InsurancePlan(String label, String tagline, List<Long> covers, String detailsLabel) {
        this.label = label;
        this.tagline = tagline;
        this.covers = covers;
        this.detailsLabel = detailsLabel;
    }

    public String getLabel() { return label; }
    public String getTagline() { return tagline; }
    public List<Long> getCovers() { return covers; }
    public String getDetailsLabel() { return detailsLabel; }
}
