package com.koustubh.bank.web;

import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.KycDocument;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.AdminService;
import com.koustubh.bank.service.InsuranceService;
import com.koustubh.bank.service.InvestmentService;
import com.koustubh.bank.service.NotificationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.security.Principal;
import java.time.LocalDate;
import java.util.function.Consumer;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;
    private final InsuranceService insurance;
    private final InvestmentService investments;
    private final NotificationService notifications;

    public AdminController(AdminService admin, InsuranceService insurance, InvestmentService investments,
                           NotificationService notifications) {
        this.admin = admin;
        this.insurance = insurance;
        this.investments = investments;
        this.notifications = notifications;
    }

    /** Staff log in on the shared /login page (Bank staff tab). */
    @GetMapping("/login")
    public String login() {
        return "redirect:/login?as=staff";
    }

    @GetMapping
    public String dashboard(Model model) {
        model.addAttribute("stats", admin.dashboard());
        model.addAttribute("pending", admin.accounts(AccountStatus.PENDING));
        return "admin/dashboard";
    }

    @GetMapping("/accounts")
    public String accounts(@RequestParam(required = false) AccountStatus status, Model model) {
        model.addAttribute("accounts", admin.accounts(status));
        model.addAttribute("status", status);
        model.addAttribute("statuses", AccountStatus.values());
        return "admin/accounts";
    }

    @GetMapping("/accounts/{id}")
    public String account(@PathVariable Long id, Model model) {
        model.addAttribute("details", admin.accountDetails(id));
        model.addAttribute("declineReasons", AdminService.DECLINE_REASONS);
        return "admin/account";
    }

    @PostMapping("/accounts/{id}/approve")
    public String approve(@PathVariable Long id, Principal staff, RedirectAttributes redirect) {
        return act(id, accountId -> admin.approve(accountId, staff.getName()),
                "Account approved. The customer has been notified by SMS and email", redirect);
    }

    @PostMapping("/accounts/{id}/decline")
    public String decline(@PathVariable Long id, @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String note, Principal staff, RedirectAttributes redirect) {
        String full = (reason == null ? "" : reason.trim())
                + (note == null || note.isBlank() ? "" : (reason == null || reason.isBlank() ? "" : ". ") + note.trim());
        return act(id, accountId -> admin.decline(accountId, full, staff.getName()),
                "Application declined. The customer has been notified by SMS and email", redirect);
    }

    /** Shows an uploaded KYC document. Only PDF, PNG and JPEG files are ever stored (checked by KycFiles). */
    @GetMapping("/documents/{id}")
    public ResponseEntity<byte[]> document(@PathVariable Long id) {
        KycDocument doc = admin.document(id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(doc.getContentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename(doc.getFileName()).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .cacheControl(CacheControl.noStore())
                .body(doc.getContent());
    }

    @PostMapping("/accounts/{id}/freeze")
    public String freeze(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::freeze, "Account frozen", redirect);
    }

    @PostMapping("/accounts/{id}/unfreeze")
    public String unfreeze(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::unfreeze, "Account unfrozen", redirect);
    }

    @PostMapping("/accounts/{id}/unblock-card")
    public String unblockCard(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::unblockCard, "Card unblocked", redirect);
    }

    @PostMapping("/accounts/{id}/unlock-login")
    public String unlockLogin(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::unlockLogin, "Net banking login unlocked", redirect);
    }

    @PostMapping("/accounts/{id}/unblock-upi")
    public String unblockUpi(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::unblockUpi, "UPI unlocked", redirect);
    }

    // ---------------------------------------------------------------- insurance requests

    @GetMapping("/insurance")
    public String insurance(Model model) {
        model.addAttribute("open", insurance.openRequests());
        model.addAttribute("recent", insurance.recentRequests());
        model.addAttribute("policies", insurance.allPolicies());
        return "admin/insurance";
    }

    @PostMapping("/insurance/{id}/contacted")
    public String contacted(@PathVariable Long id, @RequestParam(required = false) String note, Principal staff,
                            RedirectAttributes redirect) {
        return insuranceAction(() -> insurance.markContacted(id, staff.getName(), note), "Marked as contacted", redirect);
    }

    @PostMapping("/insurance/{id}/issue")
    public String issue(@PathVariable Long id, @RequestParam(required = false) String insurer,
                        @RequestParam(required = false) String policyNumber, @RequestParam(required = false) Long cover,
                        @RequestParam(required = false) BigDecimal premium,
                        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                        Principal staff, RedirectAttributes redirect) {
        return insuranceAction(() -> insurance.issue(id, staff.getName(),
                        new InsuranceService.Issue(insurer, policyNumber, cover, premium, startDate)),
                "Policy issued and first premium debited. The customer has been notified", redirect);
    }

    @PostMapping("/insurance/{id}/close")
    public String closeRequest(@PathVariable Long id, @RequestParam(required = false) String note, Principal staff,
                               RedirectAttributes redirect) {
        return insuranceAction(() -> insurance.close(id, staff.getName(), note), "Request closed", redirect);
    }

    private String insuranceAction(Runnable action, String success, RedirectAttributes redirect) {
        try {
            action.run();
            redirect.addFlashAttribute("message", success);
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/insurance";
    }

    // ---------------------------------------------------------------- investments and alerts

    @GetMapping("/investments")
    public String investments(Model model) {
        model.addAttribute("holdings", investments.allHoldings());
        return "admin/investments";
    }

    @PostMapping("/investments/run-sips")
    public String runSips(RedirectAttributes redirect) {
        int paid = investments.processDueSips();
        redirect.addFlashAttribute("message", paid == 0 ? "No SIP instalments were due" : paid + " SIP instalment(s) debited");
        return "redirect:/admin/investments";
    }

    @GetMapping("/notifications")
    public String notifications(Model model) {
        model.addAttribute("notifications", notifications.outbox());
        return "admin/notifications";
    }

    @GetMapping("/transactions")
    public String transactions(Model model) {
        model.addAttribute("transactions", admin.recentTransactions());
        return "admin/transactions";
    }

    private String act(Long id, Consumer<Long> action, String successMessage, RedirectAttributes redirect) {
        try {
            action.accept(id);
            redirect.addFlashAttribute("message", successMessage);
        } catch (BankException e) {
            redirect.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/accounts/" + id;
    }
}
