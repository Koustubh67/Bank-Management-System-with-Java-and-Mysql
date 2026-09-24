package com.koustubh.bank.market;

import java.util.List;
import java.util.Optional;

/**
 * The real mutual fund schemes on JavaBank (all Direct Plan, Growth). Codes are AMFI scheme codes; names,
 * fund houses, categories and NAVs are fetched live from mfapi.in.
 */
public final class FundCatalog {

    /** Filter groups on the Invest page. */
    public enum Group {
        EQUITY("Equity"), INDEX("Index funds"), TAX("Tax saver"), HYBRID("Hybrid"), DEBT("Debt"), GOLD("Gold");

        private final String label;

        Group(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    public record Listing(long schemeCode, String shortName, String tag, String risk, Group group) {
    }

    public static final List<Listing> FUNDS = List.of(
            new Listing(122639, "Parag Parikh Flexi Cap", "Flexi cap", "Very high", Group.EQUITY),
            new Listing(118955, "HDFC Flexi Cap", "Flexi cap", "Very high", Group.EQUITY),
            new Listing(120586, "ICICI Prudential Large Cap", "Large cap", "Very high", Group.EQUITY),
            new Listing(119598, "SBI Large Cap", "Large cap", "Very high", Group.EQUITY),
            new Listing(118989, "HDFC Mid Cap", "Mid cap", "Very high", Group.EQUITY),
            new Listing(125497, "SBI Small Cap", "Small cap", "Very high", Group.EQUITY),
            new Listing(120828, "Quant Small Cap", "Small cap", "Very high", Group.EQUITY),
            new Listing(118778, "Nippon India Small Cap", "Small cap", "Very high", Group.EQUITY),
            new Listing(120716, "UTI Nifty 50 Index", "Index fund", "Very high", Group.INDEX),
            new Listing(120684, "ICICI Prudential Nifty Next 50 Index", "Index fund", "Very high", Group.INDEX),
            new Listing(135781, "Mirae Asset ELSS Tax Saver", "Tax saver (ELSS)", "Very high", Group.TAX),
            new Listing(120503, "Axis ELSS Tax Saver", "Tax saver (ELSS)", "Very high", Group.TAX),
            new Listing(118968, "HDFC Balanced Advantage", "Balanced advantage", "High", Group.HYBRID),
            new Listing(119091, "HDFC Liquid Fund", "Liquid (debt)", "Low to moderate", Group.DEBT),
            new Listing(119707, "SBI Gilt Fund", "Gilt (debt)", "Moderate", Group.DEBT),
            new Listing(120473, "Axis Gold Fund", "Gold", "High", Group.GOLD),
            new Listing(118663, "Nippon India Gold Savings", "Gold", "High", Group.GOLD));

    /** The fund whose NAV tracks the Nifty 50, used as the "market today" indicator on the Invest page. */
    public static final long NIFTY_50_TRACKER = 120716;

    private FundCatalog() {
    }

    public static Optional<Listing> find(long schemeCode) {
        return FUNDS.stream().filter(f -> f.schemeCode() == schemeCode).findFirst();
    }
}
