package com.koustubh.bank.web;

import com.koustubh.bank.domain.Transaction;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.exception.InvalidRequestException;
import com.koustubh.bank.service.CustomerLoginService;
import com.koustubh.bank.service.CustomerService;
import com.koustubh.bank.service.CustomerService.PassbookFilter;
import com.koustubh.bank.service.InsuranceService;
import com.koustubh.bank.service.InvestmentService;
import com.koustubh.bank.service.LoanService;
import com.koustubh.bank.service.NotificationService;
import org.springframework.data.domain.Page;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/** The customer's net banking: dashboard, passbook and profile. The principal's name is the Customer ID. */
@Controller
@RequestMapping("/customer")
public class CustomerController {

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final CustomerService customers;
    private final CustomerLoginService customerLogin;
    private final Clock clock;
    private final InvestmentService investments;
    private final InsuranceService insurance;
    private final NotificationService notifications;
    private final LoanService loans;

    public CustomerController(CustomerService customers, CustomerLoginService customerLogin, Clock clock,
                              InvestmentService investments, InsuranceService insurance,
                              NotificationService notifications, LoanService loans) {
        this.loans = loans;
        this.investments = investments;
        this.insurance = insurance;
        this.notifications = notifications;
        this.customers = customers;
        this.customerLogin = customerLogin;
        this.clock = clock;
    }

    @GetMapping
    public String dashboard(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("portfolio", investments.portfolio(principal.getName()));
        model.addAttribute("policies", insurance.policiesOf(principal.getName()));
        model.addAttribute("insuranceRequests", insurance.requestsOf(principal.getName()).stream().filter(r -> r.isOpen()).toList());
        model.addAttribute("alerts", notifications.recentFor(customers.overview(principal.getName()).customer()));
        model.addAttribute("loans", loans.loansOf(principal.getName()));
        return "customer/dashboard";
    }

    @GetMapping("/passbook")
    public String passbook(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                           @RequestParam(defaultValue = "all") String type,
                           @RequestParam(defaultValue = "0") int page,
                           Principal principal, Model model) {
        PassbookFilter filter = filter(from, to, type);
        Page<Transaction> entries = customers.passbook(principal.getName(), filter, page, CustomerService.PAGE_SIZE);
        model.addAttribute("o", customers.overview(principal.getName()));
        model.addAttribute("entries", entries);
        model.addAttribute("from", filter.from());
        model.addAttribute("to", filter.to());
        model.addAttribute("type", type);
        return "customer/passbook";
    }

    /** Statement download for the selected period (up to 1,000 entries). */
    @GetMapping("/passbook.csv")
    public ResponseEntity<byte[]> passbookCsv(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "all") String type, Principal principal) {
        PassbookFilter filter = filter(from, to, type);
        StringBuilder csv = new StringBuilder("Date,Description,Reference,Debit,Credit,Balance\n");
        for (Transaction t : customers.passbook(principal.getName(), filter, 0, 1000)) {
            String description = t.getRemarks() != null ? t.getRemarks()
                    : t.getType().getLabel() + (t.getCounterpartyAccount() != null ? " A/c " + t.getCounterpartyAccount() : "");
            csv.append(t.getCreatedAt().format(CSV_TIME)).append(',')
                    .append(csvCell(description)).append(',')
                    .append(t.getReferenceId()).append(',')
                    .append(t.getType().isCredit() ? "" : t.getAmount().toPlainString()).append(',')
                    .append(t.getType().isCredit() ? t.getAmount().toPlainString() : "").append(',')
                    .append(t.getBalanceAfter().toPlainString()).append('\n');
        }
        String name = "javabank-statement-" + filter.from() + "-to-" + filter.to() + ".csv";
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + name + "\"")
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/profile")
    public String profile(Principal principal, Model model) {
        model.addAttribute("o", customers.overview(principal.getName()));
        return "customer/profile";
    }

    /** Updates the mobile number and email used for SMS and email alerts. */
    @PostMapping("/contact")
    public String updateContact(@RequestParam(required = false) String mobile, @RequestParam(required = false) String email,
                                Principal principal, RedirectAttributes redirect) {
        try {
            customers.updateContact(principal.getName(), mobile, email);
            redirect.addFlashAttribute("message", "Contact details updated");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/profile";
    }

    @PostMapping("/password")
    public String changePassword(@RequestParam String currentPassword, @RequestParam String newPassword,
                                 @RequestParam String confirmPassword, Principal principal,
                                 RedirectAttributes redirect) {
        try {
            if (!newPassword.equals(confirmPassword)) {
                throw new InvalidRequestException("New passwords do not match");
            }
            customerLogin.changePassword(principal.getName(), currentPassword, newPassword);
            redirect.addFlashAttribute("message", "Your password has been changed");
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/customer/profile#security";
    }

    /** Defaults to the last 30 days; the range is capped at one year. */
    private PassbookFilter filter(LocalDate from, LocalDate to, String type) {
        LocalDate end = to == null ? LocalDate.now(clock) : to;
        LocalDate start = from == null || from.isAfter(end) ? end.minusDays(30) : from;
        if (start.isBefore(end.minusYears(1))) {
            start = end.minusYears(1);
        }
        Boolean credit = switch (type) {
            case "credit" -> Boolean.TRUE;
            case "debit" -> Boolean.FALSE;
            default -> null;
        };
        return new PassbookFilter(start, end, credit);
    }

    /** Quotes a CSV value and stops spreadsheet formula injection (values starting with = + - @). */
    private static String csvCell(String value) {
        String safe = value.matches("^[=+\\-@].*") ? "'" + value : value;
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }
}
