package com.koustubh.bank.service;

import com.koustubh.bank.domain.Investment;
import com.koustubh.bank.domain.InvestmentType;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.market.NavPoint;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Chart data built from real NAV history: how a fund's NAV, a holding's value, or the whole portfolio's value moved
 * over a period. For SIPs the units held grow with each monthly instalment, so the "invested" line steps up too.
 */
@Service
public class ChartService {

    public enum Range {
        M1(1), M6(6), Y1(12), Y3(36), Y5(60), ALL(0);

        private final int months;

        Range(int months) {
            this.months = months;
        }

        public static Range parse(String value) {
            try {
                return valueOf(value == null ? "Y1" : value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new InvalidRequestException("Unknown chart range");
            }
        }
    }

    /** One point: value on a date, and how much had been invested by then (null for a fund's NAV chart). */
    public record Point(String date, BigDecimal value, BigDecimal invested) {
    }

    private static final int MAX_POINTS = 180;

    private final InvestmentService investments;
    private final MarketDataService market;
    private final Clock clock;

    public ChartService(InvestmentService investments, MarketDataService market, Clock clock) {
        this.investments = investments;
        this.market = market;
        this.clock = clock;
    }

    /** A fund's NAV over the range. */
    public List<Point> fund(long schemeCode, Range range) {
        LocalDate latest = market.latest(schemeCode).date();
        LocalDate from = range == Range.ALL ? LocalDate.of(1990, 1, 1) : latest.minusMonths(range.months);
        List<NavPoint> navs = thin(market.navsBetween(schemeCode, from, latest));
        return navs.stream().map(p -> new Point(p.date().toString(), p.nav(), null)).toList();
    }

    /** One holding's value over the range (never before it was bought). */
    @Transactional(readOnly = true)
    public List<Point> holding(String customerId, Long investmentId, Range range) {
        Investment inv = investments.holding(customerId, investmentId).investment();
        return series(List.of(inv), range);
    }

    /** The customer's whole portfolio (mutual funds and FDs together). */
    @Transactional(readOnly = true)
    public List<Point> portfolio(String customerId, Range range) {
        List<Investment> all = investments.portfolio(customerId).holdings().stream()
                .map(InvestmentService.Holding::investment).toList();
        return series(all, range);
    }

    private List<Point> series(List<Investment> list, Range range) {
        if (list.isEmpty()) {
            return List.of();
        }
        LocalDate today = LocalDate.now(clock);
        LocalDate firstStart = list.stream().map(Investment::getStartDate).min(LocalDate::compareTo).orElse(today);
        LocalDate from = range == Range.ALL ? firstStart : today.minusMonths(range.months);
        if (from.isBefore(firstStart)) {
            from = firstStart;
        }
        List<LocalDate> dates = sampleDates(from, today);
        // Units bought by each mutual fund instalment are worked out once, not again for every chart point
        Map<Investment, List<Purchase>> purchases = new IdentityHashMap<>();
        for (Investment inv : list) {
            if (inv.getType().isMutualFund()) {
                purchases.put(inv, purchases(inv));
            }
        }
        List<Point> points = new ArrayList<>();
        for (LocalDate d : dates) {
            BigDecimal value = BigDecimal.ZERO;
            BigDecimal invested = BigDecimal.ZERO;
            boolean any = false;
            for (Investment inv : list) {
                if (inv.getStartDate().isAfter(d)) {
                    continue;
                }
                BigDecimal[] vi = valueOn(inv, purchases.get(inv), d);
                value = value.add(vi[0]);
                invested = invested.add(vi[1]);
                any = true;
            }
            if (any) {
                points.add(new Point(d.toString(), value.setScale(2, RoundingMode.HALF_EVEN), invested));
            }
        }
        return points;
    }

    private record Purchase(LocalDate date, BigDecimal units) {
    }

    /** Each instalment (or the one-time purchase) and the units it bought at that day's real NAV. */
    private List<Purchase> purchases(Investment inv) {
        int count = inv.getType() == InvestmentType.SIP ? inv.getInstalmentsPaid() : 1;
        List<Purchase> result = new ArrayList<>();
        for (int m = 0; m < count; m++) {
            LocalDate buyDate = inv.getStartDate().plusMonths(m);
            result.add(new Purchase(buyDate, InvestmentService.units(inv.getAmount(),
                    market.navOn(inv.getSchemeCode(), buyDate).nav())));
        }
        return result;
    }

    /** {value, invested} of one investment on a date. */
    private BigDecimal[] valueOn(Investment inv, List<Purchase> purchases, LocalDate d) {
        if (inv.getType() == InvestmentType.FIXED_DEPOSIT) {
            return new BigDecimal[]{InvestmentService.accruedValue(inv, d), inv.getAmount()};
        }
        BigDecimal units = BigDecimal.ZERO;
        int bought = 0;
        for (Purchase p : purchases) {
            if (p.date().isAfter(d)) {
                break;
            }
            units = units.add(p.units());
            bought++;
        }
        BigDecimal nav = market.navOn(inv.getSchemeCode(), d).nav();
        return new BigDecimal[]{units.multiply(nav), inv.getAmount().multiply(BigDecimal.valueOf(bought))};
    }

    /** Evenly spaced dates from {@code from} to {@code to}, always including both ends. */
    private static List<LocalDate> sampleDates(LocalDate from, LocalDate to) {
        long days = Math.max(1, to.toEpochDay() - from.toEpochDay());
        long step = Math.max(1, (days + MAX_POINTS - 1) / MAX_POINTS);
        List<LocalDate> dates = new ArrayList<>();
        for (LocalDate d = from; d.isBefore(to); d = d.plusDays(step)) {
            dates.add(d);
        }
        dates.add(to);
        return dates;
    }

    private static List<NavPoint> thin(List<NavPoint> navs) {
        if (navs.size() <= MAX_POINTS) {
            return navs;
        }
        int step = (int) Math.ceil(navs.size() / (double) MAX_POINTS);
        List<NavPoint> result = new ArrayList<>();
        for (int i = 0; i < navs.size(); i += step) {
            result.add(navs.get(i));
        }
        if (!result.get(result.size() - 1).equals(navs.get(navs.size() - 1))) {
            result.add(navs.get(navs.size() - 1));
        }
        return result;
    }
}
