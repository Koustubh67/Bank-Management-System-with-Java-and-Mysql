package com.koustubh.bank.web;

import com.koustubh.bank.domain.AccountStatus;
import com.koustubh.bank.domain.KycDocument;
import com.koustubh.bank.exception.BankException;
import com.koustubh.bank.service.AdminService;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.function.Consumer;

@Controller
@RequestMapping("/admin")
public class AdminController {

    private final AdminService admin;

    public AdminController(AdminService admin) {
        this.admin = admin;
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
    public String approve(@PathVariable Long id, RedirectAttributes redirect) {
        return act(id, admin::approve, "Account approved", redirect);
    }

    @PostMapping("/accounts/{id}/decline")
    public String decline(@PathVariable Long id, @RequestParam(required = false) String reason,
                          @RequestParam(required = false) String note, RedirectAttributes redirect) {
        String full = (reason == null ? "" : reason.trim())
                + (note == null || note.isBlank() ? "" : (reason == null || reason.isBlank() ? "" : ". ") + note.trim());
        return act(id, accountId -> admin.decline(accountId, full), "Application declined", redirect);
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
