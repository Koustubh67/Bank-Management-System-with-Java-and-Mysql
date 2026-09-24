package com.koustubh.bank.domain;

import java.util.List;

/**
 * Insurance categories sold through an expert callback. {@code extraLabel} is the plan-specific detail the
 * customer gives (family members, nominee, vehicle or trip).
 */
public enum InsurancePlan {
    HEALTH("Health", "Cashless treatment at network hospitals for you and your family",
            List.of(500_000L, 1_000_000L, 2_500_000L, 5_000_000L),
            List.of("Hospitalisation & day-care procedures", "Pre & post hospitalisation (60/180 days)",
                    "Cashless at network hospitals", "Tax benefit under Section 80D"),
            "Who should be covered?", "e.g. Self, spouse, 2 children"),
    TERM_LIFE("Term Life", "A large cover at a small premium to protect your family",
            List.of(5_000_000L, 10_000_000L, 20_000_000L),
            List.of("Lump sum paid to your nominee", "Cover till age 60, 70 or 85",
                    "Optional critical illness rider", "Tax benefit under Section 80C"),
            "Nominee name & relation", "e.g. Priya Sharma, wife"),
    MOTOR("Motor", "Comprehensive car or two-wheeler cover with quick claims",
            List.of(300_000L, 600_000L, 1_000_000L),
            List.of("Own damage & third-party liability", "Zero-depreciation add-on",
                    "Cashless repairs at network garages", "24×7 roadside assistance"),
            "Vehicle number & model", "e.g. MP04 AB 1234, Hyundai i20"),
    TRAVEL("Travel", "Medical emergencies, trip delays and lost baggage, in India and abroad",
            List.of(2_500_000L, 5_000_000L),
            List.of("Emergency medical expenses abroad", "Trip delay & cancellation",
                    "Loss of baggage & passport", "Single or multi-trip plans"),
            "Destination & travel dates", "e.g. Singapore, 10–20 Dec");

    private final String label;
    private final String tagline;
    private final List<Long> covers;
    private final List<String> coverage;
    private final String extraLabel;
    private final String extraPlaceholder;

    InsurancePlan(String label, String tagline, List<Long> covers, List<String> coverage, String extraLabel,
                  String extraPlaceholder) {
        this.label = label;
        this.tagline = tagline;
        this.covers = covers;
        this.coverage = coverage;
        this.extraLabel = extraLabel;
        this.extraPlaceholder = extraPlaceholder;
    }

    public String getLabel() { return label; }
    public String getTagline() { return tagline; }
    public List<Long> getCovers() { return covers; }
    public List<String> getCoverage() { return coverage; }
    public String getExtraLabel() { return extraLabel; }
    public String getExtraPlaceholder() { return extraPlaceholder; }
}
