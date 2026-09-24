package com.koustubh.bank.market;

import com.koustubh.bank.exception.BankException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Live mutual fund data with a 6-hour cache. If the API is down, the last good copy is used; if there has never
 * been a good copy, investing in that fund is paused with a clear message instead of using made-up prices.
 */
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);
    /** AMFI publishes NAVs once a day; checking every 30 minutes picks up a new NAV soon after it's out. */
    private static final Duration TTL = Duration.ofMinutes(30);

    /** What the fund pages show. Returns are annualised (CAGR) for 3Y/5Y and simple for 1Y; null if too new. */
    public record FundSnapshot(FundCatalog.Listing listing, String schemeName, String fundHouse, String category,
                               BigDecimal nav, LocalDate navDate, BigDecimal return1y, BigDecimal return3y,
                               BigDecimal return5y, List<NavPoint> chart, BigDecimal change1d) {

        /** Up (or flat) since the previous NAV. */
        public boolean isUpToday() {
            return change1d == null || change1d.signum() >= 0;
        }
    }

    public static class MarketDataUnavailableException extends BankException {
        public MarketDataUnavailableException() {
            super("Live market prices are unavailable right now. Please try again in a few minutes");
        }
    }

    private record Cached(FundHistory history, Instant fetchedAt) {
    }

    private final NavSource source;
    private final Clock clock;
    private final Map<Long, Cached> cache = new ConcurrentHashMap<>();
    private final Executor fetchPool = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "nav-fetch");
        t.setDaemon(true);
        return t;
    });

    public MarketDataService(NavSource source, Clock clock) {
        this.source = source;
        this.clock = clock;
    }

    public FundHistory history(long schemeCode) {
        Cached cached = cache.get(schemeCode);
        if (cached != null && cached.fetchedAt().plus(TTL).isAfter(clock.instant())) {
            return cached.history();
        }
        try {
            FundHistory fresh = source.fetch(schemeCode);
            cache.put(schemeCode, new Cached(fresh, clock.instant()));
            return fresh;
        } catch (RuntimeException e) {
            log.warn("NAV fetch failed for scheme {}: {}", schemeCode, e.getMessage());
            if (cached != null) {
                return cached.history();
            }
            throw new MarketDataUnavailableException();
        }
    }

    /**
     * Snapshots for every fund in the catalogue that has data right now. Funds are fetched in parallel (a few at a
     * time, to be polite to the free API), so a cold cache fills in about a second instead of one fund at a time.
     */
    public List<FundSnapshot> catalogue() {
        List<CompletableFuture<FundSnapshot>> futures = FundCatalog.FUNDS.stream()
                .map(listing -> CompletableFuture.supplyAsync(() -> {
                    try {
                        return snapshot(listing.schemeCode());
                    } catch (BankException e) {
                        return null; // leave this fund out; the page says prices are unavailable if the list is empty
                    }
                }, fetchPool))
                .toList();
        return futures.stream().map(CompletableFuture::join).filter(Objects::nonNull).toList();
    }

    /** Result of "what if I had invested": real NAVs on each purchase date and today. */
    public record Backtest(String mode, BigDecimal amount, int years, LocalDate from, LocalDate to, BigDecimal invested,
                           BigDecimal value, BigDecimal gain, BigDecimal gainPercent, BigDecimal units) {
    }

    /**
     * A SIP of {@code amount} every month (or a one-time investment) started {@code years} ago, bought at the real
     * NAV on each date and valued at the latest NAV.
     */
    public Backtest backtest(long schemeCode, String mode, BigDecimal amount, int years) {
        if (amount == null || amount.compareTo(BigDecimal.valueOf(100)) < 0 || amount.compareTo(BigDecimal.valueOf(10_000_000)) > 0) {
            throw new BankException("Enter an amount between Rs 100 and Rs 1 crore");
        }
        if (years < 1 || years > 10) {
            throw new BankException("Choose between 1 and 10 years");
        }
        FundHistory h = history(schemeCode);
        NavPoint latest = h.navs().get(0);
        LocalDate start = latest.date().minusYears(years);
        NavPoint oldest = h.navs().get(h.navs().size() - 1);
        if (oldest.date().isAfter(start)) {
            throw new BankException("This fund doesn't have " + years + " years of history yet");
        }
        boolean sip = !"lumpsum".equalsIgnoreCase(mode);
        BigDecimal units = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;
        int purchases = sip ? years * 12 : 1;
        for (int m = 0; m < purchases; m++) {
            NavPoint nav = navOnOrBefore(h, start.plusMonths(m)).orElseThrow();
            units = units.add(amount.divide(nav.nav(), 4, RoundingMode.DOWN));
            invested = invested.add(amount);
        }
        BigDecimal value = units.multiply(latest.nav()).setScale(2, RoundingMode.HALF_EVEN);
        BigDecimal gain = value.subtract(invested);
        return new Backtest(sip ? "sip" : "lumpsum", amount, years, start, latest.date(), invested, value, gain,
                gain.multiply(BigDecimal.valueOf(100)).divide(invested, 2, RoundingMode.HALF_UP), units);
    }

    public FundSnapshot snapshot(long schemeCode) {
        FundCatalog.Listing listing = FundCatalog.find(schemeCode)
                .orElseThrow(() -> new BankException("This fund is not available on JavaBank"));
        FundHistory h = history(schemeCode);
        NavPoint latest = h.navs().get(0);
        return new FundSnapshot(listing, h.schemeName(), h.fundHouse(), h.category(), latest.nav(), latest.date(),
                simpleReturn(h, latest, 1), cagr(h, latest, 3), cagr(h, latest, 5), chart(h, latest.date()),
                dayChange(h));
    }

    /** NAV on the given day, or the last published NAV before it (markets are shut on weekends and holidays). */
    public NavPoint navOn(long schemeCode, LocalDate date) {
        return navOnOrBefore(history(schemeCode), date)
                .orElseThrow(() -> new BankException("No NAV is available for that date"));
    }

    public NavPoint latest(long schemeCode) {
        return history(schemeCode).navs().get(0);
    }

    private static Optional<NavPoint> navOnOrBefore(FundHistory h, LocalDate date) {
        return h.navs().stream().filter(p -> !p.date().isAfter(date)).findFirst();
    }

    /** Percentage change from the previous published NAV to the latest one (the fund's "today" move). */
    static BigDecimal dayChange(FundHistory h) {
        if (h.navs().size() < 2) {
            return null;
        }
        BigDecimal latest = h.navs().get(0).nav();
        BigDecimal previous = h.navs().get(1).nav();
        return latest.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 2, RoundingMode.HALF_UP);
    }

    /** NAVs between two dates (inclusive), oldest first. */
    public List<NavPoint> navsBetween(long schemeCode, LocalDate from, LocalDate to) {
        List<NavPoint> result = new ArrayList<>();
        for (NavPoint p : history(schemeCode).navs()) {   // newest first
            if (p.date().isAfter(to)) continue;
            if (p.date().isBefore(from)) break;
            result.add(p);
        }
        Collections.reverse(result);
        return result;
    }

    private static BigDecimal simpleReturn(FundHistory h, NavPoint latest, int years) {
        return navOnOrBefore(h, latest.date().minusYears(years))
                .map(then -> latest.nav().divide(then.nav(), MathContext.DECIMAL64).subtract(BigDecimal.ONE)
                        .multiply(BigDecimal.valueOf(100)).setScale(2, RoundingMode.HALF_UP))
                .orElse(null);
    }

    private static BigDecimal cagr(FundHistory h, NavPoint latest, int years) {
        return navOnOrBefore(h, latest.date().minusYears(years))
                .filter(then -> !then.date().isBefore(latest.date().minusYears(years).minusDays(10)))
                .map(then -> {
                    double growth = latest.nav().doubleValue() / then.nav().doubleValue();
                    return BigDecimal.valueOf((Math.pow(growth, 1.0 / years) - 1) * 100).setScale(2, RoundingMode.HALF_UP);
                })
                .orElse(null);
    }

    /** About one point a week for the last year, oldest first, for the price chart. */
    private static List<NavPoint> chart(FundHistory h, LocalDate latest) {
        List<NavPoint> year = h.navs().stream().filter(p -> p.date().isAfter(latest.minusYears(1))).toList();
        List<NavPoint> points = new ArrayList<>();
        for (int i = year.size() - 1; i >= 0; i -= 5) {
            points.add(year.get(i));
        }
        if (!year.isEmpty() && !points.get(points.size() - 1).equals(year.get(0))) {
            points.add(year.get(0));
        }
        return points;
    }
}
