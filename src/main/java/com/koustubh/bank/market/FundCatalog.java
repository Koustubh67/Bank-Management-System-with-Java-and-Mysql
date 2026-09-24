package com.koustubh.bank.market;

import java.util.List;
import java.util.Optional;

/**
 * The real mutual fund schemes offered for SIP and lump-sum investing (Direct Plan, Growth).
 * Codes are AMFI scheme codes; names, categories and NAVs are fetched live.
 */
public final class FundCatalog {

    public record Listing(long schemeCode, String shortName, String tag, String risk) {
    }

    public static final List<Listing> FUNDS = List.of(
            new Listing(122639, "Parag Parikh Flexi Cap", "Flexi cap", "Very high"),
            new Listing(120716, "UTI Nifty 50 Index", "Index fund", "Very high"),
            new Listing(120586, "ICICI Prudential Large Cap", "Large cap", "Very high"),
            new Listing(125497, "SBI Small Cap", "Small cap", "Very high"),
            new Listing(135781, "Mirae Asset ELSS Tax Saver", "Tax saver (ELSS)", "Very high"),
            new Listing(120473, "Axis Gold Fund", "Gold", "High"),
            new Listing(119091, "HDFC Liquid Fund", "Liquid (debt)", "Low to moderate"));

    private FundCatalog() {
    }

    public static Optional<Listing> find(long schemeCode) {
        return FUNDS.stream().filter(f -> f.schemeCode() == schemeCode).findFirst();
    }
}
