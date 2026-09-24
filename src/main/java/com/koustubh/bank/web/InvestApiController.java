package com.koustubh.bank.web;

import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.NotFoundException;
import com.koustubh.bank.market.MarketDataService;
import com.koustubh.bank.market.MarketDataService.MarketDataUnavailableException;
import com.koustubh.bank.service.ChartService;
import com.koustubh.bank.service.ChartService.Point;
import com.koustubh.bank.service.ChartService.Range;
import com.koustubh.bank.service.InvestmentService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * JSON for the interactive charts and the live updates on the investment pages (signed-in customers only;
 * a customer can only read their own holdings).
 */
@RestController
@RequestMapping("/customer/invest/api")
public class InvestApiController {

    public record Live(String asOf, BigDecimal value, BigDecimal invested, BigDecimal gain, BigDecimal gainPercent,
                       List<LiveHolding> holdings) {
    }

    public record LiveHolding(Long id, BigDecimal value, BigDecimal gain, BigDecimal gainPercent, BigDecimal nav,
                              String navDate, BigDecimal change1d) {
    }

    private final ChartService charts;
    private final InvestmentService investments;
    private final MarketDataService market;

    public InvestApiController(ChartService charts, InvestmentService investments, MarketDataService market) {
        this.charts = charts;
        this.investments = investments;
        this.market = market;
    }

    @GetMapping("/portfolio")
    public List<Point> portfolio(@RequestParam(required = false) String range, Principal principal) {
        return charts.portfolio(principal.getName(), Range.parse(range));
    }

    @GetMapping("/holdings/{id}")
    public List<Point> holding(@PathVariable Long id, @RequestParam(required = false) String range, Principal principal) {
        return charts.holding(principal.getName(), id, Range.parse(range));
    }

    @GetMapping("/funds/{code}")
    public List<Point> fund(@PathVariable long code, @RequestParam(required = false) String range) {
        market.snapshot(code); // only funds in the catalogue
        return charts.fund(code, Range.parse(range));
    }

    /** Latest values; the pages poll this every minute and animate any change. */
    @GetMapping("/live")
    public Live live(Principal principal) {
        InvestmentService.Portfolio p = investments.portfolio(principal.getName());
        List<LiveHolding> holdings = p.holdings().stream().map(h -> {
            BigDecimal change = null;
            if (h.investment().getSchemeCode() != null && h.priced()) {
                change = market.snapshot(h.investment().getSchemeCode()).change1d();
            }
            return new LiveHolding(h.investment().getId(), h.currentValue(), h.gain(), h.gainPercent(),
                    h.nav() == null ? null : h.nav().nav(), h.nav() == null ? null : h.nav().date().toString(), change);
        }).toList();
        return new Live(Instant.now().toString(), p.currentValue(), p.invested(), p.gain(), p.gainPercent(), holdings);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<Map<String, String>> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(MarketDataUnavailableException.class)
    public ResponseEntity<Map<String, String>> unavailable(MarketDataUnavailableException e) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(BankException.class)
    public ResponseEntity<Map<String, String>> bad(BankException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }
}
