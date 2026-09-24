package com.koustubh.bank.web;

import com.koustubh.bank.domain.FdTenure;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.market.FundCatalog;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.market.MarketDataService.Backtest;
import com.koustubh.bank.market.MarketDataService.FundSnapshot;
import com.koustubh.bank.service.ChartService;
import com.koustubh.bank.service.ChartService.Point;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * The public Invest marketplace: anyone can browse real funds, live NAV charts and FD rates without logging in.
 * "Invest now" goes to the customer's own fund page, which asks for login first and then comes straight back.
 */
@Controller
public class MarketController {

    private final MarketDataService market;
    private final ChartService charts;

    public MarketController(MarketDataService market, ChartService charts) {
        this.market = market;
        this.charts = charts;
    }

    @GetMapping("/invest")
    public String explore(Model model) {
        List<FundSnapshot> funds = market.catalogue();
        model.addAttribute("funds", funds);
        model.addAttribute("groups", FundCatalog.Group.values());
        model.addAttribute("tenures", FdTenure.values());
        // Only funds that actually rose (or fell) on their last NAV
        model.addAttribute("gainers", top(funds.stream().filter(f -> f.change1d() != null && f.change1d().signum() > 0).toList(),
                FundSnapshot::change1d, true));
        model.addAttribute("losers", top(funds.stream().filter(f -> f.change1d() != null && f.change1d().signum() < 0).toList(),
                FundSnapshot::change1d, false));
        model.addAttribute("best5y", top(funds, FundSnapshot::return5y, true));
        model.addAttribute("taxSavers", funds.stream().filter(f -> f.listing().group() == FundCatalog.Group.TAX).toList());
        model.addAttribute("lowRisk", funds.stream().filter(f -> f.listing().group() == FundCatalog.Group.DEBT
                || f.listing().group() == FundCatalog.Group.HYBRID).toList());
        funds.stream().filter(f -> f.listing().schemeCode() == FundCatalog.NIFTY_50_TRACKER).findFirst()
                .ifPresent(f -> model.addAttribute("nifty", f));
        return "market/index";
    }

    @GetMapping("/invest/funds/{code}")
    public String fund(@PathVariable long code, Model model) {
        if (FundCatalog.find(code).isEmpty()) {
            return "redirect:/invest";
        }
        try {
            model.addAttribute("fund", market.snapshot(code));
        } catch (BankException e) {
            model.addAttribute("error", e.getMessage());
        }
        model.addAttribute("code", code);
        return "market/fund";
    }

    /** Public NAV chart data (read-only market data, no customer information). */
    @GetMapping("/market/api/funds/{code}")
    @ResponseBody
    public ResponseEntity<?> chart(@PathVariable long code, @RequestParam(required = false) String range) {
        try {
            market.snapshot(code);
            List<Point> points = charts.fund(code, ChartService.Range.parse(range));
            return ResponseEntity.ok(points);
        } catch (BankException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /** "What if I had invested" using the fund's real NAV history. */
    @GetMapping("/market/api/funds/{code}/backtest")
    @ResponseBody
    public ResponseEntity<?> backtest(@PathVariable long code, @RequestParam(defaultValue = "sip") String mode,
                                      @RequestParam(required = false) BigDecimal amount,
                                      @RequestParam(defaultValue = "3") int years) {
        try {
            if (FundCatalog.find(code).isEmpty()) {
                throw new BankException("This fund is not available on JavaBank");
            }
            Backtest result = market.backtest(code, mode, amount, years);
            return ResponseEntity.ok(result);
        } catch (BankException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    private static List<FundSnapshot> top(List<FundSnapshot> funds, Function<FundSnapshot, BigDecimal> by, boolean highest) {
        Comparator<FundSnapshot> order = Comparator.comparing(by, Comparator.nullsFirst(Comparator.naturalOrder()));
        return funds.stream().filter(f -> by.apply(f) != null)
                .sorted(highest ? order.reversed() : order).limit(3).toList();
    }
}
